package com.reconflow;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReconcileService {
    private static final String IMPORT_ORDERS = """
        SELECT id FROM order_record WHERE import_id=:importId
        UNION SELECT o2.id FROM movement m2
        JOIN order_record o2 ON o2.partner=m2.partner AND o2.external_id=m2.external_id
        WHERE m2.import_id=:importId
        """;
    final Support s;
    final String sql;
    final int returnDays;
    ReconcileService(Support s,@Value("${reconflow.return-days}") int returnDays) throws Exception {
        this.s=s; this.returnDays=returnDays;
        Support.require(returnDays>=1 && returnDays<=365,"RETURN_GRACE_DAYS must be 1..365");
        this.sql=new ClassPathResource("reconcile.sql").getContentAsString(StandardCharsets.UTF_8);
    }
    @Transactional public Map<String,Object> run(String actor) { return calculate(actor,null,null,null); }
    @Transactional public Map<String,Object> runForImport(String actor,UUID id) { return calculate(actor,id,null,null); }
    @Transactional public Map<String,Object> runForOrder(String actor,long id) { return calculate(actor,null,id,null); }
    @Transactional public Map<String,Object> runForPartner(String actor,String partner) { return calculate(actor,null,null,partner); }

    private String scope(String column,UUID importId,Long orderId,String partner) {
        if(importId!=null) return column+" IN ("+IMPORT_ORDERS+")";
        if(orderId!=null) return column+"=:orderId";
        if(partner!=null) return column+" IN (SELECT id FROM order_record WHERE partner=:partner)";
        return "true";
    }
    private Map<String,Object> calculate(String actor,UUID importId,Long orderId,String partner) {
        s.db.execute("SELECT pg_advisory_xact_lock(721941)");
        Map<String,Object> parameters=new HashMap<>();
        parameters.put("asOf",Support.today());parameters.put("returnDays",returnDays);
        parameters.put("importId",importId);parameters.put("orderId",orderId);parameters.put("partner",partner);
        var jdbc=new NamedParameterJdbcTemplate(s.db);
        int count=jdbc.update(sql.replace("/*ORDER_SCOPE*/",scope("o.id",importId,orderId,partner)),parameters);
        jdbc.update("UPDATE exception SET active=false WHERE active AND "+scope("order_id",importId,orderId,partner),parameters);
        jdbc.update("""
            INSERT INTO exception(order_id,type,amount,opened_on)
            SELECT r.order_id,v.type,v.amount,
              CASE WHEN v.type='RETURN_NOT_RECEIVED' THEN (r.breakdown->>'returnDue')::date
                   WHEN v.type IN ('SHORT_PAID','UNPAID_PAST_DUE') THEN (r.breakdown->>'dueDate')::date ELSE r.as_of END
            FROM reconciliation_entry r CROSS JOIN LATERAL (VALUES
              (r.breakdown->>'financialStatus',(r.breakdown->>'financialRisk')::numeric),
              ('FREIGHT_OVERCHARGED',(r.breakdown->>'freightRisk')::numeric),
              ('RETURN_NOT_RECEIVED',(r.breakdown->>'returnRisk')::numeric)) v(type,amount)
            WHERE v.amount>10 AND 
            """+scope("r.order_id",importId,orderId,partner)+"""
            
            ON CONFLICT(order_id,type) DO UPDATE SET amount=excluded.amount,active=true
            """,parameters);
        Map<String,Object> details=new LinkedHashMap<>();
        details.put("orders",count);details.put("asOf",Support.today().toString());
        if(importId!=null)details.put("importId",importId.toString());
        if(orderId!=null)details.put("orderId",orderId);
        if(partner!=null)details.put("partner",partner);
        s.audit(actor,"RECONCILE","workspace","all",details);
        return Map.of("orders",count,"asOf",Support.today());
    }
}
