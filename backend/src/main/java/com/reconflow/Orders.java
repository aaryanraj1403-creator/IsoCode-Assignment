package com.reconflow;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

@RestController @RequestMapping("/api")
class Orders {
    final Support s;
    final ReconcileService engine;
    Orders(Support s,ReconcileService engine) { this.s=s; this.engine=engine; }

    @PostMapping("/reconcile") Map<String,Object> reconcile(Authentication a) { return engine.run(a.getName()); }

    @GetMapping("/orders")
    Map<String,Object> orders(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String partner,
        @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        Support.require(page>=0 && page<=100000 && size>=1 && size<=100,"Invalid pagination");
        String where=" FROM order_record o JOIN reconciliation_entry r ON r.order_id=o.id WHERE (?='' OR o.partner=?) AND (?='' OR r.status=?) AND (o.external_id ILIKE ? OR o.partner ILIKE ?)";
        Object[] filter={partner,partner,status,status,"%"+q+"%","%"+q+"%"};
        long total=s.db.queryForObject("SELECT count(*)"+where,Long.class,filter);
        List<Object> args=new ArrayList<>(Arrays.asList(filter)); args.add(size); args.add(page*size);
        var rows=s.db.queryForList("SELECT o.id,o.external_id,o.partner,o.value,o.payment,o.delivery,r.expected,r.received,r.difference,r.money_at_risk,r.status,r.tcs,r.tds,r.as_of"+where+" ORDER BY o.id DESC LIMIT ? OFFSET ?",args.toArray());
        return Map.of("rows",rows,"total",total,"page",page,"size",size);
    }

    @GetMapping("/orders/{id}") Map<String,Object> detail(@PathVariable long id) {
        var result=new LinkedHashMap<String,Object>(s.one("SELECT o.*,r.expected,r.received,r.difference,r.money_at_risk,r.status,r.tcs,r.tds,r.breakdown::text,r.as_of FROM order_record o JOIN reconciliation_entry r ON r.order_id=o.id WHERE o.id=?",id));
        result.put("breakdown",s.object((String)result.get("breakdown")));
        result.put("movements",s.db.queryForList("SELECT m.*,b.filename FROM movement m LEFT JOIN import_batch b ON b.id=m.import_id WHERE m.partner=? AND m.external_id=? ORDER BY m.id",result.get("partner"),result.get("external_id")));
        result.put("sources",s.db.queryForList("""
            SELECT sr.import_id,sr.row_number,sr.raw_data::text,b.filename,b.sha256,b.mapping_id
            FROM source_row sr JOIN import_batch b ON b.id=sr.import_id
            WHERE (sr.import_id=? AND sr.row_number=?) OR (sr.import_id,sr.row_number) IN
            (SELECT import_id,row_number FROM movement WHERE partner=? AND external_id=?) ORDER BY b.created_at,sr.row_number
            """,result.get("import_id"),result.get("row_number"),result.get("partner"),result.get("external_id")));
        result.put("exceptions",s.db.queryForList("SELECT e.*,c.id claim_id,c.status claim_status FROM exception e LEFT JOIN claim c ON c.exception_id=e.id AND c.status IN ('OPEN','IN_REVIEW') WHERE e.order_id=? ORDER BY e.id",id));
        result.put("seedNote",result.get("import_id")==null ? "Preserved client-brief sample; expected amount is authoritative. No source upload exists for this seed." : "");
        return result;
    }

    @GetMapping("/dashboard") Map<String,Object> dashboard(@RequestParam(defaultValue="") String partner) {
        String filter="(?='' OR o.partner=?)";
        Object[] args={partner,partner};
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("totals",s.one("""
            SELECT coalesce(sum(r.money_at_risk),0) money_at_risk,count(*) orders,
            count(*) FILTER(WHERE r.money_at_risk>0) needs_attention,count(*) FILTER(WHERE r.status='MANUAL_REVIEW') manual_review,
            min(r.as_of) as_of FROM reconciliation_entry r JOIN order_record o ON o.id=r.order_id WHERE
            """+filter,args));
        data.put("partners",s.db.queryForList("""
            SELECT o.partner,sum(r.money_at_risk) money_at_risk,sum(r.tcs) tcs,sum(r.tds) tds,count(*) orders
            FROM order_record o JOIN reconciliation_entry r ON r.order_id=o.id WHERE
            """+filter+" GROUP BY o.partner ORDER BY sum(r.money_at_risk) DESC",args));
        data.put("types",s.db.queryForList("SELECT e.type,sum(e.amount) amount,count(*) count FROM exception e JOIN order_record o ON o.id=e.order_id WHERE e.active AND "+filter+" GROUP BY e.type ORDER BY e.type",args));
        data.put("ages",s.db.queryForList("""
            SELECT CASE WHEN (now() AT TIME ZONE 'Asia/Kolkata')::date-e.opened_on<=7 THEN '0–7 days'
            WHEN (now() AT TIME ZONE 'Asia/Kolkata')::date-e.opened_on<=30 THEN '8–30 days' ELSE '31+ days' END bucket,
            sum(e.amount) amount FROM exception e JOIN order_record o ON o.id=e.order_id WHERE e.active AND
            """+filter+" GROUP BY bucket ORDER BY min((now() AT TIME ZONE 'Asia/Kolkata')::date-e.opened_on)",args));
        data.put("unmatched",s.db.queryForObject("SELECT count(*) FROM movement m WHERE (?='' OR m.partner=?) AND NOT EXISTS(SELECT 1 FROM order_record o WHERE o.partner=m.partner AND o.external_id=m.external_id)",Long.class,args));
        data.put("activeClaims",s.db.queryForObject("SELECT count(*) FROM claim c JOIN exception e ON e.id=c.exception_id JOIN order_record o ON o.id=e.order_id WHERE c.status IN ('OPEN','IN_REVIEW') AND "+filter,Long.class,args));
        return data;
    }

    @GetMapping("/unmatched") Map<String,Object> unmatched(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String partner,
        @RequestParam(defaultValue="") String type,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        Support.require(page>=0&&page<=100000&&size>=1&&size<=100,"Invalid pagination");
        Support.require(q.length()<=200,"Search must be at most 200 characters");
        String where=" FROM movement m WHERE NOT EXISTS(SELECT 1 FROM order_record o WHERE o.partner=m.partner AND o.external_id=m.external_id) AND (?='' OR m.partner=?) AND (?='' OR m.kind=?) AND (m.transaction_id ILIKE ? OR coalesce(m.external_id,'') ILIKE ?)";
        Object[] filter={partner,partner,type,type,"%"+q+"%","%"+q+"%"};
        var arguments=new ArrayList<>(Arrays.asList(filter));arguments.add(size);arguments.add(page*size);
        return Map.of("rows",s.db.queryForList("SELECT m.*"+where+" ORDER BY m.id DESC LIMIT ? OFFSET ?",arguments.toArray()),"total",s.db.queryForObject("SELECT count(*)"+where,Long.class,filter),"page",page,"size",size);
    }
    record Match(long orderId,UUID suggestionId) {}
    @PostMapping("/unmatched/{id}/match") @Transactional Map<String,Object> match(@PathVariable long id,@RequestBody Match request,Authentication a) {
        s.db.execute("SELECT pg_advisory_xact_lock(721941)");
        var movement=s.one("SELECT * FROM movement WHERE id=? FOR UPDATE",id);
        var order=s.one("SELECT * FROM order_record WHERE id=?",request.orderId());
        Support.require(movement.get("partner").equals(order.get("partner")),"Partner must match");
        Support.require(s.db.queryForObject("SELECT count(*) FROM order_record WHERE partner=? AND external_id=?",Long.class,movement.get("partner"),movement.get("external_id"))==0,"Movement already matches an order");
        if(request.suggestionId()!=null){
            var suggestion=s.one("SELECT * FROM match_suggestion WHERE id=? FOR UPDATE",request.suggestionId());
            Support.require(((Number)suggestion.get("movement_id")).longValue()==id && ((Number)suggestion.get("order_id")).longValue()==request.orderId() && a.getName().equals(suggestion.get("created_by")) && suggestion.get("state").equals("PENDING"),"Suggestion does not match this account, movement or order");
            Support.require(s.db.queryForObject("SELECT created_at>now()-interval '1 day' FROM match_suggestion WHERE id=?",Boolean.class,request.suggestionId()),"Suggestion expired. Request a fresh suggestion before approval");
        }
        s.db.update("UPDATE movement SET external_id=? WHERE id=?",order.get("external_id"),id);
        s.db.update("UPDATE match_suggestion SET state=CASE WHEN id=? THEN 'APPROVED' ELSE 'SUPERSEDED' END WHERE movement_id=? AND state='PENDING'",request.suggestionId(),id);
        var details=new LinkedHashMap<String,Object>();details.put("orderId",request.orderId());details.put("previousId",Objects.toString(movement.get("external_id"),""));
        if(request.suggestionId()!=null)details.put("suggestionId",request.suggestionId().toString());
        s.audit(a.getName(),request.suggestionId()==null?"APPROVE_MANUAL_MATCH":"APPROVE_AI_MATCH","movement",id,details);
        return engine.runForOrder(a.getName(),request.orderId());
    }

    @GetMapping("/partners") List<String> partners() {
        return s.db.queryForList("SELECT partner FROM order_record UNION SELECT partner FROM rate_card UNION SELECT partner FROM import_batch ORDER BY partner",String.class);
    }
    @GetMapping("/audit") List<Map<String,Object>> audit(@RequestParam String type,@RequestParam String id) {
        return s.db.queryForList("SELECT * FROM audit_event WHERE entity_type=? AND entity_id=? ORDER BY id DESC LIMIT 200",type,id);
    }
}
