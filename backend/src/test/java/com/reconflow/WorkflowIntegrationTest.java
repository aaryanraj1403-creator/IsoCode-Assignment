package com.reconflow;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=${TEST_DATABASE_URL}","spring.datasource.username=${TEST_DATABASE_USER:reconflow}",
 "spring.datasource.password=${TEST_DATABASE_PASSWORD}","DEMO_FINANCE_PASSWORD=Finance-test-2026!",
 "DEMO_FOUNDER_PASSWORD=Founder-test-2026!","reconflow.upload-dir=target/test-uploads",
 "OPENAI_API_KEY=","OPENAI_MODEL="})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL",matches=".+")
class WorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired Support support;
    @Autowired ImportService imports;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    String partner="IT-"+UUID.randomUUID();
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString());}
    String upload(String type,String content)throws Exception{
        return uploadFile(type,content.getBytes(StandardCharsets.UTF_8),"report.csv");
    }
    String uploadFile(String type,byte[] content,String filename)throws Exception{
        var file=new MockMultipartFile("file",filename,"application/octet-stream",content);
        var response=mvc.perform(multipart("/api/imports").file(file).param("partner",partner).param("type",type).with(user("finance").roles("FINANCE")).with(csrf())).andExpect(status().isOk()).andReturn();
        return body(response).get("id").asText();
    }
    JsonNode await(String id,String state)throws Exception{
        for(int i=0;i<300;i++){
            var response=body(mvc.perform(get("/api/imports/"+id).with(user("finance").roles("FINANCE"))).andExpect(status().isOk()).andReturn());
            if(state.equals(response.path("state").asText()))return response;
            if("FAILED".equals(response.path("state").asText()))fail(response.toString());
            Thread.sleep(100);
        }
        throw new AssertionError("Import timed out");
    }
    void process(String id,List<String> fields)throws Exception{
        await(id,"AWAITING_MAPPING");Map<String,String> mapping=new LinkedHashMap<>();fields.forEach(f->mapping.put(f,f));
        String content=json.writeValueAsString(Map.of("mapping",mapping,"approve",true));
        mvc.perform(post("/api/imports/"+id+"/preview").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(content)).andExpect(status().isOk());
        mvc.perform(post("/api/imports/"+id+"/approve").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(content)).andExpect(status().isOk());
        await(id,"COMPLETE");
    }
    void rate(String kind,int fee)throws Exception{
        String data=json.writeValueAsString(Map.of("partner",partner,"kind",kind,"effectiveFrom","2020-01-01","effectiveTo","2099-12-31","commission",kind.equals("CHANNEL")?10:0,"fixedFee",fee,"returnFee",96,"settlementDays",7));
        mvc.perform(post("/api/rate-cards").with(user("founder").roles("FOUNDER")).with(csrf()).contentType("application/json").content(data)).andExpect(status().isOk());
    }
    @Test void uploadMappingReconciliationClaimsAndDashboard()throws Exception{
        rate("CHANNEL",50);rate("COURIER",70);
        String orders="order_id,value,payment,delivery,order_date\nO-1,1000,PREPAID,DELIVERED,2026-01-01\nO-2,500,COD,RETURN_EXPECTED,2026-01-01\n";
        String orderImport=upload("ORDERS",orders);
        await(orderImport,"AWAITING_MAPPING");
        String bypass=json.writeValueAsString(Map.of("mapping",Map.of("order_id","order_id","value","value","payment","payment","delivery","delivery","order_date","order_date"),"approve",true));
        mvc.perform(post("/api/imports/"+orderImport+"/approve").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(bypass)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("partner",partner).with(user("finance").roles("FINANCE"))).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/imports/"+orderImport+"/suggest-mapping").with(user("finance").roles("FINANCE")).with(csrf())).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.suggestions").isEmpty());
        process(orderImport,List.of("order_id","value","payment","delivery","order_date"));
        var duplicate=new MockMultipartFile("file","renamed.csv","text/csv",orders.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/imports").file(duplicate).param("partner",partner).param("type","ORDERS").with(user("finance").roles("FINANCE")).with(csrf())).andExpect(status().isConflict());
        String settlement=upload("SETTLEMENTS","transaction_id,order_id,amount,date,tcs,tds\nT1,O-1,400,2026-01-10,3,0.50\nT2,O-1,400,2026-01-10,2,0.50\nT1,O-1,400,2026-01-10,3,0.50\nBAD,O-1,NaN,2026-01-10,0,0\nUNMATCHED,,10,2026-01-10,0,0\n");
        process(settlement,List.of("transaction_id","order_id","amount","date","tcs","tds"));
        var completed=await(settlement,"COMPLETE");assertEquals(3,completed.get("accepted").asInt());assertEquals(2,completed.get("error_count").asInt());
        String freight=upload("FREIGHT","transaction_id,order_id,amount,date\nF1,O-1,90,2026-01-10\n");
        process(freight,List.of("transaction_id","order_id","amount","date"));
        Long orderId=db.queryForObject("SELECT id FROM order_record WHERE partner=? AND external_id='O-1'",Long.class,partner);
        var detail=body(mvc.perform(get("/api/orders/"+orderId).with(user("finance").roles("FINANCE"))).andExpect(status().isOk()).andReturn());
        assertEquals(844,detail.get("expected").asInt());assertEquals(800,detail.get("received").asInt());
        assertEquals(64,detail.get("money_at_risk").asInt());assertEquals(5,detail.get("tcs").asInt());assertEquals(1,detail.get("tds").asInt());
        assertEquals("FREIGHT_OVERCHARGED",detail.get("status").asText());
        assertTrue(detail.get("sources").size()>=4);assertTrue(detail.get("breakdown").get("channelVersion").asLong()>0);
        long exceptionId=db.queryForObject("SELECT id FROM exception WHERE order_id=? AND type='SHORT_PAID'",Long.class,orderId);
        String claimBody=json.writeValueAsString(Map.of("exceptionId",exceptionId,"note","Synthetic claim for source evidence"));
        var claim=body(mvc.perform(post("/api/claims").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(claimBody)).andExpect(status().isOk()).andReturn());
        long claimId=claim.get("id").asLong();
        mvc.perform(get("/api/claims").param("partner",partner).param("q","Synthetic claim").with(user("finance").roles("FINANCE"))).andExpect(jsonPath("$.total").value(1));
        mvc.perform(post("/api/claims").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(claimBody)).andExpect(status().isConflict());
        mvc.perform(get("/api/claims/"+claimId+"/evidence").with(user("founder").roles("FOUNDER"))).andExpect(status().isOk()).andExpect(jsonPath("$.snapshot.sources").isArray());
        mvc.perform(patch("/api/claims/"+claimId).with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content("{\"status\":\"IN_REVIEW\",\"note\":\"Evidence reviewed\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/claims/"+claimId).with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content("{\"status\":\"RESOLVED\",\"note\":\"Review complete\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/claims/"+claimId).with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content("{\"status\":\"OPEN\",\"note\":\"Reopen\"}")).andExpect(status().isBadRequest());
        var dashboard=body(mvc.perform(get("/api/dashboard").with(user("founder").roles("FOUNDER"))).andExpect(status().isOk()).andReturn());
        var sum=db.queryForObject("SELECT coalesce(sum(amount),0) FROM exception WHERE active",java.math.BigDecimal.class);
        assertEquals(0,sum.compareTo(dashboard.get("totals").get("money_at_risk").decimalValue()));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM exception e JOIN order_record o ON o.id=e.order_id WHERE o.partner=? AND e.type='RETURN_NOT_RECEIVED' AND active",Integer.class,partner));
        String receipt=upload("RETURNS","transaction_id,order_id,date\nR1,O-2,2026-01-20\n");
        process(receipt,List.of("transaction_id","order_id","date"));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM exception e JOIN order_record o ON o.id=e.order_id WHERE o.partner=? AND e.type='RETURN_NOT_RECEIVED' AND active",Integer.class,partner));
        long version=db.queryForObject("SELECT mapping_id FROM import_batch WHERE id=?::uuid",Long.class,orderImport);
        mvc.perform(post("/api/mappings/"+version+"/rollback").with(user("finance").roles("FINANCE")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/mappings/"+version+"/rollback").with(user("founder").roles("FOUNDER")).with(csrf())).andExpect(status().isOk());
    }
    @Test void financialBoundariesManualMatchingAndMissingRates()throws Exception{
        rate("CHANNEL",50);rate("COURIER",70);
        StringBuilder content=new StringBuilder("order_id,value,payment,delivery,order_date,due_date\n");
        for(String id:List.of("BOUNDARY","SHORT","TAX","REFUND","RETURNTAX","PENDING","FREIGHT10","FREIGHTMORE","TINYRETURN")){
            String delivery=(id.equals("REFUND") || id.equals("RETURNTAX"))?"RETURNED":id.equals("TINYRETURN")?"RETURN_EXPECTED":"DELIVERED";
            content.append(id).append(id.equals("TINYRETURN")?",10,":",1000,").append("PREPAID,").append(delivery).append(",2026-01-01,").append(id.equals("PENDING")?"2099-01-01":"2026-01-08").append('\n');
        }
        process(upload("ORDERS",content.toString()),List.of("order_id","value","payment","delivery","order_date","due_date"));
        String settlement="transaction_id,order_id,amount,date,tcs,tds\nA,BOUNDARY,840,2026-01-10,0,0\nB,SHORT,839.99,2026-01-10,0,0\nC,TAX,844,2026-01-10,5,1\nD,REFUND,-142,2026-01-10,0,0\nE,FREIGHT10,850,2026-01-10,0,0\nF,FREIGHTMORE,850,2026-01-10,0,0\nG,TINYRETURN,-96,2026-01-10,0,0\nMISSING,,10,2026-01-10,0,0\n";
        settlement+="RT,RETURNTAX,-116,2026-01-10,20,0\n";
        process(upload("SETTLEMENTS",settlement),List.of("transaction_id","order_id","amount","date","tcs","tds"));
        process(upload("FREIGHT","transaction_id,order_id,amount,date\nF1,FREIGHT10,80,2026-01-10\nF2,FREIGHTMORE,80.01,2026-01-10\n"),List.of("transaction_id","order_id","amount","date"));
        var expected=Map.of("BOUNDARY","SETTLED","SHORT","SHORT_PAID","TAX","SETTLED","REFUND","SHORT_PAID","RETURNTAX","SETTLED","PENDING","PENDING","FREIGHT10","SETTLED","FREIGHTMORE","FREIGHT_OVERCHARGED","TINYRETURN","SETTLED");
        for(var entry:expected.entrySet())assertEquals(entry.getValue(),db.queryForObject("SELECT r.status FROM reconciliation_entry r JOIN order_record o ON o.id=r.order_id WHERE o.partner=? AND o.external_id=?",String.class,partner,entry.getKey()),entry.getKey());
        Long target=db.queryForObject("SELECT id FROM order_record WHERE partner=? AND external_id='BOUNDARY'",Long.class,partner);
        Long movement=db.queryForObject("SELECT id FROM movement WHERE partner=? AND transaction_id='MISSING'",Long.class,partner);
        Long foreign=db.queryForObject("SELECT min(id) FROM order_record WHERE partner<>?",Long.class,partner);
        mvc.perform(post("/api/unmatched/"+movement+"/match").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content("{\"orderId\":"+foreign+"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/unmatched/"+movement+"/match").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content("{\"orderId\":"+target+"}")).andExpect(status().isOk());
        mvc.perform(post("/api/unmatched/"+movement+"/match").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content("{\"orderId\":"+target+"}")).andExpect(status().isBadRequest());
        partner+="-NO-RATE";
        process(upload("ORDERS","order_id,value,payment,delivery,order_date\nNO-RATE,1000,COD,DELIVERED,2026-01-01\n"),List.of("order_id","value","payment","delivery","order_date"));
        assertEquals("MANUAL_REVIEW",db.queryForObject("SELECT r.status FROM reconciliation_entry r JOIN order_record o ON o.id=r.order_id WHERE o.partner=?",String.class,partner));
    }
    @Test void xlsxUploadThroughReconciliation()throws Exception{
        rate("CHANNEL",50);
        byte[] data;
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var out=new java.io.ByteArrayOutputStream()){
            var sheet=book.createSheet("Report");var headers=List.of("order_id","value","payment","delivery","order_date");var values=List.of("XLSX-1","1000","COD","DELIVERED","2026-01-01");
            for(int i=0;i<headers.size();i++){sheet.getRow(0);if(i==0){sheet.createRow(0);sheet.createRow(1);}sheet.getRow(0).createCell(i).setCellValue(headers.get(i));sheet.getRow(1).createCell(i).setCellValue(values.get(i));}
            book.write(out);data=out.toByteArray();
        }
        String id=uploadFile("ORDERS",data,"orders.xlsx");process(id,List.of("order_id","value","payment","delivery","order_date"));
        assertEquals(1,await(id,"COMPLETE").get("accepted").asInt());
    }
    @Test void futureTransactionsDoNotCountAsReceived()throws Exception{
        rate("CHANNEL",50);rate("COURIER",70);
        process(upload("ORDERS","order_id,value,payment,delivery,order_date\nPAYOUT,1000,COD,DELIVERED,2026-01-01\nRETURN,1000,PREPAID,RETURN_EXPECTED,2026-01-01\n"),List.of("order_id","value","payment","delivery","order_date"));
        process(upload("SETTLEMENTS","transaction_id,order_id,amount,date,tcs,tds\nFUTURE,PAYOUT,844,2099-01-01,5,1\nRETURN-FEE,RETURN,-96,2026-01-10,0,0\n"),List.of("transaction_id","order_id","amount","date","tcs","tds"));
        process(upload("RETURNS","transaction_id,order_id,date\nFUTURE-RECEIPT,RETURN,2099-01-01\n"),List.of("transaction_id","order_id","date"));
        process(upload("FREIGHT","transaction_id,order_id,amount,date\nFUTURE-FREIGHT,PAYOUT,200,2099-01-01\n"),List.of("transaction_id","order_id","amount","date"));
        var payout=db.queryForMap("SELECT r.* FROM reconciliation_entry r JOIN order_record o ON o.id=r.order_id WHERE o.partner=? AND o.external_id='PAYOUT'",partner);
        assertEquals("UNPAID_PAST_DUE",payout.get("status"));assertEquals(new java.math.BigDecimal("0.00"),payout.get("received"));assertEquals(new java.math.BigDecimal("0.00"),payout.get("tcs"));assertEquals(new java.math.BigDecimal("850.00"),payout.get("money_at_risk"));
        assertEquals("RETURN_NOT_RECEIVED",db.queryForObject("SELECT r.status FROM reconciliation_entry r JOIN order_record o ON o.id=r.order_id WHERE o.partner=? AND o.external_id='RETURN'",String.class,partner));
    }
    @Test void incrementalImportPreservesUnrelatedReconciliation()throws Exception{
        rate("CHANNEL",50);
        process(upload("ORDERS","order_id,value,payment,delivery,order_date\nA,1000,COD,DELIVERED,2026-01-01\nB,1000,COD,DELIVERED,2026-01-01\n"),List.of("order_id","value","payment","delivery","order_date"));
        var before=db.queryForMap("SELECT r.* FROM reconciliation_entry r JOIN order_record o ON o.id=r.order_id WHERE o.partner=? AND o.external_id='B'",partner);
        process(upload("COD","transaction_id,order_id,amount,date\nCOD-A,A,850,2026-01-10\n"),List.of("transaction_id","order_id","amount","date"));
        var after=db.queryForMap("SELECT r.* FROM reconciliation_entry r JOIN order_record o ON o.id=r.order_id WHERE o.partner=? AND o.external_id='B'",partner);
        assertEquals(before.get("updated_at"),after.get("updated_at"));
        assertEquals(new java.math.BigDecimal("850.00"),after.get("money_at_risk"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM exception WHERE order_id=? AND active",Integer.class,after.get("order_id")));
        assertEquals("SETTLED",db.queryForObject("SELECT r.status FROM reconciliation_entry r JOIN order_record o ON o.id=r.order_id WHERE o.partner=? AND o.external_id='A'",String.class,partner));
    }
    @Test void filtersAndPaginationReachOldUnmatchedRecords()throws Exception{
        StringBuilder report=new StringBuilder("transaction_id,order_id,amount,date\n");
        for(int i=0;i<205;i++)report.append(String.format("TX%03d,,10,2026-01-10%n",i));
        process(upload("SETTLEMENTS",report.toString()),List.of("transaction_id","order_id","amount","date"));
        var last=body(mvc.perform(get("/api/unmatched").param("partner",partner).param("type","SETTLEMENTS").param("page","4").param("size","50").with(user("finance").roles("FINANCE"))).andExpect(status().isOk()).andReturn());
        assertEquals(205,last.get("total").asInt());assertEquals(5,last.get("rows").size());
        mvc.perform(get("/api/unmatched").param("partner",partner).param("q","TX000").with(user("finance").roles("FINANCE"))).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/unmatched").param("size","0").with(user("finance").roles("FINANCE"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/imports").param("partner",partner).param("type","SETTLEMENTS").param("state","COMPLETE").param("q","report.csv").with(user("finance").roles("FINANCE"))).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/imports").param("partner",partner).param("type","ORDERS").with(user("finance").roles("FINANCE"))).andExpect(jsonPath("$.total").value(0));
        var dashboard=body(mvc.perform(get("/api/dashboard").param("partner",partner).with(user("founder").roles("FOUNDER"))).andExpect(status().isOk()).andReturn());
        assertEquals(205,dashboard.get("unmatched").asInt());assertEquals(0,dashboard.get("totals").get("orders").asInt());assertEquals(0,dashboard.get("types").size());
    }
    @Test void concurrentFinancialClaimsRemainUniqueAfterPartialPayment()throws Exception{
        rate("CHANNEL",50);rate("COURIER",70);
        process(upload("ORDERS","order_id,value,payment,delivery,order_date\nCLAIM-1,1000,COD,DELIVERED,2026-01-01\n"),List.of("order_id","value","payment","delivery","order_date"));
        long id=db.queryForObject("SELECT id FROM exception WHERE order_id=(SELECT id FROM order_record WHERE partner=? AND external_id='CLAIM-1') AND type='UNPAID_PAST_DUE'",Long.class,partner);
        String claim=json.writeValueAsString(Map.of("exceptionId",id,"note","Concurrent claim"));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var jobs=List.of(pool.submit(()->mvc.perform(post("/api/claims").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(claim)).andReturn().getResponse().getStatus()),
                pool.submit(()->mvc.perform(post("/api/claims").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(claim)).andReturn().getResponse().getStatus()));
            var statuses=new ArrayList<Integer>();for(var job:jobs)statuses.add(job.get());Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        }finally{pool.shutdownNow();}
        long original=db.queryForObject("SELECT id FROM claim WHERE exception_id=?",Long.class,id);
        process(upload("COD","transaction_id,order_id,amount,date\nPARTIAL,CLAIM-1,100,2026-01-10\n"),List.of("transaction_id","order_id","amount","date"));
        long shortId=db.queryForObject("SELECT id FROM exception WHERE order_id=(SELECT id FROM order_record WHERE partner=? AND external_id='CLAIM-1') AND type='SHORT_PAID'",Long.class,partner);
        String followup=json.writeValueAsString(Map.of("exceptionId",shortId,"note","Partial payment follow-up"));
        mvc.perform(post("/api/claims").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(followup)).andExpect(status().isConflict());
        mvc.perform(get("/api/claims/"+original+"/evidence").with(user("finance").roles("FINANCE"))).andExpect(jsonPath("$.snapshot.received").value(0)).andExpect(jsonPath("$.snapshot.claimedException.amount").value(850));
        mvc.perform(patch("/api/claims/"+original).with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content("{\"status\":\"RESOLVED\",\"note\":\"Replaced after partial receipt\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/claims").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(followup)).andExpect(status().isOk());
        process(upload("FREIGHT","transaction_id,order_id,amount,date\nFREIGHT,CLAIM-1,100,2026-01-10\n"),List.of("transaction_id","order_id","amount","date"));
        long freightId=db.queryForObject("SELECT id FROM exception WHERE order_id=(SELECT id FROM order_record WHERE partner=? AND external_id='CLAIM-1') AND type='FREIGHT_OVERCHARGED'",Long.class,partner);
        mvc.perform(post("/api/claims").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("exceptionId",freightId,"note","Independent freight claim")))).andExpect(status().isOk());
    }
    @Test void aiMatchProposalsAreRedactedBoundAndRequireExplicitApproval()throws Exception{
        rate("CHANNEL",50);
        process(upload("ORDERS","order_id,value,payment,delivery,order_date\nPRIVATE-ORDER-A,1000,COD,DELIVERED,2026-01-01\nPRIVATE-ORDER-B,1200,COD,DELIVERED,2026-01-01\n"),List.of("order_id","value","payment","delivery","order_date"));
        process(upload("COD","transaction_id,order_id,amount,date\nPRIVATE-TRANSACTION,,850,2026-01-10\n"),List.of("transaction_id","order_id","amount","date"));
        long movement=db.queryForObject("SELECT id FROM movement WHERE partner=?",Long.class,partner);
        mvc.perform(post("/api/unmatched/"+movement+"/suggest-match").with(user("finance").roles("FINANCE")).with(csrf())).andExpect(jsonPath("$.available").value(false));
        var captured=new java.util.concurrent.atomic.AtomicReference<Object>();
        var fixture=new AiProvider(json,"test-key","offline-fixture",null,java.net.URI.create("http://127.0.0.1"),java.time.Duration.ofSeconds(1)){
            @Override Result request(String name,String instructions,Object input,Object schema){
                captured.set(input);
                try{return new Result(json.readTree("{\"suggestions\":[{\"candidate\":\"C1\",\"confidence\":0.7,\"reason\":\"Review comparison facts\"}]}"),"");}
                catch(Exception e){throw new RuntimeException(e);}
            }
        };
        var matching=new AiMatching(support,fixture,transactions);
        var actor=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("finance","test",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FINANCE")));
        var suggested=matching.suggest(movement,actor);
        var proposal=(Map<?,?>)((List<?>)suggested.get("suggestions")).get(0);
        assertNull(db.queryForObject("SELECT external_id FROM movement WHERE id=?",String.class,movement));
        String redacted=json.writeValueAsString(captured.get());
        assertFalse(redacted.contains("PRIVATE-ORDER"));assertFalse(redacted.contains("PRIVATE-TRANSACTION"));assertFalse(redacted.contains(partner));
        var firstCandidate=json.valueToTree(captured.get()).get("candidates").get(0);
        assertFalse(firstCandidate.has("orderId"));assertFalse(firstCandidate.has("amount"));assertFalse(firstCandidate.has("externalId"));
        String approval=json.writeValueAsString(Map.of("orderId",proposal.get("orderId"),"suggestionId",proposal.get("id")));
        mvc.perform(post("/api/unmatched/"+movement+"/match").with(user("founder").roles("FOUNDER")).with(csrf()).contentType("application/json").content(approval)).andExpect(status().isBadRequest());
        db.update("UPDATE match_suggestion SET created_at=now()-interval '2 days' WHERE id=?",proposal.get("id"));
        mvc.perform(post("/api/unmatched/"+movement+"/match").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(approval)).andExpect(status().isBadRequest());
        var refreshed=matching.suggest(movement,actor);
        var fresh=(Map<?,?>)((List<?>)refreshed.get("suggestions")).get(0);
        assertEquals("SUPERSEDED",db.queryForObject("SELECT state FROM match_suggestion WHERE id=?",String.class,proposal.get("id")));
        mvc.perform(post("/api/unmatched/"+movement+"/match").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("orderId",fresh.get("orderId"),"suggestionId",fresh.get("id"))))).andExpect(status().isOk());
        assertEquals("APPROVED",db.queryForObject("SELECT state FROM match_suggestion WHERE id=?",String.class,fresh.get("id")));
        assertEquals("PRIVATE-ORDER-A",db.queryForObject("SELECT external_id FROM movement WHERE id=?",String.class,movement));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM audit_event WHERE action='APPROVE_AI_MATCH' AND entity_id=?",Integer.class,Long.toString(movement)));
    }
    @Test void restartPreservesCurrentResultsAndRecoversInterruptedSources()throws Exception{
        rate("CHANNEL",50);
        process(upload("ORDERS","order_id,value,payment,delivery,order_date\nSTABLE,1000,COD,DELIVERED,2026-01-01\n"),List.of("order_id","value","payment","delivery","order_date"));
        long order=db.queryForObject("SELECT id FROM order_record WHERE partner=?",Long.class,partner);
        var before=db.queryForObject("SELECT updated_at FROM reconciliation_entry WHERE order_id=?",java.sql.Timestamp.class,order);
        String interrupted=upload("ORDERS","order_id,value,payment,delivery,order_date\nNOT-PROCESSED,1000,COD,DELIVERED,2026-01-01\n");
        await(interrupted,"AWAITING_MAPPING");db.update("UPDATE import_batch SET state='PARSING' WHERE id=?::uuid",interrupted);
        imports.recover();
        assertEquals(before,db.queryForObject("SELECT updated_at FROM reconciliation_entry WHERE order_id=?",java.sql.Timestamp.class,order));
        assertEquals("FAILED",db.queryForObject("SELECT state FROM import_batch WHERE id=?::uuid",String.class,interrupted));
        mvc.perform(post("/api/imports/"+interrupted+"/retry").with(user("finance").roles("FINANCE")).with(csrf())).andExpect(status().isOk());
        await(interrupted,"AWAITING_MAPPING");
        assertEquals(1,db.queryForObject("SELECT count(*) FROM order_record WHERE partner=?",Integer.class,partner));
        db.update("UPDATE reconciliation_entry SET as_of=? WHERE order_id=?",Support.today().minusDays(1),order);imports.recover();
        assertEquals(Support.today(),db.queryForObject("SELECT as_of FROM reconciliation_entry WHERE order_id=?",java.sql.Date.class,order).toLocalDate());
    }
    @Test void permissionsAndCsrf()throws Exception{
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reconcile").with(user("finance").roles("FINANCE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders").with(user("outsider").roles("OTHER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/rate-cards").with(user("finance").roles("FINANCE")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        var result=mvc.perform(post("/api/auth/login").with(csrf()).param("username","finance").param("password","Finance-test-2026!")).andExpect(status().isOk()).andReturn();
        var session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("finance"));
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
    }
    @Test void rateDatesOverlapAndReplacementAreTransactional()throws Exception{
        rate("CHANNEL",50);
        var old=db.queryForMap("SELECT * FROM rate_card WHERE partner=?",partner);
        String overlap=json.writeValueAsString(Map.of("partner",partner,"kind","CHANNEL","effectiveFrom","2026-01-01","effectiveTo","2099-12-31","commission",10,"fixedFee",50,"returnFee",96,"settlementDays",7));
        mvc.perform(post("/api/rate-cards").with(user("founder").roles("FOUNDER")).with(csrf()).contentType("application/json").content(overlap)).andExpect(status().isBadRequest());
        var replacement=new LinkedHashMap<String,Object>(json.readValue(overlap,Map.class));replacement.put("supersedeId",old.get("id"));
        mvc.perform(post("/api/rate-cards").with(user("founder").roles("FOUNDER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(replacement))).andExpect(status().isOk());
        assertEquals("2025-12-31",db.queryForObject("SELECT effective_to::text FROM rate_card WHERE id=?",String.class,old.get("id")));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM rate_card WHERE partner=?",Integer.class,partner));
    }
}
