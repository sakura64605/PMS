package com.hongjie.pms.modules.community.feed.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.community.feed.api.controller.FeedController;
import com.hongjie.pms.modules.community.feed.infrastructure.persistence.mapper.UserInboxMapper;
import com.hongjie.pms.modules.community.feed.api.service.FeedService;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.shared.extension.target.TargetTypeCodec;
import com.hongjie.pms.modules.community.following.application.FollowInboxWriter;
import com.hongjie.pms.architecture.support.MyBatisTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
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

/** Explicit opt-in; creates and deletes only marker-owned rows in the local test database. */
@EnabledIfEnvironmentVariable(named="PMS_FEED_MYSQL_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedMySqlTest {
    AnnotationConfigApplicationContext context;PmsFeedServiceImpl service;JdbcTemplate jdbc;TransactionTemplate tx;
    long viewer,normal,pull;
    final String marker="__feed_it_"+UUID.randomUUID().toString().substring(0,20);
    final LocalDateTime base=LocalDateTime.of(2026,1,1,12,0);
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PmsFeedServiceImpl.class,FeedAssembler.class,FollowInboxWriter.class,TargetTypeCodec.class,FeedController.class})
    static class Config{@Bean FeedService legacyFeedService(){return mock(FeedService.class);}}
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");assertThat(MyBatisTestSupport.configured()).isTrue();
        Class<?>[] mappers={FeedReadMapper.class,FeedWriteMapper.class,PetPostMapper.class,ActivityMapper.class,UserMapper.class,UserInboxMapper.class};
        var factory=MyBatisTestSupport.createFactory(mappers);var source=factory.getConfiguration().getEnvironment().getDataSource();jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("user","follow","user_inbox","big_v_config","pet_post","activity"))assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        var template=new SqlSessionTemplate(factory);var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);tx.setTimeout(15);
        context=new AnnotationConfigApplicationContext();context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        for(Class<?> mapper:mappers)register(mapper,template);context.register(Config.class);context.refresh();service=context.getBean(PmsFeedServiceImpl.class);
        assertThat(context.getBean(FeedService.class)).isSameAs(service);
    }
    <T> void register(Class<T> type,SqlSessionTemplate template){context.registerBean(type,()->template.getMapper(type));}
    long insert(String sql,Object... args){return jdbc.execute((java.sql.Connection c)->{
        try(var statement=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);statement.executeUpdate();try(var keys=statement.getGeneratedKeys()){if(!keys.next())throw new IllegalStateException("missing id");return keys.getLong(1);}}
    });}
    long user(String suffix){return insert("INSERT INTO user(user_name,password,status) VALUES(?,?,1)",marker+suffix,"fixture");}
    @BeforeEach void fixtures(){viewer=user("v");normal=user("n");pull=user("p");follow(viewer,normal);follow(viewer,pull);jdbc.update("INSERT INTO big_v_config(user_id,fans_count,use_pull_mode) VALUES(?,1001,1)",pull);UserContext.setUser(new UserInfo(viewer,marker,0,null,null));}
    @AfterEach void cleanup(){
        UserContext.clear();if(jdbc==null)return;
        for(long id:List.of(viewer,normal,pull)){
            jdbc.update("DELETE i FROM user_inbox i JOIN user u ON i.user_id=u.id WHERE u.id=? AND u.user_name IN (?,?,?)",id,marker+"v",marker+"n",marker+"p");
            jdbc.update("DELETE f FROM follow f JOIN user u ON f.follower_id=u.id WHERE u.id=? AND u.user_name IN (?,?,?)",id,marker+"v",marker+"n",marker+"p");
            jdbc.update("DELETE b FROM big_v_config b JOIN user u ON b.user_id=u.id WHERE u.id=? AND u.user_name IN (?,?,?)",id,marker+"v",marker+"n",marker+"p");
            jdbc.update("DELETE p FROM pet_post p JOIN user u ON p.user_id=u.id WHERE u.id=? AND u.user_name IN (?,?,?) AND p.title=?",id,marker+"v",marker+"n",marker+"p",marker);
            jdbc.update("DELETE a FROM activity a JOIN user u ON a.user_id=u.id WHERE u.id=? AND u.user_name IN (?,?,?) AND a.title=?",id,marker+"v",marker+"n",marker+"p",marker);
        }
        jdbc.update("DELETE FROM user WHERE id IN (?,?,?) AND user_name IN (?,?,?)",viewer,normal,pull,marker+"v",marker+"n",marker+"p");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE user_name IN (?,?,?)",Long.class,marker+"v",marker+"n",marker+"p")).isZero();
    }
    @AfterAll void close(){if(context!=null)context.close();}
    void follow(long actor,long author){jdbc.update("INSERT INTO follow(follower_id,following_id) VALUES(?,?)",actor,author);}
    long pet(long author,int minutes,int audit,int status){return insert("INSERT INTO pet_post(user_id,type,title,content,contact_phone,status,audit_status,create_time) VALUES(?,0,?,?,?, ?,?,?)",author,marker,"pet-content","13800000000",status,audit,base.plusMinutes(minutes));}
    long activity(long author,int minutes,int audit,int status,int deleted){return insert("INSERT INTO activity(user_id,title,content,location,max_people,start_time,end_time,status,audit_status,deleted,create_time) VALUES(?,?,?,?,10,?,?,?,?,?,?)",author,marker,"activity-content","fixture",base,base.plusDays(1),status,audit,deleted,base.plusMinutes(minutes));}
    void inbox(long recipient,long author,long post,String type,int minute){jdbc.update("INSERT INTO user_inbox(user_id,post_id,post_type,poster_id,title,create_time,is_read) VALUES(?,?,?,?,?,?,0)",recipient,post,type,author,marker,base.plusMinutes(minute));}
    int unread(long recipient){return jdbc.queryForObject("SELECT COUNT(*) FROM user_inbox WHERE user_id=? AND is_read=0",Integer.class,recipient);}
    @Test void pushAndPullShareOneChronologicalPageAndAccurateTotal(){
        long older=pet(normal,1,1,1),newer=activity(pull,2,1,1,0);inbox(viewer,normal,older,"adopt",1);
        var first=service.getHomeFeed(1,1);var second=service.getHomeFeed(2,1);
        assertThat(first.getTotal()).isEqualTo(2);assertThat(first.getRecords()).hasSize(1);assertThat(first.getRecords().get(0).getPostId()).isEqualTo(newer);assertThat(first.getRecords().get(0).getPostType()).isEqualTo("activity");
        assertThat(second.getRecords()).hasSize(1);assertThat(second.getRecords().get(0).getPostId()).isEqualTo(older);assertThat(second.getRecords().get(0).getPostType()).isEqualTo("pet");assertThat(service.getHomeFeed(3,1).getRecords()).isEmpty();
    }
    @Test void duplicateAliasesCollapseAndReadUpdateIsScopedToViewer(){
        long id=pet(normal,1,1,1);inbox(viewer,normal,id,"ADOPT",1);inbox(viewer,normal,id,"pet",1);inbox(pull,normal,id,"pet",1);
        assertThat(service.getUnreadCount()).isEqualTo(1);var page=service.getHomeFeed(1,20);assertThat(page.getRecords()).hasSize(1);assertThat(page.getTotal()).isEqualTo(1);assertThat(service.getUnreadCount()).isZero();assertThat(unread(pull)).isEqualTo(1);
    }
    @Test void hiddenUnapprovedUnfollowedAndDeletedContentDoesNotLeakOrInflateUnread(){
        long pending=pet(normal,1,0,1),offline=pet(normal,2,1,3),visible=pet(normal,3,1,1);inbox(viewer,normal,pending,"help",1);inbox(viewer,normal,offline,"pet",2);inbox(viewer,normal,visible,"pet",3);
        activity(pull,4,0,1,0);activity(pull,5,1,1,1);long other=pet(viewer,6,1,1);inbox(viewer,viewer,other,"pet",6);
        assertThat(service.getUnreadCount()).isEqualTo(1);assertThat(service.getHomeFeed(1,20).getTotal()).isEqualTo(1);assertThat(unread(viewer)).isEqualTo(3);
        jdbc.update("DELETE FROM follow WHERE follower_id=? AND following_id=?",viewer,normal);assertThat(service.getUnreadCount()).isZero();assertThat(service.getHomeFeed(1,20).getTotal()).isZero();
    }
    @Test void pagingMarksOnlyReturnedContentAsRead(){
        long one=pet(normal,1,1,1),two=pet(normal,2,1,1);inbox(viewer,normal,one,"pet",1);inbox(viewer,normal,two,"help",2);
        assertThat(service.getUnreadCount()).isEqualTo(2);service.getHomeFeed(1,1);assertThat(service.getUnreadCount()).isEqualTo(1);assertThat(unread(viewer)).isEqualTo(1);
    }
    @Test void pullOnlyAuthorPaginatesWithoutRepeatingFirstPage(){
        pet(pull,1,1,1);pet(pull,2,1,1);activity(pull,3,1,1,0);
        Set<String> seen=new HashSet<>();for(int page=1;page<=3;page++){var result=service.getHomeFeed(page,1);assertThat(result.getTotal()).isEqualTo(3);assertThat(result.getRecords()).hasSize(1);var row=result.getRecords().get(0);assertThat(seen.add(row.getPostType()+":"+row.getPostId())).isTrue();}
    }
    @Test void concurrentDuplicatePushesDoNotCreateDuplicateInboxRows()throws Exception{
        long id=pet(normal,1,1,1);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);var ready=new CountDownLatch(2);
        java.util.function.Function<String,Callable<Void>> task=type->()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.pushToFans(normal,id,type,marker,null,marker,null,base);return null;};
        try{var one=pool.submit(task.apply("pet"));var two=pool.submit(task.apply("help"));assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);}
        finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_inbox WHERE user_id=? AND poster_id=? AND post_id=?",Long.class,viewer,normal,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT post_type FROM user_inbox WHERE user_id=? AND poster_id=? AND post_id=?",String.class,viewer,normal,id)).isEqualTo("pet");
    }
    @Test void outerFailureRollsBackModeAndFanOut(){
        long id=pet(normal,1,1,1);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{service.pushToFans(normal,id,"adopt",marker,null,marker,null,base);throw new IllegalStateException("force rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_inbox WHERE user_id=? AND poster_id=?",Long.class,viewer,normal)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM big_v_config WHERE user_id=?",Long.class,normal)).isZero();
    }
    @Test void failedReadTransactionDoesNotConsumeUnreadState(){
        long id=pet(normal,1,1,1);inbox(viewer,normal,id,"pet",1);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{service.getHomeFeed(1,20);throw new IllegalStateException("force rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(service.getUnreadCount()).isEqualTo(1);assertThat(unread(viewer)).isEqualTo(1);
    }
    @Test void historyEntrypointHonorsRequestedLimitAndExistingControllerIsTakenOver(){
        pet(normal,1,1,1);activity(normal,2,1,1,0);service.pullHistoryPosts(viewer,normal,1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_inbox WHERE user_id=? AND poster_id=?",Long.class,viewer,normal)).isEqualTo(1);
        context.getBean(FeedController.class).getHomeFeed(1,20);verifyNoInteractions(context.getBean("legacyFeedService",FeedService.class));
    }
}
