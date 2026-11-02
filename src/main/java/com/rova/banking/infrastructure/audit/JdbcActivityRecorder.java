package com.rova.banking.infrastructure.audit;

import com.rova.banking.audit.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class JdbcActivityRecorder implements ActivityRecorder {
    private final JdbcTemplate jdbc;
    private final ActorProvider actors;
    private final Clock clock;
    private final ObjectMapper json;
    public JdbcActivityRecorder(JdbcTemplate jdbc, ActorProvider actors, Clock clock, ObjectMapper json) {
        this.jdbc = jdbc;
        this.actors = actors;
        this.clock = clock;
        this.json = json;
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, UUID aggregateId) {
        UUID eventId = UUID.randomUUID();
        var now = clock.instant();
        String payload;
        try {
            payload = json.writeValueAsString(Map.of("eventId", eventId, "eventType", action,
                    "aggregateId", aggregateId, "occurredAt", now, "schemaVersion", 1));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize business event", ex);
        }
        jdbc.update("insert into audit_event(id, actor, action, aggregate_id, request_id, created_at) values (?,?,?,?,?,?)",
                eventId, actors.currentActor(), action, aggregateId, MDC.get("requestId"), Timestamp.from(now));
        jdbc.update("insert into outbox_event(id, aggregate_id, event_type, payload, created_at, available_at, attempts) values (?,?,?,?,?,?,0)",
                eventId, aggregateId, action, payload, Timestamp.from(now), Timestamp.from(now));
    }
}
