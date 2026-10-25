package com.hongjie.pms.refactor.daily;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.modules.daily.controller.DailyPostController;
import com.hongjie.pms.modules.daily.dto.*;
import com.hongjie.pms.modules.daily.entity.*;
import com.hongjie.pms.modules.daily.mapper.*;
import com.hongjie.pms.modules.daily.service.*;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.like.service.LikeService;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.search.event.DailyPostUpdatedEvent;
import com.hongjie.pms.refactor.audit.*;
import com.hongjie.pms.refactor.like.*;
import com.hongjie.pms.refactor.core.strategy.StrategyRegistry;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="PMS_DAILY_MYSQL_TESTS",matches="true")
class DailyModuleMySqlTest {
    AnnotationConfigApplicationContext context;JdbcTemplate jdbc;TransactionTemplate tx;
    DailyPostService posts;TopicService topics;DailyRecommendService recommend;DailyUserInterestService interest;AuditCommandService audit;
    long owner,viewer,admin,topic,post;final String marker="__daily_it_"+UUID.randomUUID().toString().substring(0,12);
    static class Recorder {
        final List<Long> ids=new CopyOnWriteArrayList<>();
        @EventListener public void indexed(DailyPostUpdatedEvent event){ids.add(event.getDailyId());}
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    @Import({DailyCommands.class,DailyQueries.class,DailyAssembler.class,DailyInterest.class,DailyRecommendation.class,DailyRecallConfiguration.class,
            DailyChangedListener.class,DailyLikeTarget.class,PmsDailyPostService.class,PmsTopicService.class,PmsDailyRecommendService.class,
            PmsDailyUserInterestService.class,DailyPostController.class,PmsLikeServiceImpl.class,AuditWriteTargets.class,AuditCommandService.class,
            DailyPostService.class,TopicService.class,DailyRecommendService.class,DailyRecallService.class,DailyRankService.class,DailyUserInterestService.class})
    static class Config {
        @Bean RedisTemplate<String,Object> redis(){return mock(RedisTemplate.class);}
        @Bean RedisScanUtil scan(){return mock(RedisScanUtil.class);}
        @Bean DailyCacheInvalidator cache(RedisTemplate<String,Object> redis,RedisScanUtil scan){return new DailyCacheInvalidator(scan,redis);}
        @Bean AuditService legacyAudit(){return mock(AuditService.class);}
        @Bean Recorder recorder(){return new Recorder();}
        @Bean StrategyRegistry<String,LikeTarget> likeTargetRegistry(List<LikeTarget> targets){return new StrategyRegistry<>("like",targets,target->List.of(target.type()));}
    }
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");
        assertThat(MyBatisTestSupport.configured()).isTrue();
        Class<?>[] types={DailyPostMapper.class,TopicMapper.class,DailyTopicRelMapper.class,DailyUserBehaviorMapper.class,DailyUserInterestMapper.class,
                UserMapper.class,FollowMapper.class,LikeRecordMapper.class,DailyDataMapper.class,AuditWriteMapper.class,AuditRecordMapper.class,ActivityMapper.class,PetPostMapper.class};
        var factory=MyBatisTestSupport.createFactory(types);var source=factory.getConfiguration().getEnvironment().getDataSource();
        jdbc=new JdbcTemplate(source);assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("daily_post","topic","daily_topic_rel","daily_user_behavior","daily_user_interest","audit_record","user","like_record"))
            assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);tx.setTimeout(15);
        context=new AnnotationConfigApplicationContext();context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        var template=new SqlSessionTemplate(factory);for(Class<?> type:types)register(type,template);
        context.register(Config.class);context.refresh();
        posts=context.getBean(DailyPostService.class);topics=context.getBean(TopicService.class);recommend=context.getBean(DailyRecommendService.class);
        interest=context.getBean(DailyUserInterestService.class);audit=context.getBean(AuditCommandService.class);
        assertThat(posts).isInstanceOf(PmsDailyPostService.class);assertThat(topics).isInstanceOf(PmsTopicService.class);
        assertThat(recommend).isInstanceOf(PmsDailyRecommendService.class);assertThat(interest).isInstanceOf(PmsDailyUserInterestService.class);
    }
    <T>void register(Class<T> type,SqlSessionTemplate template){context.registerBean(type,()->type==DailyTopicRelMapper.class?spy(template.getMapper(type)):template.getMapper(type));}
    long insert(String sql,Object...args){return jdbc.execute((java.sql.Connection connection)->{
        try(var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);statement.executeUpdate();
            try(var keys=statement.getGeneratedKeys()){assertThat(keys.next()).isTrue();return keys.getLong(1);}
        }
    });}
    @BeforeEach void fixture(){
        reset(context.getBean(RedisTemplate.class),context.getBean(RedisScanUtil.class));context.getBean(Recorder.class).ids.clear();
        owner=insert("INSERT INTO user(user_name,password,status,role) VALUES(?,?,1,0)",marker+"o","fixture");
        viewer=insert("INSERT INTO user(user_name,password,status,role) VALUES(?,?,1,0)",marker+"v","fixture");
        admin=insert("INSERT INTO user(user_name,password,status,role) VALUES(?,?,1,1)",marker+"a","fixture");
        topic=insert("INSERT INTO topic(name,description,status,post_count,hot_score) VALUES(?,?,1,1,0)",marker+"topic",marker);
        post=insert("INSERT INTO daily_post(user_id,content,images,status,audit_status,like_count,view_count,comment_count,create_time) VALUES(?,?,'[]',1,1,0,0,0,?)",owner,marker+" original",LocalDateTime.now().withNano(0));
        jdbc.update("INSERT INTO daily_topic_rel(daily_id,topic_id) VALUES(?,?)",post,topic);
    }
    @AfterEach void cleanup(){
        UserContext.clear();
        jdbc.update("DELETE FROM daily_user_behavior WHERE user_id IN (?,?,?)",owner,viewer,admin);
        jdbc.update("DELETE FROM daily_user_interest WHERE user_id IN (?,?,?)",owner,viewer,admin);
        jdbc.update("DELETE l FROM like_record l JOIN daily_post p ON l.target_id=p.id WHERE l.target_type='daily' AND p.user_id=?",owner);
        jdbc.update("DELETE r FROM audit_record r JOIN daily_post p ON r.target_id=p.id WHERE r.target_type='daily' AND p.user_id=?",owner);
        jdbc.update("DELETE r FROM daily_topic_rel r JOIN daily_post p ON r.daily_id=p.id WHERE p.user_id=?",owner);
        jdbc.update("DELETE FROM daily_post WHERE user_id=?",owner);
        jdbc.update("DELETE FROM topic WHERE name LIKE ? OR description=?",marker+"%",marker);
        jdbc.update("DELETE FROM follow WHERE follower_id IN (?,?,?) OR following_id IN (?,?,?)",owner,viewer,admin,owner,viewer,admin);
        jdbc.update("DELETE FROM user WHERE id IN (?,?,?) AND user_name LIKE ?",owner,viewer,admin,marker+"%");
    }
    @AfterAll void close(){if(context!=null)context.close();}
    void as(long id,int role,Runnable action){UserContext.setUser(new UserInfo(id,"fixture",role,null,null));try{action.run();}finally{UserContext.clear();}}
    PublishDailyRequest request(String content,List<Long> topics){
        var request=new PublishDailyRequest();var post=new DailyPost();post.setContent(content);request.setDailyPost(post);request.setTopicIds(topics);return request;
    }
    int topicCount(){return jdbc.queryForObject("SELECT post_count FROM topic WHERE id=?",Integer.class,topic);}
    int value(String column){return jdbc.queryForObject("SELECT "+column+" FROM daily_post WHERE id=?",Integer.class,post);}
    long pending(long id){return jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE target_type='daily' AND target_id=? AND audit_status=0",Long.class,id);}
    @Test void publishWhitelistsFieldsDeduplicatesTopicsAndSubmitsReview(){
        var request=request(marker+" created",List.of(topic,topic));var input=request.getDailyPost();
        input.setId(post);input.setUserId(viewer);input.setAuditStatus(1);input.setStatus(0);input.setLikeCount(999);input.setCreateTime(LocalDateTime.now().minusYears(1));
        as(owner,0,()->{
            var result=context.getBean(DailyPostController.class).publish(request).getData();assertThat(result.getId()).isNotEqualTo(post);
            assertThat(result.getUser().getUserId()).isEqualTo(owner);assertThat(result.getLikeCount()).isZero();
            assertThat(result.getTopics()).hasSize(1);assertThat(pending(result.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT audit_status FROM daily_post WHERE id=?",Integer.class,result.getId())).isZero();
            assertThat(context.getBean(Recorder.class).ids).containsExactly(result.getId());
        });
        assertThat(topicCount()).isEqualTo(2);assertThat(value("like_count")).isZero();
    }
    @Test void invalidTopicsAndMutedAccountsDoNotPartiallyPublish(){
        as(owner,0,()->assertThatThrownBy(()->posts.publish(request("invalid",List.of(Long.MAX_VALUE)))).isInstanceOf(BusinessException.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_post WHERE user_id=?",Long.class,owner)).isEqualTo(1);
        jdbc.update("UPDATE user SET is_muted=1,mute_end_time=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE id=?",owner);
        as(owner,0,()->assertThatThrownBy(()->posts.publish(request("muted",List.of(topic)))).isInstanceOf(BusinessException.class));
        assertThat(topicCount()).isEqualTo(1);
    }
    @Test void pendingAndRejectedDetailsAreOwnerAdminOnly(){
        jdbc.update("UPDATE daily_post SET audit_status=0 WHERE id=?",post);
        assertThatThrownBy(()->posts.getDetail(post)).isInstanceOf(BusinessException.class);
        as(viewer,0,()->assertThatThrownBy(()->posts.getDetail(post)).isInstanceOf(BusinessException.class));
        as(owner,0,()->assertThat(posts.getDetail(post).getId()).isEqualTo(post));
        as(admin,1,()->assertThat(posts.getDetail(post).getId()).isEqualTo(post));assertThat(value("view_count")).isZero();
    }
    @Test void detailReturnsCurrentViewCountAndViewerFlagsDoNotLeak(){
        as(viewer,0,()->assertThat(posts.like(post)).isTrue());
        insert("INSERT INTO follow(follower_id,following_id) VALUES(?,?)",viewer,owner);
        as(viewer,0,()->{var detail=posts.getDetail(post);assertThat(detail.getIsLiked()).isTrue();assertThat(detail.getIsFollowed()).isTrue();assertThat(detail.getViewCount()).isEqualTo(1);});
        var anonymous=posts.getDetail(post);assertThat(anonymous.getIsLiked()).isFalse();assertThat(anonymous.getIsFollowed()).isFalse();assertThat(anonymous.getViewCount()).isEqualTo(2);
    }
    @Test void missingAuthorUsesTombstone(){
        jdbc.update("DELETE FROM user WHERE id=? AND user_name=?",owner,marker+"o");
        assertThat(posts.getDetail(post).getUser().getUserId()).isEqualTo(owner);
        assertThat(posts.getDetail(post).getUser().getUsername()).isNotBlank();
    }
    @Test void deleteWithdrawsReviewUnlinksTopicsAndCannotBeApprovedAgain(){
        jdbc.update("UPDATE daily_post SET audit_status=0 WHERE id=?",post);
        insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('daily',?,0)",post);
        as(owner,0,()->context.getBean(DailyPostController.class).delete(post));
        assertThat(value("status")).isZero();assertThat(topicCount()).isZero();assertThat(pending(post)).isZero();
        as(admin,1,()->assertThatThrownBy(()->audit.approve(post,"daily")).isInstanceOf(BusinessException.class));
        as(owner,0,()->assertThatThrownBy(()->posts.getDetail(post)).isInstanceOf(BusinessException.class));
        as(owner,0,()->assertThatThrownBy(()->posts.delete(post)).isInstanceOf(BusinessException.class));
        assertThat(topicCount()).isZero();
    }
    @Test void rejectingDoesNotConfuseModerationWithDeletion(){
        jdbc.update("UPDATE daily_post SET audit_status=0 WHERE id=?",post);
        insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('daily',?,0)",post);
        as(admin,1,()->audit.reject(post,"daily","reason"));
        assertThat(value("audit_status")).isEqualTo(2);assertThat(value("status")).isEqualTo(1);
        as(owner,0,()->assertThat(posts.getDetail(post)).isNotNull());
        assertThatThrownBy(()->posts.getDetail(post)).isInstanceOf(BusinessException.class);
        as(owner,0,()->posts.delete(post));assertThat(topicCount()).isZero();
    }
    @Test void deletePermissionsAndCounterPreservation(){
        as(viewer,0,()->assertThatThrownBy(()->posts.delete(post)).isInstanceOf(BusinessException.class));
        jdbc.update("UPDATE daily_post SET like_count=7,view_count=8,comment_count=9 WHERE id=?",post);
        as(admin,1,()->posts.delete(post));assertThat(value("like_count")).isEqualTo(7);assertThat(value("comment_count")).isEqualTo(9);
    }
    @Test void publishAndDeleteRollbackRestoreAllProjectionsAndSuppressEvents(){
        as(owner,0,()->assertThatThrownBy(()->tx.executeWithoutResult(status->{posts.publish(request(marker+" rollback",List.of(topic)));throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class));
        assertThat(topicCount()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_post WHERE user_id=?",Long.class,owner)).isEqualTo(1);
        as(owner,0,()->assertThatThrownBy(()->tx.executeWithoutResult(status->{posts.delete(post);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class));
        assertThat(value("status")).isEqualTo(1);assertThat(topicCount()).isEqualTo(1);assertThat(context.getBean(Recorder.class).ids).isEmpty();
        verifyNoInteractions(context.getBean(RedisScanUtil.class),context.getBean(RedisTemplate.class));
    }
    @Test void concurrentLikesToggleWithoutDuplicateRowsOrNegativeCounters()throws Exception{
        race(()->as(viewer,0,()->posts.like(post)));
        assertThat(value("like_count")).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM like_record WHERE target_type='daily' AND target_id=?",Long.class,post)).isZero();
    }
    @Test void pendingAndDeletedTargetsCannotBeLiked(){
        jdbc.update("UPDATE daily_post SET audit_status=0 WHERE id=?",post);
        as(viewer,0,()->assertThatThrownBy(()->posts.like(post)).isInstanceOf(BusinessException.class));
        jdbc.update("UPDATE daily_post SET audit_status=1,status=0 WHERE id=?",post);
        as(viewer,0,()->assertThatThrownBy(()->posts.like(post)).isInstanceOf(BusinessException.class));
    }
    @Test void topicNamesAreNormalizedBeforeUniquenessCheck(){
        as(owner,0,()->{
            var created=context.getBean(DailyPostController.class).createTopic(marker+"new",marker).getData();
            assertThat(created.getName()).isEqualTo("#"+marker+"new");
            assertThatThrownBy(()->topics.createTopic("#"+marker+"new#",marker)).isInstanceOf(BusinessException.class);
        });
        assertThatThrownBy(()->topics.createTopic("anonymous",marker)).isInstanceOf(BusinessException.class);
    }
    @Test void concurrentTopicCreationHasOneWinner()throws Exception{
        var wins=new AtomicInteger();
        race(()->as(owner,0,()->{try{topics.createTopic(marker+"race",marker);wins.incrementAndGet();}catch(BusinessException expected){}}));
        assertThat(wins.get()).isEqualTo(1);
    }
    @Test void topicLimitsAndDisabledTopicsAreHandled(){
        jdbc.update("UPDATE topic SET status=0 WHERE id=?",topic);
        assertThat(topics.searchTopics(marker,20)).isEmpty();assertThat(topics.getHotTopics(-1).size()).isLessThanOrEqualTo(1);
        assertThat(topics.getHotTopics(9999).size()).isLessThanOrEqualTo(50);
        as(owner,0,()->assertThatThrownBy(()->posts.publish(request("disabled topic",List.of(topic)))).isInstanceOf(BusinessException.class));
    }
    @Test void interestAndBehaviorCommitAtomicallyAndConcurrentUpdatesDoNotLoseWeight()throws Exception{
        race(()->as(viewer,0,()->recommend.recordAction(viewer,post,"view")));
        assertThat(interest.getUserInterest(viewer).get("topic_"+topic)).isCloseTo(0.6,within(0.0001));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_user_behavior WHERE user_id=?",Long.class,viewer)).isEqualTo(2);
        assertThat(interest.getTopTopics(viewer,3)).containsExactly(topic);
    }
    @Test void behaviorRejectsUnknownActionsSpoofingAndHiddenContent(){
        as(viewer,0,()->{
            assertThatThrownBy(()->recommend.recordAction(viewer,post,"unknown")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->recommend.recordAction(owner,post,"view")).isInstanceOf(BusinessException.class);
        });
        jdbc.update("UPDATE daily_post SET audit_status=0 WHERE id=?",post);
        as(viewer,0,()->assertThatThrownBy(()->recommend.recordAction(viewer,post,"like")).isInstanceOf(BusinessException.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_user_behavior WHERE user_id=?",Long.class,viewer)).isZero();
    }
    @Test void behaviorRollbackDoesNotLeaveInterestOrClearViewerCache(){
        as(viewer,0,()->assertThatThrownBy(()->tx.executeWithoutResult(status->{recommend.recordAction(viewer,post,"like");throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class));
        assertThat(interest.getUserInterest(viewer)).isEmpty();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_user_behavior WHERE user_id=?",Long.class,viewer)).isZero();
        verifyNoInteractions(context.getBean(RedisScanUtil.class),context.getBean(RedisTemplate.class));
    }
    @Test void recommendationContainsOnlyPublicContentAndHandlesOutOfRangePages(){
        insert("INSERT INTO daily_post(user_id,content,status,audit_status,create_time) VALUES(?,?,1,0,NOW())",owner,marker+" pending");
        insert("INSERT INTO daily_post(user_id,content,status,audit_status,create_time) VALUES(?,?,0,1,NOW())",owner,marker+" deleted");
        var page=recommend.recommend(null,Integer.MAX_VALUE,Integer.MAX_VALUE);assertThat(page.getRecords()).isEmpty();assertThat(page.getSize()).isEqualTo(100);
        assertThat(recommend.recommend(null,1,100).getRecords()).extracting(DailyPostDto::getContent).doesNotContain(marker+" pending",marker+" deleted");
        assertThat(posts.getRecommendFeed(999999,20).getRecords()).isEmpty();
    }
    @Test void recommendationReassemblesLiveLikesAndUsesOnlyBatchTopicQueries(){
        var links=context.getBean(DailyTopicRelMapper.class);clearInvocations(links);
        as(viewer,0,()->{
            assertThat(recommend.recommend(viewer,1,100).getRecords().stream().filter(row->row.getId().equals(post)).findFirst().orElseThrow().getIsLiked()).isFalse();
            posts.like(post);
            assertThat(recommend.recommend(viewer,1,100).getRecords().stream().filter(row->row.getId().equals(post)).findFirst().orElseThrow().getIsLiked()).isTrue();
        });
        verify(links,never()).getTopicIdsByDailyId(anyLong());verify(context.getBean(RedisTemplate.class),never()).opsForValue();
    }
    @Test void staleRecallCandidatesAreFilteredBeforePagination(){
        long pending=insert("INSERT INTO daily_post(user_id,content,status,audit_status,create_time) VALUES(?,?,1,0,NOW())",owner,marker+" pending");
        DailyRecallSource stale=new DailyRecallSource(){public String name(){return "stale";}public List<Long> recall(Long actor,int limit){return List.of(post,pending,Long.MAX_VALUE);}};
        var core=new DailyRecommendation(List.of(stale),context.getBean(DailyPostMapper.class),context.getBean(FollowMapper.class),
                context.getBean(DailyInterest.class),context.getBean(DailyAssembler.class));
        assertThat(core.recommend(null,1,10).getTotal()).isEqualTo(1);
        assertThat(core.recommend(null,1,10).getRecords()).extracting(DailyPostDto::getId).containsExactly(post);
    }
    @Test void httpRoutesKeepResponseFieldsAndControllerGetsNewServices()throws Exception{
        MockMvcBuilders.standaloneSetup(context.getBean(DailyPostController.class)).build().perform(get("/pet-system/daily/"+post))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(post)).andExpect(jsonPath("$.data.topics[0].id").value(topic))
                .andExpect(jsonPath("$.data.isLiked").value(false));
    }
    @Test void duplicateDeletesDecrementTopicOnlyOnce()throws Exception{
        var wins=new AtomicInteger();
        race(()->as(owner,0,()->{try{posts.delete(post);wins.incrementAndGet();}catch(BusinessException expected){}}));
        assertThat(wins.get()).isEqualTo(1);assertThat(topicCount()).isZero();
    }
    @Test void deleteAndReviewRaceNeverResurrectsPost()throws Exception{
        jdbc.update("UPDATE daily_post SET audit_status=0 WHERE id=?",post);
        insert("INSERT INTO audit_record(target_type,target_id,audit_status) VALUES('daily',?,0)",post);
        var ready=new CountDownLatch(2);var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var one=pool.submit(()->{ready.countDown();gate.await();as(owner,0,()->posts.delete(post));return null;});
            var two=pool.submit(()->{ready.countDown();gate.await();as(admin,1,()->{try{audit.approve(post,"daily");}catch(BusinessException expected){}});return null;});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();gate.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);
        }finally{gate.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        assertThat(value("status")).isZero();assertThat(topicCount()).isZero();assertThat(pending(post)).isZero();
    }
    void race(Runnable action)throws Exception{
        var ready=new CountDownLatch(2);var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Void> task=()->{ready.countDown();if(!gate.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");action.run();return null;};
        try{var one=pool.submit(task);var two=pool.submit(task);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();gate.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);}
        finally{gate.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }

    @Test void persistedInterestsDriveTheSqlRecallRoute(){
        as(viewer,0,()->{
            recommend.recordAction(viewer,post,"like");
            assertThat(context.getBean(DailyDataMapper.class).interested(List.of(topic),10)).contains(post);
            assertThat(recommend.recommend(viewer,1,100).getRecords()).extracting(DailyPostDto::getId).contains(post);
        });
    }
}
