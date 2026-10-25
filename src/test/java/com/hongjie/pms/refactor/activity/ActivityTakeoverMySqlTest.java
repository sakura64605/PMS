package com.hongjie.pms.refactor.activity;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.punishment.scheduler.*;
import com.hongjie.pms.refactor.scheduling.DelayTaskService;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.modules.activity.controller.ActivityController;
import com.hongjie.pms.modules.activity.dto.request.ActivityListRequestDto;
import com.hongjie.pms.modules.activity.mapper.*;
import com.hongjie.pms.modules.activity.service.ActivityService;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="PMS_ACTIVITY_MYSQL_TESTS",matches="true")
class ActivityTakeoverMySqlTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    ActivityService service;
    long owner,viewer,admin,activity;
    final String marker="__activity_all_"+UUID.randomUUID().toString().substring(0,12);
    LocalDateTime start,end;

    static class Recorder {
        final List<Long> ids=new CopyOnWriteArrayList<>();
        @EventListener public void changed(ActivityUpdatedEvent event){ids.add(event.getActivityId());}
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PmsActivityServiceImpl.class,ActivityQueries.class,ActivityDtoAssembler.class,ActivityLifecycleCommands.class,ActivityScheduleWriter.class,ActivityContentListener.class,DelayTaskService.class,AuditCommandService.class,AuditWriteTargets.class,ActivityController.class})
    static class Config {
        @Bean ActivityCache cache(){return mock(ActivityCache.class);}
        @Bean ActivityContentCommands content(){return mock(ActivityContentCommands.class);}
        @Bean ActivityParticipationCommands participation(){return mock(ActivityParticipationCommands.class);}
        @Bean ActivityService legacy(){return mock(ActivityService.class);}
        @Bean Recorder recorder(){return new Recorder();}
    }
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");
        assertThat(MyBatisTestSupport.configured()).isTrue();
        Class<?>[] types={ActivityMapper.class,ActivitySignupMapper.class,UserMapper.class,LikeRecordMapper.class,
                FollowMapper.class,AuditWriteMapper.class,AuditRecordMapper.class,PetPostMapper.class,DailyPostMapper.class,DelayTaskMapper.class};
        var factory=MyBatisTestSupport.createFactory(types);var source=factory.getConfiguration().getEnvironment().getDataSource();
        jdbc=new JdbcTemplate(source);assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("activity","activity_signup","delay_task","audit_record","user","follow","like_record"))
            assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);tx.setTimeout(15);
        context=new AnnotationConfigApplicationContext();context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        var template=new SqlSessionTemplate(factory);for(Class<?> type:types)register(type,template);
        context.register(Config.class);context.refresh();service=context.getBean(ActivityService.class);
        assertThat(service).isInstanceOf(PmsActivityServiceImpl.class);
    }
    <T> void register(Class<T> type,SqlSessionTemplate template){context.registerBean(type,()->template.getMapper(type));}
    long insert(String sql,Object...args){
        return jdbc.execute((java.sql.Connection connection)->{
            try(var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);statement.executeUpdate();
                try(var keys=statement.getGeneratedKeys()){assertThat(keys.next()).isTrue();return keys.getLong(1);}
            }
        });
    }
    @BeforeEach void fixture(){
        reset(context.getBean("legacy",ActivityService.class),context.getBean(ActivityCache.class));
        context.getBean(Recorder.class).ids.clear();
        owner=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"o","fixture");
        viewer=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"v","fixture");
        admin=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"a","fixture");
        start=LocalDateTime.now().plusDays(2).withNano(0);end=start.plusHours(2);
        activity=activity("main",0,1,0);
    }
    long activity(String suffix,int status,int audit,int deleted){
        return insert("INSERT INTO activity(user_id,title,content,images,location,max_people,current_people,start_time,end_time,status,audit_status,deleted,view_count,like_count,comment_count,create_time) VALUES(?,?,?,'[\\\"https://example.test/cover.png\\\"]','fixture',10,0,?,?,?,?,?,0,0,0,?)",
                owner,marker+suffix,"fixture content",start,end,status,audit,deleted,LocalDateTime.now().withNano(0));
    }
    void signup(long id,long user,int status){
        insert("INSERT INTO activity_signup(activity_id,user_id,real_name,phone,remark,status) VALUES(?,?,'fixture','13800000000','private',?)",id,user,status);
    }
    @AfterEach void cleanup(){
        UserContext.clear();
        jdbc.update("DELETE s FROM activity_signup s JOIN activity a ON a.id=s.activity_id WHERE a.user_id=?",owner);
        jdbc.update("DELETE d FROM delay_task d JOIN activity a ON a.id=d.business_id WHERE a.user_id=? AND d.task_type LIKE 'ACTIVITY_%'",owner);
        jdbc.update("DELETE r FROM audit_record r JOIN activity a ON a.id=r.target_id WHERE a.user_id=? AND r.target_type='activity'",owner);
        jdbc.update("DELETE FROM like_record WHERE user_id=? AND target_type='pet_activity' AND target_id IN (SELECT id FROM activity WHERE user_id=?)",viewer,owner);
        jdbc.update("DELETE FROM follow WHERE follower_id=? AND following_id=?",viewer,owner);
        jdbc.update("DELETE FROM activity WHERE user_id=?",owner);
        jdbc.update("DELETE FROM user WHERE id IN (?,?,?) AND user_name IN (?,?,?)",owner,viewer,admin,marker+"o",marker+"v",marker+"a");
        verifyNoInteractions(context.getBean("legacy",ActivityService.class));
    }
    @AfterAll void close(){if(context!=null)context.close();}
    void as(long id,int role,Runnable action){
        UserContext.setUser(new UserInfo(id,"fixture",role,null,null));
        try{action.run();}finally{UserContext.clear();}
    }
    ActivityListRequestDto query(){var request=new ActivityListRequestDto();request.setKeyword(marker);return request;}
    int value(String column){return jdbc.queryForObject("SELECT "+column+" FROM activity WHERE id=?",Integer.class,activity);}
    long tasks(){return jdbc.queryForObject("SELECT COUNT(*) FROM delay_task WHERE business_id=? AND task_type LIKE 'ACTIVITY_%' AND status=0",Long.class,activity);}
    @Test void publicUserFilterCannotExposePendingRejectedOrDeletedContent(){
        activity("pending",0,0,0);activity("rejected",3,2,0);activity("deleted",0,1,1);activity("offline",3,1,0);
        var request=query();request.setUserId(owner);request.setAuditStatus(0);
        as(viewer,0,()->assertThat(service.getActivityList(request).getRecords()).extracting(row->row.getId()).containsExactly(activity));
        assertThat(service.getActivityList(request).getRecords()).extracting(row->row.getId()).containsExactly(activity);
    }
    @Test void myListIncludesPendingAndRejectedAndHonorsAuditFilter(){
        long pending=activity("pending",0,0,0),rejected=activity("rejected",3,2,0);
        as(owner,0,()->{
            var request=query();request.setUserId(viewer);
            assertThat(context.getBean(ActivityController.class).getMyActivityList(request).getData().getRecords())
                    .extracting(row->row.getId()).containsExactlyInAnyOrder(activity,pending,rejected);
            request.setAuditStatus(0);
            assertThat(service.getActivityList(request).getRecords()).extracting(row->row.getId()).containsExactly(pending);
        });
    }
    @Test void controllerPublicListIgnoresRequestedPendingAudit(){
        activity("pending",0,0,0);var request=query();request.setUserId(owner);request.setAuditStatus(0);
        as(owner,0,()->assertThat(context.getBean(ActivityController.class).getActivityList(request).getData().getRecords())
                .extracting(row->row.getId()).containsExactly(activity));
    }
    @Test void highPageRetainsTotalAndInvalidPaginationIsNormalized(){
        var request=query();request.setPageNum(999);
        var page=service.getActivityList(request);assertThat(page.getRecords()).isEmpty();assertThat(page.getTotal()).isEqualTo(1);
        request.setPageNum(-1);request.setPageSize(10000);
        page=service.getActivityList(request);assertThat(page.getCurrent()).isEqualTo(1);assertThat(page.getSize()).isEqualTo(100);
        assertThat(page.getRecords()).hasSize(1);assertThat(page.getRecords().get(0).getImages()).isEqualTo("https://example.test/cover.png");
    }
    @Test void filtersAndStableOrderingAreShared(){
        activity("second",0,1,0);
        var request=query();request.setOrderBy("startTime");request.setOrder("asc");request.setPageSize(1);
        long first=service.getActivityList(request).getRecords().get(0).getId();request.setPageNum(2);
        assertThat(service.getActivityList(request).getRecords().get(0).getId()).isGreaterThan(first);
        request.setLocation("elsewhere");assertThat(service.getActivityList(request).getTotal()).isZero();
        request.setLocation(null);request.setStartDate(end);request.setEndDate(start);
        assertThatThrownBy(()->service.getActivityList(request)).isInstanceOf(BusinessException.class);
    }
    @Test void ownerAndAdminCanPreviewPendingButPublicCannot(){
        jdbc.update("UPDATE activity SET audit_status=0 WHERE id=?",activity);
        assertThatThrownBy(()->service.getActivityDetail(activity)).isInstanceOf(BusinessException.class);
        as(viewer,0,()->assertThatThrownBy(()->service.getActivityDetail(activity)).isInstanceOf(BusinessException.class));
        as(owner,0,()->assertThat(service.getActivityDetail(activity).getAuditStatus()).isZero());
        as(admin,1,()->assertThat(service.getActivityDetail(activity).getAuditStatus()).isZero());
        assertThat(value("view_count")).isZero();
    }
    @Test void deletedDetailsAreHiddenEvenFromOwner(){
        jdbc.update("UPDATE activity SET deleted=1 WHERE id=?",activity);
        as(owner,0,()->assertThatThrownBy(()->service.getActivityDetail(activity)).isInstanceOf(BusinessException.class));
        as(admin,1,()->assertThatThrownBy(()->service.getActivityDetail(activity)).isInstanceOf(BusinessException.class));
    }
    @Test void personalizedFlagsDoNotLeakAcrossViewersAndCancelledRowsDoNotCount(){
        signup(activity,viewer,1);
        insert("INSERT INTO like_record(user_id,target_id,target_type) VALUES(?,?,'pet_activity')",viewer,activity);
        insert("INSERT INTO follow(follower_id,following_id) VALUES(?,?)",viewer,owner);
        as(viewer,0,()->{
            var detail=service.getActivityDetail(activity);
            assertThat(detail.getIsLike()).isEqualTo(1);assertThat(detail.getIsSignUp()).isEqualTo(1);
            assertThat(detail.getUser().getIsFollow()).isTrue();
            assertThat(service.getActivityList(query()).getRecords().get(0).getIsSignedUp()).isEqualTo(1);
        });
        as(admin,1,()->{
            var detail=service.getActivityDetail(activity);
            assertThat(detail.getIsLike()).isZero();assertThat(detail.getIsSignUp()).isZero();assertThat(detail.getUser().getIsFollow()).isFalse();
        });
        jdbc.update("UPDATE activity_signup SET status=2 WHERE activity_id=?",activity);
        as(viewer,0,()->{
            assertThat(service.getActivityDetail(activity).getIsSignUp()).isZero();
            assertThat(service.getActivityList(query()).getRecords().get(0).getIsSignedUp()).isZero();
        });
    }
    @Test void detailsKeepLegacyFlagAndExposeReactBoolean()throws Exception{
        insert("INSERT INTO like_record(user_id,target_id,target_type) VALUES(?,?,'pet_activity')",viewer,activity);
        UserContext.setUser(new UserInfo(viewer,"fixture",0,null,null));
        MockMvcBuilders.standaloneSetup(context.getBean(ActivityController.class)).build()
                .perform(get("/pet-system/activity/detail/"+activity)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isLike").value(1)).andExpect(jsonPath("$.data.isLiked").value(true))
                .andExpect(jsonPath("$.data.id").value(activity)).andExpect(jsonPath("$.data.content").value("fixture content"));
    }
    @Test void viewsIncrementOnlyForLoggedInNonOwnersAndNeverOverwriteCounters(){
        service.getActivityDetail(activity);
        as(owner,0,()->service.getActivityDetail(activity));assertThat(value("view_count")).isZero();
        as(viewer,0,()->service.getActivityDetail(activity));assertThat(value("view_count")).isEqualTo(1);
        assertThat(value("current_people")).isZero();assertThat(value("like_count")).isZero();
    }
    @Test void missingAuthorsProduceTombstoneInsteadOfNullPointer(){
        jdbc.update("DELETE FROM user WHERE id=? AND user_name=?",owner,marker+"o");
        assertThat(service.getActivityDetail(activity).getUser().getUserId()).isEqualTo(owner);
        assertThat(service.getActivityDetail(activity).getUser().getUsername()).isNotBlank();
        assertThat(service.getActivityList(query()).getRecords().get(0).getUser()).isNotNull();
    }
    @Test void signupPersonalDataIsOrganizerOnlyAndPagingRetainsTotal(){
        signup(activity,viewer,1);
        as(viewer,0,()->assertThatThrownBy(()->service.getSignUpList(activity,1,10)).isInstanceOf(BusinessException.class));
        as(admin,1,()->assertThatThrownBy(()->service.getSignUpList(activity,1,10)).isInstanceOf(BusinessException.class));
        as(owner,0,()->{
            var row=context.getBean(ActivityController.class).getSignUpList(activity,1,10).getData().getRecords().get(0);
            assertThat(row.getPhone()).isEqualTo("13800000000");assertThat(row.getUser().getUserId()).isEqualTo(viewer);
            assertThat(row.getIsCheckedIn()).isFalse();assertThat(row.getStatus()).isEqualTo(1);
            var page=service.getSignUpList(activity,999,10);assertThat(page.getRecords()).isEmpty();assertThat(page.getTotal()).isEqualTo(1);
        });
    }
    @Test void recycleListIsOwnerScopedAndIncludesAuditState(){
        long deleted=activity("deleted",3,2,1);
        as(owner,0,()->{
            var request=query();request.setUserId(viewer);
            var rows=context.getBean(ActivityController.class).getRecycleBinList(request).getData().getRecords();
            assertThat(rows).extracting(row->row.getId()).containsExactly(deleted);assertThat(rows.get(0).getAuditStatus()).isEqualTo(2);
        });
        as(viewer,0,()->assertThat(service.getRecycleBinList(query()).getTotal()).isZero());
        assertThatThrownBy(()->service.getRecycleBinList(query())).isInstanceOf(BusinessException.class);
    }
    @Test void deletingCancelsTasksAndRestoringRecreatesThemWithoutLosingCounters(){
        jdbc.update("UPDATE activity SET current_people=2,like_count=7 WHERE id=?",activity);
        context.getBean(DelayTaskService.class).addTask("ACTIVITY_START",activity,start);
        as(owner,0,()->context.getBean(ActivityController.class).deleteActivity(activity));
        assertThat(value("deleted")).isEqualTo(1);assertThat(tasks()).isZero();
        assertThatThrownBy(()->service.getActivityDetail(activity)).isInstanceOf(BusinessException.class);
        as(owner,0,()->context.getBean(ActivityController.class).recoverActivity(activity));
        assertThat(value("deleted")).isZero();assertThat(tasks()).isEqualTo(3);
        assertThat(value("current_people")).isEqualTo(2);assertThat(value("like_count")).isEqualTo(7);
        assertThat(context.getBean(Recorder.class).ids).containsExactly(activity,activity);
    }
    @Test void restorePendingContentRepairsMissingReview(){
        jdbc.update("UPDATE activity SET deleted=1,audit_status=0 WHERE id=?",activity);
        as(owner,0,()->service.recoverActivity(activity));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE target_type='activity' AND target_id=? AND audit_status=0",Long.class,activity)).isEqualTo(1);
        assertThat(value("audit_status")).isZero();
    }
    @Test void adminsCanRestoreButCannotDeleteAnotherOrganizersActivity(){
        as(admin,1,()->assertThatThrownBy(()->service.deleteActivity(activity)).isInstanceOf(BusinessException.class));
        jdbc.update("UPDATE activity SET deleted=1 WHERE id=?",activity);
        as(viewer,0,()->assertThatThrownBy(()->service.recoverActivity(activity)).isInstanceOf(BusinessException.class));
        as(admin,1,()->service.recoverActivity(activity));assertThat(value("deleted")).isZero();
    }
    @Test void endedByStateOrTimeCannotBeDeletedOrRestored(){
        for(String assignment:List.of("status=2","end_time=DATE_SUB(NOW(),INTERVAL 1 MINUTE)")){
            jdbc.update("UPDATE activity SET status=0,deleted=0,end_time=? WHERE id=?",end,activity);
            jdbc.update("UPDATE activity SET "+assignment+" WHERE id=?",activity);
            as(owner,0,()->assertThatThrownBy(()->service.deleteActivity(activity)).isInstanceOf(BusinessException.class));
            jdbc.update("UPDATE activity SET deleted=1 WHERE id=?",activity);
            as(owner,0,()->assertThatThrownBy(()->service.recoverActivity(activity)).isInstanceOf(BusinessException.class));
            as(admin,1,()->assertThatThrownBy(()->service.recoverActivity(activity)).isInstanceOf(BusinessException.class));
        }
    }
    @Test void outerRollbackRestoresDeleteAndTasksAndSuppressesEvents(){
        context.getBean(DelayTaskService.class).addTask("ACTIVITY_START",activity,start);
        as(owner,0,()->assertThatThrownBy(()->tx.executeWithoutResult(status->{service.deleteActivity(activity);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class));
        assertThat(value("deleted")).isZero();assertThat(tasks()).isEqualTo(1);assertThat(context.getBean(Recorder.class).ids).isEmpty();
    }
    @Test void outerRollbackRestoresRecoveryAndSuppressesEvents(){
        jdbc.update("UPDATE activity SET deleted=1,audit_status=0 WHERE id=?",activity);
        as(owner,0,()->assertThatThrownBy(()->tx.executeWithoutResult(status->{service.recoverActivity(activity);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class));
        assertThat(value("deleted")).isEqualTo(1);assertThat(tasks()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE target_type='activity' AND target_id=?",Long.class,activity)).isZero();
        assertThat(context.getBean(Recorder.class).ids).isEmpty();
    }
    @Test void deletedOrUnapprovedActivitiesCannotRunStaleStatistics(){
        ActivityMapper mapper=context.getBean(ActivityMapper.class);
        jdbc.update("UPDATE activity SET end_time=DATE_SUB(NOW(),INTERVAL 1 MINUTE),deleted=1 WHERE id=?",activity);
        assertThat(mapper.finishForStatisticsIfDue(activity,LocalDateTime.now())).isZero();
        jdbc.update("UPDATE activity SET deleted=0,audit_status=0 WHERE id=?",activity);
        assertThat(mapper.finishForStatisticsIfDue(activity,LocalDateTime.now())).isZero();
        jdbc.update("UPDATE activity SET audit_status=1 WHERE id=?",activity);
        assertThat(mapper.finishForStatisticsIfDue(activity,LocalDateTime.now())).isEqualTo(1);assertThat(value("status")).isEqualTo(2);
    }
    @Test void duplicateDeletionHasOneWinner()throws Exception{
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var gate=new CountDownLatch(1);
        var successes=new java.util.concurrent.atomic.AtomicInteger();
        Callable<Void> task=()->{ready.countDown();if(!gate.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");
            as(owner,0,()->{try{service.deleteActivity(activity);successes.incrementAndGet();}catch(BusinessException expected){}});return null;};
        try{var one=pool.submit(task);var two=pool.submit(task);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();gate.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);}
        finally{gate.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        assertThat(successes.get()).isEqualTo(1);assertThat(value("deleted")).isEqualTo(1);
        assertThat(context.getBean(Recorder.class).ids).containsExactly(activity);
    }
    @Test void anonymousMyActivityDoesNotFallBackToPublicQuery(){
        assertThatThrownBy(()->context.getBean(ActivityController.class).getMyActivityList(query())).isInstanceOf(BusinessException.class);
    }
}
