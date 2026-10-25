package com.hongjie.pms.refactor.ai;
import com.hongjie.pms.AI.modules.controller.AIAgentController;
import com.hongjie.pms.AI.modules.service.*;
import com.hongjie.pms.AI.modules.dto.request.AIAgentRequest;
import com.hongjie.pms.AI.modules.dto.response.AIAgentResponse;
import com.hongjie.pms.AI.websocket.AIAgentWebSocketHandler;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.refactor.user.UserSessions;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.net.URI;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AiTransportTest {
    @Test void existingHttpPathsAndResponseEnvelopeRemainCompatible()throws Exception{
        var gateway=mock(AiConversationGateway.class);var sessions=mock(ChatSessionService.class);var transfers=mock(HumanTransferService.class);
        var controller=new AIAgentController(gateway,sessions,transfers);var actor=new UserInfo();actor.setUserId(7L);UserContext.setUser(actor);
        when(gateway.process(eq(7L),any())).thenReturn(AIAgentResponse.builder().sessionId("owned").messageId("saved").content("reply").build());
        try{
            var mvc=MockMvcBuilders.standaloneSetup(controller).build();
            mvc.perform(post("/pet-system/ai/chat").contentType("application/json").content("{\"userId\":99,\"message\":\"hello\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value("owned")).andExpect(jsonPath("$.data.messageId").value("saved"));
            mvc.perform(delete("/pet-system/ai/memory/owned")).andExpect(status().isOk());
            mvc.perform(post("/pet-system/ai/feedback").param("messageId","saved").param("score","0")).andExpect(status().isOk());
            mvc.perform(post("/pet-system/ai/transfer").param("sessionId","owned")).andExpect(status().isOk());
            verify(sessions).clearMemory("owned");verify(sessions).submitFeedback("saved",0);verify(transfers).requestTransfer("owned",7L,null);
        }finally{UserContext.clear();}
    }
    @Test void websocketUsesDatabaseSessionAndAuthenticatedActorAcrossMessages()throws Exception{
        var gateway=mock(AiConversationGateway.class);var auth=mock(UserSessions.class);var handler=new AIAgentWebSocketHandler(gateway,auth);
        var actor=new UserInfo();actor.setUserId(7L);when(auth.authenticate("token")).thenReturn(actor);
        var session=mock(WebSocketSession.class);when(session.getId()).thenReturn("transport-id");
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ai/ws?token=token"));when(session.isOpen()).thenReturn(true);
        var prepared=new AIAgentRequest();prepared.setSessionId("database-session");
        when(gateway.prepare(eq(7L),any(),eq("websocket"))).thenReturn(prepared);
        when(gateway.process(eq(7L),any(),eq("websocket"))).thenAnswer(inv->{
            AIAgentRequest request=inv.getArgument(1);
            assertThat(request.getSessionId()).isEqualTo("database-session");assertThat(request.getUserId()).isEqualTo(7L);
            return AIAgentResponse.builder().sessionId("database-session").content("reply").build();
        });
        handler.afterConnectionEstablished(session);
        try{
            handler.handleMessage(session,new TextMessage("{\"message\":\"hello\",\"userId\":99}"));
            handler.handleMessage(session,new TextMessage("{\"message\":\"again\"}"));
            verify(gateway,times(1)).prepare(eq(7L),any(),eq("websocket"));verify(gateway,times(2)).process(eq(7L),any(),eq("websocket"));
            verify(session,times(2)).sendMessage(any(TextMessage.class));
        }finally{handler.afterConnectionClosed(session,CloseStatus.NORMAL);}
    }
    @Test void websocketRechecksAuthenticationBeforeProcessingNewMessages()throws Exception{
        var gateway=mock(AiConversationGateway.class);var auth=mock(UserSessions.class);var handler=new AIAgentWebSocketHandler(gateway,auth);
        var actor=new UserInfo();actor.setUserId(7L);when(auth.authenticate("token")).thenReturn(actor);
        var session=mock(WebSocketSession.class);when(session.getId()).thenReturn("expired");
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ai/ws?token=token"));
        handler.afterConnectionEstablished(session);when(auth.authenticate("token")).thenReturn(null);
        try{
            handler.handleMessage(session,new TextMessage("{\"message\":\"hello\"}"));
            verify(session).close(CloseStatus.NOT_ACCEPTABLE);verifyNoInteractions(gateway);
        }finally{handler.afterConnectionClosed(session,CloseStatus.NORMAL);}
    }
}
