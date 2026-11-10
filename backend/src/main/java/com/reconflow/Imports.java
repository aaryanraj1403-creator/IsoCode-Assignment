package com.reconflow;

import java.nio.file.*;
import java.util.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api")
class Imports {
    final Support s;
    final ImportService service;
    Imports(Support s,ImportService service) { this.s=s;this.service=service; }
    @PostMapping("/imports") Map<String,Object> upload(@RequestParam String partner,@RequestParam String type,@RequestParam MultipartFile file,Authentication a)throws Exception {
        return service.upload(partner,type,file,a.getName());
    }
    @GetMapping("/imports") Map<String,Object> history(@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String partner,
        @RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String state) {
        Support.require(page>=0 && page<=100000,"Invalid page");
        Support.require(q.length()<=200,"Search must be at most 200 characters");
        String where=" FROM import_batch WHERE (?='' OR partner=?) AND (?='' OR report_type=?) AND (?='' OR state=?) AND (filename ILIKE ? OR partner ILIKE ?)";
        Object[] filter={partner,partner,type,type,state,state,"%"+q+"%","%"+q+"%"};
        var arguments=new ArrayList<>(Arrays.asList(filter));arguments.add(page*25);
        return Map.of("rows",s.db.queryForList("SELECT id,partner,report_type,filename,state,row_count,processed,accepted,error_count,error_message,created_at,completed_at"+where+" ORDER BY created_at DESC,id LIMIT 25 OFFSET ?",arguments.toArray()),"total",s.db.queryForObject("SELECT count(*)"+where,Long.class,filter));
    }
    @GetMapping("/imports/{id}") Map<String,Object> detail(@PathVariable UUID id) {
        var b=new LinkedHashMap<>(service.batch(id));
        b.put("headers",service.headerList(b));
        b.put("fields",ImportService.fields(b.get("report_type").toString()));
        b.put("required",ImportService.required(b.get("report_type").toString()));
        b.put("preview",s.db.queryForList("SELECT row_number,raw_data::text FROM source_row WHERE import_id=? ORDER BY row_number LIMIT 20",id));
        b.put("mappings",s.db.queryForList("SELECT id,mapping::text,approved_by,predecessor,created_at FROM mapping_version WHERE partner=? AND report_type=? ORDER BY id DESC",b.get("partner"),b.get("report_type")));
        return b;
    }
    @GetMapping("/imports/{id}/errors") Map<String,Object> errors(@PathVariable UUID id,@RequestParam(defaultValue="0") int page) {
        service.batch(id); Support.require(page>=0 && page<=100000,"Invalid page");
        return Map.of("rows",s.db.queryForList("SELECT row_number,message FROM row_error WHERE import_id=? ORDER BY row_number,id LIMIT 100 OFFSET ?",id,page*100),"total",s.db.queryForObject("SELECT count(*) FROM row_error WHERE import_id=?",Long.class,id));
    }
    record Approval(Map<String,String> mapping,boolean approve) {}
    @PostMapping("/imports/{id}/preview") List<Map<String,Object>> preview(@PathVariable UUID id,@RequestBody Approval r,Authentication a) {
        var b=service.batch(id); String type=b.get("report_type").toString();
        Support.require(b.get("state").equals("AWAITING_MAPPING"),"Import must await mapping review");
        service.validateMapping(type,r.mapping(),service.headerList(b));
        List<Map<String,Object>> results=new ArrayList<>();
        for(var row:s.db.queryForList("SELECT row_number,raw_data::text FROM source_row WHERE import_id=? ORDER BY row_number LIMIT 20",id)) {
            Map<String,Object> result=new LinkedHashMap<>(); result.put("row",row.get("row_number"));
            try { result.put("canonical",service.canonical(type,r.mapping(),s.object(row.get("raw_data").toString()))); }
            catch(IllegalArgumentException e) { result.put("error",e.getMessage()); }
            results.add(result);
        }
        s.db.update("UPDATE import_batch SET preview_mapping=?::jsonb,preview_by=? WHERE id=? AND state='AWAITING_MAPPING'",s.json(r.mapping()),a.getName(),id);
        return results;
    }
    @PostMapping("/imports/{id}/approve") Map<String,Object> approve(@PathVariable UUID id,@RequestBody Approval r,Authentication a) {
        return service.approve(id,r.mapping(),r.approve(),a.getName());
    }
    @PostMapping("/imports/{id}/retry") Map<String,Object> retry(@PathVariable UUID id,Authentication a) { return service.retry(id,a.getName()); }
    @GetMapping("/imports/{id}/source") ResponseEntity<Resource> source(@PathVariable UUID id) {
        var b=s.one("SELECT * FROM import_batch WHERE id=?",id);
        Path path=Path.of(b.get("storage_path").toString()).normalize();
        Support.require(path.startsWith(service.root),"Invalid source path");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(b.get("filename").toString(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .body(new FileSystemResource(path));
    }
    @PostMapping("/mappings/{id}/rollback") @PreAuthorize("hasRole('FOUNDER')") @Transactional
    Map<String,Object> rollback(@PathVariable long id,Authentication a) {
        var old=s.one("SELECT *,mapping::text FROM mapping_version WHERE id=?",id);
        Long latest=s.db.queryForObject("SELECT max(id) FROM mapping_version WHERE partner=? AND report_type=?",Long.class,old.get("partner"),old.get("report_type"));
        Long version=s.db.queryForObject("INSERT INTO mapping_version(partner,report_type,mapping,approved_by,predecessor) VALUES (?,?,?::jsonb,?,?) RETURNING id",Long.class,old.get("partner"),old.get("report_type"),old.get("mapping"),a.getName(),latest);
        s.audit(a.getName(),"ROLLBACK_MAPPING","mapping",version,Map.of("restoredFrom",id));
        return Map.of("id",version,"restoredFrom",id);
    }
}
