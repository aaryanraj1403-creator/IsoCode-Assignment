package com.reconflow;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.apache.commons.csv.*;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.xssf.eventusermodel.*;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

final class ReportParser {
    record Row(int number,Map<String,String> values) {}
    private ReportParser() {}
    static void headers(List<String> headers) {
        Support.require(!headers.isEmpty() && headers.size()<=100,"Reports require 1..100 columns");
        Support.require(new HashSet<>(headers).size()==headers.size(),"Duplicate column headers");
        Support.require(headers.stream().allMatch(h -> h!=null && !h.isBlank() && h.length()<=128 && !h.equals("_parse_error")),"Headers must be nonblank and at most 128 characters");
    }
    static void read(Path path,Consumer<List<String>> onHeader,Consumer<Row> consumer) throws Exception {
        if(path.toString().endsWith(".csv")) {
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
            try(var reader=new BufferedReader(new InputStreamReader(Files.newInputStream(path),decoder))) {
                reader.mark(1); if(reader.read()!=0xfeff) reader.reset();
                try(var parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build().parse(reader)) {
                    var names=parser.getHeaderNames(); headers(names); onHeader.accept(names);
                    for(CSVRecord record:parser) {
                        Support.require(record.getRecordNumber()<=250000,"Maximum 250,000 data rows per report");
                        Map<String,String> values=new LinkedHashMap<>();
                        for(int i=0;i<names.size();i++) values.put(names.get(i),i<record.size()?limit(record.get(i)):"");
                        if(record.size()!=names.size()) values.put("_parse_error","Column count differs from header");
                        consumer.accept(new Row((int)record.getRecordNumber()+1,values));
                    }
                }
            }
        } else {
            try(OPCPackage pkg=OPCPackage.open(path.toFile(),PackageAccess.READ)) {
                XSSFReader reader=new XSSFReader(pkg,true);
                var sheets=reader.getSheetsData();
                Support.require(sheets.hasNext(),"Workbook contains no sheet");
                try(InputStream sheet=sheets.next()) {
                    var xml=XMLHelper.newXMLReader();
                    var handler=new XSSFSheetXMLHandler.SheetContentsHandler() {
                        List<String> names;
                        List<String> cells;
                        int row;
                        public void startRow(int n) { row=n; cells=new ArrayList<>(); }
                        public void cell(String ref,String value,org.apache.poi.xssf.usermodel.XSSFComment comment) {
                            int index=new CellReference(ref).getCol();
                            Support.require(index<100,"Maximum 100 columns");
                            while(cells.size()<=index) cells.add("");
                            cells.set(index,limit(value));
                        }
                        public void endRow(int n) {
                            if(names==null) { names=new ArrayList<>(cells); headers(names); onHeader.accept(names); return; }
                            if(cells.stream().allMatch(String::isBlank)) return;
                            Support.require(row<=250000,"Maximum 250,000 data rows");
                            Map<String,String> values=new LinkedHashMap<>();
                            for(int i=0;i<names.size();i++) values.put(names.get(i),i<cells.size()?cells.get(i):"");
                            if(cells.size()>names.size()) values.put("_parse_error","Extra columns beyond header");
                            consumer.accept(new Row(row+1,values));
                        }
                    };
                    var sheetHandler=new XSSFSheetXMLHandler(reader.getStylesTable(),null,reader.getSharedStringsTable(),handler,new DataFormatter(Locale.US),false);
                    xml.setContentHandler(new org.xml.sax.helpers.XMLFilterImpl() {
                        @Override public void startElement(String u,String l,String q,Attributes a)throws SAXException {
                            if("f".equals(l) || "f".equals(q)) throw new SAXException("Formula cells are not accepted; export values only");
                            sheetHandler.startElement(u,l,q,a);
                        }
                        @Override public void endElement(String u,String l,String q)throws SAXException { sheetHandler.endElement(u,l,q); }
                        @Override public void characters(char[] c,int a,int b)throws SAXException { sheetHandler.characters(c,a,b); }
                    });
                    xml.parse(new InputSource(sheet));
                }
                Support.require(!sheets.hasNext(),"Use a single-sheet XLSX report");
            }
        }
    }
    static String limit(String value) {
        Support.require(value.length()<=4096,"Cells must be at most 4096 characters");
        return value;
    }
}
