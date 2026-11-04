package com.rova.banking.infrastructure.persistence;

import com.rova.banking.model.PageResult;
import com.rova.banking.model.StaffDirectory.*;
import com.rova.banking.repository.StaffDirectoryRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStaffDirectoryRepository implements StaffDirectoryRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcStaffDirectoryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private String term(String query) {
    return "%"
        + query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }

  private <T> PageResult<T> page(
      String select,
      String from,
      String where,
      String order,
      MapSqlParameterSource parameters,
      int page,
      int size,
      RowMapper<T> mapper) {
    long total = jdbc.queryForObject("select count(*) " + from + where, parameters, Long.class);
    parameters.addValue("limit", size).addValue("offset", page * size);
    return new PageResult<>(
        jdbc.query(
            select + from + where + order + " limit :limit offset :offset", parameters, mapper),
        page,
        size,
        total);
  }

  public PageResult<CustomerRow> customers(String query, String status, int page, int size) {
    var p = new MapSqlParameterSource();
    String where = " where 1=1";
    if (!query.isEmpty()) {
      where +=
          " and (lower(c.full_name) like :q escape '!' or lower(c.email) like :q escape '!' or cast(c.id as varchar) like :q escape '!')";
      p.addValue("q", term(query));
    }
    if (!status.isEmpty()) {
      where += " and c.kyc_status=:status";
      p.addValue("status", status);
    }
    return page(
        "select c.*, (select count(*) from bank_account a where a.customer_id=c.id) as account_count",
        " from customer c",
        where,
        " order by c.created_at desc,c.id desc",
        p,
        page,
        size,
        (r, i) ->
            new CustomerRow(
                r.getObject("id", UUID.class),
                r.getString("full_name"),
                r.getString("email"),
                r.getString("kyc_status"),
                r.getTimestamp("created_at").toInstant(),
                r.getLong("account_count")));
  }

  public PageResult<AccountRow> accounts(
      String query, UUID customerId, String currency, String status, int page, int size) {
    var p = new MapSqlParameterSource();
    String where = " where 1=1";
    if (!query.isEmpty()) {
      where +=
          " and (lower(c.full_name) like :q escape '!' or lower(c.email) like :q escape '!' or cast(a.id as varchar) like :q escape '!')";
      p.addValue("q", term(query));
    }
    if (customerId != null) {
      where += " and a.customer_id=:customer";
      p.addValue("customer", customerId);
    }
    if (!currency.isEmpty()) {
      where += " and a.currency=:currency";
      p.addValue("currency", currency);
    }
    if (!status.isEmpty()) {
      where += " and a.status=:status";
      p.addValue("status", status);
    }
    return page(
        "select a.*,c.full_name",
        " from bank_account a join customer c on c.id=a.customer_id",
        where,
        " order by c.full_name,a.id",
        p,
        page,
        size,
        (r, i) ->
            new AccountRow(
                r.getObject("id", UUID.class),
                r.getObject("customer_id", UUID.class),
                r.getString("full_name"),
                r.getString("currency"),
                r.getBigDecimal("balance"),
                r.getString("status")));
  }

  public PageResult<LoanRow> loans(
      String query, UUID accountId, String status, int page, int size) {
    var p = new MapSqlParameterSource();
    String where = " where 1=1";
    if (!query.isEmpty()) {
      where +=
          " and (lower(c.full_name) like :q escape '!' or cast(l.id as varchar) like :q escape '!' or cast(a.id as varchar) like :q escape '!')";
      p.addValue("q", term(query));
    }
    if (accountId != null) {
      where += " and l.account_id=:account";
      p.addValue("account", accountId);
    }
    if (!status.isEmpty()) {
      where += " and l.status=:status";
      p.addValue("status", status);
    }
    return page(
        "select l.*,a.customer_id,a.currency,c.full_name",
        " from loan l join bank_account a on a.id=l.account_id join customer c on c.id=a.customer_id",
        where,
        " order by l.id desc",
        p,
        page,
        size,
        (r, i) ->
            new LoanRow(
                r.getObject("id", UUID.class),
                r.getObject("account_id", UUID.class),
                r.getObject("customer_id", UUID.class),
                r.getString("full_name"),
                r.getString("currency"),
                r.getBigDecimal("principal"),
                r.getBigDecimal("outstanding"),
                r.getString("status")));
  }

  public Dashboard dashboard(Instant now) {
    var empty = new MapSqlParameterSource();
    var counts =
        jdbc.queryForMap(
            "select (select count(*) from customer) as customers, (select count(*) from customer where kyc_status='VERIFIED') as verified, "
                + "(select count(*) from customer where kyc_status='PENDING') as pending, (select count(*) from bank_account) as accounts, "
                + "(select count(*) from bank_account where status='ACTIVE') as active_accounts, (select count(*) from loan) as loans, "
                + "(select count(*) from loan where status='ACTIVE') as active_loans",
            empty);
    var balances =
        jdbc.query(
            "select currency,sum(balance) as balance,count(*) as accounts from bank_account group by currency order by currency",
            empty,
            (r, i) ->
                new CurrencyBalance(
                    r.getString("currency"),
                    r.getBigDecimal("balance").setScale(2),
                    r.getLong("accounts")));
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    List<ActivityDay> activity = new ArrayList<>();
    for (int day = 6; day >= 0; day--) {
      LocalDate date = today.minusDays(day);
      var p =
          new MapSqlParameterSource(
                  "from", Timestamp.from(date.atStartOfDay().toInstant(ZoneOffset.UTC)))
              .addValue(
                  "to", Timestamp.from(date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
      long count =
          jdbc.queryForObject(
              "select count(*) from posting where created_at>=:from and created_at<:to",
              p,
              Long.class);
      activity.add(new ActivityDay(date, count));
    }
    var recent =
        jdbc.query(
            "select p.*,sc.full_name as source_name,dc.full_name as destination_name from posting p "
                + "left join bank_account sa on sa.id=p.source_id left join customer sc on sc.id=sa.customer_id "
                + "left join bank_account da on da.id=p.destination_id left join customer dc on dc.id=da.customer_id "
                + "order by p.created_at desc,p.id desc limit 8",
            empty,
            (r, i) ->
                new PostingRow(
                    r.getObject("id", UUID.class),
                    r.getString("kind"),
                    r.getBigDecimal("amount"),
                    r.getString("currency"),
                    r.getTimestamp("created_at").toInstant(),
                    r.getObject("source_id", UUID.class),
                    r.getObject("destination_id", UUID.class),
                    r.getString("source_name"),
                    r.getString("destination_name")));
    return new Dashboard(
        ((Number) counts.get("customers")).longValue(),
        ((Number) counts.get("verified")).longValue(),
        ((Number) counts.get("pending")).longValue(),
        ((Number) counts.get("accounts")).longValue(),
        ((Number) counts.get("active_accounts")).longValue(),
        ((Number) counts.get("loans")).longValue(),
        ((Number) counts.get("active_loans")).longValue(),
        activity.get(6).count(),
        balances,
        List.copyOf(activity),
        recent,
        now);
  }
}
