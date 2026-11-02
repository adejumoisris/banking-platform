package com.rova.banking;
import com.rova.banking.Models.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Service @Transactional
public class BankingService {
 @PersistenceContext EntityManager em;
 public Customer createCustomer(String name,String email) {
  Customer c=new Customer(); c.id=UUID.randomUUID(); c.fullName=name.trim();
  c.email=email.trim().toLowerCase(Locale.ROOT); c.kycStatus="PENDING"; c.createdAt=Instant.now();
  em.persist(c); return c;
 }
 public Customer verifyCustomer(UUID id) {
  Customer c=required(Customer.class,id,LockModeType.PESSIMISTIC_WRITE); c.kycStatus="VERIFIED"; return c;
 }
 public Account openAccount(UUID customerId,String currency) {
  Customer c=required(Customer.class,customerId,LockModeType.PESSIMISTIC_READ);
  if(!"VERIFIED".equals(c.kycStatus)) throw conflict("Customer KYC is not verified");
  if(!Set.of("NGN","USD","GBP","EUR").contains(currency)) throw conflict("Unsupported currency");
  Account a=new Account(); a.id=UUID.randomUUID(); a.customerId=customerId;
  a.currency=currency; a.balance=new BigDecimal("0.00"); a.status="ACTIVE"; em.persist(a); return a;
 }
 @Transactional(readOnly=true) public Account account(UUID id) { return required(Account.class,id,LockModeType.NONE); }
 @Transactional(readOnly=true) public List<LedgerEntry> ledger(UUID postingId) {
  return em.createQuery("select e from LedgerEntry e where e.postingId=:id",LedgerEntry.class).setParameter("id",postingId).getResultList();
 }
 public Posting post(String key,UUID source,UUID destination,BigDecimal amount,String kind) {
  if(key==null || !key.matches("[A-Za-z0-9._:-]{1,100}")) throw conflict("Invalid Idempotency-Key");
  money(amount);
  List<UUID> ids=new ArrayList<>(); if(source!=null) ids.add(source); if(destination!=null) ids.add(destination);
  if(ids.isEmpty() || (source!=null && source.equals(destination))) throw conflict("Invalid posting accounts");
  // Deterministic lock order avoids opposing transfers deadlocking.
  ids.sort(Comparator.naturalOrder()); Map<UUID,Account> locked=new HashMap<>();
  for(UUID id:ids) locked.put(id,required(Account.class,id,LockModeType.PESSIMISTIC_WRITE));
  List<Posting> previous=em.createQuery("select p from Posting p where p.idempotencyKey=:key",Posting.class).setParameter("key",key).getResultList();
  if(!previous.isEmpty()) {
   Posting p=previous.get(0);
   if(!Objects.equals(p.sourceId,source)||!Objects.equals(p.destinationId,destination)||p.amount.compareTo(amount)!=0||!p.kind.equals(kind))
    throw conflict("Idempotency key was used for a different request");
   return p;
  }
  Account from=locked.get(source), to=locked.get(destination);
  for(Account a:locked.values()) if(!"ACTIVE".equals(a.status)) throw conflict("Account is not active");
  if(from!=null && to!=null && !from.currency.equals(to.currency)) throw conflict("Cross-currency transfers require FX processing");
  if(from!=null && from.balance.compareTo(amount)<0) throw conflict("Insufficient funds");
  if(from!=null) from.balance=from.balance.subtract(amount);
  if(to!=null) to.balance=to.balance.add(amount);
  Posting p=new Posting(); p.id=UUID.randomUUID(); p.idempotencyKey=key; p.sourceId=source; p.destinationId=destination;
  p.amount=amount; p.currency=from!=null?from.currency:to.currency; p.kind=kind; p.createdAt=Instant.now(); em.persist(p);
  // Customer deposits are liabilities: debiting reduces them; crediting increases them.
  entry(p,from!=null?"CUSTOMER:"+from.id:(kind.equals("LOAN_DISBURSEMENT")?"LOAN_RECEIVABLE":"CASH_CLEARING"),"DEBIT");
  entry(p,to!=null?"CUSTOMER:"+to.id:(kind.equals("LOAN_REPAYMENT")?"LOAN_RECEIVABLE":"CASH_CLEARING"),"CREDIT");
  em.flush(); return p;
 }
 public Loan originate(UUID accountId,BigDecimal principal) {
  money(principal); required(Account.class,accountId,LockModeType.PESSIMISTIC_READ);
  Loan l=new Loan(); l.id=UUID.randomUUID(); l.accountId=accountId; l.principal=principal;
  l.outstanding=principal; l.status="PENDING"; em.persist(l); return l;
 }
 public Loan disburse(UUID id) {
  Loan l=required(Loan.class,id,LockModeType.PESSIMISTIC_WRITE);
  if("ACTIVE".equals(l.status)||"CLOSED".equals(l.status)) return l;
  post("loan-disbursement:"+id,null,l.accountId,l.principal,"LOAN_DISBURSEMENT"); l.status="ACTIVE"; return l;
 }
 public Posting repay(UUID id,String key,BigDecimal amount) {
  money(amount); Loan l=required(Loan.class,id,LockModeType.PESSIMISTIC_WRITE);
  if(key==null || !key.matches("[A-Za-z0-9._:-]{1,50}")) throw conflict("Invalid repayment key");
  String scoped="repay:"+id+":"+key;
  List<Posting> old=em.createQuery("select p from Posting p where p.idempotencyKey=:key",Posting.class).setParameter("key",scoped).getResultList();
  if(!old.isEmpty()) { if(old.get(0).amount.compareTo(amount)!=0 || !"LOAN_REPAYMENT".equals(old.get(0).kind) || !l.accountId.equals(old.get(0).sourceId) || old.get(0).destinationId!=null) throw conflict("Retry amount differs"); return old.get(0); }
  if(!"ACTIVE".equals(l.status)||amount.compareTo(l.outstanding)>0) throw conflict("Invalid repayment");
  Posting p=post(scoped,l.accountId,null,amount,"LOAN_REPAYMENT");
  l.outstanding=l.outstanding.subtract(amount); if(l.outstanding.signum()==0) l.status="CLOSED"; return p;
 }
 private void entry(Posting p,String account,String side) {
  LedgerEntry e=new LedgerEntry(); e.id=UUID.randomUUID(); e.postingId=p.id; e.ledgerAccount=account;
  e.side=side; e.amount=p.amount; e.currency=p.currency; em.persist(e);
 }
 private void money(BigDecimal n) { if(n==null||n.signum()<=0||n.scale()>2||n.compareTo(new BigDecimal("99999999999999999.99"))>0) throw conflict("Amount must be positive with at most two decimal places"); }
 private <T> T required(Class<T> type,UUID id,LockModeType lock) {
  T value=em.find(type,id,lock); if(value==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Resource not found"); return value;
 }
 private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
