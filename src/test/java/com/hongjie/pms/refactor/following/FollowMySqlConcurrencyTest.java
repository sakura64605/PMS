package com.hongjie.pms.refactor.following;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.feed.mapper.UserInboxMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.following.service.FollowService;
import com.hongjie.pms.modules.following.controller.FollowingController;
import com.hongjie.pms.refactor.message.api.MessageService;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="PMS_FOLLOW_MYSQL_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FollowMySqlConcurrencyTest {
    AnnotationConfigApplicationContext context;PmsFollowServiceImpl service;JdbcTemplate jdbc;TransactionTemplate tx;MessageService messages;long a,b;
    final String marker="__follow_it_"+UUID.randomUUID().toString().substring(0,20);
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PmsFollowServiceImpl.class,FollowInboxWriter.class,FollowCreatedListener.class,FollowingController.class})
    static class Config{
        @Bean com.hongjie.pms.common.cache.DistributedCache cache(){return mock(com.hongjie.pms.common.cache.DistributedCache.class);}
        @Bean MessageService messages(){return mock(MessageService.class);}
        @Bean FollowService legacyFollowService(){return mock(FollowService.class);}
    }
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");
        assertThat(MyBatisTestSupport.configured()).isTrue();
        Class<?>[] mappers={FollowWriteMapper.class,FollowMapper.class,UserMapper.class,UserInboxMapper.class,PetPostMapper.class,ActivityMapper.class};
        var factory=MyBatisTestSupport.createFactory(mappers);var source=factory.getConfiguration().getEnvironment().getDataSource();jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("user","follow","user_inbox","pet_post","activity","user_message"))assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        var template=new SqlSessionTemplate(factory);var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);tx.setTimeout(15);
        context=new AnnotationConfigApplicationContext();context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        for(Class<?> type:mappers)register(type,template);
        context.register(Config.class);context.refresh();service=context.getBean(PmsFollowServiceImpl.class);messages=context.getBean(MessageService.class);
        assertThat(context.getBean(FollowService.class)).isSameAs(service);
    }
    <T> void register(Class<T> type,SqlSessionTemplate template){context.registerBean(type,()->template.getMapper(type));}
    long insert(String sql,Object... args){return jdbc.execute((java.sql.Connection c)->{
        try(var statement=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);statement.executeUpdate();try(var keys=statement.getGeneratedKeys()){if(!keys.next())throw new IllegalStateException("missing id");return keys.getLong(1);}}
    });}
    @BeforeEach void fixtures(){reset(messages);a=insert("INSERT INTO user(user_name,password,status,follower_count,following_count) VALUES(?,?,1,0,0)",marker+"a","fixture");b=insert("INSERT INTO user(user_name,password,status,follower_count,following_count) VALUES(?,?,1,0,0)",marker+"b","fixture");}
    @AfterEach void cleanup(){
        UserContext.clear();if(jdbc==null)return;
        // Only this run's two users and their pair-owned projections are removed.
        for(long id:List.of(a,b)){
            jdbc.update("DELETE m FROM user_message m JOIN user u ON m.sender_id=u.id WHERE u.id=? AND u.user_name IN (?,?) AND m.title=?",id,marker+"a",marker+"b",marker);
            jdbc.update("DELETE i FROM user_inbox i JOIN user u ON i.user_id=u.id WHERE u.id=? AND u.user_name IN (?,?)",id,marker+"a",marker+"b");
            jdbc.update("DELETE f FROM follow f JOIN user u ON f.follower_id=u.id WHERE u.id=? AND u.user_name IN (?,?)",id,marker+"a",marker+"b");
            jdbc.update("DELETE p FROM pet_post p JOIN user u ON p.user_id=u.id WHERE u.id=? AND u.user_name IN (?,?) AND p.title=?",id,marker+"a",marker+"b",marker);
        }
        jdbc.update("DELETE FROM user WHERE id IN (?,?) AND user_name IN (?,?)",a,b,marker+"a",marker+"b");
    }
    @AfterAll void close(){if(context!=null)context.close();}
    void as(long actor,Runnable action){UserContext.setUser(new UserInfo(actor,"fixture",0,null,null));try{action.run();}finally{UserContext.clear();}}
    long relations(){return jdbc.queryForObject("SELECT COUNT(*) FROM follow WHERE follower_id IN (?,?) AND following_id IN (?,?)",Long.class,a,b,a,b);}
    int count(long id,String column){return jdbc.queryForObject("SELECT "+column+" FROM user WHERE id=?",Integer.class,id);}
    void race(Runnable one,Runnable two)throws Exception{
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        java.util.function.Function<Runnable,Callable<Void>> task=action->()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("start timeout");action.run();return null;};
        try{var first=pool.submit(task.apply(one));var second=pool.submit(task.apply(two));assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);}
        finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void twoConcurrentTogglesLeaveNoEdgeAndBalancedCounts()throws Exception{
        race(()->as(a,()->service.followUser(a,b)),()->as(a,()->service.followUser(a,b)));
        assertThat(relations()).isZero();assertThat(count(a,"following_count")).isZero();assertThat(count(b,"follower_count")).isZero();
        verify(messages).sendFollowNotification(b,a,marker+"a","/user/"+a);
    }
    @Test void oppositeDirectionsCanCoexistWithoutDeletingEachOther()throws Exception{
        race(()->as(a,()->service.followUser(a,b)),()->as(b,()->service.followUser(b,a)));
        assertThat(relations()).isEqualTo(2);
        for(long id:List.of(a,b)){assertThat(count(id,"following_count")).isEqualTo(1);assertThat(count(id,"follower_count")).isEqualTo(1);}
        as(a,()->service.followUser(a,b));assertThat(relations()).isEqualTo(1);assertThat(count(a,"follower_count")).isEqualTo(1);assertThat(count(b,"following_count")).isEqualTo(1);
    }
    @Test void outerRollbackRestoresRelationCountersAndInboxAndSuppressesNotification(){
        insert("INSERT INTO pet_post(user_id,type,title,content,contact_phone,status,audit_status) VALUES(?,0,?,?,?,1,1)",b,marker,"fixture","13800000000");
        as(a,()->assertThatThrownBy(()->tx.executeWithoutResult(status->{service.followUser(a,b);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_inbox WHERE user_id=? AND poster_id=?",Long.class,a,b)).isEqualTo(1);throw new IllegalStateException("force rollback");})).isInstanceOf(IllegalStateException.class));
        assertThat(relations()).isZero();assertThat(count(a,"following_count")).isZero();assertThat(count(b,"follower_count")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_inbox WHERE user_id=? AND poster_id=?",Long.class,a,b)).isZero();verifyNoInteractions(messages);
    }
    @Test void controllerUsesNewServiceAndPetHistoryIsReadableByExistingFeed(){
        insert("INSERT INTO pet_post(user_id,type,title,content,contact_phone,status,audit_status) VALUES(?,0,?,?,?,1,1)",b,marker,"fixture","13800000000");
        as(a,()->context.getBean(FollowingController.class).followUser(b));
        assertThat(jdbc.queryForObject("SELECT post_type FROM user_inbox WHERE user_id=? AND poster_id=?",String.class,a,b)).isEqualTo("pet");
        assertThat(jdbc.queryForObject("SELECT title FROM user_inbox WHERE user_id=? AND poster_id=?",String.class,a,b)).isEqualTo(marker);
        verifyNoInteractions(context.getBean("legacyFollowService",FollowService.class));
    }
    @Test void notificationFailureCannotRollBackCommittedRelationship(){
        doThrow(new IllegalStateException("notification unavailable")).when(messages).sendFollowNotification(anyLong(),anyLong(),anyString(),anyString());
        as(a,()->service.followUser(a,b));assertThat(relations()).isEqualTo(1);assertThat(count(a,"following_count")).isEqualTo(1);
    }
    @Test void notificationFallbackDatabaseWriteGetsItsOwnCommit(){
        doAnswer(call->{jdbc.update("INSERT INTO user_message(user_id,sender_id,type,title,content) VALUES(?,?,'FOLLOW',?,?)",b,a,marker,"fixture");return null;})
                .when(messages).sendFollowNotification(anyLong(),anyLong(),anyString(),anyString());
        as(a,()->service.followUser(a,b));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_message WHERE user_id=? AND sender_id=? AND title=?",Long.class,b,a,marker)).isEqualTo(1);
    }
    @Test void existingUserLockBlocksToggleUntilCommit()throws Exception{
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempted=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var owner=pool.submit(()->tx.executeWithoutResult(status->{context.getBean(FollowWriteMapper.class).lockUser(Math.min(a,b));held.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}));
            assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();var waiter=pool.submit(()->as(a,()->{attempted.countDown();service.followUser(a,b);}));
            assertThat(attempted.await(5,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->waiter.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();owner.get(20,TimeUnit.SECONDS);waiter.get(20,TimeUnit.SECONDS);assertThat(relations()).isEqualTo(1);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
}
