package com.reconflow;

import java.math.*;
import java.time.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FinancialRulesTest {
    static BigDecimal b(String s){return new BigDecimal(s);}
    final LocalDate today=LocalDate.of(2026,11,3);
    @Test void toleranceIsStrictAndSymmetric() {
        for(String amount:new String[]{"90","110","100"}) assertEquals(Reconciliation.Status.SETTLED,Reconciliation.reconcile(b("100"),b(amount),today.minusDays(1),today).status());
        assertEquals(Reconciliation.Status.SHORT_PAID,Reconciliation.reconcile(b("100"),b("89.99"),today.minusDays(1),today).status());
        assertEquals(Reconciliation.Status.OVERPAID,Reconciliation.reconcile(b("100"),b("110.01"),today,today).status());
    }
    @Test void dueTodayAndFutureArePending() {
        assertEquals(Reconciliation.Status.PENDING,Reconciliation.reconcile(b("100"),b("0"),today,today).status());
        assertEquals(b("0.00"),Reconciliation.reconcile(b("100"),b("0"),today.plusDays(1),today).moneyAtRisk());
    }
    @Test void taxesAndRounding() {
        assertEquals(b("844.00"),Reconciliation.expected(b("1000"),new Reconciliation.RateCard(b("50"),b("10")),b("5"),b("1")));
        assertEquals(b("0.99"),Reconciliation.expected(b("1.01"),new Reconciliation.RateCard(b("0"),b("1.5")),b("0"),b("0")));
        assertEquals(b("-1.01"),Reconciliation.money(b("-1.005")));
        assertThrows(IllegalArgumentException.class,()->new Reconciliation.RateCard(b("0"),b("100.01")));
        assertThrows(IllegalArgumentException.class,()->Reconciliation.expected(b("10"),new Reconciliation.RateCard(b("0"),b("0")),b("-1"),b("0")));
    }
    @Test void refundsAndNegativeAdjustments() {
        assertEquals(b("46.00"),Reconciliation.reconcile(b("-96"),b("-142"),today.minusDays(1),today).moneyAtRisk());
        assertEquals(Reconciliation.Status.SETTLED,Reconciliation.reconcile(b("-96"),b("-96"),today.minusDays(1),today).status());
    }
}
