package com.hongjie.pms.modules.analytics.application;

import com.hongjie.pms.architecture.support.MyBatisTestSupport;
import com.hongjie.pms.platform.utils.RedisScanUtil;
import com.hongjie.pms.modules.analytics.infrastructure.persistence.mapper.DailyStatisticsMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="PMS_STATISTICS_MYSQL_TESTS",matches="true")
class StatisticsModuleMySqlTest {
    static AnnotationConfigApplicationContext context;
    static javax.sql.DataSource dataSource;
    static StatisticsCalendar calendar;
    static RedisScanUtil scan;
    static StatisticsFactMapper facts;
    static DailyStatisticsMapper rows;
    static StatisticsDayWriter writer;
    static AtomicBoolean fail=new AtomicBoolean();
    static java.util.concurrent.atomic.AtomicReference<LocalDate> failDate=new java.util.concurrent.atomic.AtomicReference<>();
    static LocalDate base=LocalDate.of(1750+new Random().nextInt(40),8,1);
    static String owner="stats_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
    static Map<String,List<Long>> owned=new HashMap<>();
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @Import({StatisticsContributors.class,StatisticsCollector.class,StatisticsDayWriter.class,StatisticsCache.class})
    static class Config {}
    @BeforeAll static void init()throws Exception{
        Assumptions.assumeTrue(MyBatisTestSupport.configured());
        var factory=MyBatisTestSupport.createFactory(StatisticsFactMapper.class,DailyStatisticsMapper.class);
        dataSource=factory.getConfiguration().getEnvironment().getDataSource();
        try(var c=dataSource.getConnection();var p=c.prepareStatement("SELECT COUNT(*) FROM daily_statistics WHERE stat_date BETWEEN ? AND ?")){
            p.setObject(1,base.minusDays(40));p.setObject(2,base.plusDays(40));
            try(var r=p.executeQuery()){r.next();assertThat(r.getInt(1)).as("Reserved test date window must be empty").isZero();}
        }
        calendar=mock(StatisticsCalendar.class);scan=mock(RedisScanUtil.class);
        context=new AnnotationConfigApplicationContext();
        context.registerBean(StatisticsCalendar.class,()->calendar);
        context.registerBean(RedisScanUtil.class,()->scan);
        context.registerBean(org.springframework.transaction.PlatformTransactionManager.class,()->new DataSourceTransactionManager(dataSource));
        SqlSessionTemplate template=new SqlSessionTemplate(factory);
        context.registerBean(StatisticsFactMapper.class,()->template.getMapper(StatisticsFactMapper.class));
        context.registerBean(DailyStatisticsMapper.class,()->template.getMapper(DailyStatisticsMapper.class));
        context.registerBean("failureProbe",StatisticsContributor.class,()->new StatisticsContributor(){
            public String name(){return "failure-probe";}
            public void collect(com.hongjie.pms.modules.analytics.infrastructure.persistence.entity.DailyStatistics row,LocalDateTime start,LocalDateTime end){
                if(fail.get() || start.toLocalDate().equals(failDate.get()))throw new IllegalStateException("injected collector failure");
            }
        });
        context.register(Config.class);context.refresh();
        facts=context.getBean(StatisticsFactMapper.class);rows=context.getBean(DailyStatisticsMapper.class);writer=context.getBean(StatisticsDayWriter.class);
    }
    @BeforeEach void before(){
        fail.set(false);failDate.set(null);reset(scan,calendar);
        when(calendar.today()).thenReturn(base.plusDays(8));
        when(calendar.now()).thenReturn(base.plusDays(8).atTime(12,0));
    }
    @AfterEach void clean()throws Exception{
        fail.set(false);failDate.set(null);
        try(var c=dataSource.getConnection()){
            for(var entry:owned.entrySet())for(Long id:entry.getValue())
                try(var p=c.prepareStatement("DELETE FROM "+entry.getKey()+" WHERE id=?")){p.setLong(1,id);p.executeUpdate();}
            owned.clear();
            try(var p=c.prepareStatement("DELETE FROM daily_statistics WHERE stat_date BETWEEN ? AND ?")){
                p.setObject(1,base.minusDays(40));p.setObject(2,base.plusDays(40));p.executeUpdate();
            }
        }
    }
    @AfterAll static void close(){if(context!=null)context.close();}
    static long insert(String table,String columns,Object... values)throws Exception{
        String sql="INSERT INTO "+table+"("+columns+") VALUES("+String.join(",",Collections.nCopies(values.length,"?"))+")";
        try(var c=dataSource.getConnection();var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);p.executeUpdate();
            try(var r=p.getGeneratedKeys()){r.next();long id=r.getLong(1);owned.computeIfAbsent(table,t->new ArrayList<>()).add(id);return id;}
        }
    }
    static long user(LocalDateTime created,LocalDateTime active)throws Exception{
        return insert("user","user_name,password,create_time,last_active_time",owner+"_"+UUID.randomUUID().toString().substring(0,8),"test-only",created,active);
    }
    StatisticsFacts aggregate(StatisticsSource source){
        return facts.aggregate(source,base.atStartOfDay(),base.plusDays(1).atStartOfDay(),base.minusDays(6).atStartOfDay(),base.minusDays(29).atStartOfDay());
    }
    @Test void midnightAndHistoricalTotalsExcludeNextDayAndFutureUsers()throws Exception{
        var before=aggregate(StatisticsSource.USERS);
        user(base.minusDays(1).atTime(12,0),base.atTime(12,0));
        user(base.atStartOfDay(),base.atStartOfDay());
        user(base.plusDays(1).atStartOfDay(),base.plusDays(1).atStartOfDay());
        var after=aggregate(StatisticsSource.USERS);
        assertThat(after.getCreated()-before.getCreated()).isEqualTo(1);
        assertThat(after.getTotal()-before.getTotal()).isEqualTo(2);
        assertThat(after.getActive()-before.getActive()).isEqualTo(2);
    }
    @Test void weeklyAndMonthlyWindowsHaveExactlySevenAndThirtyDays()throws Exception{
        var before=aggregate(StatisticsSource.USERS);
        user(base.minusDays(100).atStartOfDay(),base.minusDays(6).atStartOfDay());
        user(base.minusDays(100).atStartOfDay(),base.minusDays(7).atStartOfDay());
        user(base.minusDays(100).atStartOfDay(),base.minusDays(29).atStartOfDay());
        user(base.minusDays(100).atStartOfDay(),base.minusDays(30).atStartOfDay());
        var after=aggregate(StatisticsSource.USERS);
        assertThat(after.getWeekActive()-before.getWeekActive()).isEqualTo(1);
        assertThat(after.getMonthActive()-before.getMonthActive()).isEqualTo(3);
    }
    @Test void decisionsUseDecisionTimestampAndHandledReportStatusOne()throws Exception{
        var auditBefore=aggregate(StatisticsSource.AUDITS);var reportBefore=aggregate(StatisticsSource.REPORTS);
        long uid=user(base.minusDays(1).atStartOfDay(),null);
        insert("audit_record","target_type,target_id,audit_status,create_time,audit_time","daily",uid,1,base.minusDays(1).atStartOfDay(),base.atTime(12,0));
        insert("audit_record","target_type,target_id,audit_status,create_time,audit_time","daily",uid,2,base.atStartOfDay(),base.plusDays(1).atStartOfDay());
        insert("report_record","reporter_id,target_type,target_id,reason,status,create_time,handle_time",uid,"daily",uid,"test",1,base.atStartOfDay(),base.atTime(12,0));
        insert("report_record","reporter_id,target_type,target_id,reason,status,create_time,handle_time",uid,"daily",uid,"test",2,base.atStartOfDay(),base.atTime(12,0));
        var audit=aggregate(StatisticsSource.AUDITS);var report=aggregate(StatisticsSource.REPORTS);
        assertThat(audit.getApproved()-auditBefore.getApproved()).isEqualTo(1);
        assertThat(audit.getRejected()-auditBefore.getRejected()).isZero();
        assertThat(audit.getPending()-auditBefore.getPending()).isEqualTo(1);
        assertThat(report.getHandled()-reportBefore.getHandled()).isEqualTo(1);
    }
    @Test void allContributorsPersistAndCacheEvictsOnlyAfterCommit(){
        assertThat(writer.write(base,false)).isTrue();
        var snapshot=rows.selectByDate(base);
        assertThat(snapshot.getId()).isNotNull();assertThat(snapshot.getNewPrivateMessageCount()).isNotNull();
        assertThat(snapshot.getTotalShareCount()).isZero();
        verify(scan).scanAndDelete("statistics:*");
        assertThat(writer.write(base,false)).isFalse();
        verifyNoMoreInteractions(scan);
    }
    @Test void failedRebuildPreservesExistingRowAndRollsBackNewPlaceholder(){
        writer.write(base,false);var old=rows.selectByDate(base);reset(scan);
        fail.set(true);
        assertThatThrownBy(()->writer.write(base,true)).isInstanceOf(IllegalStateException.class);
        assertThat(rows.selectByDate(base)).isEqualTo(old);
        assertThatThrownBy(()->writer.write(base.plusDays(1),false)).isInstanceOf(IllegalStateException.class);
        assertThat(rows.selectByDate(base.plusDays(1))).isNull();
        verifyNoInteractions(scan);
    }
    @Test void concurrentGenerateAndRebuildKeepExactlyOneCompleteSnapshot()throws Exception{
        var pool=Executors.newFixedThreadPool(4);CountDownLatch gate=new CountDownLatch(1);
        try{
            var tasks=new ArrayList<Future<Boolean>>();
            for(int i=0;i<4;i++){boolean force=i%2==0;tasks.add(pool.submit(()->{gate.await();return writer.write(base,force);}));}
            gate.countDown();for(var task:tasks)task.get(20,TimeUnit.SECONDS);
            assertThat(rows.selectByDateRange(base,base)).hasSize(1);
            assertThat(rows.selectByDate(base).getNewUserCount()).isNotNull();
        }finally{pool.shutdownNow();}
    }
    @Test void compensationCommitsOtherDaysAndRetriesOnlyTheFailedGap()throws Exception{
        user(base.atStartOfDay(),null);
        assertThat(facts.earliestBusinessDate()).isNotNull().isBeforeOrEqualTo(base);
        var metadata=mock(StatisticsFactMapper.class);
        when(metadata.earliestBusinessDate()).thenReturn(base);
        when(calendar.today()).thenReturn(base.plusDays(3));
        var generation=new StatisticsGeneration(writer,metadata,rows,calendar);
        failDate.set(base.plusDays(1));
        assertThatThrownBy(generation::compensate).isInstanceOf(IllegalStateException.class);
        assertThat(rows.selectByDate(base)).isNotNull();
        assertThat(rows.selectByDate(base.plusDays(1))).isNull();
        assertThat(rows.selectByDate(base.plusDays(2))).isNotNull();
        var firstId=rows.selectByDate(base).getId();
        failDate.set(null);generation.compensate();
        assertThat(rows.selectByDateRange(base,base.plusDays(2))).hasSize(3);
        assertThat(rows.selectByDate(base).getId()).isEqualTo(firstId);
        verify(scan,times(3)).scanAndDelete("statistics:*");
    }
    @Test void todayAndFutureCannotBeWritten(){
        assertThatThrownBy(()->writer.write(calendar.today(),true)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->writer.write(calendar.today().plusDays(1),false)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(scan);
    }
}
