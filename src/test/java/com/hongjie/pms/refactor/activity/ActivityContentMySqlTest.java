package com.hongjie.pms.refactor.activity;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.punishment.scheduler.*;
import com.hongjie.pms.refactor.scheduling.DelayTaskService;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.modules.activity.controller.ActivityController;
import com.hongjie.pms.modules.activity.dto.request.ActivityRequestDto;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.activity.service.ActivityService;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.search.event.ActivityUpdatedEvent;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.audit.*;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="PMS_ACTIVITY_MYSQL_TESTS",matches="true")
class ActivityContentMySqlTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    ActivityContentCommands commands;
    AuditCommandService audits;
    FeedService feed;
    Recorder recorder;
    long owner,admin,activity;
    LocalDateTime start,end;
    final String marker="__activity_content_"+UUID.randomUUID().toString().substring(0,12);

    static class Recorder {
        final List<Long> indexed=new CopyOnWriteArrayList<>();
        @EventListener public void indexed(ActivityUpdatedEvent event){indexed.add(event.getActivityId());}
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PmsActivityServiceImpl.class,ActivityContentCommands.class,ActivityScheduleWriter.class,ActivityContentListener.class,AuditWriteTargets.class,AuditCommandService.class,DelayTaskService.class,ActivityController.class})
    static class Config {
        @Bean ActivityQueries queries(){return mock(ActivityQueries.class);}
        @Bean ActivityLifecycleCommands lifecycle(){return mock(ActivityLifecycleCommands.class);}
        @Bean ActivityCache cache(){return mock(ActivityCache.class);}
        @Bean FeedService feed(){return mock(FeedService.class);}
        @Bean ActivityService legacy(){return mock(ActivityService.class);}
        @Bean ActivityParticipationCommands participation(){return mock(ActivityParticipationCommands.class);}
        @Bean Recorder recorder(){return new Recorder();}
    }
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");
        assertThat(MyBatisTestSupport.configured()).isTrue();
        Class<?>[] types={ActivityMapper.class,UserMapper.class,AuditWriteMapper.class,AuditRecordMapper.class,
                PetPostMapper.class,DailyPostMapper.class,DelayTaskMapper.class};
        var factory=MyBatisTestSupport.createFactory(types);
        var source=factory.getConfiguration().getEnvironment().getDataSource();
        jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("activity","audit_record","delay_task","user"))
            assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        var manager=new DataSourceTransactionManager(source);
        tx=new TransactionTemplate(manager);tx.setTimeout(15);
        var template=new SqlSessionTemplate(factory);
        context=new AnnotationConfigApplicationContext();
        context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        for(Class<?> type:types) register(type,template);
        context.register(Config.class);context.refresh();
        commands=context.getBean(ActivityContentCommands.class);audits=context.getBean(AuditCommandService.class);
        feed=context.getBean(FeedService.class);recorder=context.getBean(Recorder.class);
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
    @BeforeEach void fixture(){
        reset(feed,context.getBean("legacy",ActivityService.class),context.getBean(ActivityCache.class));
        recorder.indexed.clear();
        owner=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"o","fixture");
        admin=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"a","fixture");
        start=LocalDateTime.now().plusDays(2).withNano(0);end=start.plusHours(2);
        activity=insert("INSERT INTO activity(user_id,title,content,images,location,max_people,current_people,start_time,end_time,status,audit_status,deleted,like_count,view_count,comment_count) VALUES(?,?,?,'[]','fixture',10,2,?,?,0,1,0,4,5,6)",owner,marker,"original",start,end);
        insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('activity',?,1)",activity);
    }
    @AfterEach void cleanup(){
        UserContext.clear();
        jdbc.update("DELETE d FROM delay_task d JOIN activity a ON d.business_id=a.id WHERE a.user_id=? AND d.task_type IN ('ACTIVITY_REMIND','ACTIVITY_START','ACTIVITY_STATISTICS','SIGNUP_PUNISHMENT_END')",owner);
        jdbc.update("DELETE r FROM audit_record r JOIN activity a ON r.target_id=a.id WHERE a.user_id=? AND r.target_type='activity'",owner);
        jdbc.update("DELETE FROM activity WHERE user_id=?",owner);
        jdbc.update("DELETE FROM user WHERE id IN (?,?) AND user_name IN (?,?)",owner,admin,marker+"o",marker+"a");
    }
    @AfterAll void close(){if(context!=null)context.close();}
    void as(long actor,int role,Runnable action){
        UserContext.setUser(new UserInfo(actor,"fixture",role,null,null));
        try{action.run();}finally{UserContext.clear();}
    }
    ActivityRequestDto request(String content){
        var request=new ActivityRequestDto();request.setId(activity);request.setTitle(marker);request.setContent(content);
        request.setImages(List.of());request.setLocation("fixture");request.setMaxPeople(10);
        request.setStartTime(start);request.setEndTime(end);return request;
    }
    void edit(String content){as(owner,0,()->commands.edit(request(content)));}
    long rounds(int status){return jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE target_type='activity' AND target_id=? AND audit_status=?",Long.class,activity,status);}
    int value(String column){return jdbc.queryForObject("SELECT "+column+" FROM activity WHERE id=?",Integer.class,activity);}
    long tasks(){return jdbc.queryForObject("SELECT COUNT(*) FROM delay_task WHERE business_id=? AND task_type IN ('ACTIVITY_REMIND','ACTIVITY_START','ACTIVITY_STATISTICS') AND status=0",Long.class,activity);}
    @Test void editsStartReviewRoundsAndPreservePastDecisions(){
        edit("first");
        assertThat(rounds(0)).isEqualTo(1);assertThat(rounds(1)).isEqualTo(1);assertThat(value("audit_status")).isZero();
        as(admin,1,()->audits.approve(activity,"activity"));
        edit("second");as(admin,1,()->audits.reject(activity,"activity","fixture reason"));
        assertThat(rounds(0)).isZero();assertThat(rounds(1)).isEqualTo(2);assertThat(rounds(2)).isEqualTo(1);
        edit("third");assertThat(rounds(0)).isEqualTo(1);assertThat(value("status")).isZero();
        as(admin,1,()->audits.approve(activity,"activity"));assertThat(value("audit_status")).isEqualTo(1);
    }
    @Test void pendingEditsReuseOneRoundAndCountersStayUnchanged(){
        edit("first");edit("second");
        assertThat(rounds(0)).isEqualTo(1);assertThat(rounds(1)).isEqualTo(1);
        assertThat(value("current_people")).isEqualTo(2);assertThat(value("like_count")).isEqualTo(4);
        assertThat(value("view_count")).isEqualTo(5);assertThat(value("comment_count")).isEqualTo(6);
        assertThat(recorder.indexed).containsExactly(activity,activity);
    }
    @Test void unchangedApprovedContentDoesNotReopenReview(){
        edit("original");assertThat(rounds(0)).isZero();assertThat(value("audit_status")).isEqualTo(1);
        assertThat(recorder.indexed).isEmpty();
    }
    @Test void unchangedPendingContentRepairsMissingOpenReview(){
        jdbc.update("UPDATE activity SET audit_status=0 WHERE id=?",activity);
        edit("original");assertThat(rounds(0)).isEqualTo(1);
    }
    @Test void duplicatePendingHistoryRollsBackContentAndTaskChanges(){
        insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('activity',?,0)",activity);
        insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('activity',?,0)",activity);
        var request=request("must rollback");request.setStartTime(start.plusHours(2));request.setEndTime(end.plusHours(2));
        as(owner,0,()->assertThatThrownBy(()->commands.edit(request)).isInstanceOf(BusinessException.class));
        assertThat(jdbc.queryForObject("SELECT content FROM activity WHERE id=?",String.class,activity)).isEqualTo("original");
        assertThat(value("audit_status")).isEqualTo(1);assertThat(tasks()).isZero();assertThat(recorder.indexed).isEmpty();
    }
    @Test void endedByStateOrClockAndDeletedActivitiesCannotEdit(){
        for(String assignment:List.of("status=2","end_time=DATE_SUB(NOW(),INTERVAL 1 MINUTE)","deleted=1")){
            jdbc.update("UPDATE activity SET status=0,deleted=0,end_time=? WHERE id=?",end,activity);
            jdbc.update("UPDATE activity SET "+assignment+" WHERE id=?",activity);
            assertThatThrownBy(()->edit("forbidden")).isInstanceOf(BusinessException.class);
            assertThat(rounds(0)).isZero();assertThat(value("audit_status")).isEqualTo(1);
        }
    }
    @Test void ownershipAndCapacityAreValidated(){
        as(admin,1,()->assertThatThrownBy(()->commands.edit(request("unauthorized"))).isInstanceOf(BusinessException.class));
        var request=request("capacity");request.setMaxPeople(1);
        as(owner,0,()->assertThatThrownBy(()->commands.edit(request)).isInstanceOf(BusinessException.class));
        assertThat(rounds(0)).isZero();assertThat(value("max_people")).isEqualTo(10);
    }
    @Test void changedTimesReplaceOnlyPendingActivityTasks(){
        context.getBean(DelayTaskService.class).addTask("ACTIVITY_START",activity,start);
        context.getBean(DelayTaskService.class).addTask("SIGNUP_PUNISHMENT_END",activity,end);
        insert("INSERT INTO delay_task(task_type,business_id,execute_time,status) VALUES('ACTIVITY_START',?,?,1)",activity,start);
        var request=request("rescheduled");request.setStartTime(start.plusHours(3));request.setEndTime(end.plusHours(3));
        as(owner,0,()->commands.edit(request));
        assertThat(tasks()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delay_task WHERE business_id=? AND task_type='SIGNUP_PUNISHMENT_END'",Long.class,activity)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delay_task WHERE business_id=? AND status=1 AND task_type='ACTIVITY_START'",Long.class,activity)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT execute_time FROM delay_task WHERE business_id=? AND status=0 AND task_type='ACTIVITY_START'",LocalDateTime.class,activity)).isEqualTo(start.plusHours(3));
    }
    @Test void staleStartTaskCannotStartRescheduledOrUnapprovedActivity(){
        ActivityMapper mapper=context.getBean(ActivityMapper.class);
        assertThat(mapper.startIfDue(activity,LocalDateTime.now())).isZero();
        jdbc.update("UPDATE activity SET start_time=DATE_SUB(NOW(),INTERVAL 1 MINUTE),audit_status=0 WHERE id=?",activity);
        assertThat(mapper.startIfDue(activity,LocalDateTime.now())).isZero();
        jdbc.update("UPDATE activity SET audit_status=1 WHERE id=?",activity);
        assertThat(mapper.startIfDue(activity,LocalDateTime.now())).isEqualTo(1);
        assertThat(mapper.startIfDue(activity,LocalDateTime.now())).isZero();
        assertThat(value("current_people")).isEqualTo(2);assertThat(value("like_count")).isEqualTo(4);
    }
    @Test void controllerChecksPathIdAndCanPopulateMissingBodyId(){
        var request=request("controller");request.setId(null);
        as(owner,0,()->context.getBean(ActivityController.class).updateActivity(activity,request));
        assertThat(request.getId()).isEqualTo(activity);assertThat(rounds(0)).isEqualTo(1);
        request.setId(activity+1);
        as(owner,0,()->assertThatThrownBy(()->context.getBean(ActivityController.class).updateActivity(activity,request)).isInstanceOf(BusinessException.class));
        verifyNoInteractions(context.getBean("legacy",ActivityService.class));
    }
    @Test void publishingCreatesActivityReviewTasksAndResponse(){
        var request=request("created");request.setId(null);request.setUserId(admin);
        as(owner,0,()->{
            var response=context.getBean(ActivityController.class).postActivity(request).getData();
            long id=response.getId();
            assertThat(response.getUserId()).isEqualTo(owner);assertThat(response.getStatus()).isZero();
            assertThat(response.getTitle()).isEqualTo(marker);
            assertThat(jdbc.queryForObject("SELECT current_people FROM activity WHERE id=?",Integer.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT audit_status FROM activity WHERE id=?",Integer.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE target_type='activity' AND target_id=? AND audit_status=0",Long.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delay_task WHERE business_id=? AND status=0 AND task_type IN ('ACTIVITY_START','ACTIVITY_REMIND','ACTIVITY_STATISTICS')",Long.class,id)).isEqualTo(3);
            assertThat(recorder.indexed).containsExactly(id);
            verify(feed).pushToFans(eq(owner),eq(id),eq("activity"),eq(marker),eq(List.of()),eq(marker+"o"),isNull(),any());
        });
        verifyNoInteractions(context.getBean("legacy",ActivityService.class));
    }
    @Test void outerRollbackRestoresActivityReviewTasksAndSuppressesSearchEvent(){
        var request=request("rollback");request.setStartTime(start.plusHours(1));request.setEndTime(end.plusHours(1));
        as(owner,0,()->assertThatThrownBy(()->tx.executeWithoutResult(status->{
            commands.edit(request);throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class));
        assertThat(value("audit_status")).isEqualTo(1);assertThat(rounds(0)).isZero();
        assertThat(tasks()).isZero();assertThat(recorder.indexed).isEmpty();
    }
    @Test void publishingFeedFailureRollsBackActivityReviewAndTasks(){
        long[] created={0L};
        doAnswer(call->{created[0]=call.getArgument(1);throw new IllegalStateException("feed write failed");})
                .when(feed).pushToFans(any(),any(),any(),any(),any(),any(),any(),any());
        as(owner,0,()->assertThatThrownBy(()->commands.publish(request("rollback create"))).isInstanceOf(IllegalStateException.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity WHERE user_id=?",Long.class,owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_record r JOIN activity a ON a.id=r.target_id WHERE a.user_id=? AND r.target_type='activity'",Long.class,owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delay_task d JOIN activity a ON a.id=d.business_id WHERE a.user_id=? AND d.task_type LIKE 'ACTIVITY_%'",Long.class,owner)).isZero();
        assertThat(created[0]).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity WHERE id=?",Long.class,created[0])).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE target_type='activity' AND target_id=?",Long.class,created[0])).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delay_task WHERE business_id=? AND task_type LIKE 'ACTIVITY_%'",Long.class,created[0])).isZero();
        assertThat(recorder.indexed).isEmpty();
    }
    @Test void invalidTimesAndAnonymousCreationAreRejected(){
        assertThatThrownBy(()->commands.publish(request("anonymous"))).isInstanceOf(BusinessException.class);
        var request=request("invalid time");request.setEndTime(start);
        as(owner,0,()->assertThatThrownBy(()->commands.publish(request)).isInstanceOf(BusinessException.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity WHERE user_id=?",Long.class,owner)).isEqualTo(1);
    }
    @Test void editAndAuditRaceKeepReviewStateConsistent()throws Exception{
        edit("initial");
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var startGate=new CountDownLatch(1);
        java.util.function.Function<Runnable,Callable<Void>> task=action->()->{ready.countDown();if(!startGate.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");action.run();return null;};
        try{
            var edit=pool.submit(task.apply(()->edit("concurrent")));
            var review=pool.submit(task.apply(()->as(admin,1,()->audits.reject(activity,"activity","fixture reason"))));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();startGate.countDown();edit.get(20,TimeUnit.SECONDS);review.get(20,TimeUnit.SECONDS);
        }finally{startGate.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        assertThat(rounds(2)).isEqualTo(1);
        assertThat(rounds(0)).isEqualTo(value("audit_status")==0?1:0);
    }

    @Test void imageOnlyEditTriggersReviewAndCanClearOldImages(){
        jdbc.update("UPDATE activity SET images='[\"https://example.test/old.png\"]' WHERE id=?",activity);
        edit("original");
        assertThat(rounds(0)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT JSON_LENGTH(images) FROM activity WHERE id=?",Integer.class,activity)).isZero();
    }
    @Test void rejectedScheduleReplacementRollsBackTheWholeEdit(){
        context.getBean(DelayTaskService.class).addTask("ACTIVITY_START",activity,start);
        var request=request("rollback schedule");request.setStartTime(start.plusHours(3));request.setEndTime(end.plusHours(3));
        as(owner,0,()->assertThatThrownBy(()->tx.executeWithoutResult(status->{commands.edit(request);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class));
        assertThat(rounds(0)).isZero();assertThat(value("audit_status")).isEqualTo(1);assertThat(tasks()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT execute_time FROM delay_task WHERE business_id=? AND task_type='ACTIVITY_START' AND status=0",LocalDateTime.class,activity)).isEqualTo(start);
    }
}
