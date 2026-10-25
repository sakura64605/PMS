package com.hongjie.pms.refactor.petpost;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.utils.OssUtils;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.petpost.dto.request.PetPostRequestDto;
import com.hongjie.pms.modules.petpost.mapper.*;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.refactor.audit.*;
import com.hongjie.pms.refactor.petpost.mapper.PmsPetPostMapper;
import com.hongjie.pms.refactor.petpost.converter.PetPostDtoConverter;
import com.hongjie.pms.refactor.petpost.service.PmsPetPostServiceImpl;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="PMS_PET_REVIEW_MYSQL_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PetReviewMySqlTest {
    AnnotationConfigApplicationContext context;JdbcTemplate jdbc;PmsPetPostServiceImpl pets;AuditCommandService audits;long owner,admin,post;
    final String marker="__pet_review_"+UUID.randomUUID().toString().substring(0,17);
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PmsPetPostServiceImpl.class,PetPostDtoConverter.class,AuditWriteTargets.class,AuditQuerySources.class,AuditCommandService.class,TargetTypeCodec.class,com.hongjie.pms.refactor.activity.ActivityContentCommands.class})
    static class Config{
        @Bean com.hongjie.pms.refactor.activity.ActivityScheduleWriter activitySchedules(){return mock(com.hongjie.pms.refactor.activity.ActivityScheduleWriter.class);}
        @Bean DistributedCache distributedCache(){return mock(DistributedCache.class);}
        @Bean com.hongjie.pms.modules.activity.cache.ActivityCache activityCache(){return mock(com.hongjie.pms.modules.activity.cache.ActivityCache.class);}
        @Bean OssUtils ossUtils(){return mock(OssUtils.class);}
        @Bean FeedService feedService(){return mock(FeedService.class);}
        @Bean AuditService auditService(){return mock(AuditService.class);}
        @Bean com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper activitySignupMapper(){return mock(com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper.class);}
        @Bean com.hongjie.pms.refactor.message.api.MessageService messageService(){return mock(com.hongjie.pms.refactor.message.api.MessageService.class);}
        @Bean com.hongjie.pms.refactor.scheduling.DelayTaskService delayTaskService(){return mock(com.hongjie.pms.refactor.scheduling.DelayTaskService.class);}
        @Bean com.hongjie.pms.common.punishment.scheduler.DelayTaskMapper delayTaskMapper(){return mock(com.hongjie.pms.common.punishment.scheduler.DelayTaskMapper.class);}
        @Bean org.redisson.api.RedissonClient redissonClient(){return mock(org.redisson.api.RedissonClient.class);}
    }
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");assertThat(MyBatisTestSupport.configured()).isTrue();
        Class<?>[] mappers={PmsPetPostMapper.class,PetPostMapper.class,ActivityMapper.class,DailyPostMapper.class,AuditWriteMapper.class,AuditRecordMapper.class,UserMapper.class,LikeRecordMapper.class,FavoriteRecordMapper.class,FollowMapper.class};
        var factory=MyBatisTestSupport.createFactory(mappers);var source=factory.getConfiguration().getEnvironment().getDataSource();jdbc=new JdbcTemplate(source);assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("user","pet_post","audit_record"))assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        var template=new SqlSessionTemplate(factory);context=new AnnotationConfigApplicationContext();context.registerBean("transactionManager",DataSourceTransactionManager.class,()->new DataSourceTransactionManager(source));
        for(Class<?> type:mappers)register(type,template);context.register(Config.class);context.refresh();pets=context.getBean(PmsPetPostServiceImpl.class);audits=context.getBean(AuditCommandService.class);
    }
    <T> void register(Class<T> type,SqlSessionTemplate template){context.registerBean(type,()->template.getMapper(type));}
    long insert(String sql,Object... args){return jdbc.execute((java.sql.Connection c)->{try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();try(var keys=s.getGeneratedKeys()){if(!keys.next())throw new IllegalStateException("missing id");return keys.getLong(1);}}});}
    @BeforeEach void fixture(){owner=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"o","fixture");admin=insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+"a","fixture");post=insert("INSERT INTO pet_post(user_id,type,title,content,contact_phone,status,audit_status) VALUES(?,0,?,?,?,1,1)",owner,marker,"original","13800000000");insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('adopt',?,1)",post);}
    @AfterEach void cleanup(){UserContext.clear();if(jdbc==null)return;
        jdbc.update("DELETE r FROM audit_record r JOIN activity a ON r.target_id=a.id WHERE r.target_type='activity' AND a.user_id=? AND a.title=?",owner,marker);
        jdbc.update("DELETE FROM activity WHERE user_id=? AND title=?",owner,marker);jdbc.update("DELETE r FROM audit_record r JOIN pet_post p ON r.target_id=p.id WHERE r.target_type='adopt' AND p.id=? AND p.user_id=?",post,owner);jdbc.update("DELETE FROM pet_post WHERE id=? AND user_id=?",post,owner);jdbc.update("DELETE FROM user WHERE id IN (?,?) AND user_name IN (?,?)",owner,admin,marker+"o",marker+"a");}
    @AfterAll void close(){if(context!=null)context.close();}
    void asOwner(){UserContext.setUser(new UserInfo(owner,marker,0,null,null));}
    void asAdmin(){UserContext.setUser(new UserInfo(admin,marker,1,null,null));}
    void edit(String content){asOwner();var request=new PetPostRequestDto();request.setId(post);request.setContent(content);pets.update(request);}
    long rounds(int status){return jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE target_type='adopt' AND target_id=? AND audit_status=?",Long.class,post,status);}
    @Test void editingApprovedThenRejectedContentStartsNewRoundsAndPreservesHistory(){edit("first edit");assertThat(rounds(0)).isEqualTo(1);assertThat(rounds(1)).isEqualTo(1);asAdmin();audits.approve(post,"adopt");edit("second edit");asAdmin();audits.reject(post,"adopt","reason");assertThat(rounds(0)).isZero();assertThat(rounds(1)).isEqualTo(2);assertThat(rounds(2)).isEqualTo(1);edit("third edit");assertThat(rounds(0)).isEqualTo(1);}
    @Test void repeatedPendingEditsReuseOneOpenReview(){edit("one");edit("two");assertThat(rounds(0)).isEqualTo(1);assertThat(rounds(1)).isEqualTo(1);asAdmin();audits.reject(post,"adopt","reason");assertThat(rounds(2)).isEqualTo(1);}
    @Test void pendingPreviewIsRestrictedToOwnerAndAdmin(){edit("preview");asOwner();assertThat(pets.detail(post,owner).getAuditStatus()).isZero();asAdmin();assertThat(pets.detail(post,admin).getAuditStatus()).isZero();UserContext.clear();assertThatThrownBy(()->pets.detail(post,null)).isInstanceOf(com.hongjie.pms.common.exception.BusinessException.class);}
    @Test void conflictingDuplicateReviewsRollBackThePostEdit(){insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('adopt',?,0)",post);insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('adopt',?,0)",post);assertThatThrownBy(()->edit("should rollback")).isInstanceOf(com.hongjie.pms.common.exception.BusinessException.class);assertThat(jdbc.queryForObject("SELECT audit_status FROM pet_post WHERE id=?",Integer.class,post)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT content FROM pet_post WHERE id=?",String.class,post)).isEqualTo("original");}
    @Test void concurrentEditAndDecisionNeverLeaveOrphanPendingState()throws Exception{
        edit("initial");var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);var ready=new CountDownLatch(2);
        Callable<Void> editor=()->{ready.countDown();start.await();try{edit("concurrent");return null;}finally{UserContext.clear();}};
        Callable<Void> reviewer=()->{ready.countDown();start.await();try{asAdmin();audits.reject(post,"adopt","reason");return null;}finally{UserContext.clear();}};
        try{var a=pool.submit(editor);var b=pool.submit(reviewer);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);}finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        int status=jdbc.queryForObject("SELECT audit_status FROM pet_post WHERE id=?",Integer.class,post);assertThat(rounds(0)).isEqualTo(status==0?1:0);assertThat(rounds(2)).isEqualTo(1);
    }
    @Test void completedAdoptionAndRescueCannotEditOrReenterReview(){
        for(int type:new int[]{0,1}){
            jdbc.update("UPDATE pet_post SET type=?,status=2,audit_status=1 WHERE id=?",type,post);
            assertThatThrownBy(()->edit("forbidden edit")).isInstanceOf(com.hongjie.pms.common.exception.BusinessException.class);
            assertThat(jdbc.queryForObject("SELECT content FROM pet_post WHERE id=?",String.class,post)).isEqualTo("original");
            assertThat(jdbc.queryForObject("SELECT audit_status FROM pet_post WHERE id=?",Integer.class,post)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE target_id=? AND target_type IN ('adopt','help') AND audit_status=0",Long.class,post)).isZero();
        }
    }
    @Test void completedLegacyPendingContentCannotBeApprovedOrListedAsPending(){
        jdbc.update("UPDATE pet_post SET status=2,audit_status=0 WHERE id=?",post);
        insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('adopt',?,0)",post);asAdmin();
        assertThatThrownBy(()->audits.approve(post,"adopt")).isInstanceOf(com.hongjie.pms.common.exception.BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM pet_post WHERE id=?",Integer.class,post)).isEqualTo(2);
        var source=context.getBeansOfType(AuditQuerySource.class).values().stream().filter(q->q.type().equals("adopt")).findFirst().orElseThrow();
        assertThat(source.pending(null)).noneMatch(item->item.view().getId().equals(post));
    }
    @Test void finishedActivityCannotEditOrReenterReview(){
        var start=java.time.LocalDateTime.now().plusDays(1);var end=start.plusHours(2);
        long id=insert("INSERT INTO activity(user_id,title,content,location,max_people,start_time,end_time,status,audit_status,deleted) VALUES(?,?,?,'fixture',10,?,?,2,0,0)",owner,marker,"finished original",start,end);
        insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('activity',?,0)",id);
        var request=new com.hongjie.pms.modules.activity.dto.request.ActivityRequestDto();request.setId(id);request.setStartTime(start);request.setEndTime(end);request.setTitle("forbidden change");asOwner();
        assertThatThrownBy(()->context.getBean(com.hongjie.pms.refactor.activity.ActivityContentCommands.class).edit(request)).isInstanceOf(com.hongjie.pms.common.exception.BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT title FROM activity WHERE id=?",String.class,id)).isEqualTo(marker);
        asAdmin();assertThatThrownBy(()->audits.approve(id,"activity")).isInstanceOf(com.hongjie.pms.common.exception.BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM activity WHERE id=?",Integer.class,id)).isEqualTo(2);
        var source=context.getBeansOfType(AuditQuerySource.class).values().stream().filter(q->q.type().equals("activity")).findFirst().orElseThrow();
        assertThat(source.pending(null)).noneMatch(item->item.view().getId().equals(id));
    }

}
