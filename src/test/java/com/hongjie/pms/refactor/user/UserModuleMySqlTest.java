package com.hongjie.pms.refactor.user;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.utils.*;
import com.hongjie.pms.common.interceptor.AuthInterceptor;
import com.hongjie.pms.modules.user.controller.*;
import com.hongjie.pms.modules.user.dto.request.*;
import com.hongjie.pms.modules.user.service.*;
import com.hongjie.pms.modules.user.mapper.*;
import com.hongjie.pms.modules.admin.service.AdminService;
import com.hongjie.pms.modules.admin.controller.AdminController;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.common.punishment.scheduler.*;
import com.hongjie.pms.refactor.admin.PmsAdminServiceImpl;
import com.hongjie.pms.refactor.audit.AuditCommandService;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import com.hongjie.pms.refactor.scheduling.ScheduledTaskMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.api.RBloomFilter;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.redis.core.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="PMS_USER_MYSQL_TESTS",matches="true")
class UserModuleMySqlTest {
    AnnotationConfigApplicationContext context;JdbcTemplate jdbc;TransactionTemplate tx;
    UserService users;AdminService admin;AvatarService avatars;UserSessions sessions;JWTUtils jwt;PasswordEncoder encoder;
    long actor,other,manager;String passwordHash;MockMvc mvc;
    final String marker="__user_it_"+UUID.randomUUID().toString().substring(0,12);
    final List<Long> extra=new ArrayList<>();
    final Map<String,String> redisData=new ConcurrentHashMap<>();
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PmsUserServiceImpl.class,UserAuthentication.class,UserProfiles.class,UserSessions.class,UserAssembler.class,UserCache.class,
            UserAdministration.class,UserAdminItem.class,PmsAdminServiceImpl.class,UserAvatarCommands.class,PmsAvatarServiceImpl.class,
            UserAuthController.class,UserController.class,AdminController.class,AuthInterceptor.class,
            com.hongjie.pms.refactor.scheduling.ScheduledTaskWorker.class,com.hongjie.pms.refactor.scheduling.ScheduledBusinessTasks.class,
            com.hongjie.pms.refactor.scheduling.BusinessTaskConfiguration.class,DelayTaskService.class,
            com.hongjie.pms.modules.user.service.Impl.UserServiceImpl.class,
            com.hongjie.pms.modules.user.service.Impl.AvatarServiceImpl.class,
            com.hongjie.pms.modules.admin.service.Impl.AdminServiceImpl.class})
    static class Config {
        @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder(4);}
        @Bean JWTUtils jwt(){var jwt=new JWTUtils();jwt.setSecret("user-module-test-signing-key-only-1234567890");jwt.setExpire(3600L);jwt.setHeader("Authorization");jwt.setTokenPrefix("Bearer ");return jwt;}
        @Bean DistributedCache cache(){return mock(DistributedCache.class);}
        @Bean RBloomFilter<String> bloom(){return mock(RBloomFilter.class);}
        @Bean OssUtils oss(){return mock(OssUtils.class);}
        @Bean PetPostMapper posts(){return mock(PetPostMapper.class);}
        @Bean AuditCommandService audits(){return mock(AuditCommandService.class);}
        @Bean com.hongjie.pms.modules.activity.cache.ActivityCache activityCache(){return mock(com.hongjie.pms.modules.activity.cache.ActivityCache.class);}
        @Bean com.hongjie.pms.common.punishment.util.PunishmentUtil punishment(){return mock(com.hongjie.pms.common.punishment.util.PunishmentUtil.class);}
        @Bean MessageService messages(){return mock(MessageService.class);}
    }
    @BeforeAll void open(){
        assertThat(MyBatisTestSupport.jdbcUrl()).matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?");
        assertThat(MyBatisTestSupport.configured()).isTrue();
        Class<?>[] types={UserMapper.class,AvatarHistoryMapper.class,FollowMapper.class,DelayTaskMapper.class,ScheduledTaskMapper.class,com.hongjie.pms.modules.activity.mapper.ActivityMapper.class,com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper.class};
        var factory=MyBatisTestSupport.createFactory(types);var source=factory.getConfiguration().getEnvironment().getDataSource();
        jdbc=new JdbcTemplate(source);assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("pet_system_test");
        for(String table:List.of("user","avatar_history","follow","delay_task"))
            assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",String.class,table)).isEqualToIgnoringCase("InnoDB");
        var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);tx.setTimeout(15);
        context=new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test-jwt",Map.of("jwt.secret","user-module-test-signing-key-only-1234567890","jwt.expire","3600","jwt.header","Authorization","jwt.token-prefix","Bearer ")));
        context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        var redis=mock(StringRedisTemplate.class);ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call->redisData.get(call.getArgument(0)));
        doAnswer(call->{redisData.put(call.getArgument(0),call.getArgument(1));return null;}).when(values).set(anyString(),anyString(),any(Duration.class));
        context.registerBean("redis",StringRedisTemplate.class,()->redis);
        var template=new SqlSessionTemplate(factory);for(Class<?> type:types)register(type,template);
        context.register(Config.class);context.refresh();users=context.getBean(UserService.class);admin=context.getBean(AdminService.class);
        avatars=context.getBean(AvatarService.class);sessions=context.getBean(UserSessions.class);jwt=context.getBean(JWTUtils.class);
        encoder=context.getBean(PasswordEncoder.class);passwordHash=encoder.encode("initial123");
        assertThat(users).isInstanceOf(PmsUserServiceImpl.class);assertThat(admin).isInstanceOf(PmsAdminServiceImpl.class);
        assertThat(avatars).isInstanceOf(PmsAvatarServiceImpl.class);
        mvc=MockMvcBuilders.standaloneSetup(context.getBean(UserAuthController.class),context.getBean(UserController.class),context.getBean(AdminController.class))
                .addInterceptors(context.getBean(AuthInterceptor.class)).build();
    }
    <T>void register(Class<T> type,SqlSessionTemplate template){context.registerBean(type,()->template.getMapper(type));}
    long insert(String sql,Object...args){return jdbc.execute((java.sql.Connection connection)->{
        try(var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);statement.executeUpdate();
            try(var keys=statement.getGeneratedKeys()){assertThat(keys.next()).isTrue();return keys.getLong(1);}
        }
    });}
    @BeforeEach void fixture(){
        redisData.clear();extra.clear();reset(context.getBean(DistributedCache.class),context.getBean(OssUtils.class));
        actor=insert("INSERT INTO user(user_name,password,status,role,nick_name,phone,searchable,follower_count,following_count,like_count) VALUES(?,?,1,0,?,'13899990001',1,7,8,9)",marker+"a",passwordHash,"original");
        other=insert("INSERT INTO user(user_name,password,status,role,nick_name,searchable) VALUES(?,?,1,0,?,1)",marker+"b",passwordHash,"other");
        manager=insert("INSERT INTO user(user_name,password,status,role) VALUES(?,?,1,1)",marker+"m",passwordHash);
    }
    @AfterEach void cleanup(){
        UserContext.clear();
        for(long id:allIds()){
            jdbc.update("DELETE FROM avatar_history WHERE user_id=?",id);
            jdbc.update("DELETE FROM delay_task WHERE business_id=? AND task_type IN ('BANNED_PUNISHMENT_END','MUTED_PUNISHMENT_END','SIGNUP_PUNISHMENT_END')",id);
            jdbc.update("DELETE FROM follow WHERE follower_id=? OR following_id=?",id,id);
            jdbc.update("DELETE FROM user WHERE id=? AND user_name LIKE ?",id,marker+"%");
        }
    }
    List<Long> allIds(){var ids=new ArrayList<>(List.of(actor,other,manager));ids.addAll(extra);return ids;}
    @AfterAll void close(){if(context!=null)context.close();}
    void as(long id,int role,Runnable action){UserContext.setUser(new UserInfo(id,"unused-username",role,null,null));try{action.run();}finally{UserContext.clear();}}
    LoginRequestDto login(String account,String password){var r=new LoginRequestDto();r.setAccount(account);r.setPassword(password);return r;}
    String token(){return users.login(login(marker+"a","initial123")).getToken();}
    int value(long id,String field){return jdbc.queryForObject("SELECT "+field+" FROM user WHERE id=?",Integer.class,id);}
    RegisterRequestDto registration(String name,String phone){var r=new RegisterRequestDto();r.setUserName(name);r.setPhone(phone);r.setPassword("initial123");return r;}
    ChangePasswordRequestDto change(String old,String next){var r=new ChangePasswordRequestDto();r.setOldPassword(old);r.setNewPassword(next);r.setConfirmPassword(next);return r;}
    long timedBan(){return insert("INSERT INTO delay_task(task_type,business_id,execute_time,status) VALUES('BANNED_PUNISHMENT_END',?,DATE_SUB(NOW(),INTERVAL 1 MINUTE),0)",actor);}
    @Test void badPasswordDoesNotChangeActivityTimestampOrCounters(){
        assertThatThrownBy(()->users.login(login(marker+"a","wrong"))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT last_active_time FROM user WHERE id=?",LocalDateTime.class,actor)).isNull();
        assertThat(value(actor,"follower_count")).isEqualTo(7);
    }
    @Test void loginSupportsPhoneAndUsernameAndRejectsDisabled(){
        assertThat(sessions.authenticate(users.login(login("13899990001","initial123")).getToken()).getUserId()).isEqualTo(actor);
        assertThat(sessions.authenticate(token()).getRole()).isZero();
        jdbc.update("UPDATE user SET status=0 WHERE id=?",actor);
        assertThatThrownBy(()->users.login(login(marker+"a","initial123"))).isInstanceOf(BusinessException.class);
    }
    @Test void infoUsesIdAndNeverReturnsPasswordHash(){
        as(actor,0,()->{var info=users.getUserInfo();assertThat(info.getUserName()).isEqualTo(marker+"a");assertThat(info.getPassword()).isNull();assertThat(info.getFollowingCount()).isEqualTo(8);});
    }
    @Test void patchPreservesUnspecifiedFieldsAndReturnsCompleteModel(){
        as(actor,0,()->{
            var result=users.updateUserInfo(actor,UserUpdateRequestDto.builder().nickName("changed").tags(List.of("cat")).build());
            assertThat(result.getUserName()).isEqualTo(marker+"a");assertThat(result.getNickName()).isEqualTo("changed");
            assertThat(result.getPhone()).isEqualTo("13899990001");assertThat(result.getTags()).containsExactly("cat");
            assertThat(result.getPassword()).isNull();assertThat(result.getFollowerCount()).isEqualTo(7);
        });
        assertThat(value(actor,"role")).isZero();assertThat(value(actor,"following_count")).isEqualTo(8);
    }
    @Test void foreignUpdateAndPasswordChangeFailWithoutWrites(){
        as(actor,0,()->{
            assertThatThrownBy(()->users.updateUserInfo(other,UserUpdateRequestDto.builder().nickName("stolen").build())).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->users.changePassword(other,change("initial123","new12345"))).isInstanceOf(BusinessException.class);
        });
        assertThat(jdbc.queryForObject("SELECT nick_name FROM user WHERE id=?",String.class,other)).isEqualTo("other");
    }
    @Test void clearingEmailPersistsNullAndDuplicateEmailRollsBack(){
        jdbc.update("UPDATE user SET email='occupied@example.test' WHERE id=?",other);
        as(actor,0,()->{
            assertThatThrownBy(()->users.updateUserInfo(actor,UserUpdateRequestDto.builder().nickName("rollback").email("occupied@example.test").build())).isInstanceOf(BusinessException.class);
            assertThat(users.getUserInfo().getNickName()).isEqualTo("original");
            users.updateUserInfo(actor,UserUpdateRequestDto.builder().email("mine@example.test").build());
            assertThat(users.updateUserInfo(actor,UserUpdateRequestDto.builder().email("").build()).getEmail()).isNull();
        });
    }
    @Test void privacyAndFollowStateAreEvaluatedPerViewer(){
        as(actor,0,()->users.updateUserInfo(actor,UserUpdateRequestDto.builder().email("private@example.test").privacySettings(Map.of("phone",false,"email",false,"tags",false)).build()));
        insert("INSERT INTO follow(follower_id,following_id) VALUES(?,?)",other,actor);
        as(other,0,()->{var result=users.getUserProfileInfo(actor);assertThat(result.getPhone()).isNull();assertThat(result.getEmail()).isNull();assertThat(result.getUser().getIsFollow()).isTrue();});
        as(manager,1,()->assertThat(users.getUserProfileInfo(actor).getUser().getIsFollow()).isFalse());
        as(actor,0,()->users.updateUserInfo(actor,UserUpdateRequestDto.builder().privacySettings(Map.of("phone",true)).build()));
        assertThat(users.getUserProfileInfo(actor).getPhone()).isEqualTo("13899990001");
    }
    @Test void passwordChangeRevokesNewAndLegacyTokensButNewLoginWorks(){
        String old=token(),legacy=jwt.generateToken(marker+"a",0,actor);
        as(actor,0,()->users.changePassword(actor,change("initial123","new12345")));
        assertThat(sessions.authenticate(old)).isNull();assertThat(sessions.authenticate(legacy)).isNull();
        assertThat(sessions.authenticate(users.login(login(marker+"a","new12345")).getToken())).isNotNull();
    }
    @Test void failedPasswordChangeAndOuterRollbackKeepCredentialsAndCache(){
        String old=token();as(actor,0,()->{
            assertThatThrownBy(()->users.changePassword(actor,change("wrong","new12345"))).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->tx.executeWithoutResult(status->{users.changePassword(actor,change("initial123","new12345"));throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
        });
        assertThat(sessions.authenticate(old)).isNotNull();assertThat(redisData).isEmpty();verifyNoInteractions(context.getBean(DistributedCache.class));
    }
    @Test void logoutRevokesOnlyOneTokenIncludingBearerRepresentation(){
        String first=token(),second=token();assertThat(first).isNotEqualTo(second);sessions.logout("Bearer "+first);
        assertThat(sessions.authenticate(first)).isNull();assertThat(sessions.authenticate(second)).isNotNull();
    }
    @Test void tokenRoleAndDisabledStateFollowTheDatabaseImmediately(){
        String old=token();jdbc.update("UPDATE user SET role=1 WHERE id=?",actor);
        assertThat(sessions.authenticate(old).getRole()).isEqualTo(1);
        jdbc.update("UPDATE user SET role=0,status=0 WHERE id=?",actor);assertThat(sessions.authenticate(old)).isNull();
    }
    @Test void registrationReturnsNormalAccountAndDatabaseEnforcesUniqueness(){
        var result=users.register(registration(marker+"r","13899990002"));extra.add(result.getUserId());
        assertThat(result.getToken()).isNotBlank();assertThat(value(result.getUserId(),"role")).isZero();
        assertThat(encoder.matches("initial123",jdbc.queryForObject("SELECT password FROM user WHERE id=?",String.class,result.getUserId()))).isTrue();
        assertThatThrownBy(()->users.register(registration(marker+"r","13899990003"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->users.register(registration(marker+"s","13899990002"))).isInstanceOf(BusinessException.class);
    }
    @Test void concurrentRegistrationHasOneWinner()throws Exception{
        var wins=new AtomicInteger();race(()->{
            try{users.register(registration(marker+"race","13899990004"));wins.incrementAndGet();}catch(BusinessException expected){}
        });
        extra.addAll(jdbc.queryForList("SELECT id FROM user WHERE user_name=?",Long.class,marker+"race"));
        assertThat(wins.get()).isEqualTo(1);assertThat(extra).hasSize(1);
    }
    @Test void searchHonorsSearchabilityAndStatus(){
        jdbc.update("UPDATE user SET searchable=0 WHERE id=?",actor);jdbc.update("UPDATE user SET status=0 WHERE id=?",other);
        assertThat(users.searchUsers(marker)).noneMatch(user->user.getUserId().equals(actor)||user.getUserId().equals(other));
        assertThat(users.searchUsers(" ")).isEmpty();
    }
    @Test void adminRejectsNonAdminBeforeAnyMutation(){
        as(actor,0,()->{
            assertThatThrownBy(()->admin.batchDisableUsers(List.of(other))).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->admin.userList(1,10,null,null)).isInstanceOf(BusinessException.class);
        });
        assertThat(value(other,"status")).isEqualTo(1);
    }
    @Test void batchPreservesPartialSuccessAndRejectsSelfAndDuplicates(){
        as(manager,1,()->{
            var result=admin.batchDisableUsers(List.of(actor,actor,manager,-1L));
            assertThat(result.getTotalCount()).isEqualTo(4);assertThat(result.getSuccessCount()).isEqualTo(1);assertThat(result.getFailCount()).isEqualTo(3);
        });
        assertThat(value(actor,"status")).isZero();assertThat(value(manager,"status")).isEqualTo(1);assertThat(value(actor,"follower_count")).isEqualTo(7);
    }
    @Test void manualDisableCancelsOldTimedBanEvenIfAlreadyDisabled(){
        jdbc.update("UPDATE user SET status=0 WHERE id=?",actor);long job=timedBan();
        as(manager,1,()->assertThat(admin.batchDisableUsers(List.of(actor)).getSuccessCount()).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delay_task WHERE id=?",Long.class,job)).isZero();
        assertThat(value(actor,"status")).isZero();
    }
    @Test void resetPasswordRevokesSessionsWithoutOverwritingProfile(){
        String old=token();as(manager,1,()->assertThat(admin.batchResetPassword(List.of(actor)).getSuccessCount()).isEqualTo(1));
        assertThat(sessions.authenticate(old)).isNull();
        assertThat(sessions.authenticate(users.login(login(marker+"a","123456")).getToken())).isNotNull();
        assertThat(value(actor,"follower_count")).isEqualTo(7);assertThat(value(actor,"role")).isZero();
    }
    @Test void adminHighPageKeepsTotalAndPageSizeIsBounded(){
        as(manager,1,()->{
            var result=admin.userList(999,10,marker,null);assertThat(result.getRecords()).isEmpty();assertThat(result.getTotal()).isEqualTo(3);
            assertThat(admin.userList(-1,10000,marker,null).getSize()).isEqualTo(100);
        });
    }
    @Test void avatarHistoryAndSwitchAreOwnerScopedAndUploadUsesNewService(){
        long foreign=insert("INSERT INTO avatar_history(user_id,avatar_url) VALUES(?,'https://example.test/other')",other);
        as(actor,0,()->assertThatThrownBy(()->avatars.switchToHistoryAvatar(foreign)).isInstanceOf(BusinessException.class));
        when(context.getBean(OssUtils.class).uploadAvatar(any(),eq(actor))).thenReturn("https://example.test/uploaded");
        as(actor,0,()->{
            assertThat(avatars.uploadAvatar(new MockMultipartFile("file","avatar.png","image/png",new byte[]{1})).getAvatarUrl()).isEqualTo("https://example.test/uploaded");
            var row=avatars.historyAvatar().get(0);assertThat(avatars.switchToHistoryAvatar(row.getId())).isEqualTo(row.getAvatarUrl());
            assertThat(users.getUserInfo().getAvatar()).isEqualTo(row.getAvatarUrl());
        });
    }
    @Test void repeatedProfileAvatarChangesPruneHistoryButNotOssObjects(){
        as(actor,0,()->{
            for(int i=0;i<13;i++)users.updateUserInfo(actor,UserUpdateRequestDto.builder().avatar("https://example.test/a"+i).build());
            assertThat(avatars.historyAvatar()).hasSize(11);
        });
        verify(context.getBean(OssUtils.class),never()).deleteFile(anyString());
    }
    @Test void profileRollbackDoesNotLeaveHistoryOrInvalidateCache(){
        as(actor,0,()->assertThatThrownBy(()->tx.executeWithoutResult(status->{
            users.updateUserInfo(actor,UserUpdateRequestDto.builder().avatar("https://example.test/rollback").build());throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM avatar_history WHERE user_id=?",Long.class,actor)).isZero();
        verifyNoInteractions(context.getBean(DistributedCache.class));
    }
    @Test void concurrentPasswordChangesFromSameOldPasswordHaveOneWinner()throws Exception{
        var wins=new AtomicInteger();
        race(()->as(actor,0,()->{try{users.changePassword(actor,change("initial123","new12345"));wins.incrementAndGet();}catch(BusinessException expected){}}));
        assertThat(wins.get()).isEqualTo(1);
    }
    @Test void httpInfoLogoutAndAdminAuthorizationUseNewServices()throws Exception{
        String token=token();
        mvc.perform(get("/pet-system/user/info").header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userName").value(marker+"a")).andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(post("/pet-system/user/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/pet-system/user/info").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    void race(Runnable action)throws Exception{
        var ready=new CountDownLatch(2);var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Void> task=()->{ready.countDown();if(!gate.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");action.run();return null;};
        try{var one=pool.submit(task);var two=pool.submit(task);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();gate.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);}
        finally{gate.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }

    @Test void manualDisableRacingExpiryRemainsDisabled()throws Exception{
        jdbc.update("UPDATE user SET status=0 WHERE id=?",actor);long job=timedBan();
        var ready=new CountDownLatch(2);var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var one=pool.submit(()->{ready.countDown();gate.await();as(manager,1,()->admin.batchDisableUsers(List.of(actor)));return null;});
            var two=pool.submit(()->{ready.countDown();gate.await();context.getBean(com.hongjie.pms.refactor.scheduling.ScheduledTaskWorker.class).execute(job,LocalDateTime.now());return null;});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();gate.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);
        }finally{gate.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        assertThat(value(actor,"status")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delay_task WHERE id=? AND status=0",Long.class,job)).isZero();
    }
    @Test void httpProfilePatchAcceptsExistingJsonShape()throws Exception{
        mvc.perform(put("/pet-system/user/update").header("Authorization","Bearer "+token())
                .contentType("application/json").content("{\"nickName\":\"http changed\",\"privacySettings\":{\"phone\":false}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickName").value("http changed"))
                .andExpect(jsonPath("$.data.userName").value(marker+"a"));
    }
    @Test void notificationWebSocketStopsSendingAfterAccountDisabled()throws Exception{
        var handler=new com.hongjie.pms.modules.message.websocket.WebSocketHandler(sessions);
        var socket=mock(org.springframework.web.socket.WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(socket.getUri()).thenReturn(java.net.URI.create("ws://localhost/ws?token="+token()));
        when(socket.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(socket);
        jdbc.update("UPDATE user SET status=0 WHERE id=?",actor);
        assertThat(com.hongjie.pms.modules.message.websocket.WebSocketHandler.pushToUser(actor,Map.of("message","fixture"))).isFalse();
        verify(socket).close(org.springframework.web.socket.CloseStatus.NOT_ACCEPTABLE);
        verify(socket,never()).sendMessage(any());
        handler.afterConnectionClosed(socket,org.springframework.web.socket.CloseStatus.NORMAL);
    }

    @Test void signupExpiryHonorsTheLaterLegacyDeadline(){
        jdbc.update("UPDATE user SET is_banned_signup=1,ban_signup_end_time=DATE_SUB(NOW(),INTERVAL 1 DAY),punishment_end_time=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE id=?",actor);
        long job=insert("INSERT INTO delay_task(task_type,business_id,execute_time,status) VALUES('SIGNUP_PUNISHMENT_END',?,DATE_SUB(NOW(),INTERVAL 1 MINUTE),0)",actor);
        context.getBean(com.hongjie.pms.refactor.scheduling.ScheduledTaskWorker.class).execute(job,LocalDateTime.now());
        assertThat(value(actor,"is_banned_signup")).isEqualTo(1);
        var row=context.getBean(UserMapper.class).selectById(actor);
        assertThat(UserRestrictions.signupBlocked(row,LocalDateTime.now())).isTrue();
    }
}
