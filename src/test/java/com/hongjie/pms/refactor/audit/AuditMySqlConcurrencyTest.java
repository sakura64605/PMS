package com.hongjie.pms.refactor.audit;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.modules.admin.service.AdminService;
import com.hongjie.pms.refactor.admin.PmsAdminServiceImpl;
import static org.mockito.Mockito.mock;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 显式启用、仅写本机测试库。只创建和清理本测试的随机标记记录，不灌全表种子。 */
@EnabledIfEnvironmentVariable(named="PMS_AUDIT_MYSQL_TESTS", matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditMySqlConcurrencyTest {
    AnnotationConfigApplicationContext context;
    AuditCommandService service;
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    Probe probe;
    final String marker="__audit_it_"+UUID.randomUUID();
    final List<Long> created=new ArrayList<>();

    static class Probe {
        final List<AuditChanged> events=new CopyOnWriteArrayList<>();
        @TransactionalEventListener public void committed(AuditChanged event){events.add(event);}
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({AuditWriteTargets.class,AuditCommandService.class,PmsAdminServiceImpl.class})
    static class Config {
        @Bean com.hongjie.pms.refactor.user.UserAdministration accounts(){return mock(com.hongjie.pms.refactor.user.UserAdministration.class);}
        @Bean Probe probe(){return new Probe();}
        @Bean AdminService adminServiceImpl(){return mock(AdminService.class);}
    }
    @BeforeAll void open() {
        assumeTrue(MyBatisTestSupport.configured(),MyBatisTestSupport.skipReason());
        AuditMySqlTestDatabase.requireLocalTestDatabase(MyBatisTestSupport.jdbcUrl());
        SqlSessionFactory factory=MyBatisTestSupport.createFactory(AuditWriteMapper.class,AuditRecordMapper.class,
                PetPostMapper.class,ActivityMapper.class,DailyPostMapper.class);
        DataSource source=factory.getConfiguration().getEnvironment().getDataSource();
        jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("pet_post","audit_record")) {
            assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                    String.class,table)).isEqualToIgnoringCase("InnoDB");
        }
        var template=new SqlSessionTemplate(factory);
        var manager=new DataSourceTransactionManager(source);
        transaction=new TransactionTemplate(manager);transaction.setTimeout(15);
        context=new AnnotationConfigApplicationContext();
        context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        context.registerBean(AuditWriteMapper.class,()->template.getMapper(AuditWriteMapper.class));
        context.registerBean(AuditRecordMapper.class,()->template.getMapper(AuditRecordMapper.class));
        context.registerBean(PetPostMapper.class,()->template.getMapper(PetPostMapper.class));
        context.registerBean(ActivityMapper.class,()->template.getMapper(ActivityMapper.class));
        context.registerBean(DailyPostMapper.class,()->template.getMapper(DailyPostMapper.class));
        context.register(Config.class);context.refresh();
        service=context.getBean(AuditCommandService.class);probe=context.getBean(Probe.class);
    }
    @BeforeEach void resetEvents(){probe.events.clear();}
    @AfterEach void cleanup() {
        UserContext.clear();
        for(Long id:created){
            // 只有目标行仍带本测试标记时才允许清理其审核记录。
            jdbc.update("DELETE a FROM audit_record a JOIN pet_post p ON p.id=a.target_id "
                    +"WHERE a.target_type='adopt' AND p.id=? AND p.title=?",id,marker);
            jdbc.update("DELETE FROM pet_post WHERE id=? AND title=?",id,marker);
        }
        created.clear();
    }
    @AfterAll void close(){if(context!=null)context.close();}
    long post(int auditStatus) {
        Long id=jdbc.execute((java.sql.Connection connection)->{
            try(var insert=connection.prepareStatement("INSERT INTO pet_post(user_id,type,title,content,contact_phone,status,audit_status) VALUES(1,0,?,?,?,0,?)",Statement.RETURN_GENERATED_KEYS)){
                insert.setString(1,marker);insert.setString(2,"integration fixture");insert.setString(3,"13800000000");insert.setInt(4,auditStatus);
                insert.executeUpdate();try(var keys=insert.getGeneratedKeys()){if(!keys.next())throw new IllegalStateException("missing generated id");return keys.getLong(1);}
            }
        });created.add(id);return id;
    }
    void pending(long id){jdbc.update("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('adopt',?,0)",id);}
    long count(long id,int state){return jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE target_type='adopt' AND target_id=? AND audit_status=?",Long.class,id,state);}
    void admin(){UserContext.setUser(new UserInfo(1L,"integration-admin",1,null,null));}
    boolean attempt(Runnable action){try{admin();action.run();return true;}catch(BusinessException expected){return false;}finally{UserContext.clear();}}
    List<Boolean> race(Runnable action) throws Exception {return race(action,action);}
    List<Boolean> race(Runnable firstAction,Runnable secondAction) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        java.util.function.Function<Runnable,Callable<Boolean>> task=action->()->{
            ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("start timeout");return attempt(action);
        };
        try{
            Future<Boolean> first=pool.submit(task.apply(firstAction)),second=pool.submit(task.apply(secondAction));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void legacyAdminAndNewAuditEntryShareTheSameLockProtocol() throws Exception {
        long id=post(0);pending(id);
        AdminService admin=context.getBean(AdminService.class);
        assertThat(race(()->service.approve(id,"adopt"),()->admin.accept(id))).containsExactlyInAnyOrder(true,false);
        assertThat(count(id,1)).isEqualTo(1);assertThat(count(id,0)).isZero();
        assertThat(jdbc.queryForObject("SELECT audit_status FROM pet_post WHERE id=?",Integer.class,id)).isEqualTo(1);
        assertThat(probe.events).hasSize(1);
    }
    @Test void concurrentSubmitsCreateExactlyOnePendingRecord() throws Exception {
        long id=post(0);
        assertThat(race(()->service.submit("adopt",id))).containsExactlyInAnyOrder(true,false);
        assertThat(count(id,0)).isEqualTo(1);
        assertThat(probe.events).hasSize(1);
    }
    @Test void concurrentApprovalsCommitExactlyOnce() throws Exception {
        long id=post(0);pending(id);
        assertThat(race(()->service.approve(id,"adopt"))).containsExactlyInAnyOrder(true,false);
        assertThat(count(id,1)).isEqualTo(1);assertThat(count(id,0)).isZero();
        assertThat(jdbc.queryForObject("SELECT audit_status FROM pet_post WHERE id=?",Integer.class,id)).isEqualTo(1);
        assertThat(probe.events).hasSize(1);
    }
    @Test void batchTargetFailureRollsBackEarlierDatabaseUpdatesAndEvents() {
        long first=post(0),second=post(1);pending(first);pending(second);admin();
        assertThatThrownBy(()->service.batchApprove(List.of(first,second),"adopt")).isInstanceOf(BusinessException.class);
        assertThat(count(first,0)).isEqualTo(1);assertThat(count(second,0)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT audit_status FROM pet_post WHERE id=?",Integer.class,first)).isZero();
        assertThat(jdbc.queryForObject("SELECT audit_status FROM pet_post WHERE id=?",Integer.class,second)).isEqualTo(1);
        assertThat(probe.events).isEmpty();
    }
    @Test void anotherTransactionCannotPassAnExistingTargetLock() throws Exception {
        long id=post(0);var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempted=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        Future<?> owner=null,waiter=null;
        try{
            owner=pool.submit(()->transaction.executeWithoutResult(status->{
                context.getBean(AuditWriteMapper.class).lockPet(id,0);held.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            }));
            assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();
            waiter=pool.submit(()->{attempted.countDown();service.submit("adopt",id);});
            assertThat(attempted.await(5,TimeUnit.SECONDS)).isTrue();
            Future<?> blocked=waiter;
            assertThatThrownBy(()->blocked.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();owner.get(20,TimeUnit.SECONDS);waiter.get(20,TimeUnit.SECONDS);
            assertThat(count(id,0)).isEqualTo(1);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
}
