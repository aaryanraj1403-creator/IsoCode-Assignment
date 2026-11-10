package com.reconflow;

import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/claims")
class Claims {
    final Support s;
    final Orders orders;
    Claims(Support s,Orders orders) { this.s=s; this.orders=orders; }
    @GetMapping Map<String,Object> list(@RequestParam(defaultValue="") String partner,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q) {
        Support.require(page>=0 && page<=100000,"Invalid page");
        Support.require(q.length()<=200,"Search must be at most 200 characters");
        String from=" FROM claim c JOIN exception e ON e.id=c.exception_id JOIN order_record o ON o.id=e.order_id WHERE (?='' OR o.partner=?) AND (?='' OR c.status=?) AND (o.external_id ILIKE ? OR c.note ILIKE ? OR c.id::text ILIKE ?)";
        Object[] filter={partner,partner,status,status,"%"+q+"%","%"+q+"%","%"+q+"%"};
        var arguments=new ArrayList<>(Arrays.asList(filter));arguments.add(page*25);
        return Map.of("rows",s.db.queryForList("SELECT c.id,c.status,c.note,c.created_at,c.updated_at,c.created_by,e.type,e.amount,e.active,o.external_id,o.partner,o.id order_id"+from+" ORDER BY c.id DESC LIMIT 25 OFFSET ?",arguments.toArray()),
            "total",s.db.queryForObject("SELECT count(*)"+from,Long.class,filter));
    }
    record Create(long exceptionId,String note) {}
    @PostMapping @Transactional Map<String,Object> create(@RequestBody Create request,Authentication a) {
        Support.require(request.note()!=null && !request.note().isBlank() && request.note().length()<=4000,"Claim note required (max 4000 characters)");
        s.db.execute("SELECT pg_advisory_xact_lock(721941)");
        var exception=s.one("SELECT * FROM exception WHERE id=? FOR UPDATE",request.exceptionId());
        Support.require(Boolean.TRUE.equals(exception.get("active")),"Exception is no longer active");
        long orderId=((Number)exception.get("order_id")).longValue();
        String family=Set.of("SHORT_PAID","UNPAID_PAST_DUE").contains(exception.get("type").toString())?"SETTLEMENT":exception.get("type").toString();
        long active=s.db.queryForObject("SELECT count(*) FROM claim c JOIN exception e ON e.id=c.exception_id WHERE e.order_id=? AND c.status IN ('OPEN','IN_REVIEW') AND (CASE WHEN e.type IN ('SHORT_PAID','UNPAID_PAST_DUE') THEN 'SETTLEMENT' ELSE e.type END)=?",Long.class,orderId,family);
        if(active>0)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"An active claim already covers this order's "+family.toLowerCase()+" exception. Review or close it before creating another.");
        var snapshot=new LinkedHashMap<>(orders.detail(orderId)); snapshot.put("claimedException",exception);
        Long id=s.db.queryForObject("INSERT INTO claim(exception_id,note,snapshot,created_by) VALUES (?,?,?::jsonb,?) RETURNING id",Long.class,request.exceptionId(),request.note(),s.json(snapshot),a.getName());
        s.audit(a.getName(),"CREATE_CLAIM","claim",id,Map.of("exceptionId",request.exceptionId(),"note",request.note()));
        return Map.of("id",id,"status","OPEN");
    }
    @GetMapping("/{id}") Map<String,Object> detail(@PathVariable long id) {
        var claim=new LinkedHashMap<>(s.one("SELECT id,exception_id,status,note,snapshot::text,created_by,created_at,updated_at FROM claim WHERE id=?",id));
        claim.put("snapshot",s.object(claim.get("snapshot").toString()));
        claim.put("history",s.db.queryForList("SELECT id,actor,action,details::text,created_at FROM audit_event WHERE entity_type='claim' AND entity_id=? ORDER BY id",Long.toString(id)));
        return claim;
    }
    record Update(String status,String note) {}
    @PatchMapping("/{id}") @Transactional Map<String,Object> update(@PathVariable long id,@RequestBody Update r,Authentication a) {
        Support.require(r.status()!=null && Set.of("OPEN","IN_REVIEW","RESOLVED","REJECTED").contains(r.status()),"Invalid claim status");
        Support.require(r.note()!=null && !r.note().isBlank() && r.note().length()<=4000,"A note is required (max 4000 characters)");
        var old=s.one("SELECT status FROM claim WHERE id=? FOR UPDATE",id);
        String before=old.get("status").toString();
        Support.require(before.equals("OPEN") || before.equals("IN_REVIEW"),"Closed claims are immutable; create a new claim if needed");
        Support.require(!r.status().equals("OPEN") || before.equals("OPEN"),"A claim in review cannot return to OPEN");
        s.db.update("UPDATE claim SET status=?,note=?,updated_at=now() WHERE id=?",r.status(),r.note(),id);
        s.audit(a.getName(),"UPDATE_CLAIM","claim",id,Map.of("from",before,"to",r.status(),"note",r.note()));
        return detail(id);
    }
    @GetMapping("/{id}/evidence") ResponseEntity<String> export(@PathVariable long id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=claim-"+id+"-evidence.json")
            .body(s.json(detail(id)));
    }
}
