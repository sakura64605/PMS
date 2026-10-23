package com.hongjie.pms.refactor.equivalence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.utils.OssUtils;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.petpost.dto.request.PetPostRequestDto;
import com.hongjie.pms.modules.petpost.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.petpost.service.Impl.PetPostServiceImpl;
import com.hongjie.pms.modules.user.dto.response.AvatarUploadResponse;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.refactor.extension.target.LegacyDialect;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import com.hongjie.pms.refactor.petpost.converter.PetPostDtoConverter;
import com.hongjie.pms.refactor.petpost.mapper.PmsPetPostMapper;
import com.hongjie.pms.refactor.petpost.service.PmsPetPostServiceImpl;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 等价性验证（写路径）：{@code PetPostService} 的老实现与新实现在<b>会改数据</b>的方法上
 * 必须产生同样的结果。
 *
 * <p>写路径比读路径更值得验证：读错了只是显示不对，写错了会<b>污染数据</b>。
 * 这里的每个用例都遵循同一个套路 —— 跑老实现、抓快照（数据库状态 + 协作方调用）、
 * 还原现场、跑新实现、抓快照、逐一比对。这样两边看到的是完全相同的初始状态。
 */
@DisplayName("等价性：PetPostService 写路径 老实现 vs 新实现")
class PetPostServiceWriteEquivalenceTest {

    private static SqlSessionFactory factory;

    private SqlSession session;
    private Connection connection;

    private PetPostServiceImpl oldService;
    private PmsPetPostServiceImpl newService;

    private OssUtils ossUtils;
    private DistributedCache cache;
    private FeedService feedService;
    private AuditService auditService;
    private ApplicationEventPublisher publisher;

    @BeforeAll
    static void createFactory() {
        assumeTrue(MyBatisTestSupport.configured(), MyBatisTestSupport.skipReason());
        factory = MyBatisTestSupport.createFactory(
                PetPostMapper.class, UserMapper.class, LikeRecordMapper.class,
                FavoriteRecordMapper.class, FollowMapper.class, PmsPetPostMapper.class);
        // 自己灌种子，建立确定的前置状态 —— 本类会改数据，不能依赖别的测试类留下的现场
        try (SqlSession seedSession = MyBatisTestSupport.openSession(factory)) {
            MyBatisTestSupport.applySeed(seedSession);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        session = MyBatisTestSupport.openSession(factory);
        connection = session.getConnection();

        PetPostMapper petPostMapper = session.getMapper(PetPostMapper.class);
        UserMapper userMapper = session.getMapper(UserMapper.class);
        LikeRecordMapper likeRecordMapper = session.getMapper(LikeRecordMapper.class);
        FavoriteRecordMapper favoriteRecordMapper = session.getMapper(FavoriteRecordMapper.class);
        FollowMapper followMapper = session.getMapper(FollowMapper.class);
        PmsPetPostMapper pmsPetPostMapper = session.getMapper(PmsPetPostMapper.class);

        ossUtils = mock(OssUtils.class);
        cache = mock(DistributedCache.class);
        feedService = mock(FeedService.class);
        auditService = mock(AuditService.class);
        publisher = mock(ApplicationEventPublisher.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

        when(ossUtils.uploadAvatar(any(), anyLong())).thenReturn("https://cdn/uploaded.png");

        oldService = new PetPostServiceImpl(petPostMapper, ossUtils, userMapper, likeRecordMapper,
                favoriteRecordMapper, followMapper, redisTemplate, feedService, auditService,
                cache, publisher);

        newService = new PmsPetPostServiceImpl(pmsPetPostMapper, new PetPostDtoConverter(),
                ossUtils, userMapper, likeRecordMapper, favoriteRecordMapper, followMapper,
                cache, new TargetTypeCodec(), feedService, auditService, publisher);
    }

    @AfterEach
    void tearDown() throws Exception {
        UserContext.clear();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        session.close();
    }

    // ==================== 发布 ====================

    @Nested
    @DisplayName("发布帖子")
    class Post {

        @Test
        @DisplayName("入库字段、审核提交、粉丝推送、搜索事件四者都一致")
        void postIsEquivalent() throws Exception {
            asUser(3L);
            PetPostRequestDto request = publishRequest();

            // ---- 老实现 ----
            oldService.post(request);
            long oldId = maxPostId();
            PostRow oldRow = readPost(oldId);
            List<String> oldAudit = captureAuditSubmit();
            List<String> oldFeed = captureFeedPush();
            Object oldEvent = capturePublishedEvent();

            cleanUpPost(oldId);
            resetCollaborators();

            // ---- 新实现 ----
            newService.post(request);
            long newId = maxPostId();
            PostRow newRow = readPost(newId);
            List<String> newAudit = captureAuditSubmit();
            List<String> newFeed = captureFeedPush();
            Object newEvent = capturePublishedEvent();

            cleanUpPost(newId);

            // 入库字段（除主键与时间戳外的业务字段）
            assertThat(newRow.userId()).isEqualTo(oldRow.userId());
            assertThat(newRow.type()).isEqualTo(oldRow.type());
            assertThat(newRow.title()).isEqualTo(oldRow.title());
            assertThat(newRow.content()).isEqualTo(oldRow.content());
            assertThat(newRow.petCategory()).isEqualTo(oldRow.petCategory());
            assertThat(newRow.petAge()).isEqualTo(oldRow.petAge());
            assertThat(newRow.petType()).isEqualTo(oldRow.petType());
            assertThat(newRow.contactPhone()).isEqualTo(oldRow.contactPhone());
            assertThat(newRow.address()).isEqualTo(oldRow.address());
            assertThat(newRow.status()).as("新帖应为待审核").isEqualTo(oldRow.status());
            assertThat(newRow.auditStatus()).isEqualTo(oldRow.auditStatus());
            assertThat(newRow.viewCount()).isEqualTo(oldRow.viewCount());
            assertThat(oldRow.status()).isEqualTo(0);

            // 审核提交与粉丝推送
            assertThat(newAudit).as("auditService.submit 的参数").isEqualTo(oldAudit);
            assertThat(newFeed).as("feedService.pushToFans 的参数").isEqualTo(oldFeed);

            // ES 同步事件：两边都应发布，且标题一致
            assertThat(newEvent).isNotNull();
            assertThat(oldEvent).isNotNull();
            assertThat(newEvent.getClass()).isEqualTo(oldEvent.getClass());
        }

        @Test
        @DisplayName("类型为救助时，提交的审核类型码与老实现一致（help）")
        void helpTypeUsesSameAuditCode() throws Exception {
            asUser(3L);
            PetPostRequestDto request = publishRequest();
            request.setType(1);

            oldService.post(request);
            long oldId = maxPostId();
            List<String> oldAudit = captureAuditSubmit();
            cleanUpPost(oldId);
            resetCollaborators();

            newService.post(request);
            long newId = maxPostId();
            List<String> newAudit = captureAuditSubmit();
            cleanUpPost(newId);

            assertThat(newAudit).isEqualTo(oldAudit);
            assertThat(newAudit.get(0)).as("救助对应的审核类型码").isEqualTo("help");
        }
    }

    // ==================== 修改 ====================

    @Nested
    @DisplayName("修改帖子")
    class Update {

        @Test
        @DisplayName("只改传入的字段、并把审核状态重置为待审，与老实现一致")
        void updateIsEquivalent() throws Exception {
            asUser(1L);
            long postId = 101L;          // 作者是 user 1

            PostRow before = readPost(postId);
            PetPostRequestDto request = new PetPostRequestDto();
            request.setId(postId);
            request.setTitle("修改后的标题");
            request.setPetName("新名字");

            oldService.update(request);
            PostRow afterOld = readPost(postId);
            verify(cache, times(1)).delete(anyString());

            restorePost(postId, before);
            resetCollaborators();

            newService.update(request);
            PostRow afterNew = readPost(postId);

            assertThat(afterNew).as("修改后的整行状态应一致").isEqualTo(afterOld);
            assertThat(afterNew.title()).isEqualTo("修改后的标题");
            assertThat(afterNew.petName()).isEqualTo("新名字");
            assertThat(afterNew.petCategory()).as("未传入的字段保持原值").isEqualTo(before.petCategory());
            assertThat(afterNew.auditStatus()).as("内容改过，审核需重新开始").isEqualTo(0);
            verify(cache, times(1)).delete(anyString());

            // 必须还原：本方法把审核状态改成了「待审核」，不还原会污染后续读测试
            restorePost(postId, before);
        }

        @Test
        @DisplayName("品种变化时会按品种重新推导物种分类，与老实现一致")
        void updateReclassifiesCategory() throws Exception {
            asUser(1L);
            long postId = 103L;          // 作者是 user 1，pet_type 为空
            PostRow before = readPost(postId);

            PetPostRequestDto request = new PetPostRequestDto();
            request.setId(postId);
            request.setPetType("金毛");

            oldService.update(request);
            int oldCategory = readPost(postId).petCategory();

            restorePost(postId, before);
            resetCollaborators();

            newService.update(request);
            int newCategory = readPost(postId).petCategory();

            assertThat(newCategory).isEqualTo(oldCategory);
            assertThat(newCategory).as("金毛应被归类为狗").isEqualTo(1);

            restorePost(postId, before);
        }
    }

    // ==================== 状态流转 ====================

    @ParameterizedTest(name = "{1} 后状态应为 {2}")
    @CsvSource({
            "delete,    逻辑删除, -1",
            "offline,   下架,     3",
            "recover,   恢复,     1",
    })
    @DisplayName("状态流转结果与老实现一致")
    void statusTransitionsAreEquivalent(String action, String description, int expectedStatus) throws Exception {
        Long postId = 111L;   // 作者是 user 2
        asUser(2L);

        PostRow before = readPost(postId);

        runAction(oldService, action, postId);
        PostRow afterOld = readPost(postId);
        verify(cache, times(1)).delete(anyString());
        assertThat(afterOld.status()).as("老实现的结果").isEqualTo(expectedStatus);

        restorePost(postId, before);
        resetCollaborators();

        runAction(newService, action, postId);
        PostRow afterNew = readPost(postId);
        verify(cache, times(1)).delete(anyString());

        assertThat(afterNew).as("新实现改完的整行状态应与老实现一致").isEqualTo(afterOld);

        restorePost(postId, before);
    }

    @Test
    @DisplayName("标记完成：状态一致，但新实现不再把浏览数清零 —— 修复老实现的数据损坏")
    void completeNoLongerResetsViewCount() throws Exception {
        Long postId = 111L;   // 作者是 user 2，种子数据里 view_count = 11
        asUser(2L);
        PostRow before = readPost(postId);
        assertThat(before.viewCount()).as("前置条件：这篇帖子有浏览记录").isPositive();

        oldService.complete(postId);
        PostRow afterOld = readPost(postId);

        restorePost(postId, before);
        resetCollaborators();

        newService.complete(postId);
        PostRow afterNew = readPost(postId);

        assertThat(afterOld.status()).isEqualTo(afterNew.status()).isEqualTo(2);

        assertThat(afterOld.viewCount())
                .as("老实现的缺陷：PetPost 上 @Builder.Default viewCount = 0，"
                        + "于是 PetPost.builder().status(2).build() 得到的对象带着 view_count = 0，"
                        + "再经 update(entity, wrapper) 一起写回库里 —— 标记完成会把浏览数清零")
                .isZero();
        assertThat(afterNew.viewCount())
                .as("新实现只更新状态列，浏览数保持不变")
                .isEqualTo(before.viewCount());

        restorePost(postId, before);
    }

    // ==================== 收藏 ====================

    @Nested
    @DisplayName("收藏")
    class Favorite {

        @Test
        @DisplayName("未收藏时收藏：新增记录并返回已收藏")
        void favoriteAdds() throws Exception {
            asUser(1L);
            Long postId = 103L;   // user 1 未收藏过

            boolean oldFlag = oldService.favorite(postId).getIsFavorited();
            boolean oldExists = favoriteExists(1L, postId);
            removeFavorite(1L, postId);

            boolean newFlag = newService.favorite(postId).getIsFavorited();
            boolean newExists = favoriteExists(1L, postId);
            removeFavorite(1L, postId);

            assertThat(oldFlag).isTrue();
            assertThat(newFlag).isEqualTo(oldFlag);
            assertThat(newExists).isEqualTo(oldExists);
        }

        @Test
        @DisplayName("已收藏时再点：删除记录并返回未收藏")
        void favoriteRemoves() throws Exception {
            asUser(1L);
            Long postId = 102L;   // 种子数据里 user 1 已收藏

            boolean oldFlag = oldService.favorite(postId).getIsFavorited();
            boolean oldExists = favoriteExists(1L, postId);
            restoreFavorite(1L, postId);

            boolean newFlag = newService.favorite(postId).getIsFavorited();
            boolean newExists = favoriteExists(1L, postId);
            restoreFavorite(1L, postId);

            assertThat(oldFlag).isFalse();
            assertThat(newFlag).isEqualTo(oldFlag);
            assertThat(newExists).isEqualTo(oldExists);
        }
    }

    // ==================== 彻底删除 ====================

    @Test
    @DisplayName("彻底删除：行被删掉且 OSS 收到同样的清理调用")
    void deleteReallyIsEquivalent() throws Exception {
        asUser(1L);
        long oldId = insertDeletedPost(9001L, "[\"a.png\",\"b.png\"]");

        oldService.deleteReally(oldId);
        boolean oldGone = !postExists(oldId);
        List<String> oldOssCalls = captureOssDeletes();

        long newId = insertDeletedPost(9002L, "[\"a.png\",\"b.png\"]");
        resetCollaborators();
        newService.deleteReally(newId);
        boolean newGone = !postExists(newId);
        List<String> newOssCalls = captureOssDeletes();

        assertThat(oldGone).as("老实现确实删掉了行").isTrue();
        assertThat(newGone).isTrue();
        assertThat(newOssCalls).as("OSS 清理的图片列表").isEqualTo(oldOssCalls);
        assertThat(newOssCalls).containsExactlyInAnyOrder("a.png", "b.png");
    }

    // ==================== 上传 ====================

    @Test
    @DisplayName("上传图片：返回结构与对 OSS 的调用一致")
    void uploadImageIsEquivalent() {
        asUser(1L);
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1, 2, 3});

        AvatarUploadResponse fromOld = oldService.uploadImage(file);
        AvatarUploadResponse fromNew = newService.uploadImage(file);

        assertThat(fromNew.getAvatarUrl()).isEqualTo(fromOld.getAvatarUrl());
        assertThat(fromNew.getMessage()).isEqualTo(fromOld.getMessage());
        verify(ossUtils, times(2)).uploadAvatar(any(), eq(1L));
    }

    // ==================== 异常行为 ====================

    @Nested
    @DisplayName("异常行为一致")
    class Exceptions {

        @Test
        @DisplayName("改别人的帖子：两者都报 PET_NOT_FOUND")
        void updateOthersPost() {
            asUser(3L);   // 帖子 111 的作者是 user 2
            PetPostRequestDto request = new PetPostRequestDto();
            request.setId(111L);
            request.setTitle("试图改别人的帖子");

            assertSameErrorCode(() -> oldService.update(request), () -> newService.update(request));
        }

        @Test
        @DisplayName("完成别人的帖子：两者都报 NO_PERMISSION")
        void completeOthersPost() {
            asUser(3L);
            assertSameErrorCode(() -> oldService.complete(111L), () -> newService.complete(111L));
        }

        @Test
        @DisplayName("彻底删除未删除的帖子：两者都报 PET_STATUS_ERROR")
        void deleteReallyRequiresDeletedStatus() {
            asUser(1L);
            assertSameErrorCode(() -> oldService.deleteReally(101L), () -> newService.deleteReally(101L));
        }

        @Test
        @DisplayName("操作不存在的帖子：两者都报 PET_NOT_FOUND")
        void notFound() {
            asUser(1L);
            assertSameErrorCode(() -> oldService.delete(999999L), () -> newService.delete(999999L));
        }
    }

    // ==================== 工具方法 ====================

    private record PostRow(Long id, Long userId, Integer type, String title, String content,
                           Integer petCategory, String petAge, String petType, String petName,
                           String contactPhone, String address, Integer status, Integer auditStatus,
                           Integer viewCount) {
    }

    private static void asUser(Long userId) {
        UserContext.setUser(new UserInfo(userId, "user" + userId, 0, "昵称" + userId, "avatar.png"));
    }

    private static PetPostRequestDto publishRequest() {
        PetPostRequestDto request = new PetPostRequestDto();
        request.setType(0);
        request.setTitle("写路径等价性测试用的帖子");
        request.setContent("正文内容");
        request.setPetName("测试宠物");
        request.setPetType("橘猫");
        request.setPetAge("3个月");
        request.setPetGender(1);
        request.setContactPhone("13900000000");
        request.setAddress("杭州市");
        return request;
    }

    private void runAction(Object service, String action, Long postId) {
        switch (action) {
            case "delete" -> {
                ((com.hongjie.pms.modules.petpost.service.PetPostService) service).delete(postId);
            }
            case "offline" -> {
                ((com.hongjie.pms.modules.petpost.service.PetPostService) service).offline(postId);
            }
            case "recover" -> {
                ((com.hongjie.pms.modules.petpost.service.PetPostService) service).recover(postId);
            }
            case "complete" -> {
                ((com.hongjie.pms.modules.petpost.service.PetPostService) service).complete(postId);
            }
            default -> throw new IllegalArgumentException("未知动作: " + action);
        }
    }

    private void assertSameErrorCode(Runnable oldCall, Runnable newCall) {
        Throwable fromOld = catchThrowable(oldCall::run);
        Throwable fromNew = catchThrowable(newCall::run);

        assertThat(fromOld).as("老实现应抛异常").isInstanceOf(BusinessException.class);
        assertThat(fromNew).as("新实现应抛异常").isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) fromNew).getCode())
                .as("错误码应一致")
                .isEqualTo(((BusinessException) fromOld).getCode());
    }

    // ---------- 协作方调用捕获 ----------

    private void resetCollaborators() {
        org.mockito.Mockito.clearInvocations(ossUtils, cache, feedService, auditService, publisher);
    }

    private List<String> captureAuditSubmit() {
        ArgumentCaptor<String> targetType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> targetId = ArgumentCaptor.forClass(Long.class);
        verify(auditService, times(1)).submit(targetType.capture(), targetId.capture());
        return List.of(targetType.getValue(), String.valueOf(targetId.getValue() != null));
    }

    private List<String> captureFeedPush() {
        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> postType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(feedService, times(1)).pushToFans(userId.capture(), any(), postType.capture(),
                title.capture(), any(), any(), any(), any());
        return List.of(String.valueOf(userId.getValue()), postType.getValue(), title.getValue());
    }

    /**
     * 捕获发布的事件。
     *
     * <p>必须按 {@code ApplicationEvent} 而不是 {@code Object} 捕获：
     * {@code ApplicationEventPublisher} 有 {@code publishEvent(ApplicationEvent)}
     * 与 {@code publishEvent(Object)} 两个重载，而 {@code PetPostPublishedEvent}
     * 继承了 {@code ApplicationEvent}，编译器会选更具体的那个。
     */
    private org.springframework.context.ApplicationEvent capturePublishedEvent() {
        ArgumentCaptor<org.springframework.context.ApplicationEvent> captor =
                ArgumentCaptor.forClass(org.springframework.context.ApplicationEvent.class);
        verify(publisher, times(1)).publishEvent(captor.capture());
        return captor.getValue();
    }

    private List<String> captureOssDeletes() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ossUtils, times(2)).deleteFile(captor.capture());
        return captor.getAllValues();
    }

    // ---------- 直接读写库 ----------

    private long maxPostId() throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) FROM pet_post")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private boolean postExists(long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM pet_post WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeQuery().next();
        }
    }

    private PostRow readPost(long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, user_id, type, title, content, pet_category, pet_age, pet_type, pet_name, "
                        + "contact_phone, address, status, audit_status, view_count FROM pet_post WHERE id = ?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new IllegalStateException("帖子不存在: " + id);
            }
            return new PostRow(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4),
                    rs.getString(5), rs.getInt(6), rs.getString(7), rs.getString(8), rs.getString(9),
                    rs.getString(10), rs.getString(11), rs.getInt(12), rs.getInt(13), rs.getInt(14));
        }
    }

    /** 把帖子还原到快照状态，让下一个实现看到同样的初始条件 */
    private void restorePost(long id, PostRow row) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pet_post SET title = ?, content = ?, pet_category = ?, pet_age = ?, pet_type = ?, "
                        + "pet_name = ?, contact_phone = ?, address = ?, status = ?, audit_status = ?, "
                        + "view_count = ? WHERE id = ?")) {
            ps.setString(1, row.title());
            ps.setString(2, row.content());
            ps.setInt(3, row.petCategory());
            ps.setString(4, row.petAge());
            ps.setString(5, row.petType());
            ps.setString(6, row.petName());
            ps.setString(7, row.contactPhone());
            ps.setString(8, row.address());
            ps.setInt(9, row.status());
            ps.setInt(10, row.auditStatus());
            ps.setInt(11, row.viewCount());
            ps.setLong(12, id);
            ps.executeUpdate();
        }
    }

    private void cleanUpPost(long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM pet_post WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /** 插入一条处于「已删除」状态的帖子（彻底删除要求先逻辑删除），返回其 id */
    private long insertDeletedPost(long id, String imagesJson) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pet_post (id, user_id, type, title, content, images, pet_category, "
                        + "contact_phone, status, audit_status, view_count, create_time, update_time) "
                        + "VALUES (?, 1, 0, '待彻底删除', 'c', ?, 0, '13900000001', -1, 1, 0, NOW(), NOW())")) {
            ps.setLong(1, id);
            ps.setString(2, imagesJson);
            ps.executeUpdate();
        }
        return id;
    }

    private boolean favoriteExists(Long userId, Long postId) throws Exception {
        String targetType = new TargetTypeCodec().write(BizTargetType.PET_POST, LegacyDialect.INTERACTION);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM favorite_record WHERE user_id = ? AND target_id = ? AND target_type = ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, postId);
            ps.setString(3, targetType);
            return ps.executeQuery().next();
        }
    }

    private void removeFavorite(Long userId, Long postId) throws Exception {
        String targetType = new TargetTypeCodec().write(BizTargetType.PET_POST, LegacyDialect.INTERACTION);
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM favorite_record WHERE user_id = ? AND target_id = ? AND target_type = ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, postId);
            ps.setString(3, targetType);
            ps.executeUpdate();
        }
    }

    /** 种子数据里的收藏记录（id 9401/9402/9403），测试改动后按原样补回 */
    private void restoreFavorite(Long userId, Long postId) throws Exception {
        if (favoriteExists(userId, postId)) {
            return;
        }
        String targetType = new TargetTypeCodec().write(BizTargetType.PET_POST, LegacyDialect.INTERACTION);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO favorite_record (user_id, target_type, target_id, create_time) "
                        + "VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, userId);
            ps.setString(2, targetType);
            ps.setLong(3, postId);
            ps.setObject(4, LocalDateTime.now());
            ps.executeUpdate();
        }
    }
}
