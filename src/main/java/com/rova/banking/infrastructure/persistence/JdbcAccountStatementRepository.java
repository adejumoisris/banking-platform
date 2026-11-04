package com.rova.banking.infrastructure.persistence;

import com.rova.banking.domain.PostingKind;
import com.rova.banking.model.AccountStatement.Totals;
import com.rova.banking.model.AccountTransaction;
import com.rova.banking.repository.AccountStatementRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountStatementRepository implements AccountStatementRepository {
  private static final String JOIN = " from ledger_entry e join posting p on p.id=e.posting_id ";
  private static final String SCOPE = " where e.ledger_account=:account ";
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcAccountStatementRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private MapSqlParameterSource parameters(UUID accountId, Instant from, Instant to) {
    var parameters = new MapSqlParameterSource("account", "CUSTOMER:" + accountId);
    if (from != null) parameters.addValue("from", boundary(from));
    if (to != null) parameters.addValue("to", boundary(to));
    return parameters;
  }

  // Stored timestamps have microsecond precision. Ceiling both thresholds preserves >= / <
  // semantics for finer Instant inputs instead of letting JDBC round to a different interval.
  private Timestamp boundary(Instant value) {
    Instant truncated = value.truncatedTo(ChronoUnit.MICROS);
    return Timestamp.from(truncated.equals(value) ? value : truncated.plus(1, ChronoUnit.MICROS));
  }

  @Override
  public Totals summarize(UUID accountId, Instant from, Instant to) {
    String period = from == null ? "1=1" : "p.created_at>=:from";
    String opening =
        from == null
            ? "0"
            : "coalesce(sum(case when p.created_at<:from then case when e.side='CREDIT' then e.amount else -e.amount end else 0 end),0)";
    String sql =
        "select "
            + opening
            + " as opening_balance, "
            + "coalesce(sum(case when "
            + period
            + " and e.side='CREDIT' then e.amount else 0 end),0) as money_in, "
            + "coalesce(sum(case when "
            + period
            + " and e.side='DEBIT' then e.amount else 0 end),0) as money_out, "
            + "coalesce(sum(case when "
            + period
            + " then 1 else 0 end),0) as transaction_count"
            + JOIN
            + SCOPE
            + (to == null ? "" : " and p.created_at<:to");
    return jdbc.queryForObject(
        sql,
        parameters(accountId, from, to),
        (row, index) ->
            new Totals(
                row.getBigDecimal("opening_balance").setScale(2),
                row.getBigDecimal("money_in").setScale(2),
                row.getBigDecimal("money_out").setScale(2),
                row.getLong("transaction_count")));
  }

  @Override
  public List<AccountTransaction> transactions(
      UUID accountId, Instant from, Instant to, int offset, int limit, boolean chronological) {
    String direction = chronological ? " asc" : " desc";
    String sql =
        "select p.id as reference,p.created_at,p.kind, "
            + "case when e.side='DEBIT' then p.destination_id else p.source_id end as counterparty, "
            + "case when e.side='CREDIT' then e.amount else 0 end as money_in, "
            + "case when e.side='DEBIT' then e.amount else 0 end as money_out"
            + JOIN
            + SCOPE
            + (from == null ? "" : " and p.created_at>=:from")
            + (to == null ? "" : " and p.created_at<:to")
            + " order by p.created_at"
            + direction
            + ",p.id"
            + direction
            + ",e.id"
            + direction
            + " limit :limit offset :offset";
    var parameters =
        parameters(accountId, from, to).addValue("limit", limit).addValue("offset", offset);
    return jdbc.query(
        sql,
        parameters,
        (row, index) ->
            new AccountTransaction(
                row.getObject("reference", UUID.class), row.getTimestamp("created_at").toInstant(),
                PostingKind.valueOf(row.getString("kind")),
                    row.getObject("counterparty", UUID.class),
                row.getBigDecimal("money_in").setScale(2),
                    row.getBigDecimal("money_out").setScale(2)));
  }
}
