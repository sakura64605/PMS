package com.hongjie.pms.refactor.ai;
import com.hongjie.pms.AI.agent.AIAgentEngine;
import com.hongjie.pms.AI.modules.dto.request.AIAgentRequest;
import com.hongjie.pms.AI.modules.dto.response.AIAgentResponse;
import com.hongjie.pms.AI.modules.entity.AiChatSession;
import com.hongjie.pms.AI.modules.service.*;
import com.hongjie.pms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiConversationTest {
    AiSessions sessions=mock(AiSessions.class);AIAgentEngine engine=mock(AIAgentEngine.class);
    AiConversationGateway gateway=new AiConversationGateway(sessions,engine);
    AIAgentRequest input(String id){var req=new AIAgentRequest();req.setSessionId(id);req.setMessage("hello");req.setUserId(99L);return req;}
    @Test void authenticatedActorReplacesClientIdentityAndResponseUsesPersistedId(){
        when(sessions.create(7L,"web")).thenReturn("owned");
        when(engine.process(any())).thenAnswer(inv->{AiTurnContext.saved("assistant","persisted");return AIAgentResponse.builder().messageId("model-only").build();});
        var input=input(null);var response=gateway.process(7L,input);
        var request=ArgumentCaptor.forClass(AIAgentRequest.class);verify(engine).process(request.capture());
        assertThat(request.getValue().getUserId()).isEqualTo(7L);assertThat(request.getValue().getSessionId()).isEqualTo("owned");
        assertThat(response.getMessageId()).isEqualTo("persisted");assertThat(input.getUserId()).isEqualTo(99L);
    }
    @Test void invalidMessagesDoNotCreateSessionsOrCallModel(){
        assertThatThrownBy(()->gateway.process(null,input(null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->gateway.process(7L,null)).isInstanceOf(BusinessException.class);
        var request=input(null);request.setMessage(" ");assertThatThrownBy(()->gateway.process(7L,request)).isInstanceOf(BusinessException.class);
        request.setMessage("a".repeat(8001));assertThatThrownBy(()->gateway.process(7L,request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(sessions,engine);
    }
    @Test void foreignAndHumanSessionsAreRejectedBeforeModelCalls(){
        when(sessions.require("foreign",7L,false,false)).thenThrow(new BusinessException(404,"missing"));
        assertThatThrownBy(()->gateway.process(7L,input("foreign"))).isInstanceOf(BusinessException.class);
        var human=new AiChatSession();human.setStatus(3);when(sessions.require("human",7L,false,false)).thenReturn(human);
        assertThatThrownBy(()->gateway.process(7L,input("human"))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(engine);
    }
    @Test void turnContextIsCleanedAfterModelFailureAndThreadLocal(){
        when(sessions.create(7L,"web")).thenReturn("owned");when(engine.process(any())).thenThrow(new IllegalStateException("model failure"));
        assertThatThrownBy(()->gateway.process(7L,input(null))).isInstanceOf(IllegalStateException.class);
        try(var context=AiTurnContext.open()){assertThat(context.assistantId()).isNull();}
    }
    @Test void concurrentUsersCannotReceiveEachOthersPersistedMessageId()throws Exception{
        when(sessions.create(anyLong(),eq("web"))).thenAnswer(inv->"session"+inv.getArgument(0));
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        when(engine.process(any())).thenAnswer(inv->{
            AIAgentRequest request=inv.getArgument(0);AiTurnContext.saved("assistant","saved"+request.getUserId());
            barrier.await(5,java.util.concurrent.TimeUnit.SECONDS);return AIAgentResponse.builder().content("reply").build();
        });
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->gateway.process(7L,input(null)));
            var b=pool.submit(()->gateway.process(8L,input(null)));
            assertThat(a.get(5,java.util.concurrent.TimeUnit.SECONDS).getMessageId()).isEqualTo("saved7");
            assertThat(b.get(5,java.util.concurrent.TimeUnit.SECONDS).getMessageId()).isEqualTo("saved8");
        }finally{pool.shutdownNow();}
    }
    @Test void allLegacyPublicMethodsAreOverridden()throws Exception{
        for(var pair:List.of(List.of(ChatSessionService.class,PmsChatSessionService.class),List.of(ChatMemoryService.class,PmsChatMemoryService.class),
                List.of(HumanTransferService.class,PmsHumanTransferService.class)))
            for(var method:pair.get(0).getDeclaredMethods())if(java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                assertThat(pair.get(1).getDeclaredMethod(method.getName(),method.getParameterTypes())).isNotNull();
    }
}
