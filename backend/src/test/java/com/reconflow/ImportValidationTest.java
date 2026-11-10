package com.reconflow;

import java.nio.file.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

class ImportValidationTest {
    @TempDir Path temp;
    @Test void csvQuotedFieldsAndBom() throws Exception {
        Path file=temp.resolve("report.csv");
        Files.writeString(file,"\ufefforder_id,note\nO-1,\"quoted, field\"\n");
        var rows=new ArrayList<ReportParser.Row>();
        ReportParser.read(file,h->assertEquals(List.of("order_id","note"),h),rows::add);
        assertEquals(2,rows.get(0).number());assertEquals("quoted, field",rows.get(0).values().get("note"));
    }
    @Test void duplicateHeadersAreRejected()throws Exception {
        var file=temp.resolve("bad.csv");Files.writeString(file,"id,id\n1,2");
        assertThrows(IllegalArgumentException.class,()->ReportParser.read(file,h->{},r->{}));
    }
    @Test void inconsistentColumnsRetainSourceRowError()throws Exception {
        var file=temp.resolve("bad.csv");Files.writeString(file,"id,value\n1,2,3\n");
        var rows=new ArrayList<ReportParser.Row>();ReportParser.read(file,h->{},rows::add);
        assertTrue(rows.get(0).values().containsKey("_parse_error"));
    }
    @Test void xlsxIsReadWithoutEvaluatingFormulas()throws Exception {
        var file=temp.resolve("report.xlsx");
        try(var book=new XSSFWorkbook()){var sheet=book.createSheet("report");sheet.createRow(0).createCell(0).setCellValue("order_id");sheet.createRow(1).createCell(0).setCellValue("O-1");try(var out=Files.newOutputStream(file)){book.write(out);}}
        var rows=new ArrayList<ReportParser.Row>();ReportParser.read(file,h->{},rows::add);
        assertEquals("O-1",rows.get(0).values().get("order_id"));
        try(var book=new XSSFWorkbook()){var sheet=book.createSheet("report");sheet.createRow(0).createCell(0).setCellValue("value");sheet.createRow(1).createCell(0).setCellFormula("1+1");try(var out=Files.newOutputStream(file)){book.write(out);}}
        assertThrows(Exception.class,()->ReportParser.read(file,h->{},r->{}));
    }
    @Test void strictAmountsAndDates()throws Exception {
        assertEquals("-96.00",ImportService.amount("-96",true));
        for(String value:List.of("NaN","1e3","10.001","1,000.00","9999999999999"))assertThrows(IllegalArgumentException.class,()->ImportService.amount(value,true));
        assertThrows(IllegalArgumentException.class,()->ImportService.amount("-1",false));
        Map<String,Object> date=new HashMap<>();date.put("date","2026-02-30");
        assertThrows(IllegalArgumentException.class,()->ImportService.date(date,"date",true));
    }
    @Test void missingMappingFieldsAndRowValidation()throws Exception {
        var service=new ImportService(null,null,new DataSourceTransactionManager(),temp.toString());
        try {
            assertThrows(IllegalArgumentException.class,()->service.validateMapping("ORDERS",Map.of("order_id","id"),List.of("id")));
            var mapping=Map.of("order_id","id","value","value","payment","pay","delivery","state","order_date","date","return_received","received");
            var raw=Map.<String,Object>of("id","O-1","value","100","pay","COD","state","Delivered","date","2026-01-01","received","TRUE");
            var row=service.canonical("ORDERS",mapping,raw);
            assertEquals("DELIVERED",row.get("delivery"));assertEquals("100.00",row.get("value"));
            assertEquals(true,row.get("return_received"));
        } finally {service.stop();}
    }
}
