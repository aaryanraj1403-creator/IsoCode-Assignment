package com.reconflow;
import java.math.*;
import java.time.*;
import java.util.*;

/** Pure domain calculations. No AI, database or HTTP dependencies. */
public final class Reconciliation {
 public enum Status { SETTLED, PENDING, SHORT_PAID, UNPAID_PAST_DUE, OVERPAID }
 public record RateCard(BigDecimal fixedFee, BigDecimal commissionPercent) {
  public RateCard { requireNonNegative(fixedFee); requireNonNegative(commissionPercent);
   if(commissionPercent.compareTo(new BigDecimal("100"))>0) throw new IllegalArgumentException("Commission exceeds 100%"); }
 }
 public record Result(BigDecimal expected, BigDecimal received, BigDecimal difference, BigDecimal moneyAtRisk, Status status) {}
 private static final BigDecimal TOLERANCE=new BigDecimal("10.00");
 private Reconciliation() {}
 private static void requireNonNegative(BigDecimal v) { if(v==null || v.signum()<0) throw new IllegalArgumentException("Non-negative amount required"); }
 public static BigDecimal money(BigDecimal v) { return Objects.requireNonNull(v).setScale(2,RoundingMode.HALF_UP); }
 public static BigDecimal expected(BigDecimal value, RateCard card, BigDecimal tcs, BigDecimal tds) {
  requireNonNegative(value); requireNonNegative(tcs); requireNonNegative(tds);
  BigDecimal fee=money(value.multiply(card.commissionPercent()).divide(new BigDecimal("100")));
  return money(value.subtract(fee).subtract(card.fixedFee()).subtract(tcs).subtract(tds));
 }
 /** Difference = received minus expected, including negative return adjustments. */
 public static Result reconcile(BigDecimal expected, BigDecimal received, LocalDate due, LocalDate asOf) {
  Objects.requireNonNull(due); Objects.requireNonNull(asOf);
  expected=money(expected); received=money(received);
  BigDecimal diff=money(received.subtract(expected));
  Status status;
  if(diff.abs().compareTo(TOLERANCE)<=0) status=Status.SETTLED;
  else if(diff.signum()>0) status=Status.OVERPAID;
  else if(!asOf.isAfter(due)) status=Status.PENDING;
  else if(received.signum()==0 && expected.signum()>0) status=Status.UNPAID_PAST_DUE;
  else status=Status.SHORT_PAID;
  BigDecimal risk=(status==Status.SHORT_PAID || status==Status.UNPAID_PAST_DUE)? diff.negate():new BigDecimal("0.00");
  return new Result(expected,received,diff,risk,status);
 }
 /** Aggregate split settlements in O(n), using partner + order as the identity. */
 public record Settlement(String partner,String orderId,String transactionId,BigDecimal amount) {}
 public record Key(String partner,String orderId) {}
 public static Map<Key,BigDecimal> aggregate(List<Settlement> rows) {
  Map<Key,BigDecimal> sums=new HashMap<>(); Set<String> transactions=new HashSet<>();
  for(Settlement row:rows) {
   if(row.partner()==null || row.partner().isBlank() || row.orderId()==null || row.orderId().isBlank() || row.transactionId()==null || row.transactionId().isBlank()) throw new IllegalArgumentException("Missing settlement identity");
   String identity=row.partner()+"\u0000"+row.transactionId();
   if(!transactions.add(identity)) throw new IllegalArgumentException("Duplicate transaction: "+row.transactionId());
   sums.merge(new Key(row.partner(),row.orderId()),money(row.amount()),BigDecimal::add);
  }
  return Map.copyOf(sums);
 }
}
