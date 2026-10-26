package com.hongjie.pms.aiservice.core;

import com.hongjie.pms.ai.contract.AiUserProfile;
import com.hongjie.pms.modules.ai.api.dto.ToolCall;
import com.hongjie.pms.platform.pojo.*;
import com.hongjie.pms.platform.exception.BusinessException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.*;
import java.util.Map;

/** All remote failures stay failures; an unavailable core never becomes an empty personal result. */
public class AiCoreClient {
    private final RestClient http;
    private final String secret;
    public AiCoreClient(RestClient http,String secret) {
        if(secret==null||secret.length()<32)throw new IllegalArgumentException("AI core service token is required (32+ characters)");
        this.http=http;this.secret=secret;
    }
    public UserInfo authenticate(String token) {
        if(token==null||token.isBlank())return null;
        try {
            return checked(http.post().uri("/internal/ai/v1/authenticate").header("X-PMS-Service-Token",secret)
                .body(Map.of("token",token)).retrieve().body(new ParameterizedTypeReference<CommonResult<UserInfo>>(){}));
        } catch(BusinessException failure) {if(failure.getCode()==401)return null;throw failure;}
          catch(RestClientException failure) {throw unavailable();}
    }
    public ToolCall tool(String name,Map<String,Object> args,Long actor) {
        if(actor==null||actor<=0)throw new BusinessException(401,"请先登录");
        try {return checked(http.post().uri("/internal/ai/v1/tools/{name}",name).header("X-PMS-Service-Token",secret)
            .body(Map.of("actor",actor,"arguments",args==null?Map.of():args)).retrieve().body(new ParameterizedTypeReference<CommonResult<ToolCall>>(){}));}
        catch(RestClientException failure){throw unavailable();}
    }
    public AiUserProfile profile(Long actor) {
        try {return checked(http.get().uri("/internal/ai/v1/profile/{actor}",actor).header("X-PMS-Service-Token",secret)
            .retrieve().body(new ParameterizedTypeReference<CommonResult<AiUserProfile>>(){}));}
        catch(RestClientException failure){throw unavailable();}
    }
    private <T> T checked(CommonResult<T> reply) {
        if(reply==null||reply.getCode()==null)throw unavailable();
        if(reply.getCode()!=200)throw new BusinessException(reply.getCode(),reply.getMessage());
        if(reply.getData()==null)throw unavailable();
        return reply.getData();
    }
    private BusinessException unavailable(){return new BusinessException(503,"业务查询服务暂时不可用，请稍后重试");}
}
