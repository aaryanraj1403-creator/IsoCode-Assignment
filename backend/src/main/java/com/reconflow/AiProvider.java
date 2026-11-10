package com.reconflow;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** The endpoint is fixed in production; the package-private transport seam is for offline HTTP tests. */
@Component
class AiProvider {
    static final int MAX_RESPONSE_BYTES=128*1024;
    record Result(JsonNode data,String message) { boolean available(){return data!=null;} }
    final ObjectMapper json;
    final String key,model;
    final HttpClient client;
    final URI endpoint;
    final Duration timeout;
    @Autowired AiProvider(ObjectMapper json,Environment env) {
        this(json,env.getProperty("OPENAI_API_KEY",""),env.getProperty("OPENAI_MODEL",""),
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            URI.create("https://api.openai.com/v1/responses"),Duration.ofSeconds(25));
    }
    AiProvider(ObjectMapper json,String key,String model,HttpClient client,URI endpoint,Duration timeout) {
        this.json=json;this.key=key;this.model=model;this.client=client;this.endpoint=endpoint;this.timeout=timeout;
    }
    boolean configured(){return !key.isBlank()&&!model.isBlank();}
    Result request(String name,String instructions,Object input,Object schema) {
        if(!configured())return new Result(null,"AI is not configured. Set OPENAI_API_KEY and OPENAI_MODEL on the backend, or continue manually.");
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            var payload=Map.of("model",model,"store",false,"max_output_tokens",4000,"instructions",instructions,
                "input",json.writeValueAsString(input),
                "text",Map.of("format",Map.of("type","json_schema","name",name,"strict",true,"schema",schema)));
            var request=HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization","Bearer "+key).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            pending=client.sendAsync(request,info->new LimitedBody());
            var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
            if(response.statusCode()!=200)return new Result(null,"AI provider is unavailable or rejected the request. Continue manually.");
            var root=json.readTree(response.body());
            if(!"completed".equals(root.path("status").asText()))return new Result(null,"AI response was incomplete. Continue manually.");
            String output=null;int texts=0;
            for(var item:root.path("output"))for(var content:item.path("content")){
                if("refusal".equals(content.path("type").asText()))return new Result(null,"AI declined this request. Continue manually.");
                if("output_text".equals(content.path("type").asText())){output=content.path("text").asText();texts++;}
            }
            if(texts!=1 || output==null || output.isBlank())return new Result(null,"AI returned no usable response. Continue manually.");
            return new Result(json.readTree(output),"");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();return new Result(null,"AI request interrupted. Continue manually.");
        } catch(Exception e) {
            return new Result(null,"AI response was invalid, too large, or timed out. Continue manually.");
        } finally {
            if(pending!=null && !pending.isDone())pending.cancel(true);
        }
    }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(ByteBuffer buffer:buffers){
                if(buffer.remaining()>MAX_RESPONSE_BYTES-bytes.size()){
                    subscription.cancel();result.completeExceptionally(new IOException("AI response exceeds limit"));return;
                }
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable e){result.completeExceptionally(e);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
