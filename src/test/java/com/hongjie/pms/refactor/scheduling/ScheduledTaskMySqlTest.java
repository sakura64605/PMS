package com.hongjie.pms.refactor.scheduling;

import com.hongjie.pms.common.punishment.scheduler.*;
import com.hongjie.pms.common.punishment.util.PunishmentUtil;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.modules.activity.mapper.*;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="PMS_SCHEDULING_MYSQL_TESTS",matches="true")
class ScheduledTaskMySqlTest {
    AnnotationConfigApplicationContext context;JdbcTemplate jdbc;ScheduledTaskWorker worker;DueTaskBatch batch;
    MessageService messages;AtomicHandler handler;long owner,user,activity;
    final String marker="__xxl_it_"+UUID.randomUUID().toString().substring(0,16);
    final List<Long> taskIds=new ArrayList<>();
    static class AtomicHandler implements DelayTaskExecutor {
        final JdbcTemplate jdbc;final AtomicBoolean fail=new AtomicBoolean();final AtomicInteger calls=new AtomicInteger();
        AtomicHandler(JdbcTemplate jdbc){this.jdbc=jdbc;}
        public String taskType(){return "TEST_ATOMIC";}
        public void execute(Long id){calls.incrementAndGet();jdbc.update("UPDATE user SET following_count=following_count+1 WHERE id=?",id);if(fail.get())throw new IllegalStateException("injected failure");}
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    @Import({ScheduledTaskWorker.class,DueTaskBatch.class,ScheduledBusinessTasks.class,BusinessTaskConfiguration.class,DelayTaskService.class})
    static class Config {
        @Bean SchedulingProperties settings(){return new SchedulingProperties();}
        @Bean ActivityCache cache(){return mock(ActivityCache.class);}
        @Bean MessageService messages(){return mock(MessageService.class);}
        @Bean PunishmentUtil punishment(){return mock(PunishmentUtil.class);}
        @Bean AtomicHandler handler(JdbcTemplate jdbc){return new AtomicHandler(jdbc);}
    }
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");
        assertThat(MyBatisTestSupport.configured()).isTrue();
        Class<?>[] types={ScheduledTaskMapper.class,ActivityMapper.class,ActivitySignupMapper.class,UserMapper.class,DelayTaskMapper.class};
        var factory=MyBatisTestSupport.createFactory(types);var source=factory.getConfiguration().getEnvironment().getDataSource();
        jdbc=new JdbcTemplate(source);assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("delay_task","activity","activity_signup","user"))
            assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        context=new AnnotationConfigApplicationContext();
        context.registerBean("jdbc",JdbcTemplate.class,()->jdbc);
        context.registerBean("transactionManager",DataSourceTransactionManager.class,()->new DataSourceTransactionManager(source));
        var template=new SqlSessionTemplate(factory);for(Class<?> type:types)register(type,template);
        context.register(Config.class);context.refresh();
        worker=context.getBean(ScheduledTaskWorker.class);batch=context.getBean(DueTaskBatch.class);
        messages=context.getBean(MessageService.class);handler=context.getBean(AtomicHandler.class);
    }
    <T>void register(Class<T>type,SqlSessionTemplate template){context.registerBean(type,()->template.getMapper(type));}
    long insert(String sql,Object...args){return jdbc.execute((java.sql.Connection connection)->{
        try(var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);statement.executeUpdate();
            try(var keys=statement.getGeneratedKeys()){assertThat(keys.next()).isTrue();return keys.getLong(1);}
        }
    });}
    @BeforeEach void fixture(){
        reset(messages,context.getBean(PunishmentUtil.class));handler.fail.set(false);handler.calls.set(0);taskIds.clear();
        owner=insert("INSERT INTO user(user_name,password,status,following_count) VALUES(?,?,1,0)",marker+"o","fixture");
        user=insert("INSERT INTO user(user_name,password,status,following_count) VALUES(?,?,1,0)",marker+"u","fixture");
        activity=insert("INSERT INTO activity(user_id,title,content,location,max_people,current_people,start_time,end_time,status,audit_status,deleted) VALUES(?,?,?,'fixture',10,1,DATE_SUB(NOW(),INTERVAL 2 HOUR),DATE_SUB(NOW(),INTERVAL 1 HOUR),1,1,0)",owner,marker,"fixture");
    }
    long task(String type,long business,LocalDateTime time){long id=insert("INSERT INTO delay_task(task_type,business_id,execute_time,status) VALUES(?,?,?,0)",type,business,time);taskIds.add(id);return id;}
    long due(String type,long business){return task(type,business,LocalDateTime.now().minusMinutes(1));}
    int state(long id){return jdbc.queryForObject("SELECT status FROM delay_task WHERE id=?",Integer.class,id);}
    int count(String field){return jdbc.queryForObject("SELECT "+field+" FROM user WHERE id=?",Integer.class,user);}
    @AfterEach void cleanup(){
        for(Long id:taskIds)jdbc.update("DELETE FROM delay_task WHERE id=?",id);
        jdbc.update("DELETE FROM delay_task WHERE business_id=? AND task_type='SIGNUP_PUNISHMENT_END'",user);
        jdbc.update("DELETE FROM activity_signup WHERE activity_id=? AND user_id=?",activity,user);
        jdbc.update("DELETE FROM activity WHERE id=? AND user_id=? AND title=?",activity,owner,marker);
        jdbc.update("DELETE FROM user WHERE id IN (?,?) AND user_name IN (?,?)",owner,user,marker+"o",marker+"u");
    }
    @AfterAll void close(){if(context!=null)context.close();}
    void race(Runnable action)throws Exception{
        var ready=new CountDownLatch(2);var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Void> task=()->{ready.countDown();if(!gate.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");action.run();return null;};
        try{var one=pool.submit(task);var two=pool.submit(task);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();gate.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);}
        finally{gate.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void concurrentExecutionCommitsBusinessEffectOnce()throws Exception{
        long id=due("TEST_ATOMIC",user);race(()->worker.execute(id,LocalDateTime.now()));
        assertThat(state(id)).isEqualTo(1);assertThat(count("following_count")).isEqualTo(1);assertThat(handler.calls.get()).isEqualTo(1);
    }
    @Test void failureRollsBackBusinessAndCompletionThenCanRetry(){
        long id=due("TEST_ATOMIC",user);handler.fail.set(true);
        assertThatThrownBy(()->worker.execute(id,LocalDateTime.now())).isInstanceOf(IllegalStateException.class);
        assertThat(state(id)).isZero();assertThat(count("following_count")).isZero();
        handler.fail.set(false);assertThat(worker.execute(id,LocalDateTime.now())).isTrue();
        assertThat(state(id)).isEqualTo(1);assertThat(count("following_count")).isEqualTo(1);
    }
    @Test void futureCompletedAndDeletedTasksDoNotExecute(){
        long id=task("TEST_ATOMIC",user,LocalDateTime.now().plusDays(1));
        assertThat(worker.execute(id,LocalDateTime.now())).isFalse();
        jdbc.update("UPDATE delay_task SET execute_time=DATE_SUB(NOW(),INTERVAL 1 MINUTE),status=1 WHERE id=?",id);
        assertThat(worker.execute(id,LocalDateTime.now())).isFalse();jdbc.update("DELETE FROM delay_task WHERE id=?",id);
        assertThat(worker.execute(id,LocalDateTime.now())).isFalse();assertThat(handler.calls.get()).isZero();
    }
    @Test void unknownTypeStaysPendingAndDoesNotGetAcknowledged(){
        long id=due("UNKNOWN_FIXTURE",user);
        assertThatThrownBy(()->worker.execute(id,LocalDateTime.now())).isInstanceOf(IllegalStateException.class);
        assertThat(state(id)).isZero();
    }
    @Test void batchContinuesAfterPoisonTaskButReportsFailure(){
        long bad=due("UNKNOWN_FIXTURE",user),good=due("TEST_ATOMIC",user);
        var scan=mock(ScheduledTaskMapper.class);
        when(scan.due(any(),anyLong(),anyInt())).thenReturn(List.of(bad,good),List.of());
        var scopedBatch=new DueTaskBatch(scan,worker,new SchedulingProperties());
        assertThatThrownBy(scopedBatch::execute).isInstanceOf(IllegalStateException.class);
        assertThat(state(bad)).isZero();assertThat(state(good)).isEqualTo(1);assertThat(count("following_count")).isEqualTo(1);
    }
    @Test void activityStatisticsAndDuplicateRecordsDoNotDoubleCountOrNotify(){
        insert("INSERT INTO activity_signup(activity_id,user_id,real_name,phone,status) VALUES(?,?,'fixture','13800000000',1)",activity,user);
        long one=due("ACTIVITY_STATISTICS",activity),two=due("ACTIVITY_STATISTICS",activity);
        worker.execute(one,LocalDateTime.now());worker.execute(two,LocalDateTime.now());
        assertThat(state(one)).isEqualTo(1);assertThat(state(two)).isEqualTo(1);
        assertThat(count("total_signups")).isEqualTo(1);assertThat(count("total_no_shows")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM activity_signup WHERE activity_id=?",Integer.class,activity)).isEqualTo(4);
        verify(messages,times(1)).sendActivityStatisticsNotification(owner,marker,activity,1L,0L,1L);
    }
    @Test void statisticsFailureRestoresSignupAndCountersBeforeRetry(){
        insert("INSERT INTO activity_signup(activity_id,user_id,real_name,phone,status) VALUES(?,?,'fixture','13800000000',1)",activity,user);
        long id=due("ACTIVITY_STATISTICS",activity);
        doThrow(new IllegalStateException("notification validation failure")).when(messages).sendActivityStatisticsNotification(any(),any(),any(),anyLong(),anyLong(),anyLong());
        assertThatThrownBy(()->worker.execute(id,LocalDateTime.now())).isInstanceOf(IllegalStateException.class);
        assertThat(state(id)).isZero();assertThat(jdbc.queryForObject("SELECT status FROM activity_signup WHERE activity_id=?",Integer.class,activity)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM activity WHERE id=?",Integer.class,activity)).isEqualTo(1);
        reset(messages);worker.execute(id,LocalDateTime.now());assertThat(count("total_no_shows")).isEqualTo(1);
    }
    @Test void cancelledParticipantsNeverBecomeAbsent(){
        insert("INSERT INTO activity_signup(activity_id,user_id,real_name,phone,status) VALUES(?,?,'fixture','13800000000',2)",activity,user);
        worker.execute(due("ACTIVITY_STATISTICS",activity),LocalDateTime.now());
        assertThat(jdbc.queryForObject("SELECT status FROM activity_signup WHERE activity_id=?",Integer.class,activity)).isEqualTo(2);
        verify(messages).sendActivityStatisticsNotification(owner,marker,activity,0L,0L,0L);
        assertThat(count("total_no_shows")).isIn((Integer)null, 0);
    }
    @Test void staleExpiryDoesNotClearExtendedSignupBanAndActualExpiryClearsNullFields(){
        jdbc.update("UPDATE user SET is_banned_signup=1,ban_signup_end_time=DATE_ADD(NOW(),INTERVAL 1 DAY),punishment_end_time=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE id=?",user);
        worker.execute(due("SIGNUP_PUNISHMENT_END",user),LocalDateTime.now());assertThat(count("is_banned_signup")).isEqualTo(1);
        jdbc.update("UPDATE user SET ban_signup_end_time=DATE_SUB(NOW(),INTERVAL 1 MINUTE),punishment_end_time=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=?",user);
        worker.execute(due("SIGNUP_PUNISHMENT_END",user),LocalDateTime.now());assertThat(count("is_banned_signup")).isZero();
        assertThat(jdbc.queryForObject("SELECT ban_signup_end_time FROM user WHERE id=?",LocalDateTime.class,user)).isNull();
        assertThat(jdbc.queryForObject("SELECT punishment_end_time FROM user WHERE id=?",LocalDateTime.class,user)).isNull();
    }
    @Test void missingUsersAreHarmlessAndOldBanCannotOverrideFutureExpiry(){
        worker.execute(due("MUTED_PUNISHMENT_END",user+1000000),LocalDateTime.now());
        jdbc.update("UPDATE user SET status=0 WHERE id=?",user);
        long old=due("BANNED_PUNISHMENT_END",user);task("BANNED_PUNISHMENT_END",user,LocalDateTime.now().plusDays(1));
        worker.execute(old,LocalDateTime.now());assertThat(count("status")).isZero();
    }
    @Test void punishmentIsCreatedOnceAcrossRepeatedStatisticsRecords(){
        when(context.getBean(PunishmentUtil.class).calculateRecentPunishmentDays(1,1)).thenReturn(2);
        insert("INSERT INTO activity_signup(activity_id,user_id,real_name,phone,status) VALUES(?,?,'fixture','13800000000',1)",activity,user);
        worker.execute(due("ACTIVITY_STATISTICS",activity),LocalDateTime.now());
        worker.execute(due("ACTIVITY_STATISTICS",activity),LocalDateTime.now());
        assertThat(count("is_banned_signup")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delay_task WHERE business_id=? AND task_type='SIGNUP_PUNISHMENT_END'",Long.class,user)).isEqualTo(1);
        verify(messages,times(1)).sendPunishmentStartNotification(eq(user),anyString());
    }
}
