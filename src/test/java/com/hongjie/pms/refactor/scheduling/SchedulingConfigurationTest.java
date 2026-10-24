package com.hongjie.pms.refactor.scheduling;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulingConfigurationTest {
    @Configuration(proxyBeanMethods=false)
    @EnableConfigurationProperties(SchedulingProperties.class)
    static class Binding {}
    ApplicationContextRunner modes(){
        return new ApplicationContextRunner().withUserConfiguration(Binding.class,LocalJobScheduler.class,XxlJobHandlers.class)
                .withBean(PmsJobActions.class,()->mock(PmsJobActions.class));
    }
    @Test void defaultUsesOnlyLocalScheduler(){
        modes().run(context->{assertThat(context).hasSingleBean(LocalJobScheduler.class);assertThat(context).doesNotHaveBean(XxlJobHandlers.class);});
    }
    @Test void xxlModeDisablesLocalScheduler(){
        modes().withPropertyValues("pms.scheduling.mode=xxl").run(context->{
            assertThat(context).doesNotHaveBean(LocalJobScheduler.class);assertThat(context).hasSingleBean(XxlJobHandlers.class);
        });
    }
    @Test void invalidModeFailsInsteadOfSilentlyDisablingAllJobs(){
        modes().withPropertyValues("pms.scheduling.mode=typo").run(context->assertThat(context).hasFailed());
    }
    @Test void xxlModeRequiresAccessToken(){
        new ApplicationContextRunner().withUserConfiguration(SchedulingConfiguration.class)
                .withPropertyValues("pms.scheduling.mode=xxl")
                .run(context->assertThat(context.getStartupFailure()).hasRootCauseMessage("XXL-JOB access token must be configured"));
    }
    @Test void allFiveStableHandlersAreRegistered(){
        Set<String> handlers=new HashSet<>();
        for(var method:XxlJobHandlers.class.getDeclaredMethods()){
            var annotation=method.getAnnotation(XxlJob.class);if(annotation!=null)handlers.add(annotation.value());
        }
        assertThat(handlers).containsExactlyInAnyOrder("pmsDueTasks","pmsActivityStatus","pmsInboxCleanup","pmsDailyStatistics","pmsStatisticsCompensation");
    }
    @Test void executorAcceptsAuthenticatedTriggerReportsFailureAndSuccessToAdmin()throws Exception{
        String token="integration-only-token";
        var callbacks=new CopyOnWriteArrayList<String>();
        var admin=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        admin.createContext("/xxl-job-admin/api/",exchange->{
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            if(exchange.getRequestURI().getPath().endsWith("/callback"))callbacks.add(body);
            byte[] data="{\"code\":200,\"msg\":null,\"content\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);
            try(var stream=exchange.getResponseBody()){stream.write(data);}
        });admin.start();
        int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        var jobs=mock(PmsJobActions.class);
        doThrow(new IllegalStateException("injected job failure")).doNothing().when(jobs).dueTasks();
        var client=HttpClient.newHttpClient();var json=new ObjectMapper();int executorPort=port;
        try{
            new ApplicationContextRunner().withUserConfiguration(SchedulingConfiguration.class,XxlJobHandlers.class)
                    .withBean(PmsJobActions.class,()->jobs)
                    .withPropertyValues("pms.scheduling.mode=xxl","pms.scheduling.xxl.access-token="+token,
                            "pms.scheduling.xxl.admin-addresses=http://127.0.0.1:"+admin.getAddress().getPort()+"/xxl-job-admin",
                            "pms.scheduling.xxl.port="+port,"pms.scheduling.xxl.logpath="+Path.of("target/xxl-executor-it").toAbsolutePath())
                    .run(context->{
                        assertThat(context).hasNotFailed();
                        String url="http://127.0.0.1:"+executorPort;
                        await(()->{try{return post(client,url+"/beat","{}",token).contains("\"code\":200");}catch(Exception e){return false;}},10000);
                        assertThat(post(client,url+"/beat","{}",null)).contains("\"code\":500");
                        for(long logId:List.of(91001L,91002L)){
                            Map<String,Object> payload=new HashMap<>();
                            payload.put("jobId",901);payload.put("executorHandler","pmsDueTasks");payload.put("executorParams","");
                            payload.put("executorBlockStrategy","DISCARD_LATER");payload.put("executorTimeout",0);
                            payload.put("logId",logId);payload.put("logDateTime",System.currentTimeMillis());
                            payload.put("glueType","BEAN");payload.put("glueUpdatetime",0);payload.put("broadcastIndex",0);payload.put("broadcastTotal",1);
                            assertThat(post(client,url+"/run",json.writeValueAsString(payload),token)).contains("\"code\":200");
                            await(()->callbacks.stream().anyMatch(body->body.contains(String.valueOf(logId))),15000);
                        }
                        assertThat(callbacks.stream().filter(body->body.contains("91001")).findFirst().orElseThrow()).contains("\"handleCode\":500");
                        assertThat(callbacks.stream().filter(body->body.contains("91002")).findFirst().orElseThrow()).contains("\"handleCode\":200");
                        verify(jobs,times(2)).dueTasks();
                    });
        }finally{admin.stop(0);}
    }
    static String post(HttpClient client,String url,String body,String token)throws Exception{
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(3))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if(token!=null)request.header("XXL-JOB-ACCESS-TOKEN",token);
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString()).body();
    }
    static void await(java.util.function.BooleanSupplier condition,long timeout)throws Exception{
        long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
        while(!condition.getAsBoolean()){if(System.nanoTime()>until)throw new AssertionError("timeout waiting for executor");Thread.sleep(50);}
    }
}
