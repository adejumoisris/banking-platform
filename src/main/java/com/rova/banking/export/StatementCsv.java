package com.rova.banking.export;

import com.rova.banking.model.AccountStatement;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Collectors;

/** CSV contains typed IDs, enums, timestamps and amounts, never user names or free text. */
public final class StatementCsv {
  private StatementCsv() {}

  public static String write(AccountStatement statement) {
    StringBuilder csv = new StringBuilder();
    row(
        csv,
        "record_type",
        "account_id",
        "currency",
        "occurred_at_utc",
        "reference",
        "type",
        "counterparty_account_id",
        "money_in",
        "money_out",
        "balance");
    row(
        csv,
        "OPENING_BALANCE",
        statement.accountId(),
        statement.currency(),
        statement.from(),
        null,
        null,
        null,
        null,
        null,
        statement.totals().openingBalance());
    for (var transaction : statement.transactions()) {
      row(
          csv,
          "TRANSACTION",
          statement.accountId(),
          statement.currency(),
          transaction.occurredAt(),
          transaction.reference(),
          transaction.type(),
          transaction.counterpartyAccountId(),
          transaction.moneyIn(),
          transaction.moneyOut(),
          null);
    }
    row(
        csv,
        "CLOSING_BALANCE",
        statement.accountId(),
        statement.currency(),
        statement.to(),
        null,
        null,
        null,
        statement.totals().totalMoneyIn(),
        statement.totals().totalMoneyOut(),
        statement.totals().closingBalance());
    return csv.toString();
  }

  private static void row(StringBuilder csv, Object... values) {
    csv.append(Arrays.stream(values).map(StatementCsv::cell).collect(Collectors.joining(",")))
        .append("\r\n");
  }

  private static String cell(Object value) {
    String text =
        value == null
            ? ""
            : value instanceof BigDecimal money ? money.toPlainString() : value.toString();
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
