package com.rova.banking.infrastructure.persistence;

import com.rova.banking.model.FineractCommand;
import com.rova.banking.model.FineractCommand.Kind;
import com.rova.banking.model.FineractCommand.Status;
import com.rova.banking.repository.FineractCommandRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcFineractCommandRepository implements FineractCommandRepository {
  private final JdbcTemplate jdbc;

  public JdbcFineractCommandRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private FineractCommand read(ResultSet row, int index) throws SQLException {
    Timestamp lease = row.getTimestamp("lease_until");
    return new FineractCommand(
        row.getObject("id", UUID.class),
        row.getString("target_scope"),
        Kind.valueOf(row.getString("resource_kind")),
        row.getObject("local_id", UUID.class),
        row.getString("external_id"),
        row.getString("payload"),
        Status.valueOf(row.getString("status")),
        row.getInt("attempts"),
        row.getBoolean("create_attempted"),
        row.getObject("remote_id", Long.class),
        row.getObject("lease_token", UUID.class),
        lease == null ? null : lease.toInstant(),
        row.getTimestamp("available_at").toInstant(),
        row.getString("error_code"));
  }

  public Optional<FineractCommand> find(String scope, UUID id) {
    return jdbc
        .query(
            "select * from fineract_command where target_scope=? and id=?", this::read, scope, id)
        .stream()
        .findFirst();
  }

  public Optional<FineractCommand> find(String scope, Kind kind, UUID localId) {
    return jdbc
        .query(
            "select * from fineract_command where target_scope=? and resource_kind=? and local_id=?",
            this::read,
            scope,
            kind.name(),
            localId)
        .stream()
        .findFirst();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void insert(FineractCommand command, Instant now) {
    jdbc.update(
        "insert into fineract_command(id,target_scope,resource_kind,local_id,external_id,payload,status,attempts,create_attempted,available_at,created_at,updated_at) values (?,?,?,?,?,?,'PENDING',0,false,?,?,?)",
        command.id(),
        command.scope(),
        command.kind().name(),
        command.localId(),
        command.externalId(),
        command.payload(),
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now));
  }

  public List<UUID> ready(String scope, Instant now) {
    return jdbc.query(
        "select id from fineract_command where target_scope=? and available_at<=? and (status in ('PENDING','RETRY') or (status='PROCESSING' and lease_until<=?)) order by available_at,created_at limit 10",
        (row, index) -> row.getObject(1, UUID.class),
        scope,
        Timestamp.from(now),
        Timestamp.from(now));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public Optional<FineractCommand> claim(String scope, UUID id, Instant now) {
    UUID token = UUID.randomUUID();
    int changed =
        jdbc.update(
            "update fineract_command set status='PROCESSING', attempts=attempts+1, lease_token=?, lease_until=?, updated_at=? where target_scope=? and id=? and available_at<=? and (status in ('PENDING','RETRY') or (status='PROCESSING' and lease_until<=?))",
            token,
            Timestamp.from(now.plusSeconds(90)),
            Timestamp.from(now),
            scope,
            id,
            Timestamp.from(now),
            Timestamp.from(now));
    return changed == 1 ? find(scope, id) : Optional.empty();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public boolean prepareCreate(FineractCommand command, Instant now) {
    return jdbc.update(
            "update fineract_command set create_attempted=true,updated_at=? where id=? and target_scope=? and status='PROCESSING' and lease_token=? and lease_until>? and create_attempted=false",
            Timestamp.from(now),
            command.id(),
            command.scope(),
            command.leaseToken(),
            Timestamp.from(now))
        == 1;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public boolean finish(
      FineractCommand command,
      Status status,
      Long remoteId,
      String error,
      Instant next,
      Instant now) {
    return jdbc.update(
            "update fineract_command set status=?,remote_id=?,error_code=?,available_at=?,updated_at=?,lease_token=null,lease_until=null where id=? and target_scope=? and status='PROCESSING' and lease_token=?",
            status.name(),
            remoteId,
            error,
            Timestamp.from(next),
            Timestamp.from(now),
            command.id(),
            command.scope(),
            command.leaseToken())
        == 1;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean reconcile(String scope, UUID id, Instant now) {
    return jdbc.update(
            "update fineract_command set status='RETRY',attempts=0,available_at=?,updated_at=? where target_scope=? and id=? and status='REVIEW'",
            Timestamp.from(now),
            Timestamp.from(now),
            scope,
            id)
        == 1;
  }

  public com.rova.banking.model.PageResult<FineractCommand> list(
      String scope, String query, Status status, int page, int size) {
    String where = " where target_scope=?";
    java.util.List<Object> args = new java.util.ArrayList<>();
    args.add(scope);
    if (!query.isEmpty()) {
      where +=
          " and (cast(id as varchar) like ? escape '!' or cast(local_id as varchar) like ? escape '!' or external_id like ? escape '!')";
      String q =
          "%"
              + query
                  .toLowerCase(java.util.Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%";
      args.add(q);
      args.add(q);
      args.add(q);
    }
    if (status != null) {
      where += " and status=?";
      args.add(status.name());
    }
    long count =
        jdbc.queryForObject(
            "select count(*) from fineract_command" + where, Long.class, args.toArray());
    args.add(size);
    args.add(page * size);
    var rows =
        jdbc.query(
            "select * from fineract_command"
                + where
                + " order by created_at desc,id desc limit ? offset ?",
            this::read,
            args.toArray());
    return new com.rova.banking.model.PageResult<>(rows, page, size, count);
  }
}
