package com.reconflow;
import java.math.*;import java.time.*;import java.util.*;
public class DomainChecks {
 static BigDecimal b(String s){return new BigDecimal(s);} static int count=0;
 static void check(boolean ok){count++;if(!ok)throw new AssertionError("Check "+count);}
 public static void main(String[] args){
  LocalDate now=LocalDate.of(2026,11,3),past=now.minusDays(1);
  var a=Reconciliation.reconcile(b("1409"),b("0"),past,now);check(a.status()==Reconciliation.Status.UNPAID_PAST_DUE);check(a.moneyAtRisk().equals(b("1409.00")));
  check(Reconciliation.reconcile(b("899"),b("862"),past,now).difference().equals(b("-37.00")));
  check(Reconciliation.reconcile(b("-96"),b("-142"),past,now).moneyAtRisk().equals(b("46.00")));
  check(Reconciliation.reconcile(b("100"),b("90"),past,now).status()==Reconciliation.Status.SETTLED);
  check(Reconciliation.reconcile(b("100"),b("89.99"),past,now).status()==Reconciliation.Status.SHORT_PAID);
  check(Reconciliation.reconcile(b("100"),b("0"),now,now).status()==Reconciliation.Status.PENDING);
  check(Reconciliation.reconcile(b("100"),b("111"),past,now).status()==Reconciliation.Status.OVERPAID);
  check(Reconciliation.expected(b("1000"),new Reconciliation.RateCard(b("50"),b("10")),b("5"),b("1")).equals(b("844.00")));
  var rows=List.of(new Reconciliation.Settlement("A","1","t1",b("40")),new Reconciliation.Settlement("A","1","t2",b("60")),new Reconciliation.Settlement("B","1","t1",b("10")));
  check(Reconciliation.aggregate(rows).get(new Reconciliation.Key("A","1")).equals(b("100.00")));
  try{Reconciliation.aggregate(List.of(rows.get(0),rows.get(0)));throw new AssertionError();}catch(IllegalArgumentException e){check(true);}
  List<Reconciliation.Settlement> bulk=new ArrayList<>();for(int i=0;i<100000;i++)bulk.add(new Reconciliation.Settlement("A","O"+i,"T"+i,b("99.00")));
  long start=System.nanoTime();check(Reconciliation.aggregate(bulk).size()==100000);System.out.printf("%d checks passed; 100,000-row in-memory aggregation: %.3fs%n",count,(System.nanoTime()-start)/1e9);
 }
}
