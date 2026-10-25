package com.hongjie.pms.refactor.ai;
import com.hongjie.pms.refactor.ai.api.HumanTransferService;
import com.hongjie.pms.refactor.ai.api.ChatSessionService;
import com.hongjie.pms.refactor.ai.api.ChatMemoryService;

import com.hongjie.pms.refactor.ai.agent.AIAgentEngine;
import com.hongjie.pms.AI.modules.service.*;
import com.hongjie.pms.AI.modules.entity.*;
import com.hongjie.pms.AI.modules.mapper.*;
import com.hongjie.pms.AI.modules.dto.request.AIAgentRequest;
import com.hongjie.pms.AI.modules.dto.response.AIAgentResponse;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

@EnabledIfEnvironmentVariable(named="PMS_AI_MYSQL_TESTS",matches="true")
class AiSessionModuleMySqlTest {
    static AnnotationConfigApplicationContext context;
    static AiSessions sessions;static AiTransfers transfers;static AiMessageWriter writer;
    static AiChatSessionMapper rows;static AiChatMessageMapper messages;static AiHumanTransferMapper handoffs;
    static RedisTemplate<String,Object> redis;static AIAgentEngine engine;
    static AtomicBoolean failUpdate=new AtomicBoolean();
    Set<String> owned=new HashSet<>();long actor;
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @Import({AiSessions.class,AiTransfers.class,AiSessionCache.class,AiMessageWriter.class,AiConversationGateway.class,PmsChatSessionService.class,PmsChatMemoryService.class,PmsHumanTransferService.class})
    static class Config {}
    @BeforeAll static void setup(){
        Assumptions.assumeTrue(MyBatisTestSupport.configured());
        var factory=MyBatisTestSupport.createFactory(AiChatSessionMapper.class,AiChatMessageMapper.class,AiHumanTransferMapper.class);
        var template=new SqlSessionTemplate(factory);
        var actual=template.getMapper(AiChatSessionMapper.class);
        rows=mock(AiChatSessionMapper.class,delegatesTo(actual));
        doAnswer(inv->{
            if(failUpdate.get())throw new IllegalStateException("injected session update failure");
            return actual.update(inv.getArgument(0),inv.getArgument(1));
        }).when(rows).update(nullable(AiChatSession.class),any(Wrapper.class));
        messages=template.getMapper(AiChatMessageMapper.class);handoffs=template.getMapper(AiHumanTransferMapper.class);
        redis=mock(RedisTemplate.class);engine=mock(AIAgentEngine.class);
        context=new AnnotationConfigApplicationContext();
        context.registerBean(AiChatSessionMapper.class,()->rows);context.registerBean(AiChatMessageMapper.class,()->messages);
        context.registerBean(AiHumanTransferMapper.class,()->handoffs);context.registerBean(RedisTemplate.class,()->redis);
        context.registerBean(AIAgentEngine.class,()->engine);
        context.registerBean(org.springframework.transaction.PlatformTransactionManager.class,
            ()->new DataSourceTransactionManager(factory.getConfiguration().getEnvironment().getDataSource()));
        context.register(Config.class);context.refresh();
        sessions=context.getBean(AiSessions.class);transfers=context.getBean(AiTransfers.class);writer=context.getBean(AiMessageWriter.class);
    }
    @BeforeEach void before(){
        actor=900000000L+new Random().nextInt(10000000);identity(actor,0);failUpdate.set(false);reset(redis,engine);
    }
    @AfterEach void cleanup(){
        failUpdate.set(false);UserContext.clear();
        for(String id:owned){
            var row=rows.selectBySessionId(id);assertThat(row.getUserId()).isEqualTo(actor);
            messages.delete(new LambdaQueryWrapper<AiChatMessage>().eq(AiChatMessage::getSessionId,id));
            handoffs.delete(new LambdaQueryWrapper<AiHumanTransfer>().eq(AiHumanTransfer::getSessionId,id));
            rows.delete(new LambdaQueryWrapper<AiChatSession>().eq(AiChatSession::getSessionId,id));
        }
    }
    @AfterAll static void close(){if(context!=null)context.close();}
    static void identity(long id,int role){var user=new UserInfo();user.setUserId(id);user.setRole(role);UserContext.setUser(user);}
    String create(){String id=sessions.create(actor,"web");owned.add(id);return id;}
    AiHumanTransfer transfer(String id){return handoffs.selectOne(new LambdaQueryWrapper<AiHumanTransfer>().eq(AiHumanTransfer::getSessionId,id));}
    @Test void primaryFacadesAreActiveAndSessionListKeepsHumanSessions(){
        assertThat(context.getBean(ChatSessionService.class)).isInstanceOf(PmsChatSessionService.class);
        assertThat(context.getBean(ChatMemoryService.class)).isInstanceOf(PmsChatMemoryService.class);
        assertThat(context.getBean(HumanTransferService.class)).isInstanceOf(PmsHumanTransferService.class);
        String id=create();transfers.request(id,actor,"help");
        assertThat(sessions.list(actor)).extracting(AiChatSession::getSessionId).contains(id);
    }
    @Test void foreignHistoryClearFeedbackTransferAndDeletionAreRejected(){
        String id=create(),message=writer.append(id,"assistant","reply",actor,1,null);reset(redis);
        assertThatThrownBy(()->sessions.history(id,actor+1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->sessions.clear(id,actor+1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->sessions.feedback(message,1,actor+1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->transfers.request(id,actor+1,"bad")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->sessions.delete(id,actor+1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(redis);assertThat(rows.selectBySessionId(id).getStatus()).isEqualTo(1);assertThat(transfer(id)).isNull();
    }
    @Test void feedbackAcceptsOnlyOwnedAssistantMessagesAndZeroOrOne(){
        String id=create(),user=writer.append(id,"user","question",actor,null,null),answer=writer.append(id,"assistant","reply",actor,3,null);
        assertThatThrownBy(()->sessions.feedback(user,1,actor)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->sessions.feedback(answer,2,actor)).isInstanceOf(BusinessException.class);
        sessions.feedback(answer,0,actor);
        assertThat(sessions.history(id,actor).get(1).getFeedback()).isZero();
        sessions.feedback(answer,1,actor);assertThat(sessions.history(id,actor).get(1).getFeedback()).isEqualTo(1);
    }
    @Test void messagesAndCountsCommitTogetherAndRecentMemoryUsesDatabase(){
        String id=create();var memory=context.getBean(ChatMemoryService.class);
        memory.saveMessage(id,"user","question",actor);memory.saveMessage(id,"assistant","reply",actor,8,"[]");
        assertThat(rows.selectBySessionId(id).getMessageCount()).isEqualTo(2);
        assertThat(memory.getRecentMessages(id,1)).extracting(ChatMemoryService.MemoryMessage::getContent).containsExactly("question","reply");
        assertThat(memory.getRecentMessages(id,0)).isEmpty();verify(redis,times(2)).delete("ai:memory:session:"+id);
        assertThat(sessions.history(id,actor).get(1).getTokensUsed()).isEqualTo(8);
    }
    @Test void databaseFailureRollsBackMessageWithoutTouchingRedis(){
        String id=create();reset(redis);failUpdate.set(true);
        assertThatThrownBy(()->writer.append(id,"assistant","reply",actor,1,null)).isInstanceOf(IllegalStateException.class);
        failUpdate.set(false);assertThat(sessions.history(id,actor)).isEmpty();
        assertThat(rows.selectBySessionId(id).getMessageCount()).isZero();verifyNoInteractions(redis);
    }
    @Test void redisFailureDoesNotDiscardCommittedMessage(){
        String id=create();doThrow(new IllegalStateException("injected redis failure")).when(redis).delete(anyString());
        assertThatCode(()->writer.append(id,"assistant","reply",actor,1,null)).doesNotThrowAnyException();
        assertThat(sessions.history(id,actor)).hasSize(1);
        assertThat(context.getBean(ChatMemoryService.class).getRecentMessages(id,1)).hasSize(1);
    }
    @Test void clearKeepsDatabaseHistoryButReportsRedisFailure(){
        String id=create();writer.append(id,"assistant","reply",actor,1,null);reset(redis);
        doThrow(new IllegalStateException("injected redis failure")).when(redis).delete(anyString());
        assertThatThrownBy(()->sessions.clear(id,actor)).isInstanceOf(IllegalStateException.class);
        assertThat(sessions.history(id,actor)).hasSize(1);
    }
    @Test void duplicateTransferRequestsCreateOneOpenRow()throws Exception{
        String id=create();race(()->transfers.request(id,actor,"a"),()->transfers.request(id,actor,"b"));
        assertThat(handoffs.selectCount(new LambdaQueryWrapper<AiHumanTransfer>().eq(AiHumanTransfer::getSessionId,id))).isEqualTo(1);
        assertThat(rows.selectBySessionId(id).getStatus()).isEqualTo(3);
    }
    @Test void transferAcceptanceAndClosureRequireCurrentAdministrator(){
        String id=create();transfers.request(id,actor,"help");Long transfer=transfer(id).getId();
        assertThatThrownBy(()->transfers.accept(transfer,actor)).isInstanceOf(BusinessException.class);
        identity(21,1);assertThatThrownBy(()->transfers.accept(transfer,22L)).isInstanceOf(BusinessException.class);
        transfers.accept(transfer,21L);identity(22,1);
        assertThatThrownBy(()->transfers.close(transfer)).isInstanceOf(BusinessException.class);
        identity(21,1);transfers.close(transfer);assertThat(rows.selectBySessionId(id).getStatus()).isEqualTo(1);
    }
    @Test void closingTransferCannotResurrectDeletedSession()throws Exception{
        String id=create();transfers.request(id,actor,"help");Long transfer=transfer(id).getId();identity(21,1);transfers.accept(transfer,21L);
        race(()->sessions.delete(id,actor),()->{identity(21,1);try{transfers.close(transfer);}finally{UserContext.clear();}});
        assertThat(rows.selectBySessionId(id).getStatus()).isZero();assertThat(transfer(id).getStatus()).isEqualTo(3);
        assertThat(sessions.owns(id,actor)).isFalse();assertThatThrownBy(()->writer.append(id,"assistant","late reply",actor,1,null)).isInstanceOf(BusinessException.class);
    }
    @Test void failedTransferInsertOrStatusUpdateLeavesNoOrphan(){
        String id=create();failUpdate.set(true);
        assertThatThrownBy(()->transfers.request(id,actor,"help")).isInstanceOf(IllegalStateException.class);
        failUpdate.set(false);assertThat(transfer(id)).isNull();assertThat(rows.selectBySessionId(id).getStatus()).isEqualTo(1);
    }
    @Test void deletionRollbackKeepsStatusAndDoesNotEvictCache(){
        String id=create();transfers.request(id,actor,"help");reset(redis);failUpdate.set(true);
        assertThatThrownBy(()->sessions.delete(id,actor)).isInstanceOf(IllegalStateException.class);
        failUpdate.set(false);assertThat(rows.selectBySessionId(id).getStatus()).isEqualTo(3);assertThat(transfer(id).getStatus()).isEqualTo(1);
        verifyNoInteractions(redis);
    }
    @Test void gatewayCorrelatesPersistedMessageIdAndNeverHoldsTransactionDuringModelCall(){
        when(engine.process(any())).thenAnswer(inv->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            AIAgentRequest req=inv.getArgument(0);owned.add(req.getSessionId());
            context.getBean(ChatMemoryService.class).saveMessage(req.getSessionId(),"user",req.getMessage(),req.getUserId());
            context.getBean(ChatMemoryService.class).saveMessage(req.getSessionId(),"assistant","reply",req.getUserId(),2);
            return AIAgentResponse.builder().messageId("different-engine-id").content("reply").build();
        });
        AIAgentRequest request=new AIAgentRequest();request.setMessage("hello");request.setUserId(actor+1);
        var response=context.getBean(AiConversationGateway.class).process(actor,request);
        var history=sessions.history(response.getSessionId(),actor);
        assertThat(response.getMessageId()).isEqualTo(history.get(1).getMessageId());
        sessions.feedback(response.getMessageId(),1,actor);
        assertThat(sessions.history(response.getSessionId(),actor).get(1).getFeedback()).isEqualTo(1);
    }
    @Test void deletingWhileMessageIsSavingNeverAllowsLaterWrites()throws Exception{
        String id=create();
        race(()->{
            try{writer.append(id,"assistant","in flight",actor,1,null);}
            catch(BusinessException denied){assertThat(denied.getCode()).isEqualTo(404);}
        },()->sessions.delete(id,actor));
        assertThat(rows.selectBySessionId(id).getStatus()).isZero();
        long count=messages.selectCount(new LambdaQueryWrapper<AiChatMessage>().eq(AiChatMessage::getSessionId,id));
        assertThat(count).isBetween(0L,1L);
        assertThat(rows.selectBySessionId(id).getMessageCount().longValue()).isEqualTo(count);
        assertThatThrownBy(()->writer.append(id,"assistant","too late",actor,1,null)).isInstanceOf(BusinessException.class);
    }
    private void race(Runnable a,Runnable b)throws Exception{
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{
            Future<?> first=pool.submit(()->{await(gate);a.run();}),second=pool.submit(()->{await(gate);b.run();});
            gate.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
    }
    private void await(CountDownLatch gate){try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
