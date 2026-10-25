package com.hongjie.pms.refactor.message;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.enums.MessageType;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.message.controller.MessageController;
import com.hongjie.pms.modules.message.entity.UserMessage;
import com.hongjie.pms.modules.message.mapper.UserMessageMapper;
import com.hongjie.pms.modules.message.mq.*;
import com.hongjie.pms.refactor.message.api.MessageService;
import com.hongjie.pms.refactor.extension.notify.*;
import com.hongjie.pms.refactor.extension.notify.handler.*;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="PMS_MESSAGE_MYSQL_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageMySqlTest {
    AnnotationConfigApplicationContext context;MessageService service;MessageMqProducer producer;JdbcTemplate jdbc;TransactionTemplate tx;
    long a,b;final String marker="__message_it_"+UUID.randomUUID().toString().substring(0,18);final List<MessageMqDto> sent=new CopyOnWriteArrayList<>();
    record Trigger(Long recipient) {}
    static class TriggerListener {
        final MessageService service;TriggerListener(MessageService service){this.service=service;}
        @TransactionalEventListener public void committed(Trigger trigger){service.sendFollowNotification(trigger.recipient(),null,"sender","/user/1");}
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PmsMessageService.class,MessageInbox.class,MessageDelivery.class,LikeNotificationHandler.class,CommentNotificationHandler.class,TargetTypeCodec.class,MessageController.class,NotificationSenderAdapter.class,NotificationFacade.class,NotificationHandlerRegistry.class})
    static class Config {
        @Bean MessageMqProducer producer(){return mock(MessageMqProducer.class);}
        @Bean MessageService legacyMessageService(){return mock(MessageService.class);}
        @Bean TriggerListener triggerListener(MessageService service){return new TriggerListener(service);}
    }
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");assertThat(MyBatisTestSupport.configured()).isTrue();
        var factory=MyBatisTestSupport.createFactory(UserMessageMapper.class,MessageInboxMapper.class);var source=factory.getConfiguration().getEnvironment().getDataSource();jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");for(String table:List.of("user","user_message"))assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        var template=new SqlSessionTemplate(factory);var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);tx.setTimeout(15);
        context=new AnnotationConfigApplicationContext();context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        context.registerBean(UserMessageMapper.class,()->template.getMapper(UserMessageMapper.class));context.registerBean(MessageInboxMapper.class,()->template.getMapper(MessageInboxMapper.class));
        context.register(Config.class);context.refresh();service=context.getBean(MessageService.class);producer=context.getBean(MessageMqProducer.class);assertThat(service).isInstanceOf(PmsMessageService.class);
    }
    long insert(String sql,Object... args){return jdbc.execute((java.sql.Connection c)->{try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();try(var keys=s.getGeneratedKeys()){if(!keys.next())throw new IllegalStateException("missing id");return keys.getLong(1);}}});}
    @BeforeEach void fixtures(){
        a=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"a","fixture");b=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"b","fixture");as(a);sent.clear();reset(producer);
        doAnswer(call->{MessageMqDto dto=call.getArgument(0);sent.add(dto);jdbc.update("INSERT INTO user_message(user_id,sender_id,type,title,content,business_id,link,create_time,is_read) VALUES(?,?,?,?,?,?,?,?,0)",dto.getUserId(),dto.getSenderId(),dto.getType(),dto.getTitle(),dto.getContent(),dto.getBusinessId(),dto.getLink(),dto.getCreateTime());return null;}).when(producer).send(any());
    }
    @AfterEach void cleanup(){
        UserContext.clear();if(jdbc==null)return;
        jdbc.update("DELETE m FROM user_message m JOIN user u ON m.user_id=u.id WHERE u.id IN (?,?) AND u.user_name IN (?,?)",a,b,marker+"a",marker+"b");
        jdbc.update("DELETE FROM user WHERE id IN (?,?) AND user_name IN (?,?)",a,b,marker+"a",marker+"b");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE user_name IN (?,?)",Long.class,marker+"a",marker+"b")).isZero();
    }
    @AfterAll void close(){if(context!=null)context.close();}
    void as(long id){UserContext.setUser(new UserInfo(id,marker,0,null,null));}
    long message(long user,String type){return insert("INSERT INTO user_message(user_id,type,title,content,is_read,create_time) VALUES(?,?,?,?,0,'2026-01-01 00:00:00')",user,type,marker,"fixture");}
    long count(long user){return jdbc.queryForObject("SELECT COUNT(*) FROM user_message WHERE user_id=?",Long.class,user);}
    UserMessage draft(long user,String content){var row=new UserMessage();row.setUserId(user);row.setType("SYSTEM");row.setTitle(marker);row.setContent(content);return row;}
    @Test void inboxPaginationKeepsTotalAndDeterministicOrder(){
        long first=message(a,"PUNISHMENT"),second=message(a,"PUNISHMENT");message(a,"SYSTEM");message(b,"PUNISHMENT");
        var page=service.getMessageList(a,1,1,"PUNISHMENT");assertThat(page.getTotal()).isEqualTo(2);assertThat(page.getRecords().get(0).getId()).isEqualTo(second);assertThat(service.getMessageList(a,2,1,"PUNISHMENT").getRecords().get(0).getId()).isEqualTo(first);assertThat(service.getUnreadCount(a)).isEqualTo(3);
        context.getBean(MessageController.class).getUnreadCount();verifyNoInteractions(context.getBean("legacyMessageService",MessageService.class));
    }
    @Test void foreignInboxAndReadAreRejectedWithoutChangingMessage(){
        long id=message(b,"SYSTEM");assertThatThrownBy(()->service.getUnreadCount(b)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.getMessageList(b,1,20,null)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.markAsRead(id)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT is_read FROM user_message WHERE id=?",Integer.class,id)).isZero();
    }
    @Test void concurrentReadIsIdempotentAndPreservesFirstReadTime()throws Exception{
        long id=message(a,"SYSTEM");var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        Callable<Void> task=()->{as(a);ready.countDown();try{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.markAsRead(id);return null;}finally{UserContext.clear();}};
        try{var one=pool.submit(task);var two=pool.submit(task);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);}
        finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        as(a);assertThat(service.getUnreadCount(a)).isZero();jdbc.update("UPDATE user_message SET read_time='2000-01-01 00:00:00' WHERE id=?",id);service.markAsRead(id);assertThat(jdbc.queryForObject("SELECT read_time FROM user_message WHERE id=?",Timestamp.class,id)).isEqualTo(Timestamp.valueOf("2000-01-01 00:00:00"));
    }
    @Test void readAllIsScopedToRecipientAndOptionalType(){message(a,"FOLLOW");message(a,"SYSTEM");message(b,"FOLLOW");service.markAllAsRead("FOLLOW");assertThat(service.getUnreadCount(a)).isEqualTo(1);as(b);assertThat(service.getUnreadCount(b)).isEqualTo(1);}
    @Test void activeTransactionDefersDeliveryAndSnapshotsInput(){
        var message=draft(a,"before");tx.executeWithoutResult(status->{service.sendAndPush(message);message.setContent("after");assertThat(sent).isEmpty();assertThat(count(a)).isZero();});
        assertThat(sent).hasSize(1);assertThat(sent.get(0).getContent()).isEqualTo("before");assertThat(count(a)).isEqualTo(1);
    }
    @Test void rollbackSuppressesNotificationAndReadState(){
        long id=message(a,"SYSTEM");assertThatThrownBy(()->tx.executeWithoutResult(status->{service.markAsRead(id);service.sendAndPush(draft(a,"rolled back"));throw new IllegalStateException("force rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(sent).isEmpty();assertThat(count(a)).isEqualTo(1);assertThat(service.getUnreadCount(a)).isEqualTo(1);
    }
    @Test void callsFromAfterCommitListenerDeliverAndFallbackWritesCommit(){
        tx.executeWithoutResult(status->context.publishEvent(new Trigger(a)));assertThat(sent).hasSize(1);assertThat(count(a)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT type FROM user_message WHERE user_id=?",String.class,a)).isEqualTo("FOLLOW");
    }
    @Test void deliveryFailureDoesNotUndoCommittedBusinessTransaction(){
        doThrow(new IllegalStateException("transport failed")).when(producer).send(any());
        tx.executeWithoutResult(status->{jdbc.update("UPDATE user SET remark=? WHERE id=?",marker,a);service.sendAndPush(draft(a,"event"));});
        assertThat(jdbc.queryForObject("SELECT remark FROM user WHERE id=?",String.class,a)).isEqualTo(marker);assertThat(count(a)).isZero();
    }
    @Test void invalidBatchDoesNotPartiallyDeliverOutsideTransaction(){var invalid=draft(b,"bad");invalid.setUserId(null);assertThatThrownBy(()->service.sendAndPushBatch(List.of(draft(a,"valid"),invalid))).isInstanceOf(BusinessException.class);assertThat(sent).isEmpty();}
    @Test void existingNotificationFacadeResolvesNewServiceWithoutCircularDependency(){
        context.getBean(NotificationFacade.class).notify(MessageType.LIKE,new LikeNotificationContext(a,b,BizTargetType.PET_POST,"title",10L,"/pet/10"));assertThat(sent).hasSize(1);assertThat(count(a)).isEqualTo(1);assertThat(sent.get(0).getTitle()).isEqualTo("有人点赞了你的帖子");
    }
}
