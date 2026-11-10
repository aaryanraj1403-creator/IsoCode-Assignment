package com.reconflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@Component
class Support {
    final JdbcTemplate db;
    final ObjectMapper json;
    Support(JdbcTemplate db, ObjectMapper json) { this.db=db; this.json=json; }
    String json(Object value) {
        try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalArgumentException("Invalid JSON",e); }
    }
    Map<String,Object> object(String value) {
        try { return json.readValue(value,new TypeReference<Map<String,Object>>(){}); }
        catch(Exception e) { throw new IllegalArgumentException("Invalid object",e); }
    }
    Map<String,Object> one(String sql,Object... args) {
        return db.queryForList(sql,args).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found"));
    }
    void audit(String actor,String action,String type,Object id,Object details) {
        db.update("INSERT INTO audit_event(actor,action,entity_type,entity_id,details) VALUES (?,?,?,?,?::jsonb)",actor,action,type,id.toString(),json(details));
    }
    static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Kolkata")); }
    static void require(boolean valid,String message) { if(!valid) throw new IllegalArgumentException(message); }
}

@RestControllerAdvice
class Errors {
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(DataIntegrityViolationException e) { return ResponseEntity.status(409).body(Map.of("message","Duplicate record or conflicting state. Refresh and try again.")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> size() { return ResponseEntity.status(413).body(Map.of("message","Maximum upload size is 30 MB.")); }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.toString(e.getReason(),"Request failed"))); }
}
