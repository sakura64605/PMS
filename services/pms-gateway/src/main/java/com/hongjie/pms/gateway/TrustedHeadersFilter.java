package com.hongjie.pms.gateway;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import java.util.Locale;
@Component
public class TrustedHeadersFilter implements GlobalFilter,Ordered {
    public int getOrder(){return -100;}
    public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange,GatewayFilterChain chain){
        if(exchange.getRequest().getPath().value().contains("/internal/")){
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);return exchange.getResponse().setComplete();
        }
        var request=exchange.getRequest().mutate().headers(headers->{
            for(String name:java.util.List.copyOf(headers.keySet()))if(name.toLowerCase(Locale.ROOT).startsWith("x-pms-"))headers.remove(name);
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
