package com.rova.banking;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles({"local","test"}) @Transactional
class BankingServiceTest {
 @Autowired BankingService service;
 private UUID account(String currency) {
  var c=service.createCustomer("Test Customer",UUID.randomUUID()+"@example.com");
  service.verifyCustomer(c.id); return service.openAccount(c.id,currency).id;
 }
 @Test void unverifiedCustomerCannotOpenAccount() {
  var c=service.createCustomer("Pending",UUID.randomUUID()+"@example.com");
  assertThatThrownBy(()->service.openAccount(c.id,"NGN")).isInstanceOf(ResponseStatusException.class);
 }
 @Test void transferIsBalancedAndRetryDoesNotMoveMoneyTwice() {
  var a=account("NGN"); var b=account("NGN");
  service.post("fund",null,a,new BigDecimal("100.00"),"DEPOSIT");
  var p=service.post("transfer",a,b,new BigDecimal("35.00"),"TRANSFER");
  assertThat(service.post("transfer",a,b,new BigDecimal("35.00"),"TRANSFER").id).isEqualTo(p.id);
  assertThat(service.account(a).balance).isEqualByComparingTo("65.00");
  assertThat(service.account(b).balance).isEqualByComparingTo("35.00");
  var entries=service.ledger(p.id); assertThat(entries).hasSize(2);
  assertThat(entries.stream().filter(e->e.side.equals("DEBIT")).map(e->e.amount).reduce(BigDecimal.ZERO,BigDecimal::add))
   .isEqualByComparingTo(entries.stream().filter(e->e.side.equals("CREDIT")).map(e->e.amount).reduce(BigDecimal.ZERO,BigDecimal::add));
 }
 @Test void rejectsInsufficientFundsAndCurrencyMismatch() {
  var a=account("NGN"); var b=account("USD");
  assertThatThrownBy(()->service.post("empty",a,null,BigDecimal.ONE,"WITHDRAWAL")).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.post("fx",a,b,BigDecimal.ONE,"TRANSFER")).isInstanceOf(ResponseStatusException.class);
 }
 @Test void rejectsChangedRetryAndFractionalMinorUnits() {
  var a=account("NGN"); service.post("one",null,a,BigDecimal.ONE,"DEPOSIT");
  assertThatThrownBy(()->service.post("one",null,a,BigDecimal.TEN,"DEPOSIT")).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.post("fraction",null,a,new BigDecimal("0.001"),"DEPOSIT")).isInstanceOf(ResponseStatusException.class);
 }
 @Test void loanDisbursesAndRepaysExactlyOnce() {
  var a=account("NGN"); var l=service.originate(a,new BigDecimal("100.00"));
  service.disburse(l.id); service.disburse(l.id);
  assertThat(service.account(a).balance).isEqualByComparingTo("100.00");
  var p=service.repay(l.id,"final",new BigDecimal("100.00"));
  assertThat(service.repay(l.id,"final",new BigDecimal("100.00")).id).isEqualTo(p.id);
  assertThat(l.status).isEqualTo("CLOSED"); assertThat(service.account(a).balance).isEqualByComparingTo("0.00");
 }
}
