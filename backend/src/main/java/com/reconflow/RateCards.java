package com.reconflow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/rate-cards")
class RateCards {
    final Support s;
    final ReconcileService engine;
    RateCards(Support s,ReconcileService engine) { this.s=s;this.engine=engine; }
    record Rate(String partner,String kind,LocalDate effectiveFrom,LocalDate effectiveTo,BigDecimal commission,
        BigDecimal fixedFee,BigDecimal returnFee,int settlementDays,Long supersedeId) {}
    @GetMapping List<Map<String,Object>> list() { return s.db.queryForList("SELECT * FROM rate_card ORDER BY partner,kind,effective_from DESC"); }

    @PostMapping @PreAuthorize("hasRole('FOUNDER')") @Transactional
    Map<String,Object> save(@RequestBody Rate r,Authentication a) {
        Support.require(r.partner()!=null && !r.partner().isBlank() && r.partner().length()<=100,"Partner required (max 100 characters)");
        Support.require(r.kind()!=null && Set.of("CHANNEL","COURIER").contains(r.kind()),"Kind must be CHANNEL or COURIER");
        Support.require(r.effectiveFrom()!=null && r.effectiveTo()!=null && !r.effectiveTo().isBefore(r.effectiveFrom()),"Invalid effective dates");
        new Reconciliation.RateCard(r.fixedFee(),r.commission());
        Support.require(!r.kind().equals("COURIER") || r.commission().signum()==0,"Courier cards use flat fees; commission must be zero");
        Support.require(r.commission().scale()<=4,"Commission supports four decimal places");
        Support.require(r.fixedFee().scale()<=2 && r.returnFee()!=null && r.returnFee().signum()>=0 && r.returnFee().scale()<=2,"Fees require nonnegative amounts with at most two decimal places");
        Support.require(r.settlementDays()>=0 && r.settlementDays()<=365,"Settlement days must be 0..365");
        Support.require(r.partner().equals(r.partner().trim()),"Partner must not contain surrounding spaces");
        Support.require(r.fixedFee().precision()-r.fixedFee().scale()<=12 && r.returnFee().precision()-r.returnFee().scale()<=12,"Fee exceeds supported amount");
        s.db.execute("SELECT pg_advisory_xact_lock(721941)");
        if(r.supersedeId()!=null) {
            var old=s.one("SELECT * FROM rate_card WHERE id=? FOR UPDATE",r.supersedeId());
            Support.require(old.get("partner").equals(r.partner()) && old.get("kind").equals(r.kind()),"Replacement must use the same partner and kind");
            LocalDate from=((java.sql.Date)old.get("effective_from")).toLocalDate();
            LocalDate to=((java.sql.Date)old.get("effective_to")).toLocalDate();
            Support.require(r.effectiveFrom().isAfter(from) && !r.effectiveFrom().isAfter(to),"Replacement must start within the old period, after its first day");
            s.db.update("UPDATE rate_card SET effective_to=? WHERE id=?",r.effectiveFrom().minusDays(1),r.supersedeId());
        }
        long overlaps=s.db.queryForObject("SELECT count(*) FROM rate_card WHERE partner=? AND kind=? AND effective_from<=? AND effective_to>=?",Long.class,r.partner(),r.kind(),r.effectiveTo(),r.effectiveFrom());
        Support.require(overlaps==0,"Effective periods overlap an existing rate card (dates are inclusive)");
        Long id=s.db.queryForObject("INSERT INTO rate_card(partner,kind,effective_from,effective_to,commission,fixed_fee,return_fee,settlement_days,created_by) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,r.partner().trim(),r.kind(),r.effectiveFrom(),r.effectiveTo(),r.commission(),r.fixedFee(),r.returnFee(),r.settlementDays(),a.getName());
        s.audit(a.getName(),"CREATE_RATE_VERSION","rate_card",id,r);
        engine.runForPartner(a.getName(),r.partner());
        return s.one("SELECT * FROM rate_card WHERE id=?",id);
    }
}
