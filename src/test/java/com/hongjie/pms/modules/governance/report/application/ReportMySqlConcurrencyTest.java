package com.hongjie.pms.modules.governance.report.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.scheduling.api.*;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.scheduling.application.DelayTaskService;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.mapper.CommentMapper;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.governance.report.api.dto.ReportRequest;
import com.hongjie.pms.modules.governance.report.infrastructure.persistence.mapper.ReportRecordMapper;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.shared.extension.target.TargetTypeCodec;
import com.hongjie.pms.architecture.support.MyBatisTestSupport;
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
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in local database tests. No schema changes or shared seed/reset scripts. */
@EnabledIfEnvironmentVariable(named="PMS_REPORT_MYSQL_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportMySqlConcurrencyTest {
    AnnotationConfigApplicationContext context;
    PmsReportServiceImpl service;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    Probe probe;
    long actor;
    final String marker="__report_it_"+UUID.randomUUID().toString().substring(0,20);
    static class Probe {
        final List<ReportTargetChanged> events=new CopyOnWriteArrayList<>();
        @TransactionalEventListener public void committed(ReportTargetChanged event){events.add(event);}
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PmsReportServiceImpl.class,ReportTargets.class,DelayTaskService.class,TargetTypeCodec.class})
    static class Config {@Bean Probe probe(){return new Probe();}}
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");
        assertThat(MyBatisTestSupport.configured()).as("test database password configured").isTrue();
        Class<?>[] mappers={ReportWriteMapper.class,ReportRecordMapper.class,PetPostMapper.class,ActivityMapper.class,CommentMapper.class,UserMapper.class,DelayTaskMapper.class};
        var factory=MyBatisTestSupport.createFactory(mappers);
        var source=factory.getConfiguration().getEnvironment().getDataSource();jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("user","pet_post","report_record","comment","delay_task"))
            assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        var template=new SqlSessionTemplate(factory);var manager=new DataSourceTransactionManager(source);
        tx=new TransactionTemplate(manager);tx.setTimeout(15);
        context=new AnnotationConfigApplicationContext();context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        for(Class<?> mapper:mappers)register(mapper,template);
        context.register(Config.class);context.refresh();service=context.getBean(PmsReportServiceImpl.class);probe=context.getBean(Probe.class);
    }
    <T> void register(Class<T> type,SqlSessionTemplate template){context.registerBean(type,()->template.getMapper(type));}
    long insert(String sql,Object... args){
        return jdbc.execute((java.sql.Connection c)->{
            try(var statement=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);
                statement.executeUpdate();try(var keys=statement.getGeneratedKeys()){if(!keys.next())throw new IllegalStateException("missing id");return keys.getLong(1);}
            }
        });
    }
    @BeforeEach void fixtures(){
        probe.events.clear();actor=insert("INSERT INTO user(user_name,password,status,is_muted) VALUES(?,?,1,0)",marker,"not-a-login-password");
    }
    @AfterEach void cleanup(){
        UserContext.clear();
        if(jdbc==null)return;
        jdbc.update("DELETE r FROM report_record r JOIN user u ON r.reporter_id=u.id WHERE u.id=? AND u.user_name=? AND r.reason=?",actor,marker,marker);
        jdbc.update("DELETE d FROM delay_task d JOIN user u ON d.business_id=u.id WHERE u.id=? AND u.user_name=? AND d.task_type IN ('MUTED_PUNISHMENT_END','BANNED_PUNISHMENT_END')",actor,marker);
        jdbc.update("DELETE c FROM comment c JOIN user u ON c.user_id=u.id WHERE u.id=? AND u.user_name=? AND c.content=?",actor,marker,marker);
        jdbc.update("DELETE p FROM pet_post p JOIN user u ON p.user_id=u.id WHERE u.id=? AND u.user_name=? AND p.title=?",actor,marker,marker);
        jdbc.update("DELETE FROM user WHERE id=? AND user_name=?",actor,marker);
    }
    @AfterAll void close(){if(context!=null)context.close();}
    void admin(){UserContext.setUser(new UserInfo(actor,marker,1,null,null));}
    long post(){return insert("INSERT INTO pet_post(user_id,type,title,content,contact_phone,status,audit_status) VALUES(?,0,?,?,?,1,1)",actor,marker,"fixture","13800000000");}
    long pending(String type,long id){return insert("INSERT INTO report_record(reporter_id,target_type,target_id,reason,status) VALUES(?,?,?,?,0)",actor,type,id,marker);}
    void submit(String type,long id){var r=new ReportRequest();r.setTargetType(type);r.setTargetId(id);r.setReason(marker);service.submit(actor,r);}
    int state(String table,long id){return jdbc.queryForObject("SELECT status FROM "+table+" WHERE id=?",Integer.class,id);}
    long tasks(String type){return jdbc.queryForObject("SELECT COUNT(*) FROM delay_task WHERE business_id=? AND task_type=?",Long.class,actor,type);}
    List<Boolean> race(Runnable a,Runnable b)throws Exception{
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        java.util.function.Function<Runnable,Callable<Boolean>> task=action->()->{
            ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("start timeout");
            try{admin();action.run();return true;}catch(BusinessException expected){return false;}finally{UserContext.clear();}
        };
        try{var first=pool.submit(task.apply(a));var second=pool.submit(task.apply(b));assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));}
        finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void aliasSubmissionsCreateOnePendingReport()throws Exception{
        long id=post();assertThat(race(()->submit("pet",id),()->submit("pet_post",id))).containsExactlyInAnyOrder(true,false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_record WHERE reporter_id=? AND target_id=? AND reason=?",Long.class,actor,id,marker)).isEqualTo(1);
    }
    @Test void concurrentAcceptsDisposeExactlyOnce()throws Exception{
        long id=post(),report=pending("pet",id);assertThat(race(()->service.handle(report,actor,1,"done"),()->service.handle(report,actor,1,"done"))).containsExactlyInAnyOrder(true,false);
        assertThat(state("report_record",report)).isEqualTo(1);assertThat(state("pet_post",id)).isEqualTo(3);assertThat(probe.events).hasSize(1);
    }
    @Test void acceptAndRejectHaveExactlyOneWinner()throws Exception{
        long id=post(),report=pending("pet",id);assertThat(race(()->service.handle(report,actor,1,"accepted"),()->service.handle(report,actor,2,"rejected"))).containsExactlyInAnyOrder(true,false);
        int status=state("report_record",report);assertThat(status).isIn(1,2);
        assertThat(state("pet_post",id)).isEqualTo(status==1?3:1);assertThat(probe.events).hasSize(status==1?1:0);
    }
    @Test void twoReportsAgainstSameUserCreateOneRecoveryTask()throws Exception{
        long one=pending("user",actor),two=pending("user",actor);
        assertThat(race(()->service.handle(one,actor,1,"done"),()->service.handle(two,actor,1,"done"))).containsOnly(true);
        assertThat(state("user",actor)).isZero();assertThat(tasks("BANNED_PUNISHMENT_END")).isEqualTo(1);
        assertThat(state("report_record",one)).isEqualTo(1);assertThat(state("report_record",two)).isEqualTo(1);
    }
    @Test void concurrentCommentReportsPersistOneMuteAndRecoveryTask()throws Exception{
        long post=post();long comment=insert("INSERT INTO comment(user_id,target_type,target_id,content,status) VALUES(?,'pet_post',?,?,1)",actor,post,marker);
        long one=pending("comment",comment),two=pending("pet_comment",comment);
        assertThat(race(()->service.handle(one,actor,1,"done"),()->service.handle(two,actor,1,"done"))).containsOnly(true);
        assertThat(state("comment",comment)).isZero();assertThat(tasks("MUTED_PUNISHMENT_END")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_muted FROM user WHERE id=?",Integer.class,actor)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT mute_end_time FROM user WHERE id=?",java.sql.Timestamp.class,actor)).isNotNull();
    }
    @Test void outerFailureRollsBackDecisionPunishmentTaskAndEvent(){
        long report=pending("user",actor);admin();
        assertThatThrownBy(()->tx.executeWithoutResult(status->{service.handle(report,actor,1,"done");throw new IllegalStateException("force rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(state("report_record",report)).isZero();assertThat(state("user",actor)).isEqualTo(1);assertThat(tasks("BANNED_PUNISHMENT_END")).isZero();assertThat(probe.events).isEmpty();
    }
    @Test void targetLockActuallyBlocksAnotherSubmission()throws Exception{
        long id=post();var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempted=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var owner=pool.submit(()->tx.executeWithoutResult(status->{context.getBean(ReportWriteMapper.class).lockPet(id);held.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}));
            assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
            var waiter=pool.submit(()->{admin();try{attempted.countDown();submit("pet",id);}finally{UserContext.clear();}});
            assertThat(attempted.await(5,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->waiter.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();owner.get(20,TimeUnit.SECONDS);waiter.get(20,TimeUnit.SECONDS);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
}
