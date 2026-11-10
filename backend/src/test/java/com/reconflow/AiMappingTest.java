package com.reconflow;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiMappingTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void validUncertainOutputRequiresManualReview()throws Exception {
        var rows=AiMapping.validate(json.readTree("{\"suggestions\":[{\"field\":\"order_id\",\"header\":\"ID\",\"confidence\":0.6,\"reason\":\"Ambiguous\"}]}"),List.of("order_id"),List.of("ID"));
        assertEquals(true,rows.get(0).get("uncertain"));
    }
    @Test void maliciousUnknownAndMalformedOutputsAreRejected()throws Exception {
        for(String value:List.of(
            "{\"suggestions\":[{\"field\":\"order_id\",\"header\":\"DROP TABLE\",\"confidence\":1,\"reason\":\"\"}]}",
            "{\"suggestions\":[{\"field\":\"order_id\",\"header\":\"ID\",\"confidence\":2,\"reason\":\"\"}]}",
            "{\"suggestions\":[],\"approve\":true}","{\"suggestions\":[]}","{\"mapping\":{}}")){
            var node=json.readTree(value);assertThrows(IllegalArgumentException.class,()->AiMapping.validate(node,List.of("order_id"),List.of("ID")));
        }
    }
    @Test void failureDoesNotFabricateSuggestions() {
        var result=AiMapping.unavailable("timeout");
        assertEquals(false,result.get("available"));assertEquals(List.of(),result.get("suggestions"));
    }
}
