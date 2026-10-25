package com.hongjie.pms.refactor.activity;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.modules.activity.controller.ActivityController;
import com.hongjie.pms.modules.activity.dto.request.SignUpInfoRequest;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import com.hongjie.pms.modules.activity.service.ActivityService;
import com.hongjie.pms.refactor.message.api.MessageService;
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
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="PMS_ACTIVITY_MYSQL_TESTS",matches="true")
class ActivityParticipationMySqlTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    ActivityParticipationCommands commands;
    MessageService messages;
    ActivityCache cache;
    long owner,a,b,activity;
    final String marker="__activity_it_"+UUID.randomUUID().toString().substring(0,16);

    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PmsActivityServiceImpl.class,ActivityParticipationCommands.class,ActivityParticipationListener.class,ActivityController.class})
    static class Config {
        @Bean ActivityQueries queries(){return mock(ActivityQueries.class);}
        @Bean ActivityLifecycleCommands lifecycle(){return mock(ActivityLifecycleCommands.class);}
        @Bean ActivityContentCommands content(){return mock(ActivityContentCommands.class);}
        @Bean ActivityCache cache(){return mock(ActivityCache.class);}
        @Bean MessageService messages(){return mock(MessageService.class);}
        @Bean ActivityService oldService(){return mock(ActivityService.class);}
    }
    @BeforeAll void open() {
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");
        assertThat(MyBatisTestSupport.configured()).isTrue();
        Class<?>[] mappers={ActivityParticipationMapper.class,ActivitySignupMapper.class,UserMapper.class};
        var factory=MyBatisTestSupport.createFactory(mappers);
        var source=factory.getConfiguration().getEnvironment().getDataSource();
        jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("activity","activity_signup","user"))
            assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        var manager=new DataSourceTransactionManager(source); tx=new TransactionTemplate(manager);tx.setTimeout(15);
        var template=new SqlSessionTemplate(factory);
        context=new AnnotationConfigApplicationContext();
        context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        for(Class<?> mapper:mappers) register(mapper,template);
        context.register(Config.class);context.refresh();
        commands=context.getBean(ActivityParticipationCommands.class);
        messages=context.getBean(MessageService.class);cache=context.getBean(ActivityCache.class);
    }
    <T> void register(Class<T> type,SqlSessionTemplate template){context.registerBean(type,()->template.getMapper(type));}
    long insert(String sql,Object...args) {
        return jdbc.execute((java.sql.Connection connection)->{
            try(var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
                statement.executeUpdate();
                try(var keys=statement.getGeneratedKeys()){assertThat(keys.next()).isTrue();return keys.getLong(1);}
            }
        });
    }
    @BeforeEach void fixtures() {
        reset(messages,cache,context.getBean("oldService",ActivityService.class));
        owner=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"o","fixture");
        a=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"a","fixture");
        b=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"b","fixture");
        activity=insert("INSERT INTO activity(user_id,title,content,location,max_people,current_people,start_time,end_time,status,audit_status,deleted) VALUES(?,?,?,'fixture',1,0,DATE_ADD(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 2 DAY),0,1,0)",owner,marker,"fixture");
    }
    @AfterEach void cleanup() {
        UserContext.clear();
        jdbc.update("DELETE s FROM activity_signup s JOIN activity a ON a.id=s.activity_id WHERE a.id=? AND a.title=? AND a.user_id=?",activity,marker,owner);
        jdbc.update("DELETE FROM activity WHERE id=? AND title=? AND user_id=?",activity,marker,owner);
        jdbc.update("DELETE FROM user WHERE id IN (?,?,?) AND user_name IN (?,?,?)",owner,a,b,marker+"o",marker+"a",marker+"b");
    }
    @AfterAll void close(){if(context!=null)context.close();}
    void as(long actor,Runnable action) {
        UserContext.setUser(new UserInfo(actor,"fixture",0,null,null));
        try{action.run();}finally{UserContext.clear();}
    }
    SignUpInfoRequest request(){var r=new SignUpInfoRequest();r.setActivityId(activity);r.setRealName("fixture");r.setPhone("13800000000");return r;}
    void signup(long actor){as(actor,()->commands.signUp(request()));}
    int seats(){return jdbc.queryForObject("SELECT current_people FROM activity WHERE id=?",Integer.class,activity);}
    long rows(){return jdbc.queryForObject("SELECT COUNT(*) FROM activity_signup WHERE activity_id=?",Long.class,activity);}
    void race(Runnable first,Runnable second)throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        java.util.function.Function<Runnable,Callable<Void>> task=action->()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");action.run();return null;};
        try{
            var one=pool.submit(task.apply(first));var two=pool.submit(task.apply(second));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    void attempt(Runnable action,AtomicInteger success) {
        try{action.run();success.incrementAndGet();}catch(BusinessException expected){}
    }
    @Test void lastSeatHasOneWinner()throws Exception {
        var success=new AtomicInteger();
        race(()->attempt(()->signup(a),success),()->attempt(()->signup(b),success));
        assertThat(success.get()).isEqualTo(1);assertThat(seats()).isEqualTo(1);assertThat(rows()).isEqualTo(1);
        verify(messages,times(1)).sendActivityFullNotification(owner,marker,activity);
    }
    @Test void duplicateSignupCannotConsumeTwoSeats()throws Exception {
        jdbc.update("UPDATE activity SET max_people=2 WHERE id=?",activity);
        var success=new AtomicInteger();
        race(()->attempt(()->signup(a),success),()->attempt(()->signup(a),success));
        assertThat(success.get()).isEqualTo(1);assertThat(seats()).isEqualTo(1);assertThat(rows()).isEqualTo(1);
    }
    @Test void duplicateCancellationDecrementsOnceAndAllowsRejoin()throws Exception {
        signup(a);var success=new AtomicInteger();
        race(()->attempt(()->as(a,()->commands.cancel(activity)),success),()->attempt(()->as(a,()->commands.cancel(activity)),success));
        assertThat(success.get()).isEqualTo(1);assertThat(seats()).isZero();assertThat(rows()).isZero();
        signup(a);assertThat(seats()).isEqualTo(1);assertThat(rows()).isEqualTo(1);
    }
    @Test void duplicateCheckinSendsOneNotification()throws Exception {
        signup(a);reset(messages);var success=new AtomicInteger();
        race(()->attempt(()->as(owner,()->commands.signIn(activity,a)),success),()->attempt(()->as(owner,()->commands.signIn(activity,a)),success));
        assertThat(success.get()).isEqualTo(1);assertThat(seats()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM activity_signup WHERE activity_id=?",Integer.class,activity)).isEqualTo(3);
        verify(messages,times(1)).sendSignInSuccessNotification(a,marker,activity);
        as(a,()->assertThatThrownBy(()->commands.cancel(activity)).isInstanceOf(BusinessException.class));
    }
    @Test void outerRollbackRestoresSeatAndSuppressesNotification() {
        as(a,()->assertThatThrownBy(()->tx.executeWithoutResult(status->{
            commands.signUp(request());throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class));
        assertThat(seats()).isZero();assertThat(rows()).isZero();verifyNoInteractions(messages);
    }
    @Test void cancellationCounterMismatchRollsBackRecordDeletion() {
        signup(a);jdbc.update("UPDATE activity SET current_people=0 WHERE id=?",activity);
        as(a,()->assertThatThrownBy(()->commands.cancel(activity)).isInstanceOf(BusinessException.class));
        assertThat(rows()).isEqualTo(1);assertThat(seats()).isZero();
    }
    @Test void invalidStatesCannotJoin() {
        for(String assignment:List.of("deleted=1","audit_status=0","status=2","start_time=DATE_SUB(NOW(),INTERVAL 1 MINUTE)")){
            jdbc.update("UPDATE activity SET deleted=0,audit_status=1,status=0,start_time=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE id=?",activity);
            jdbc.update("UPDATE activity SET "+assignment+" WHERE id=?",activity);
            assertThatThrownBy(()->signup(a)).isInstanceOf(BusinessException.class);
            assertThat(rows()).isZero();assertThat(seats()).isZero();
        }
    }
    @Test void bannedDisabledAndOwnerCannotJoin() {
        assertThatThrownBy(()->signup(owner)).isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE user SET is_banned_signup=1,ban_signup_end_time=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE id=?",a);
        assertThatThrownBy(()->signup(a)).isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE user SET status=0 WHERE id=?",b);
        assertThatThrownBy(()->signup(b)).isInstanceOf(BusinessException.class);
        assertThat(rows()).isZero();
    }
    @Test void endedActivityCannotCancelOrCheckin() {
        signup(a);jdbc.update("UPDATE activity SET status=2 WHERE id=?",activity);
        as(a,()->assertThatThrownBy(()->commands.cancel(activity)).isInstanceOf(BusinessException.class));
        as(owner,()->assertThatThrownBy(()->commands.signIn(activity,a)).isInstanceOf(BusinessException.class));
        assertThat(seats()).isEqualTo(1);assertThat(rows()).isEqualTo(1);
    }
    @Test void controllerRoutesParticipationToNewCommands() {
        as(a,()->context.getBean(ActivityController.class).signUp(request()));
        as(owner,()->context.getBean(ActivityController.class).signIn(activity,a));
        verifyNoInteractions(context.getBean("oldService",ActivityService.class));assertThat(seats()).isEqualTo(1);
    }
    @Test void notificationFailureDoesNotUndoCommittedSignupOrSkipOtherRecipients() {
        doThrow(new IllegalStateException("offline")).when(messages).sendActivityFullNotification(owner,marker,activity);
        signup(a);assertThat(seats()).isEqualTo(1);assertThat(rows()).isEqualTo(1);
        verify(messages).sendSignUpSuccessNotification(a,marker,activity);
        verify(messages).sendSomeoneSignUpNotification(owner,a,"fixture",marker,activity);
    }

    @Test void activityLockIsHeldUntilOuterTransactionCommits()throws Exception {
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempted=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var holder=pool.submit(()->tx.executeWithoutResult(status->{
                context.getBean(ActivityParticipationMapper.class).lock(activity);held.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            }));
            assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
            var waiter=pool.submit(()->{attempted.countDown();signup(a);});
            assertThat(attempted.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->waiter.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();holder.get(20,TimeUnit.SECONDS);waiter.get(20,TimeUnit.SECONDS);
            assertThat(seats()).isEqualTo(1);assertThat(rows()).isEqualTo(1);
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void notificationFallbackWritesCommitIndependently() {
        doAnswer(call->{
            jdbc.update("INSERT INTO user_message(user_id,type,title,content,business_id) VALUES(?,'SIGN_UP',?,?,?)",a,marker,"fixture",activity);
            return null;
        }).when(messages).sendSignUpSuccessNotification(a,marker,activity);
        try {
            signup(a);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_message WHERE user_id=? AND title=? AND business_id=?",Long.class,a,marker,activity)).isEqualTo(1);
        } finally {jdbc.update("DELETE FROM user_message WHERE user_id=? AND title=? AND business_id=?",a,marker,activity);}
    }
    @Test void controllerCancellationAndCheckinOwnershipAreEnforced() {
        signup(a);
        as(b,()->assertThatThrownBy(()->context.getBean(ActivityController.class).signIn(activity,a)).isInstanceOf(BusinessException.class));
        as(a,()->context.getBean(ActivityController.class).cancelSignUp(activity));
        assertThat(rows()).isZero();assertThat(seats()).isZero();
        verifyNoInteractions(context.getBean("oldService",ActivityService.class));
    }
}
