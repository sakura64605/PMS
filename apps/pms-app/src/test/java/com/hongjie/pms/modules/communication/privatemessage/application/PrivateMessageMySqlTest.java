package com.hongjie.pms.modules.communication.privatemessage.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.communication.privatemessage.api.controller.PrivateMessageController;
import com.hongjie.pms.modules.communication.privatemessage.api.dto.*;
import com.hongjie.pms.modules.communication.privatemessage.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.communication.privatemessage.api.service.PrivateMessageService;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.architecture.support.MyBatisTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="PMS_PRIVATE_MYSQL_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrivateMessageMySqlTest {
    AnnotationConfigApplicationContext context;PmsPrivateMessageServiceImpl service;JdbcTemplate jdbc;TransactionTemplate tx;DataSource source;PrivateMessagePush push;
    long a,b,outsider;final String marker="__private_it_"+UUID.randomUUID().toString().substring(0,18);
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PmsPrivateMessageServiceImpl.class,PrivateMessageCommittedListener.class,PrivateMessageController.class})
    static class Config {
        @Bean PrivateMessagePush push(){return mock(PrivateMessagePush.class);}
        @Bean PrivateMessageService legacyPrivateMessageService(){return mock(PrivateMessageService.class);}
    }
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");assertThat(MyBatisTestSupport.configured()).isTrue();
        Class<?>[] mappers={PrivateMessageWriteMapper.class,PrivateConversationMapper.class,PrivateMessageMapper.class,UserMapper.class};
        var factory=MyBatisTestSupport.createFactory(mappers);source=factory.getConfiguration().getEnvironment().getDataSource();jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("user","private_conversation","private_message"))assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        // Guarded local test schema migration; never touches pet_system or a remote host.
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='private_message' AND column_name='client_message_id'",Integer.class)==0)
            jdbc.execute("ALTER TABLE private_message ADD COLUMN client_message_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL, ADD UNIQUE INDEX uk_private_message_sender_client (from_user_id,client_message_id)");
        assertThat(jdbc.queryForObject("SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='private_message' AND index_name='uk_private_message_sender_client' AND NON_UNIQUE=0",String.class)).isEqualTo("from_user_id,client_message_id");
        var template=new SqlSessionTemplate(factory);var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);tx.setTimeout(15);
        context=new AnnotationConfigApplicationContext();context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        for(Class<?> mapper:mappers)register(mapper,template);context.register(Config.class);context.refresh();service=context.getBean(PmsPrivateMessageServiceImpl.class);push=context.getBean(PrivateMessagePush.class);
        assertThat(context.getBean(PrivateMessageService.class)).isSameAs(service);
    }
    <T> void register(Class<T> type,SqlSessionTemplate template){context.registerBean(type,()->template.getMapper(type));}
    long user(String suffix){return jdbc.execute((java.sql.Connection c)->{
        try(var statement=c.prepareStatement("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",Statement.RETURN_GENERATED_KEYS)){statement.setString(1,marker+suffix);statement.setString(2,"fixture");statement.executeUpdate();try(var keys=statement.getGeneratedKeys()){if(!keys.next())throw new IllegalStateException("missing id");return keys.getLong(1);}}
    });}
    @BeforeEach void fixtures(){reset(push);a=user("a");b=user("b");outsider=user("o");as(a);}
    @AfterEach void cleanup(){
        UserContext.clear();if(jdbc==null)return;
        String owned="c.user_a IN (?,?,?) AND c.user_b IN (?,?,?) AND u.user_name IN (?,?,?)";
        Object[] params={a,b,outsider,a,b,outsider,marker+"a",marker+"b",marker+"o"};
        jdbc.update("DELETE m FROM private_message m JOIN private_conversation c ON m.conversation_id=c.id JOIN user u ON c.user_a=u.id WHERE "+owned,params);
        jdbc.update("DELETE c FROM private_conversation c JOIN user u ON c.user_a=u.id WHERE "+owned,params);
        jdbc.update("DELETE FROM user WHERE id IN (?,?,?) AND user_name IN (?,?,?)",a,b,outsider,marker+"a",marker+"b",marker+"o");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE user_name IN (?,?,?)",Long.class,marker+"a",marker+"b",marker+"o")).isZero();
    }
    @AfterAll void close(){if(context!=null)context.close();}
    void as(long actor){UserContext.setUser(new UserInfo(actor,marker,0,null,null));}
    MessageDto send(long from,long to,String content){as(from);var r=new SendMessageRequest();r.setToUserId(to);r.setContent(content);return service.sendMessage(from,r);}
    long conversation(){return jdbc.queryForObject("SELECT id FROM private_conversation WHERE user_a=? AND user_b=?",Long.class,Math.min(a,b),Math.max(a,b));}
    long messages(){return jdbc.queryForObject("SELECT COUNT(*) FROM private_message WHERE from_user_id IN (?,?) AND to_user_id IN (?,?)",Long.class,a,b,a,b);}
    void race(Runnable one,Runnable two)throws Exception{
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        java.util.function.Function<Runnable,Callable<Void>> task=action->()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");try{action.run();return null;}finally{UserContext.clear();}};
        try{var first=pool.submit(task.apply(one));var second=pool.submit(task.apply(two));assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);}
        finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void concurrentOppositeSendsCreateOneConversationAndPreserveBothUnreadCounts()throws Exception{
        race(()->send(a,b,"from-a"),()->send(b,a,"from-b"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM private_conversation WHERE user_a=? AND user_b=?",Long.class,Math.min(a,b),Math.max(a,b))).isEqualTo(1);assertThat(messages()).isEqualTo(2);
        as(a);assertThat(service.getTotalUnreadCount(a)).isEqualTo(1);as(b);assertThat(service.getTotalUnreadCount(b)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT unread_count_a FROM private_conversation WHERE id=?",Integer.class,conversation())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT unread_count_b FROM private_conversation WHERE id=?",Integer.class,conversation())).isEqualTo(1);verify(push,times(2)).send(anyLong(),any());
    }
    @Test void concurrentCreationReturnsTheSameConversation()throws Exception{
        List<Long> ids=new CopyOnWriteArrayList<>();race(()->{as(a);ids.add(service.getOrCreateConversation(a,b).getConversationId());},()->{as(b);ids.add(service.getOrCreateConversation(b,a).getConversationId());});assertThat(ids).hasSize(2);assertThat(new HashSet<>(ids)).hasSize(1);verifyNoInteractions(push);
    }
    @Test void concurrentReadAndSendKeepCounterConsistentWithMessageRows()throws Exception{
        send(a,b,"first");long id=conversation();race(()->{as(b);service.markConversationRead(b,id);},()->send(a,b,"second"));
        as(b);int unread=service.getTotalUnreadCount(b);assertThat(unread).isBetween(0,1);
        String column=b==Math.min(a,b)?"unread_count_a":"unread_count_b";assertThat(jdbc.queryForObject("SELECT "+column+" FROM private_conversation WHERE id=?",Integer.class,id)).isEqualTo(unread);assertThat(messages()).isEqualTo(2);
    }
    @Test void oneSidedDeletePreservesPeerHistoryAndPreviewAndAllowsNewMessages(){
        send(a,b,"first");send(b,a,"second");long id=conversation();as(a);service.deleteConversation(a,id);assertThat(service.getMessageList(a,id,1,20).getTotal()).isZero();assertThat(service.getConversationList(a,1,20).getRecords().get(0).getLastMessage()).isNull();
        as(b);assertThat(service.getConversationList(b,1,20).getRecords().get(0).getLastMessage()).isEqualTo("second");assertThat(service.getMessageList(b,id,1,20).getTotal()).isEqualTo(2);
        send(a,b,"new");as(a);assertThat(service.getMessageList(a,id,1,20).getTotal()).isEqualTo(1);as(b);assertThat(service.getMessageList(b,id,1,20).getTotal()).isEqualTo(3);
    }
    @Test void outsiderCannotUseAnyConversationReadOrMutationEntrypoint(){
        send(a,b,"private");long id=conversation();as(b);service.getMessageList(b,id,1,20);as(outsider);
        assertThatThrownBy(()->service.getMessageList(outsider,id,1,20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.markConversationRead(outsider,id)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.clearMessages(outsider,id)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.deleteConversation(outsider,id)).isInstanceOf(BusinessException.class);assertThat(messages()).isEqualTo(1);
        as(b);assertThat(service.getMessageList(b,id,1,20).getRecords().get(0).getContent()).isEqualTo("private");
    }
    @Test void outerFailureRollsBackCreationMessageCountersAndPush(){
        assertThatThrownBy(()->tx.executeWithoutResult(status->{send(a,b,"rollback");throw new IllegalStateException("force rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(messages()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM private_conversation WHERE user_a=? AND user_b=?",Long.class,Math.min(a,b),Math.max(a,b))).isZero();verifyNoInteractions(push);
    }
    @Test void pushOnlySeesRowsAlreadyCommittedOnAnIndependentConnection(){
        var committed=new AtomicBoolean();
        doAnswer(call->{MessageDto dto=call.getArgument(1);try(var c=source.getConnection();var s=c.prepareStatement("SELECT COUNT(*) FROM private_message WHERE id=?")){s.setLong(1,dto.getId());try(var rows=s.executeQuery()){rows.next();committed.set(rows.getInt(1)==1);}}return null;}).when(push).send(anyLong(),any());
        send(a,b,"committed");assertThat(committed.get()).isTrue();
    }
    @Test void pushFailureDoesNotRollBackPersistedMessage(){doThrow(new IllegalStateException("offline socket")).when(push).send(anyLong(),any());send(a,b,"saved");assertThat(messages()).isEqualTo(1);as(b);assertThat(service.getTotalUnreadCount(b)).isEqualTo(1);}
    @Test void longContentIsPreservedWhilePreviewFitsExistingColumn(){String content="猫".repeat(800);send(a,b,content);long id=conversation();assertThat(jdbc.queryForObject("SELECT CHAR_LENGTH(last_message) FROM private_conversation WHERE id=?",Integer.class,id)).isEqualTo(500);as(b);assertThat(service.getMessageList(b,id,1,20).getRecords().get(0).getContent()).isEqualTo(content);}
    @Test void controllerUsesCursorWhileLegacyJavaPaginationStillPreservesTotals(){
        var first=send(a,b,"one");var second=send(a,b,"two");long id=conversation();as(b);
        var page=service.getMessageList(b,id,1,1);assertThat(page.getTotal()).isEqualTo(2);assertThat(page.getRecords().get(0).getId()).isEqualTo(first.getId());
        var cursor=context.getBean(PrivateMessageController.class).getMessages(id,null,null,1).getData();
        assertThat(cursor.records()).extracting(MessageDto::getId).containsExactly(second.getId());assertThat(cursor.hasMore()).isTrue();verifyNoInteractions(context.getBean("legacyPrivateMessageService",PrivateMessageService.class));
    }
    @Test void idCursorStartsAtLatestAndWalksWithoutDuplicatesWhenNewMessagesArrive(){
        List<Long> ids=new ArrayList<>();for(int i=0;i<8;i++)ids.add(send(a,b,"history-"+i).getId());
        long id=conversation();
        jdbc.update("UPDATE private_message SET create_time='2026-01-01 00:00:00' WHERE conversation_id=?",id);
        as(b);var first=service.getMessageHistory(b,id,null,null,3);
        assertThat(first.records()).extracting(MessageDto::getId).containsExactly(ids.get(7),ids.get(6),ids.get(5));
        assertThat(first.hasMore()).isTrue();assertThat(first.nextCursor()).isEqualTo(ids.get(5));
        var incoming=send(a,b,"arrived while reading");
        as(b);var second=service.getMessageHistory(b,id,first.nextCursor(),null,3);
        var third=service.getMessageHistory(b,id,second.nextCursor(),null,3);
        assertThat(second.records()).extracting(MessageDto::getId).containsExactly(ids.get(4),ids.get(3),ids.get(2));
        assertThat(third.records()).extracting(MessageDto::getId).containsExactly(ids.get(1),ids.get(0));
        assertThat(third.hasMore()).isFalse();
        var newer=service.getMessageHistory(b,id,null,ids.get(7),3);
        assertThat(newer.records()).extracting(MessageDto::getId).containsExactly(incoming.getId());
        assertThat(newer.hasMore()).isFalse();
        assertThat(service.getMessageHistory(b,id,ids.get(0),null,3).records()).isEmpty();
        assertThat(service.getTotalUnreadCount(b)).isZero();
    }
    @Test void deletedCursorIsStillUsableAndVisibilityRemainsPerParticipant(){
        var one=send(a,b,"one");var two=send(a,b,"two");send(a,b,"three");long id=conversation();
        as(b);var first=service.getMessageHistory(b,id,null,null,2);
        assertThat(first.nextCursor()).isEqualTo(two.getId());
        jdbc.update("DELETE FROM private_message WHERE id=? AND conversation_id=?",two.getId(),id);
        assertThat(service.getMessageHistory(b,id,first.nextCursor(),null,2).records()).extracting(MessageDto::getId).containsExactly(one.getId());
        service.clearMessages(b,id);
        assertThat(service.getMessageHistory(b,id,null,null,30).records()).isEmpty();
        as(a);assertThat(service.getMessageHistory(a,id,null,null,30).records()).hasSize(2);
    }
    SendMessageRequest keyed(long to,String content,String key){var request=new SendMessageRequest();request.setToUserId(to);request.setContent(content);request.setClientMessageId(key);return request;}
    @Test void replayAfterLostResponseReturnsOriginalWithoutIncrementingUnreadOrPushingAgain(){
        String key="lost-response-"+UUID.randomUUID();as(a);var request=keyed(b,"persisted once",key);
        var first=service.sendMessage(a,request);var replay=service.sendMessage(a,request);
        assertThat(replay.getId()).isEqualTo(first.getId());assertThat(replay.getClientMessageId()).isEqualTo(key);
        assertThat(messages()).isEqualTo(1);as(b);assertThat(service.getTotalUnreadCount(b)).isEqualTo(1);verify(push,times(1)).send(anyLong(),any());
        assertThat(service.getMessageHistory(b,conversation(),null,null,30).records().get(0).getClientMessageId()).isEqualTo(key);
    }
    @Test void concurrentSameKeyRetryCreatesOneMessageAndPush()throws Exception{
        String key="concurrent-"+UUID.randomUUID();List<Long> ids=new CopyOnWriteArrayList<>();
        race(()->{as(a);ids.add(service.sendMessage(a,keyed(b,"same retry",key)).getId());},()->{as(a);ids.add(service.sendMessage(a,keyed(b,"same retry",key)).getId());});
        assertThat(ids).hasSize(2);assertThat(new HashSet<>(ids)).hasSize(1);assertThat(messages()).isEqualTo(1);verify(push,times(1)).send(anyLong(),any());
    }
    @Test void sameKeyCannotChangePayloadOrRecipientButIndependentMessagesMayHaveSameContent(){
        String key="different-"+UUID.randomUUID();as(a);service.sendMessage(a,keyed(b,"original",key));
        assertThatThrownBy(()->service.sendMessage(a,keyed(b,"changed",key))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.sendMessage(a,keyed(outsider,"original",key))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM private_conversation WHERE user_a=? AND user_b=?",Integer.class,Math.min(a,outsider),Math.max(a,outsider))).isZero();
        service.sendMessage(a,keyed(b,"original","another-"+UUID.randomUUID()));assertThat(messages()).isEqualTo(2);
    }
    @Test void clearedMessageCannotBeResurrectedByRetry(){
        String key="cleared-"+UUID.randomUUID();as(a);var request=keyed(b,"cleared",key);service.sendMessage(a,request);service.clearMessages(a,conversation());
        assertThatThrownBy(()->service.sendMessage(a,request)).isInstanceOf(BusinessException.class);
        assertThat(service.getMessageHistory(a,conversation(),null,null,30).records()).isEmpty();assertThat(messages()).isEqualTo(1);
    }
    @Test void rolledBackSendDoesNotConsumeIdempotencyKey(){
        String key="rollback-"+UUID.randomUUID();as(a);var request=keyed(b,"retry rollback",key);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{service.sendMessage(a,request);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(messages()).isZero();service.sendMessage(a,request);assertThat(messages()).isEqualTo(1);verify(push,times(1)).send(anyLong(),any());
    }
    @Test void differentSendersMayUseTheSameClientKey(){
        String key="per-sender-"+UUID.randomUUID();as(a);var first=service.sendMessage(a,keyed(b,"from a",key));
        as(b);var second=service.sendMessage(b,keyed(a,"from b",key));assertThat(first.getId()).isNotEqualTo(second.getId());assertThat(messages()).isEqualTo(2);
    }

}
