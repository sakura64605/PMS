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
import com.hongjie.pms.modules.petpost.dto.PetDetailDto;
import com.hongjie.pms.modules.petpost.dto.request.PetQueryRequestDto;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import com.hongjie.pms.modules.petpost.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.petpost.service.Impl.PetPostServiceImpl;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.petpost.converter.PetPostDtoConverter;
import com.hongjie.pms.refactor.petpost.mapper.PmsPetPostMapper;
import com.hongjie.pms.refactor.petpost.service.PmsPetPostServiceImpl;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * 等价性验证：{@code PetPostService} 的老实现与新实现在<b>同一份数据</b>上
 * 必须给出<b>同样的结果</b>。
 *
 * <p>这是渐进式重构最关键的一道保障。整个收敛路线的终点是删掉老实现 ——
 * 在那之前，老实现就是唯一的规格说明。只要这里全绿，"新实现与老实现行为一致"
 * 这句话才有依据；否则它只是一句自我声明。
 *
 * <p><b>为什么用真实数据库而不是 mock</b>：单测能验证纯逻辑（枚举、转换器、分页桥接），
 * 但验证不了最容易出错的一层 —— <b>实体字段与真实表列的映射</b>。
 * {@code @TableField("pet_age")} 这种注解写错，编译能过、纯单测全绿，
 * 一上真库就是 {@code Unknown column}。只有打真库才能发现。
 *
 * <p>库地址与账号见 {@link MyBatisTestSupport}；未设置 {@code PMS_TEST_DB_PASSWORD}
 * 时整个类会被跳过。
 */
@DisplayName("等价性：PetPostService 老实现 vs 新实现")
class PetPostServiceEquivalenceTest {

    private static SqlSessionFactory factory;

    private SqlSession session;
    private PetPostServiceImpl oldService;
    private PmsPetPostServiceImpl newService;

    /** 用于在两次调用之间重置浏览计数（detail 会自增它，属于预期副作用） */
    private Connection connection;

    @BeforeAll
    static void createFactory() {
        assumeTrue(MyBatisTestSupport.configured(), MyBatisTestSupport.skipReason());
        factory = MyBatisTestSupport.createFactory(
                PetPostMapper.class,
                UserMapper.class,
                LikeRecordMapper.class,
                FavoriteRecordMapper.class,
                FollowMapper.class,
                PmsPetPostMapper.class);
        // 自己灌种子：本测试套里还有会改数据的写路径测试，不能依赖它们留下的现场
        try (SqlSession seedSession = MyBatisTestSupport.openSession(factory)) {
            MyBatisTestSupport.applySeed(seedSession);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        session = MyBatisTestSupport.openSession(factory);
        connection = session.getConnection();

        PetPostMapper petPostMapper = session.getMapper(PetPostMapper.class);
        UserMapper userMapper = session.getMapper(UserMapper.class);
        LikeRecordMapper likeRecordMapper = session.getMapper(LikeRecordMapper.class);
        FavoriteRecordMapper favoriteRecordMapper = session.getMapper(FavoriteRecordMapper.class);
        FollowMapper followMapper = session.getMapper(FollowMapper.class);
        PmsPetPostMapper pmsPetPostMapper = session.getMapper(PmsPetPostMapper.class);

        // 可 mock 的协作者：它们只负责缓存、推送、事件，不参与查询语义。
        // 缓存用默认行为（get 返回 null）即代表「缓存未命中」，走 DB 路径。
        DistributedCache cache = mock(DistributedCache.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        FeedService feedService = mock(FeedService.class);
        AuditService auditService = mock(AuditService.class);
        OssUtils ossUtils = mock(OssUtils.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

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

    // ==================== 列表 ====================

    /**
     * 新实现相对老实现<b>有意</b>补齐的字段。老实现在这几个字段上<b>自身就不一致</b>：
     * <ul>
     *   <li>{@code petCategory} —— list / detail / favoriteList 全都不设，恒为 null；</li>
     *   <li>{@code updateTime} —— {@code list} 不设，detail / favoriteList 设；</li>
     *   <li>{@code auditStatus} —— {@code list} 设，favoriteList / detail 不设。</li>
     * </ul>
     * 新实现三者都统一设置。这些字段本就在 DTO 上存在，补上不改变响应结构。
     *
     * <p><b>这里的分工</b>：等价性断言负责「老实现可靠设置的部分必须逐字段一致」；
     * 至于老实现漏设的部分，由 {@link IntentionalDifferences} 里的<b>正向断言</b>
     * 单独覆盖 —— 即「新实现确实把它们补上了」。这样既不会因为老实现的疏漏而误报
     * 不一致，也不会让新实现补错值却无人发现。
     */
    private static final String[] INTENTIONALLY_ADDED_FIELDS = {"petCategory", "updateTime", "auditStatus"};

    static Stream<Arguments> listQueries() {
        return Stream.of(
                query(q -> q.setOrderBy("createTime"), "公开列表（默认口径，按创建时间倒序）"),
                query(q -> {
                    q.setUserId(2L);
                    q.setOrderBy("createTime");
                }, "我的发布（bob，含待审与已下架、不含已删除）"),
                query(q -> {
                    q.setUserId(2L);
                    q.setStatus(-1);
                }, "回收站（status = -1）"),
                query(q -> {
                    q.setStatus(0);
                    q.setOrderBy("createTime");
                }, "待审核"),
                query(q -> {
                    q.setStatus(1);
                    q.setAuditStatus(1);
                    q.setOrderBy("createTime");
                }, "已发布且审核通过（控制器 list 的口径）"),
                query(q -> {
                    q.setType(1);
                    q.setOrderBy("createTime");
                }, "按类型：救助"),
                query(q -> {
                    q.setPetCategory(0);
                    q.setOrderBy("createTime");
                }, "按物种：猫"),
                query(q -> q.setPetType("橘"), "按品种模糊：橘"),
                query(q -> q.setKeyword("猫"), "按关键词（标题+正文）"),
                query(q -> q.setKeyword("受伤"), "关键词命中正文"),
                query(q -> {
                    q.setGender(2);
                    q.setOrderBy("createTime");
                }, "按性别：母"),
                query(q -> {
                    q.setOrderBy("viewCount");
                    q.setOrder("desc");
                }, "按浏览量排序"),
                query(q -> {
                    q.setOrderBy("viewCount");
                    q.setOrder("asc");
                }, "按浏览量升序"),
                query(q -> {
                    q.setPageNum(2);
                    q.setPageSize(3);
                    q.setOrderBy("createTime");
                }, "分页第二页（每页 3 条）"),
                query(q -> {
                    q.setPageNum(1);
                    q.setPageSize(100);
                    q.setOrderBy("createTime");
                }, "超大页大小"),
                query(q -> q.setUserId(999L), "不存在的用户（空结果）"),
                query(q -> {
                    q.setStatus(-1);
                    q.setOrderBy("createTime");
                }, "已删除（公开口径下的回收站）"));
    }

    @ParameterizedTest(name = "列表：{1}")
    @MethodSource("listQueries")
    @DisplayName("列表结果与老实现逐字段一致")
    void listReturnsSameResult(PetQueryRequestDto query, String description) {
        asUser(1L);

        IPage<PetListResponseDto> oldPage = oldService.list(query);
        IPage<PetListResponseDto> newPage = newService.list(query);

        assertSamePage(oldPage, newPage);
    }

    @ParameterizedTest(name = "列表（换视角用户）：{1}")
    @MethodSource("listQueries")
    @DisplayName("换一个登录用户，列表结果依然一致")
    void listIsSameForAnotherViewer(PetQueryRequestDto query, String description) {
        asUser(3L);

        assertSamePage(oldService.list(query), newService.list(query));
    }

    // ==================== 详情 ====================

    @Nested
    @DisplayName("详情")
    class Detail {

        @Test
        @DisplayName("已发布且审核通过：返回结果一致")
        void publishedPost() throws Exception {
            assertSameDetail(101L, 1L);
        }

        @Test
        @DisplayName("未登录用户查看：返回结果一致（互动状态保持 null）")
        void anonymousViewer() throws Exception {
            UserContext.clear();
            assertSameDetail(102L, null);
        }

        @Test
        @DisplayName("待审核：老实现与新实现都抛 AUDIT_WAITING")
        void pendingAuditDoesNotLeak() {
            assertBothThrow(104L, 1L, "AUDIT_WAITING");
        }

        @Test
        @DisplayName("已拒绝：老实现与新实现都抛 AUDIT_REJECT")
        void rejectedDoesNotLeak() {
            assertBothThrow(105L, 1L, "AUDIT_REJECT");
        }
    }

    // ==================== 收藏列表 ====================

    @Test
    @DisplayName("我的收藏列表结果一致")
    void favoriteListReturnsSameResult() {
        asUser(1L);

        IPage<PetListResponseDto> oldPage = oldService.favoriteList(1, 10);
        IPage<PetListResponseDto> newPage = newService.favoriteList(1, 10);

        assertSamePage(oldPage, newPage);
    }

    // ==================== 有意的差异 ====================

    @Nested
    @DisplayName("有意的差异（新实现修正了老实现的问题）")
    class IntentionalDifferences {

        @Test
        @DisplayName("新实现补齐了老实现漏设的 petCategory 与 updateTime")
        void fillsFieldsOldImplLeftNull() {
            asUser(1L);
            PetQueryRequestDto query = new PetQueryRequestDto();
            query.setOrderBy("createTime");

            PetListResponseDto fromOld = oldService.list(query).getRecords().get(0);
            PetListResponseDto fromNew = newService.list(query).getRecords().get(0);

            assertThat(fromOld.getPetCategory()).as("老实现不设置这个字段").isNull();
            assertThat(fromOld.getUpdateTime()).as("老实现也不设置这个字段").isNull();
            assertThat(fromNew.getPetCategory()).as("新实现补齐").isNotNull();
            assertThat(fromNew.getUpdateTime()).as("新实现补齐").isNotNull();
        }

        @Test
        @DisplayName("已删除的帖子：老实现能读到，新实现返回 404 —— 修复「知道 id 就能看已删帖」")
        void deletedPostIsNoLongerReadable() {
            asUser(1L);

            PetDetailDto fromOld = oldService.detail(108L, 1L);
            assertThat(fromOld).as("老实现确实把已删除的帖子返回了").isNotNull();

            BusinessException thrown = (BusinessException) catchThrowable(() -> newService.detail(108L, 1L));
            assertThat(thrown).isNotNull();
            assertThat(thrown.getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("不存在的帖子：老实现抛 NPE（500），新实现返回 404 —— 修复判空顺序")
        void missingPostIsNotFoundInsteadOfNpe() {
            asUser(1L);

            Throwable oldThrown = catchThrowable(() -> oldService.detail(999999L, 1L));
            assertThat(oldThrown).as("老实现在这里抛的是 NPE").isInstanceOf(NullPointerException.class);

            Throwable newThrown = catchThrowable(() -> newService.detail(999999L, 1L));
            assertThat(newThrown).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) newThrown).getCode())
                    .as("抛领域错误码 PET_NOT_FOUND，比笼统的 404 更有助于前端区分对象类型")
                    .isEqualTo(ErrorCode.PET_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("收藏列表：新实现补上了老实现漏设的 auditStatus")
        void favoriteListAlsoFillsAuditStatus() {
            asUser(1L);

            PetListResponseDto fromOld = oldService.favoriteList(1, 10).getRecords().get(0);
            PetListResponseDto fromNew = newService.favoriteList(1, 10).getRecords().get(0);

            assertThat(fromOld.getAuditStatus())
                    .as("老实现的 favoriteList 漏设了这个字段，而它的 list 是设的 —— 自身不一致")
                    .isNull();
            assertThat(fromNew.getAuditStatus()).as("新实现统一设置").isNotNull();
        }

        @Test
        @DisplayName("匿名访问详情：isLiked / isFavorite 保持 null，与老实现一致")
        void anonymousViewerKeepsNullInteractionFlags() {
            UserContext.clear();

            PetDetailDto fromOld = oldService.detail(102L, null);
            PetDetailDto fromNew = newService.detail(102L, null);

            assertThat(fromNew.getIsLiked()).as("不能悄悄把 null 变成 false").isNull();
            assertThat(fromNew.getIsFavorite()).isNull();
            assertThat(fromOld.getIsLiked()).isNull();
        }
    }

    // ==================== 工具方法 ====================

    /** 构造一个查询用例：DTO + 人类可读的描述（描述会显示在参数化测试的名字里） */
    private static Arguments query(java.util.function.Consumer<PetQueryRequestDto> customizer,
                                  String description) {
        PetQueryRequestDto dto = new PetQueryRequestDto();
        customizer.accept(dto);
        return Arguments.of(dto, description);
    }

    private static void asUser(Long userId) {
        UserContext.setUser(new UserInfo(userId, "user" + userId, 0, "昵称" + userId, "avatar.png"));
    }

    private void assertSamePage(IPage<PetListResponseDto> oldPage, IPage<PetListResponseDto> newPage) {
        assertThat(newPage.getTotal()).as("总条数").isEqualTo(oldPage.getTotal());
        assertThat(newPage.getCurrent()).as("当前页").isEqualTo(oldPage.getCurrent());
        assertThat(newPage.getSize()).as("页大小").isEqualTo(oldPage.getSize());
        assertThat(newPage.getRecords())
                .as("记录内容")
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields(INTENTIONALLY_ADDED_FIELDS)
                .containsExactlyElementsOf(oldPage.getRecords());
    }

    /**
     * 详情必须分两次调用并**在中间重置浏览计数** ——
     * {@code detail} 会自增 {@code view_count}，不重置的话第二次读到的值会比第一次大 1，
     * 那是预期副作用造成的假差异，不是行为不一致。
     */
    private void assertSameDetail(Long postId, Long viewerId) throws Exception {
        if (viewerId != null) {
            asUser(viewerId);
        }
        int originalViewCount = readViewCount(postId);

        PetDetailDto fromOld = oldService.detail(postId, viewerId);

        resetViewCount(postId, originalViewCount);
        PetDetailDto fromNew = newService.detail(postId, viewerId);

        resetViewCount(postId, originalViewCount);

        assertThat(fromNew)
                .usingRecursiveComparison()
                .ignoringFields(INTENTIONALLY_ADDED_FIELDS)
                .isEqualTo(fromOld);
    }

    private void assertBothThrow(Long postId, Long viewerId, String errorCodeName) {
        asUser(viewerId);

        Throwable fromOld = catchThrowable(() -> oldService.detail(postId, viewerId));
        Throwable fromNew = catchThrowable(() -> newService.detail(postId, viewerId));

        assertThat(fromOld).as("老实现应抛异常").isInstanceOf(BusinessException.class);
        assertThat(fromNew).as("新实现应抛异常").isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) fromNew).getCode())
                .as("错误码应一致（%s）", errorCodeName)
                .isEqualTo(((BusinessException) fromOld).getCode());
    }

    private int readViewCount(Long postId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT view_count FROM pet_post WHERE id = ?")) {
            ps.setLong(1, postId);
            var rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void resetViewCount(Long postId, int value) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pet_post SET view_count = ? WHERE id = ?")) {
            ps.setInt(1, value);
            ps.setLong(2, postId);
            ps.executeUpdate();
        }
    }
}
