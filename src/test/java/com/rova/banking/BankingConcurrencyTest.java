package com.rova.banking;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles({"local","test"})
class BankingConcurrencyTest {
 @Autowired BankingService service;
 private UUID account() {
  var c=service.createCustomer("Concurrent Test",UUID.randomUUID()+"@example.com");
  service.verifyCustomer(c.id); return service.openAccount(c.id,"NGN").id;
 }
 @Test void competingWithdrawalsCannotOverdraw() throws Exception {
  UUID account=account(); service.post(UUID.randomUUID().toString(),null,account,new BigDecimal("100.00"),"DEPOSIT");
  var gate=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
  Callable<Boolean> task=()-> { gate.await(); try {
   service.post(UUID.randomUUID().toString(),account,null,new BigDecimal("80.00"),"WITHDRAWAL"); return true;
  } catch(ResponseStatusException ex) { return false; } };
  try {
   var first=pool.submit(task); var second=pool.submit(task); gate.countDown();
   assertThat((first.get(15,TimeUnit.SECONDS)?1:0)+(second.get(15,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
   assertThat(service.account(account).balance).isEqualByComparingTo("20.00");
  } finally { pool.shutdownNow(); }
 }
 @Test void failedTransferRollsBackBothBalances() {
  UUID source=account(), destination=account();
  service.post(UUID.randomUUID().toString(),null,source,BigDecimal.ONE,"DEPOSIT");
  service.post(UUID.randomUUID().toString(),null,destination,new BigDecimal("99999999999999999.99"),"DEPOSIT");
  assertThatThrownBy(()->service.post(UUID.randomUUID().toString(),source,destination,BigDecimal.ONE,"TRANSFER")).isInstanceOf(RuntimeException.class);
  assertThat(service.account(source).balance).isEqualByComparingTo("1.00");
  assertThat(service.account(destination).balance).isEqualByComparingTo("99999999999999999.99");
 }
}
