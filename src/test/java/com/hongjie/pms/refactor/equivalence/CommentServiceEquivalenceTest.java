package com.hongjie.pms.refactor.equivalence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.refactor.core.event.DomainEventPublisher;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.comment.dto.request.CommentCreateRequest;
import com.hongjie.pms.modules.comment.dto.response.CommentRespDto;
import com.hongjie.pms.modules.comment.mapper.CommentMapper;
import com.hongjie.pms.modules.comment.service.Impl.CommentServiceImpl;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.activity.handler.ActivityTargetHandler;
import com.hongjie.pms.refactor.comment.converter.CommentDtoConverter;
import com.hongjie.pms.refactor.comment.event.CommentNotificationEvent;
import com.hongjie.pms.refactor.comment.service.PmsCommentServiceImpl;
import com.hongjie.pms.refactor.daily.handler.DailyPostTargetHandler;
import com.hongjie.pms.refactor.extension.target.TargetHandlerRegistry;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import com.hongjie.pms.refactor.petpost.handler.PetPostTargetHandler;
import com.hongjie.pms.refactor.petpost.mapper.PmsPetPostMapper;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 等价性验证：{@code CommentService} 的老实现与新实现必须给出同样的结果。
 *
 * <p>与 petpost 那套同源（见 {@link MyBatisTestSupport} 的说明），
 * 额外覆盖了<b>写路径</b>：发表评论后，评论表里的行、目标内容的评论数增量、
 * 以及通知的参数都必须一致。
 *
 * <p><b>通知怎么比</b>：老实现直接调 {@code MessageService.sendCommentNotification(...)}，
 * 新实现发布 {@code CommentNotificationEvent}。两者机制不同，但可以比对
 * 「收件人 / 发送者 / businessId / 链接 / 正文」这几项 —— 它们最终决定用户看到什么。
 * 至于<b>渲染后的文案</b>，由 {@code NotificationHandlerTextTest} 单独对齐（那是个纯函数测试）。
 */
@DisplayName("等价性：CommentService 老实现 vs 新实现")
class CommentServiceEquivalenceTest {

    private static SqlSessionFactory factory;

    private SqlSession session;
    private Connection connection;

    private CommentServiceImpl oldService;
    private PmsCommentServiceImpl newService;

    /** 老实现用它发通知，本测试只做捕获 */
    private MessageService messageService;
    /** 新实现用它发布事件，本测试只做捕获 */
    private DomainEventPublisher eventPublisher;

    @BeforeAll
    static void createFactory() {
        assumeTrue(MyBatisTestSupport.configured(), MyBatisTestSupport.skipReason());
        factory = MyBatisTestSupport.createFactory(
                CommentMapper.class,
                UserMapper.class,
                PetPostMapper.class,
                PmsPetPostMapper.class,
                ActivityMapper.class,
                DailyPostMapper.class,
                LikeRecordMapper.class);
        // 自己灌种子，建立确定的前置状态（与本类的写路径测试相互隔离）
        try (SqlSession seedSession = MyBatisTestSupport.openSession(factory)) {
            MyBatisTestSupport.applySeed(seedSession);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        session = MyBatisTestSupport.openSession(factory);
        connection = session.getConnection();

        messageService = mock(MessageService.class);
        eventPublisher = mock(DomainEventPublisher.class);

        oldService = new CommentServiceImpl(
                session.getMapper(CommentMapper.class),
                session.getMapper(UserMapper.class),
                session.getMapper(PetPostMapper.class),
                session.getMapper(ActivityMapper.class),
                messageService,
                session.getMapper(DailyPostMapper.class));

        TargetHandlerRegistry registry = new TargetHandlerRegistry(List.of(
                new PetPostTargetHandler(session.getMapper(PmsPetPostMapper.class)),
                new ActivityTargetHandler(session.getMapper(ActivityMapper.class)),
                new DailyPostTargetHandler(session.getMapper(DailyPostMapper.class))));

        newService = new PmsCommentServiceImpl(
                session.getMapper(CommentMapper.class),
                session.getMapper(UserMapper.class),
                session.getMapper(LikeRecordMapper.class),
                registry,
                new TargetTypeCodec(),
                eventPublisher,
                new CommentDtoConverter());
    }

    @AfterEach
    void tearDown() throws Exception {
        UserContext.clear();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        session.close();
    }

    // ==================== 读路径：评论树 ====================

    static Stream<Arguments> listCases() {
        return Stream.of(
                Arguments.of("pet_post", 101L, 1, 10, "帖子的评论树（含两层回复）"),
                Arguments.of("pet_post", 101L, 1, 2, "同一目标分页（每页 2 条）"),
                Arguments.of("pet_activity", 1L, 1, 10, "活动的评论"),
                Arguments.of("pet_post", 102L, 1, 10, "另一篇帖子的评论"),
                Arguments.of("pet_post", 106L, 1, 10, "只有一条顶级评论"),
                Arguments.of("pet_post", 999L, 1, 10, "目标不存在（空结果）"));
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("listCases")
    @DisplayName("评论列表结果与老实现一致")
    void listReturnsSameResult(String targetType, Long targetId, Integer pageNum,
                               Integer pageSize, String description) {
        assertSameCommentPage(
                oldService.getCommentList(targetType, targetId, pageNum, pageSize),
                newService.getCommentList(targetType, targetId, pageNum, pageSize),
                true);
    }

    @Nested
    @DisplayName("有意的差异（读路径）")
    class ReadIntentionalDifferences {

        @Test
        @DisplayName("空结果：老实现返回固定的 current=1/size=10，新实现回显请求的分页参数")
        void emptyPageMetadataDiffers() {
            IPage<CommentRespDto> fromOld = oldService.getCommentList("pet_post", 999L, 3, 7);
            IPage<CommentRespDto> fromNew = newService.getCommentList("pet_post", 999L, 3, 7);

            assertThat(fromOld.getCurrent()).as("老实现无视请求参数，恒返回 1").isEqualTo(1);
            assertThat(fromOld.getSize()).isEqualTo(10);
            assertThat(fromNew.getCurrent()).as("新实现回显请求的页码").isEqualTo(3);
            assertThat(fromNew.getSize()).isEqualTo(7);

            assertThat(fromNew.getTotal()).as("总条数这个语义是一致的").isEqualTo(fromOld.getTotal());
            assertThat(fromNew.getRecords()).isEmpty();
        }

        @Test
        @DisplayName("按历史方言查询：新实现返回老实现结果的超集")
        void newImplementationReturnsSupersetForLegacyDialect() {
            // 种子数据里 9207 的 target_type = 'pet'（历史方言），其余挂在 101 上的是 'pet_post'
            List<Long> fromOld = idsOf(oldService.getCommentList("pet", 101L, 1, 50));
            List<Long> fromNew = idsOf(newService.getCommentList("pet", 101L, 1, 50));

            assertThat(fromOld)
                    .as("老实现按字面量等值匹配，只能查到 target_type='pet' 的那条")
                    .contains(9207L)
                    .doesNotContain(9201L);

            assertThat(fromNew)
                    .as("新实现同时匹配规范码与调用方传入的字面量，因此是超集")
                    .containsAll(fromOld)
                    .contains(9201L, 9202L);

            // 按规范码查询时，两者口径一致（因为历史方言行本就不该被规范码查到）
            assertSameCommentPage(
                    oldService.getCommentList("pet_post", 101L, 1, 50),
                    newService.getCommentList("pet_post", 101L, 1, 50),
                    true);
        }

        @Test
        @DisplayName("新实现填充 isLiked，老实现建了这个字段却从不赋值")
        void newImplementationFillsIsLiked() {
            asUser(1L);
            // 种子数据里 9304：user 1 点赞了评论 9201
            CommentRespDto fromOld = firstComment(oldService.getCommentList("pet_post", 101L, 1, 50));
            CommentRespDto fromNew = firstComment(newService.getCommentList("pet_post", 101L, 1, 50));

            assertThat(fromOld.getIsLiked()).as("老实现永远是 null").isNull();

            List<CommentRespDto> newList = newService.getCommentList("pet_post", 101L, 1, 50).getRecords();
            assertThat(flatten(newList))
                    .as("新实现至少把点赞过的评论标出来了")
                    .anyMatch(dto -> Boolean.TRUE.equals(dto.getIsLiked()));
            assertThat(fromNew.getId()).isEqualTo(fromOld.getId());
        }
    }

    // ==================== 写路径 ====================

    @Nested
    @DisplayName("写路径：发表评论")
    class CreateComment {

        @Test
        @DisplayName("评论帖子：入库内容、评论数增量、通知参数三者都一致")
        void commentOnPost() throws Exception {
            asUser(3L);
            Long postId = 101L;
            String content = "一条用于比对的评论";
            int countBefore = readCommentCount(postId);

            // ---- 老实现 ----
            oldService.createComment(request("pet_post", postId, content, null));
            CommentRow oldRow = readCommentById(maxCommentId());
            int oldCount = readCommentCount(postId);
            List<Notification> oldNotifications = captureOldNotifications(1);

            cleanUpComment(oldRow.id());
            writeCommentCount(postId, countBefore);

            // ---- 新实现 ----
            newService.createComment(request("pet_post", postId, content, null));
            CommentRow newRow = readCommentById(maxCommentId());
            int newCount = readCommentCount(postId);
            List<Notification> newNotifications = captureNewNotifications(1);

            cleanUpComment(newRow.id());
            writeCommentCount(postId, countBefore);

            // 入库的行：两次正文相同，因此可以逐字段比对
            assertThat(newRow.userId()).isEqualTo(oldRow.userId());
            assertThat(newRow.targetType()).isEqualTo(oldRow.targetType());
            assertThat(newRow.targetId()).isEqualTo(oldRow.targetId());
            assertThat(newRow.parentId()).isEqualTo(oldRow.parentId());
            assertThat(newRow.status()).isEqualTo(oldRow.status());
            assertThat(readContent(newRow.id())).isEqualTo(readContent(oldRow.id()));

            // 评论数：两边都恰好 +1（老实现此处原本会 +2，缺陷已修）
            assertThat(oldCount).as("老实现 +1").isEqualTo(countBefore + 1);
            assertThat(newCount).as("新实现 +1").isEqualTo(countBefore + 1);

            // 通知：一条，发给帖子作者；四个可比字段应完全一致
            assertThat(newNotifications).as("通知参数应逐项一致").isEqualTo(oldNotifications);
            assertThat(newNotifications.get(0).receiver()).as("帖子 101 的作者是 user 1").isEqualTo(1L);
            assertThat(newNotifications.get(0).link()).as("链接指向帖子").isEqualTo("/pet/101");
        }

        @Test
        @DisplayName("回复评论：两条通知，且被回复人的链接带评论锚点 —— 缺陷修复的验证点")
        void replyToComment() throws Exception {
            asUser(3L);
            Long postId = 101L;
            Long parentCommentId = 9201L;   // 作者是 user 2
            String content = "一条用于比对的回复";
            int countBefore = readCommentCount(postId);

            oldService.createComment(request("pet_post", postId, content, parentCommentId));
            CommentRow oldRow = readCommentById(maxCommentId());
            List<Notification> oldNotifications = captureOldNotifications(2);
            cleanUpComment(oldRow.id());
            writeCommentCount(postId, countBefore);

            newService.createComment(request("pet_post", postId, content, parentCommentId));
            CommentRow newRow = readCommentById(maxCommentId());
            List<Notification> newNotifications = captureNewNotifications(2);
            cleanUpComment(newRow.id());
            writeCommentCount(postId, countBefore);

            assertThat(newRow.parentId()).isEqualTo(parentCommentId);
            assertThat(oldRow.parentId()).isEqualTo(parentCommentId);

            // 两条通知：帖子作者 + 被回复评论的作者。
            // 「收件人 / businessId」逐项比对；链接里含评论 id，两次插入的 id 不同，
            // 所以链接改为各自校验（见下），而不是直接比对字符串。
            assertThat(newNotifications).hasSize(2);
            assertThat(oldNotifications).hasSize(2);
            for (int i = 0; i < 2; i++) {
                assertThat(newNotifications.get(i).receiver())
                        .as("第 %d 条通知的收件人", i + 1)
                        .isEqualTo(oldNotifications.get(i).receiver());
                assertThat(newNotifications.get(i).businessId())
                        .as("第 %d 条通知的 businessId", i + 1)
                        .isEqualTo(oldNotifications.get(i).businessId());
            }

            // 链接：各自指向自己的那条评论，且都带锚点。
            // 老实现这里曾因三元运算符优先级被截断（活动分支只剩 "/activity/"），
            // 也曾在日常动态分支被错发成 "/pet/..." —— 这两点由 buildCommentAnchorLink 的
            // 单元测试覆盖，这里验证的是「新实现给出的链接相对它自己插入的行是正确的」。
            assertThat(oldNotifications.get(1).link())
                    .isEqualTo("/pet/101?commentId=" + oldRow.id());
            assertThat(newNotifications.get(1).link())
                    .isEqualTo("/pet/101?commentId=" + newRow.id());

            Notification toParentAuthor = newNotifications.get(1);
            assertThat(toParentAuthor.receiver()).as("被回复评论的作者是 user 2").isEqualTo(2L);
            assertThat(toParentAuthor.businessId())
                    .as("老实现这里传的是被回复评论的 id，新实现保持一致")
                    .isEqualTo(parentCommentId);
        }

        @Test
        @DisplayName("新实现拒绝未知目标类型，老实现静默入库 —— 修复「孤儿评论」")
        void unknownTargetTypeIsRejectedByNewImplementation() throws Exception {
            asUser(3L);
            long countBefore = countComments();

            oldService.createComment(request("不存在的类型", 101L, "老实现照样入库", null));
            assertThat(countComments())
                    .as("老实现确实插进去了 —— 既不计数也不通知，用户看不到任何反馈")
                    .isEqualTo(countBefore + 1);

            Throwable thrown = catchThrowable(() ->
                    newService.createComment(request("不存在的类型", 101L, "新实现应拒绝", null)));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            assertThat(countComments()).as("新实现不应留下任何行").isEqualTo(countBefore + 1);

            cleanUpComment(readCommentByContent("老实现照样入库").id());
        }
    }

    // ==================== 工具方法 ====================

    private record CommentRow(Long id, Long userId, String targetType, Long targetId,
                              Long parentId, Integer status) {
    }

    private record Notification(Long receiver, Long sender, String content, Long businessId, String link) {
    }

    private static CommentCreateRequest request(String targetType, Long targetId,
                                                String content, Long parentId) {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setContent(content);
        request.setParentId(parentId);
        return request;
    }

    private static void asUser(Long userId) {
        UserContext.setUser(new UserInfo(userId, "user" + userId, 0, "昵称" + userId, "avatar.png"));
    }

    /**
     * 比对两个评论页。
     *
     * <p>{@code isLiked} 恒被忽略：老实现建了这个字段却从不赋值，
     * 新实现填充了它 —— 这是有意改进，由
     * {@link ReadIntentionalDifferences#newImplementationFillsIsLiked} 正向断言。
     */
    private void assertSameCommentPage(IPage<CommentRespDto> oldPage,
                                       IPage<CommentRespDto> newPage,
                                       boolean comparePagingMetadata) {
        assertThat(newPage.getTotal()).as("总条数").isEqualTo(oldPage.getTotal());
        if (comparePagingMetadata && !oldPage.getRecords().isEmpty()) {
            assertThat(newPage.getCurrent()).isEqualTo(oldPage.getCurrent());
            assertThat(newPage.getSize()).isEqualTo(oldPage.getSize());
        }

        // isLiked 由新实现填充、老实现恒为 null，属于有意改进（见 ReadIntentionalDifferences）。
        // 这里把它置空后再用 DTO 自身的 equals 比较 —— 比依赖断言库的「忽略字段」更直白，
        // 而且能确保嵌套的 replies 里同样被忽略（那正是最开始漏掉的地方）。
        stripIsLiked(newPage.getRecords());
        stripIsLiked(oldPage.getRecords());

        assertThat(newPage.getRecords()).as("评论树内容").isEqualTo(oldPage.getRecords());
    }

    /** 递归清空 isLiked，使两边的评论树在「用户视角状态」这个维度上可比 */
    private static void stripIsLiked(List<CommentRespDto> comments) {
        if (comments == null) {
            return;
        }
        for (CommentRespDto comment : comments) {
            comment.setIsLiked(null);
            stripIsLiked(comment.getReplies());
        }
    }

    private static List<Long> idsOf(IPage<CommentRespDto> page) {
        return page.getRecords().stream().map(CommentRespDto::getId).toList();
    }

    private static CommentRespDto firstComment(IPage<CommentRespDto> page) {
        return page.getRecords().get(0);
    }

    /** 把评论树拍平成列表，便于对整棵树做断言 */
    private static List<CommentRespDto> flatten(List<CommentRespDto> roots) {
        return roots.stream()
                .flatMap(root -> Stream.concat(Stream.of(root),
                        root.getReplies() == null ? Stream.empty() : flatten(root.getReplies()).stream()))
                .toList();
    }

    /** 捕获老实现传给 MessageService 的通知参数（调用次数由调用方明确给出） */
    private List<Notification> captureOldNotifications(int expectedCount) {
        ArgumentCaptor<Long> receiver = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> sender = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> businessId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);

        verify(messageService, times(expectedCount))
                .sendCommentNotification(receiver.capture(), sender.capture(),
                        anyString(), any(), content.capture(), businessId.capture(), link.capture());

        List<Notification> result = new java.util.ArrayList<>();
        for (int i = 0; i < expectedCount; i++) {
            result.add(new Notification(receiver.getAllValues().get(i), sender.getAllValues().get(i),
                    content.getAllValues().get(i), businessId.getAllValues().get(i),
                    link.getAllValues().get(i)));
        }
        return result;
    }

    /** 捕获新实现发布的事件，并映射成与老实现可比的形状 */
    private List<Notification> captureNewNotifications(int expectedCount) {
        ArgumentCaptor<CommentNotificationEvent> captor =
                ArgumentCaptor.forClass(CommentNotificationEvent.class);
        verify(eventPublisher, times(expectedCount)).publish(captor.capture());

        return captor.getAllValues().stream()
                .map(e -> new Notification(e.getReceiverId(), e.getAuthorId(), e.getContent(),
                        e.getBusinessId(), e.getLink()))
                .toList();
    }

    // ---------- 直接读库校验状态（绕开 MyBatis 会话缓存） ----------

    private long maxCommentId() throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) FROM comment")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * 按正文定位刚插入的评论行，并返回全表评论数。
     *
     * <p>用正文而不是「自增 id + 1」：InnoDB 的自增计数在删除后<b>不会回退</b>，
     * 上一轮测试删掉的行不会让 id 重复，所以自增推算并不可靠。
     * 两个实现用的正文各不相同，因此正文是可靠的定位键。
     */
    private CommentRow readCommentByContent(String content) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, user_id, target_type, target_id, parent_id, status "
                        + "FROM comment WHERE content = ?")) {
            ps.setString(1, content);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new IllegalStateException("未找到刚插入的评论，正文=" + content);
            }
            return toRow(rs);
        }
    }

    /** 刚插入完就调用，此时全表最大 id 就是新行（测试里没有并发写入） */
    private CommentRow readCommentById(long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, user_id, target_type, target_id, parent_id, status "
                        + "FROM comment WHERE id = ?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new IllegalStateException("未找到评论 id=" + id);
            }
            return toRow(rs);
        }
    }

    private CommentRow toRow(ResultSet rs) throws Exception {
        return new CommentRow(rs.getLong(1), rs.getLong(2), rs.getString(3),
                rs.getLong(4), rs.getLong(5), rs.getInt(6));
    }

    private String readContent(long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT content FROM comment WHERE id = ?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private long countComments() throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM comment")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void cleanUpComment(long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM comment WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private int readCommentCount(long postId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT comment_count FROM pet_post WHERE id = ?")) {
            ps.setLong(1, postId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void writeCommentCount(long postId, int value) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pet_post SET comment_count = ? WHERE id = ?")) {
            ps.setInt(1, value);
            ps.setLong(2, postId);
            ps.executeUpdate();
        }
    }
}
