package com.reconflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AiMatchingTest{
    final ObjectMapper json=new ObjectMapper();
    @Test void candidatesAreAdvisoryAndBoundToKnownOpaqueTokens()throws Exception{
        var rows=AiMatching.validate(json.readTree("{\"suggestions\":[{\"candidate\":\"C1\",\"confidence\":0.6,\"reason\":\"Ambiguous comparison\"}]}"),Set.of("C1"));
        assertEquals(true,rows.get(0).get("uncertain"));assertEquals("C1",rows.get(0).get("candidate"));
    }
    @Test void inventedDuplicateAndInvalidCandidatesAreRejected()throws Exception{
        for(String body:List.of(
            "{\"suggestions\":[{\"candidate\":\"C2\",\"confidence\":1,\"reason\":\"\"}]}",
            "{\"suggestions\":[{\"candidate\":\"C1\",\"confidence\":-1,\"reason\":\"\"}]}",
            "{\"suggestions\":[],\"approve\":true}",
            "{\"suggestions\":[{\"candidate\":\"C1\",\"confidence\":1,\"reason\":\"\"},{\"candidate\":\"C1\",\"confidence\":1,\"reason\":\"\"}]}")){
            var node=json.readTree(body);assertThrows(IllegalArgumentException.class,()->AiMatching.validate(node,Set.of("C1")));
        }
    }
}
