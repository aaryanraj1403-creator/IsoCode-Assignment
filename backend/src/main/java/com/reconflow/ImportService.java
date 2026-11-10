package com.reconflow;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
class ImportService {
    static final Set<String> TYPES=Set.of("ORDERS","SETTLEMENTS","COD","FREIGHT","RETURNS");
    static final List<String> ORDER_FIELDS=List.of("order_id","value","payment","delivery","order_date","due_date","return_due","return_received");
    static final List<String> MONEY_FIELDS=List.of("transaction_id","order_id","amount","date","tcs","tds");
    final Support s;
    final ReconcileService engine;
    final Path root;
    final TransactionTemplate tx;
    final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    ImportService(Support s,ReconcileService engine,PlatformTransactionManager manager,@Value("${reconflow.upload-dir}") String directory)throws IOException {
        this.s=s; this.engine=engine; this.tx=new TransactionTemplate(manager);
        root=Path.of(directory).toAbsolutePath().normalize(); Files.createDirectories(root);
    }
    @PreDestroy void stop() { workers.shutdown(); }
    @EventListener(ApplicationReadyEvent.class) public void recover() {
        s.db.update("UPDATE import_batch SET state='FAILED',error_message='Server restarted during processing; retry this import' WHERE state IN ('PARSING','PROCESSING')");
        boolean stale=Boolean.TRUE.equals(s.db.queryForObject("SELECT (SELECT count(*) FROM order_record)<>(SELECT count(*) FROM reconciliation_entry) OR EXISTS(SELECT 1 FROM reconciliation_entry WHERE as_of<>?)",Boolean.class,Support.today()));
        if(stale) engine.run("system");
    }
    @Scheduled(cron="0 1 0 * * *",zone="Asia/Kolkata") public void daily() { engine.run("system"); }

    Map<String,Object> upload(String partner,String type,MultipartFile file,String actor)throws Exception {
        Support.require(partner!=null && !partner.isBlank() && partner.equals(partner.trim()) && partner.length()<=100,"Partner required, without surrounding spaces (max 100 characters)");
        Support.require(TYPES.contains(type),"Unsupported report type");
        String name=Objects.toString(file.getOriginalFilename(),"report").replaceAll(".*[/\\\\]","");
        Support.require(name.length()<=200,"Filename too long");
        String lower=name.toLowerCase(Locale.ROOT);
        Support.require(lower.endsWith(".csv") || lower.endsWith(".xlsx"),"Use CSV or XLSX");
        Support.require(file.getSize()>0 && file.getSize()<=30L*1024*1024,"File must contain 1 byte to 30 MB");
        UUID id=UUID.randomUUID();
        Path path=root.resolve(id+(lower.endsWith(".csv")?".csv":".xlsx"));
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var input=new DigestInputStream(file.getInputStream(),digest)) { Files.copy(input,path); }
        String hash=HexFormat.of().formatHex(digest.digest());
        var existing=s.db.queryForList("SELECT id FROM import_batch WHERE partner=? AND report_type=? AND sha256=?",partner,type,hash);
        if(!existing.isEmpty()) {
            Files.deleteIfExists(path);
            throw new ResponseStatusException(HttpStatus.CONFLICT,"File already imported: "+existing.get(0).get("id"));
        }
        try {
            s.db.update("INSERT INTO import_batch(id,partner,report_type,filename,sha256,storage_path,state,created_by) VALUES (?,?,?,?,?,?,'PARSING',?)",id,partner,type,name,hash,path.toString(),actor);
        } catch(RuntimeException e) { Files.deleteIfExists(path); throw e; }
        s.audit(actor,"UPLOAD","import",id,Map.of("filename",name,"sha256",hash,"type",type));
        submit(id,()->parse(id));
        return batch(id);
    }
    void submit(UUID id,Runnable task) {
        try { workers.execute(()-> { try { task.run(); } catch(Exception e) { fail(id,e); } }); }
        catch(RejectedExecutionException e) { fail(id,new IllegalArgumentException("Processing queue is full. Retry later.")); }
    }
    void fail(UUID id,Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Import {} failed",id,e);
        String message=e instanceof IllegalArgumentException?e.getMessage():"Processing failed. Check the file format or server log, then retry.";
        s.db.update("UPDATE import_batch SET state='FAILED',error_message=? WHERE id=?",message,id);
    }
    Map<String,Object> batch(UUID id) {
        return s.one("SELECT id,partner,report_type,filename,sha256,state,headers::text,row_count,processed,accepted,error_count,error_message,mapping_id,created_by,created_at,completed_at FROM import_batch WHERE id=?",id);
    }
    void parse(UUID id) {
        var b=s.one("SELECT * FROM import_batch WHERE id=?",id);
        s.db.update("DELETE FROM row_error WHERE import_id=?",id);
        s.db.update("DELETE FROM source_row WHERE import_id=?",id);
        List<Object[]> rows=new ArrayList<>();
        int[] count={0};
        try {
            ReportParser.read(Path.of(b.get("storage_path").toString()),headers -> s.db.update("UPDATE import_batch SET headers=?::jsonb WHERE id=?",s.json(headers),id),row-> {
                rows.add(new Object[]{id,row.number(),s.json(row.values())}); count[0]++;
                if(rows.size()==1000) { rawBatch(rows); s.db.update("UPDATE import_batch SET row_count=? WHERE id=?",count[0],id); }
            });
            rawBatch(rows);
            Support.require(count[0]>0,"Report contains no data rows");
            s.db.update("UPDATE import_batch SET state='AWAITING_MAPPING',row_count=?,processed=0,error_count=0,error_message=null WHERE id=?",count[0],id);
        } catch(Exception e) { throw new IllegalArgumentException("Cannot parse report: "+e.getMessage(),e); }
    }
    void rawBatch(List<Object[]> rows) {
        if(rows.isEmpty()) return;
        s.db.batchUpdate("INSERT INTO source_row(import_id,row_number,raw_data) VALUES (?,?,?::jsonb)",rows);
        rows.clear();
    }
    static List<String> fields(String type) { return type.equals("ORDERS")?ORDER_FIELDS:MONEY_FIELDS; }
    static Set<String> required(String type) {
        if(type.equals("ORDERS")) return Set.of("order_id","value","payment","delivery","order_date");
        return type.equals("RETURNS")?Set.of("transaction_id","order_id","date"):Set.of("transaction_id","amount","date");
    }
    void validateMapping(String type,Map<String,String> mapping,List<String> headers) {
        Support.require(mapping!=null && mapping.keySet().containsAll(required(type)),"Map all required fields: "+required(type));
        Support.require(fields(type).containsAll(mapping.keySet()),"Unknown canonical field");
        Support.require(mapping.values().stream().allMatch(headers::contains),"Mapping refers to an unknown header");
        Support.require(new HashSet<>(mapping.values()).size()==mapping.size(),"Each source column can map to only one field");
    }
    List<String> headerList(Map<String,Object> batch) {
        try { return s.json.readValue(batch.get("headers").toString(),s.json.getTypeFactory().constructCollectionType(List.class,String.class)); }
        catch(Exception e) { throw new IllegalArgumentException("Invalid headers"); }
    }
    Map<String,Object> canonical(String type,Map<String,String> mapping,Map<String,Object> raw) {
        Support.require(!raw.containsKey("_parse_error"),Objects.toString(raw.get("_parse_error"),"Invalid columns"));
        Map<String,Object> result=new LinkedHashMap<>();
        for(var entry:mapping.entrySet()) result.put(entry.getKey(),Objects.toString(raw.get(entry.getValue()),"").trim());
        for(String field:required(type)) Support.require(!Objects.toString(result.get(field),"").isBlank(),"Missing "+field);
        for(String field:List.of("order_id","transaction_id")) {
            String value=Objects.toString(result.get(field),"");
            Support.require(value.length()<=128,"Identifier exceeds 128 characters");
            result.put(field,value);
        }
        if(type.equals("ORDERS")) {
            result.put("value",amount(result.get("value"),false));
            String payment=Objects.toString(result.get("payment"),"").toUpperCase(Locale.ROOT);
            Support.require(Set.of("COD","PREPAID").contains(payment),"Payment must be COD or Prepaid");
            result.put("payment",payment);
            String delivery=Objects.toString(result.get("delivery"),"").toUpperCase(Locale.ROOT).replace(' ','_');
            if(delivery.equals("RETURNED_TO_WAREHOUSE")) delivery="RETURNED";
            Support.require(Set.of("DELIVERED","RETURN_EXPECTED","RETURNED").contains(delivery),"Delivery must be DELIVERED, RETURN_EXPECTED or RETURNED");
            result.put("delivery",delivery);
            date(result,"order_date",true); date(result,"due_date",false); date(result,"return_due",false);
            if(result.containsKey("due_date")) Support.require(!LocalDate.parse(result.get("due_date").toString()).isBefore(LocalDate.parse(result.get("order_date").toString())),"Due date precedes order date");
            String received=Objects.toString(result.get("return_received"),"false").toLowerCase(Locale.ROOT);
            Support.require(Set.of("","true","false").contains(received),"return_received must be true or false");
            result.put("return_received",received.equals("true") || delivery.equals("RETURNED"));
        } else {
            result.put("amount",amount(result.getOrDefault("amount","0"),!type.equals("FREIGHT")));
            result.put("tcs",amount(result.getOrDefault("tcs","0"),false));
            result.put("tds",amount(result.getOrDefault("tds","0"),false));
            if(type.equals("FREIGHT") || type.equals("RETURNS")) Support.require(new BigDecimal(result.get("tcs").toString()).signum()==0 && new BigDecimal(result.get("tds").toString()).signum()==0,"Tax columns only apply to settlement and COD reports");
            date(result,"date",true);
        }
        return result;
    }
    static String amount(Object raw,boolean signed) {
        String value=Objects.toString(raw,"").trim();
        if(value.isBlank()) value="0";
        Support.require(value.matches("-?[0-9]{1,12}(\\.[0-9]{1,2})?"),"Amounts need plain decimal numbers with at most 2 decimal places");
        BigDecimal amount=new BigDecimal(value);
        Support.require(signed || amount.signum()>=0,"Amount must be nonnegative");
        return amount.setScale(2).toPlainString();
    }
    static void date(Map<String,Object> map,String key,boolean required) {
        String value=Objects.toString(map.get(key),"");
        if(value.isBlank() && !required) { map.remove(key); return; }
        Support.require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),key+" needs ISO date YYYY-MM-DD");
        try { map.put(key,LocalDate.parse(value).toString()); } catch(Exception e) { throw new IllegalArgumentException("Invalid "+key); }
    }
    void canonicalBatch(UUID id,List<Object[]> rows) {
        if(rows.isEmpty()) return;
        String values=String.join(",",Collections.nCopies(rows.size(),"(?,?::jsonb)"));
        List<Object> parameters=new ArrayList<>(rows.size()*2+1);
        for(Object[] row:rows) { parameters.add(row[2]); parameters.add(row[0]); }
        parameters.add(id);
        s.db.update("UPDATE source_row target SET canonical=incoming.data FROM (VALUES "+values+") incoming(number,data) WHERE target.row_number=incoming.number AND target.import_id=?",parameters.toArray());
    }

    Map<String,Object> approve(UUID id,Map<String,String> mapping,boolean approved,String actor) {
        Support.require(approved,"Explicit user approval is required");
        tx.executeWithoutResult(status-> {
            var b=s.one("SELECT *,headers::text FROM import_batch WHERE id=? FOR UPDATE",id);
            Support.require(b.get("state").equals("AWAITING_MAPPING"),"Import is not awaiting mapping");
            validateMapping(b.get("report_type").toString(),mapping,headerList(b));
            Support.require(actor.equals(b.get("preview_by")) && b.get("preview_mapping")!=null && s.object(b.get("preview_mapping").toString()).equals(mapping),"Validate the 20-row preview with this mapping before approving");
            Long previous=s.db.queryForObject("SELECT max(id) FROM mapping_version WHERE partner=? AND report_type=?",Long.class,b.get("partner"),b.get("report_type"));
            Long version=s.db.queryForObject("INSERT INTO mapping_version(partner,report_type,mapping,approved_by,predecessor) VALUES (?,?,?::jsonb,?,?) RETURNING id",Long.class,b.get("partner"),b.get("report_type"),s.json(mapping),actor,previous);
            s.db.update("UPDATE import_batch SET mapping_id=?,state='PROCESSING',processed=0 WHERE id=?",version,id);
            s.audit(actor,"APPROVE_MAPPING","import",id,Map.of("mappingVersion",version,"mapping",mapping));
        });
        submit(id,()->process(id));
        return batch(id);
    }
    void process(UUID id) {
        var b=s.one("SELECT b.*,m.mapping::text FROM import_batch b JOIN mapping_version m ON m.id=b.mapping_id WHERE b.id=?",id);
        String type=b.get("report_type").toString();
        Map<String,String> mapping=new LinkedHashMap<>(); s.object(b.get("mapping").toString()).forEach((k,v)->mapping.put(k,v.toString()));
        s.db.update("DELETE FROM row_error WHERE import_id=?",id);
        int last=0,processed=0;
        while(true) {
            var rows=s.db.queryForList("SELECT row_number,raw_data::text FROM source_row WHERE import_id=? AND row_number>? ORDER BY row_number LIMIT 1000",id,last);
            if(rows.isEmpty()) break;
            List<Object[]> updates=new ArrayList<>(),errors=new ArrayList<>();
            for(var row:rows) {
                int number=((Number)row.get("row_number")).intValue(); last=number; processed++;
                try { updates.add(new Object[]{s.json(canonical(type,mapping,s.object(row.get("raw_data").toString()))),id,number}); }
                catch(IllegalArgumentException e) { errors.add(new Object[]{id,number,e.getMessage()}); }
            }
            canonicalBatch(id,updates);
            if(!errors.isEmpty()) s.db.batchUpdate("INSERT INTO row_error(import_id,row_number,message) VALUES (?,?,?)",errors);
            s.db.update("UPDATE import_batch SET processed=?,error_count=(SELECT count(*) FROM row_error WHERE import_id=?) WHERE id=?",processed,id,id);
        }
        tx.executeWithoutResult(status-> {
            s.db.execute("SELECT pg_advisory_xact_lock(721941)");
            String key=type.equals("ORDERS")?"order_id":"transaction_id";
            String table=type.equals("ORDERS")?"order_record":"movement";
            String column=type.equals("ORDERS")?"external_id":"transaction_id";
            s.db.update("""
                WITH ranked AS (SELECT row_number,canonical,
                row_number() OVER(PARTITION BY canonical->>? ORDER BY row_number) occurrence FROM source_row WHERE import_id=? AND canonical IS NOT NULL)
                INSERT INTO row_error(import_id,row_number,message)
                SELECT ?,r.row_number,'Duplicate identity: '||(r.canonical->>?) FROM ranked r
                WHERE occurrence>1 OR EXISTS(SELECT 1 FROM """+" "+table+" t WHERE t.partner=? AND t."+column+"=r.canonical->>?)",
                key,id,id,key,b.get("partner"),key);
            s.db.update("UPDATE source_row SET canonical=null WHERE import_id=? AND row_number IN (SELECT row_number FROM row_error WHERE import_id=?)",id,id);
            int accepted;
            if(type.equals("ORDERS")) accepted=s.db.update("""
                INSERT INTO order_record(partner,external_id,value,payment,delivery,order_date,due_date,return_due,return_received,import_id,row_number)
                SELECT ?,canonical->>'order_id',(canonical->>'value')::numeric,canonical->>'payment',canonical->>'delivery',
                (canonical->>'order_date')::date,(canonical->>'due_date')::date,(canonical->>'return_due')::date,
                (canonical->>'return_received')::boolean,import_id,row_number FROM source_row WHERE import_id=? AND canonical IS NOT NULL
                """,b.get("partner"),id);
            else accepted=s.db.update("""
                INSERT INTO movement(partner,external_id,transaction_id,kind,amount,tcs,tds,occurred_on,import_id,row_number)
                SELECT ?,nullif(canonical->>'order_id',''),canonical->>'transaction_id',?,(canonical->>'amount')::numeric,
                (canonical->>'tcs')::numeric,(canonical->>'tds')::numeric,(canonical->>'date')::date,import_id,row_number
                FROM source_row WHERE import_id=? AND canonical IS NOT NULL
                """,b.get("partner"),type,id);
            engine.runForImport(b.get("created_by").toString(),id);
            s.db.update("UPDATE import_batch SET state='COMPLETE',accepted=?,error_count=(SELECT count(*) FROM row_error WHERE import_id=?),completed_at=now(),error_message=null WHERE id=?",accepted,id,id);
            s.audit(b.get("created_by").toString(),"IMPORT_COMPLETE","import",id,Map.of("accepted",accepted));
        });
    }
    Map<String,Object> retry(UUID id,String actor) {
        var b=tx.execute(status -> {
            var locked=s.one("SELECT * FROM import_batch WHERE id=? FOR UPDATE",id);
            Support.require(locked.get("state").equals("FAILED"),"Only failed imports may be retried");
            s.db.update("UPDATE import_batch SET state=?,error_message=null WHERE id=?",locked.get("mapping_id")==null?"PARSING":"PROCESSING",id);
            s.audit(actor,"RETRY_IMPORT","import",id,Map.of());
            return locked;
        });
        if(b.get("mapping_id")==null) submit(id,()->parse(id)); else submit(id,()->process(id));
        return batch(id);
    }
}
