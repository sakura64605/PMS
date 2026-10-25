package com.hongjie.pms.refactor.petpost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.toolkit.CacheUtil;
import com.hongjie.pms.common.circuitbreaker.annotation.CircuitBreaker;
import com.hongjie.pms.refactor.core.enums.BizTargetType;
import com.hongjie.pms.refactor.core.enums.CodableEnum;
import com.hongjie.pms.refactor.core.enums.biz.AuditStatus;
import com.hongjie.pms.refactor.core.enums.biz.PetPostStatus;
import com.hongjie.pms.refactor.core.enums.biz.PetPostType;
import com.hongjie.pms.refactor.core.exception.Guard;
import com.hongjie.pms.refactor.core.page.PageBridge;
import com.hongjie.pms.refactor.core.page.PageQuery;
import com.hongjie.pms.refactor.core.page.PageResult;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.enums.PostType;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.exception.SystemException;
import com.hongjie.pms.refactor.extension.target.LegacyDialect;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import com.hongjie.pms.common.utils.OssUtils;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.modules.following.entity.Follow;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.petpost.dto.PetDetailDto;
import com.hongjie.pms.modules.petpost.dto.request.PetPostRequestDto;
import com.hongjie.pms.modules.petpost.dto.request.PetQueryRequestDto;
import com.hongjie.pms.modules.petpost.dto.response.FavoriteResponseDto;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import com.hongjie.pms.modules.petpost.entity.FavoriteRecord;
import com.hongjie.pms.modules.petpost.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.petpost.service.PetPostService;
import com.hongjie.pms.refactor.petpost.classification.PetCategoryClassifier;
import com.hongjie.pms.modules.search.event.PetPostPublishedEvent;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.dto.response.AvatarUploadResponse;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.petpost.converter.PetPostDtoConverter;
import com.hongjie.pms.refactor.petpost.dto.PetPostWithAuthor;
import com.hongjie.pms.refactor.petpost.entity.PmsPetPost;
import com.hongjie.pms.refactor.petpost.mapper.PmsPetPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 宠物帖子服务（重构后实现）。
 *
 * <p>它实现的是<b>原有的</b> {@link PetPostService} 接口，返回原有的 DTO，
 * 因此对外的接口契约完全不变。标 {@link Primary} 后，容器会自动把它注入给
 * {@code PetPostController} —— <b>Controller 一行都不用改</b>，
 * 老的 {@code PetPostServiceImpl} 也不再被任何人引用，等同「被注释掉」。
 *
 * <p><b>回滚方式</b>：删除 {@code refactor/} 目录即可。届时
 * {@code PetPostService} 只剩老实现一个 bean，容器自动回退到它，
 * 不需要改任何配置、也不会有第二个 bean 的歧义。
 *
 * <p><b>本轮不换物理表</b>：读写的仍是老表 {@code pet_post}。这是有意的 ——
 * 该表被 comment / like / favorite / report / audit / feed / search / statistics
 * 共 8 个以上模块读取，单独换表会让那些未迁移的模块静默读到不一致的数据
 * （例如评论新帖时给不存在的行自增计数、审核链路找不到帖子、ES 搜不到新帖）。
 * 因此先重写实现、保持表不变，等整个「帖子簇」重构完成后统一切表。
 *
 * <p><b>相比老实现的行为改进</b>（都是修 bug 或补齐字段，不改变接口结构）：
 * <ol>
 *   <li>{@code detail} 不再对不存在的帖子抛 NPE。老实现先调
 *       {@code pet.getAuditStatus()} 再判 null，帖子不存在时返回 500 而非 404。</li>
 *   <li>{@code detail} 拒绝已删除的帖子。老实现不检查删除态，
 *       知道 id 就能读到已删除的帖子。</li>
 *   <li>{@code deleteReally} 不再对不存在的帖子抛 NPE（同样是判空顺序问题）。</li>
 *   <li>列表与详情补齐 {@code petCategory} / {@code updateTime}（老实现漏设，恒为 null）。</li>
 *   <li>分页参数统一归一化（老实现直接把入参塞进 {@code Page}，
 *       {@code pageSize} 没有上限；且入参为 null 时 NPE）。</li>
 * </ol>
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class PmsPetPostServiceImpl implements PetPostService {

    /** 详情缓存 TTL，与老实现一致 */
    private static final int DETAIL_CACHE_SECONDS = 1800;

    private final PmsPetPostMapper petPostMapper;
    private final PetPostDtoConverter converter;
    private final OssUtils ossUtils;
    private final UserMapper userMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final FavoriteRecordMapper favoriteRecordMapper;
    private final FollowMapper followMapper;
    private final DistributedCache distributedCache;
    private final TargetTypeCodec targetTypeCodec;
    private final FeedService feedService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.hongjie.pms.refactor.audit.AuditCommandService auditCommands;

    // ==================== 发布 / 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PetListResponseDto post(PetPostRequestDto request) {
        Long userId = UserContext.getUserId();
        Guard.requireTrue(userId != null, ErrorCode.UNAUTHORIZED, "未登录");

        PmsPetPost post = new PmsPetPost();
        post.setUserId(userId);
        post.setType(request.getType());
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setImages(request.getImages());
        post.setPetGender(request.getPetGender());
        post.setPetAgeText(request.getPetAge());
        post.setPetBreed(request.getPetType());
        // 物种分类：前端显式传则用之，否则按品种推导（复用老的分类器，行为不变）
        post.setPetCategory(request.getPetCategory() != null
                ? request.getPetCategory()
                : PetCategoryClassifier.classify(request.getPetType()));
        post.setPetName(request.getPetName());
        post.setContactPhone(request.getContactPhone());
        post.setContactWechat(request.getContactWechat());
        post.setAddress(request.getAddress());
        post.setStatus(PetPostStatus.PENDING_AUDIT.getCode());
        post.setAuditStatus(AuditStatus.PENDING.getCode());
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setShareCount(0);

        petPostMapper.insert(post);

        PetPostType postType = CodableEnum.of(PetPostType.class, request.getType());
        if (postType != null) {
            User publisher = userMapper.selectById(userId);
            // 审核类型码沿用 PostType（写的是 audit_record.target_type 的历史方言 adopt/help）
            String auditTargetType = postType == PetPostType.ADOPT
                    ? PostType.ADOPT.getCode()
                    : PostType.HELP.getCode();
            auditService.submit(auditTargetType, post.getId());
            feedService.pushToFans(userId, post.getId(), auditTargetType,
                    post.getTitle(), post.getImages(),
                    publisher.getUserName(), publisher.getAvatar(), post.getCreateTime());
        }

        // 迁移期适配：老事件只接受老实体，两者指向同一张表，投影后语义一致
        eventPublisher.publishEvent(new PetPostPublishedEvent(this, converter.toLegacyEntity(post)));

        return converter.toView(new PetPostWithAuthor(post, null, false, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PetListResponseDto update(PetPostRequestDto request) {
        Long userId = UserContext.getUserId();

        PmsPetPost post = petPostMapper.selectOne(new LambdaQueryWrapper<PmsPetPost>()
                .eq(PmsPetPost::getId, request.getId()).last("FOR UPDATE"));
        if (post == null || !post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        }
        if (PetPostStatus.COMPLETED.getCode().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "已完成的领养/救助不能修改或重新送审");
        }

        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        if (request.getImages() != null) {
            post.setImages(request.getImages());
        }
        if (request.getPetGender() != null) {
            post.setPetGender(request.getPetGender());
        }
        if (request.getPetAge() != null) {
            post.setPetAgeText(request.getPetAge());
        }
        if (request.getPetType() != null) {
            post.setPetBreed(request.getPetType());
        }
        // 物种分类：前端显式传则用之；否则品种变了就按品种重新推导
        if (request.getPetCategory() != null) {
            post.setPetCategory(request.getPetCategory());
        } else if (request.getPetType() != null) {
            post.setPetCategory(PetCategoryClassifier.classify(request.getPetType()));
        }
        if (request.getPetName() != null) {
            post.setPetName(request.getPetName());
        }
        if (request.getContactPhone() != null) {
            post.setContactPhone(request.getContactPhone());
        }
        if (request.getContactWechat() != null) {
            post.setContactWechat(request.getContactWechat());
        }
        if (request.getAddress() != null) {
            post.setAddress(request.getAddress());
        }
        // 内容被改动，审核需重新开始 —— 与老实现一致
        post.setAuditStatus(AuditStatus.PENDING.getCode());
        petPostMapper.updateById(post);
        auditCommands.ensurePending(post.getType() == 0 ? "adopt" : "help", post.getId());

        evictDetailCache(post.getId());

        return converter.toView(new PetPostWithAuthor(post, null, false, false));
    }

    @Override
    public AvatarUploadResponse uploadImage(MultipartFile file) {
        Long userId = UserContext.getUserId();
        try {
            String url = ossUtils.uploadAvatar(file, userId);
            log.info("图片上传成功: {}", url);
            return AvatarUploadResponse.builder()
                    .avatarUrl(url)
                    .message("图片上传成功")
                    .build();
        } catch (Exception e) {
            log.error("图片上传失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.UPLOAD_FAIL);
        }
    }

    // ==================== 查询 ====================

    @CircuitBreaker(
            value = "getPetList",
            windowSize = 10,
            minRequestAmount = 5,
            errorRateThreshold = 0.5,
            openDurationSeconds = 10,
            fallbackMethod = "fallbackGetPetList"
    )
    @Override
    public IPage<PetListResponseDto> list(PetQueryRequestDto queryDto) {
        PageQuery pageQuery = PageQuery.of(asLong(queryDto.getPageNum()), asLong(queryDto.getPageSize()));
        Page<PmsPetPost> page = pageQuery.toPage();

        IPage<PmsPetPost> postPage = petPostMapper.selectPage(page, buildWrapper(queryDto));
        if (postPage.getRecords().isEmpty()) {
            return PageBridge.toIPage(PageResult.empty(pageQuery));
        }

        Enrichment enrichment = enrich(postPage.getRecords());
        return PageBridge.toIPage(PageBridge.of(postPage)
                .map(post -> converter.toView(enrichment.of(post))));
    }

    /** 熔断降级：返回空页，与老实现一致 */
    public IPage<PetListResponseDto> fallbackGetPetList(PetQueryRequestDto queryDto) {
        log.warn("宠物列表熔断降级: pageNum={}, pageSize={}", queryDto.getPageNum(), queryDto.getPageSize());
        return PageBridge.toIPage(PageResult.empty(
                PageQuery.of(asLong(queryDto.getPageNum()), asLong(queryDto.getPageSize()))));
    }

    public IPage<PetListResponseDto> fallbackGetPetList(PetQueryRequestDto queryDto, Exception e) {
        log.error("宠物列表熔断降级: error={}", e.getMessage());
        return fallbackGetPetList(queryDto);
    }

    @CircuitBreaker(
            value = "getPetDetail",
            windowSize = 10,
            minRequestAmount = 5,
            errorRateThreshold = 0.5,
            openDurationSeconds = 10,
            fallbackMethod = "fallbackGetPetDetail"
    )
    @Override
    public PetDetailDto detail(Long id, Long currentUserId) {
        String cacheKey = detailCacheKey(id);

        PmsPetPost post = petPostMapper.selectById(id);
        // 修复：老实现先调 post.getAuditStatus() 再判 null，帖子不存在时抛 NPE（500）
        Guard.requireFound(post, ErrorCode.PET_NOT_FOUND, id);
        // 修复：老实现不检查删除态，知道 id 就能读到已删除的帖子
        Guard.requireTrue(!PetPostStatus.DELETED.getCode().equals(post.getStatus()),
                ErrorCode.PET_NOT_FOUND, "帖子不存在: " + id);

        AuditStatus auditStatus = CodableEnum.of(AuditStatus.class, post.getAuditStatus());
        boolean canPreview = currentUserId != null &&
                (post.getUserId().equals(currentUserId) || UserContext.isAdmin());
        if (!canPreview && auditStatus == AuditStatus.REJECTED) {
            throw new BusinessException(ErrorCode.AUDIT_REJECT);
        }
        if (!canPreview && auditStatus == AuditStatus.PENDING) {
            throw new BusinessException(ErrorCode.AUDIT_WAITING);
        }
        // Validate visibility against the current row before consulting the shared cache.
        boolean publicDetail = auditStatus == AuditStatus.APPROVED;
        if (publicDetail) {
            PetDetailDto cached = distributedCache.get(cacheKey, PetDetailDto.class);
            if (cached != null && java.util.Objects.equals(cached.getUpdateTime(), post.getUpdateTime())) {
                cached.setAuditStatus(post.getAuditStatus());
                cached.setStatus(post.getStatus());
                cached.setLikeCount(post.getLikeCount());
                cached.setCommentCount(post.getCommentCount());
                cached.setViewCount(post.getViewCount());
                cached.setUser(loadAuthor(post.getUserId(), currentUserId));
                fillViewerState(cached, id, currentUserId);
                return cached;
            }
        }

        UserSimpleDto author = loadAuthor(post.getUserId(), currentUserId);

        // 浏览计数：仅对已发布的、且不是自己发的帖子累加（与老实现一致）
        if (publicDetail && PetPostStatus.PUBLISHED.getCode().equals(post.getStatus())
                && !post.getUserId().equals(currentUserId)) {
            petPostMapper.incrementViewCount(id);
        }

        // 互动状态先留 null（与老实现一致：匿名访问时不计算），登录后再由 fillViewerState 填充
        PetDetailDto detail = converter.toDetail(new PetPostWithAuthor(post, author, null, null));
        fillViewerState(detail, id, currentUserId);
        if (publicDetail) distributedCache.put(cacheKey, detail, DETAIL_CACHE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        return detail;
    }

    public PetDetailDto fallbackGetPetDetail(Long id, Long userId) {
        return fallbackGetPetDetail(id, userId, null);
    }

    public PetDetailDto fallbackGetPetDetail(Long id, Long userId, Exception e) {
        if (e != null) {
            log.error("宠物详情熔断降级: id={}, error={}", id, e.getMessage());
        }
        // 返回占位详情，与老实现一致
        return PetDetailDto.builder()
                .id(id)
                .title("宠物信息暂时不可用")
                .content("系统繁忙，请稍后再试")
                .status(PetPostStatus.PENDING_AUDIT.getCode())
                .build();
    }

    @Override
    public IPage<PetListResponseDto> favoriteList(Integer pageNum, Integer pageSize) {
        Long userId = UserContext.getUserId();
        PageQuery pageQuery = PageQuery.of(asLong(pageNum), asLong(pageSize));

        IPage<FavoriteRecord> favoritePage = favoriteRecordMapper.selectPage(
                pageQuery.toPage(),
                new LambdaQueryWrapper<FavoriteRecord>()
                        .eq(FavoriteRecord::getUserId, userId)
                        .orderByDesc(FavoriteRecord::getCreateTime));

        if (favoritePage.getRecords().isEmpty()) {
            return PageBridge.toIPage(PageResult.empty(pageQuery));
        }

        List<Long> postIds = favoritePage.getRecords().stream()
                .map(FavoriteRecord::getTargetId)
                .toList();

        // 按收藏记录的顺序返回：一条收藏对应一篇帖子，用 Map 找回原对象
        Map<Long, PmsPetPost> postMap = petPostMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(PmsPetPost::getId, post -> post));
        Map<Long, UserSimpleDto> authorMap = loadAuthors(
                postMap.values().stream().map(PmsPetPost::getUserId).distinct().toList());

        List<PetListResponseDto> records = favoritePage.getRecords().stream()
                .map(record -> postMap.get(record.getTargetId()))
                .filter(java.util.Objects::nonNull)
                .map(post -> converter.toView(new PetPostWithAuthor(
                        post, authorMap.get(post.getUserId()), true, false)))
                .toList();

        return PageBridge.toIPage(PageResult.of(
                records, favoritePage.getTotal(), favoritePage.getCurrent(), favoritePage.getSize()));
    }

    // ==================== 状态流转 ====================

    /**
     * 逻辑删除。
     *
     * <p><b>切表点</b>：这里用 {@code status = -1} 表达删除，是因为老表把删除态
     * 塞在了 status 里。切到 {@code pms_pet_post} 后应改为设置
     * {@code deleted = 1}（并保留 status 不变）—— 这是切表改动中唯一涉及
     * 业务语义的地方，其余都是改表名与列名。
     */
    @Override
    public void delete(Long id) {
        PmsPetPost post = requirePost(id);
        post.setStatus(PetPostStatus.DELETED.getCode());
        petPostMapper.updateById(post);
        evictDetailCache(id);
    }

    @Override
    public void offline(Long id) {
        PmsPetPost post = requirePost(id);
        post.setStatus(PetPostStatus.OFFLINE.getCode());
        petPostMapper.updateById(post);
        evictDetailCache(id);
    }

    @Override
    public void recover(Long id) {
        PmsPetPost post = requirePost(id);
        post.setStatus(PetPostStatus.PUBLISHED.getCode());
        petPostMapper.updateById(post);
        evictDetailCache(id);
    }

    @Override
    public void complete(Long id) {
        Long userId = UserContext.getUserId();

        PmsPetPost post = petPostMapper.selectById(id);
        Guard.requireFound(post, ErrorCode.PET_NOT_FOUND, id);
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 只更新状态列，不整行覆盖（与老实现一致）
        PmsPetPost patch = new PmsPetPost();
        patch.setId(id);
        patch.setStatus(PetPostStatus.COMPLETED.getCode());
        petPostMapper.updateById(patch);

        evictDetailCache(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReally(Long id) {
        Long userId = UserContext.getUserId();

        PmsPetPost post = petPostMapper.selectById(id);
        // 修复：老实现先调 post.getUserId() 再判 null，帖子不存在时抛 NPE
        Guard.requireFound(post, ErrorCode.PET_NOT_FOUND, id);

        if (!UserContext.isAdmin() && !post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!PetPostStatus.DELETED.getCode().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.PET_STATUS_ERROR);
        }

        if (petPostMapper.deleteById(id) != 1) {
            throw new SystemException(ErrorCode.DB_ERROR);
        }

        // 数据库已删，OSS 清理失败不回滚：留日志人工清理，避免删不掉帖子
        try {
            deleteFromOss(post.getImages());
        } catch (Exception e) {
            log.error("OSS 删除失败，需要人工清理: {}", post.getImages(), e);
        }
    }

    // ==================== 收藏 ====================

    @Override
    public FavoriteResponseDto favorite(Long id) {
        requirePost(id);

        Long userId = UserContext.getUserId();
        String targetType = targetTypeCodec.write(BizTargetType.PET_POST, LegacyDialect.INTERACTION);

        FavoriteRecord existing = favoriteRecordMapper.selectOne(new QueryWrapper<FavoriteRecord>()
                .eq("user_id", userId)
                .eq("target_id", id)
                .eq("target_type", targetType));

        if (existing != null) {
            favoriteRecordMapper.deleteById(existing);
            log.info("取消收藏成功: postId={}", id);
            return FavoriteResponseDto.builder().isFavorited(false).build();
        }

        favoriteRecordMapper.insert(FavoriteRecord.builder()
                .userId(userId)
                .targetId(id)
                .targetType(targetType)
                .createTime(LocalDateTime.now())
                .build());
        log.info("收藏成功: postId={}", id);
        return FavoriteResponseDto.builder().isFavorited(true).build();
    }

    // ==================== 内部方法 ====================

    private PmsPetPost requirePost(Long id) {
        return Guard.requireFound(petPostMapper.selectById(id), ErrorCode.PET_NOT_FOUND, id);
    }

    private LambdaQueryWrapper<PmsPetPost> buildWrapper(PetQueryRequestDto query) {
        LambdaQueryWrapper<PmsPetPost> wrapper = new LambdaQueryWrapper<>();

        if (query.getType() != null) {
            wrapper.eq(PmsPetPost::getType, query.getType());
        }
        if (query.getGender() != null) {
            wrapper.eq(PmsPetPost::getPetGender, query.getGender());
        }
        if (query.getPetCategory() != null) {
            wrapper.eq(PmsPetPost::getPetCategory, query.getPetCategory());
        }
        if (StringUtils.hasText(query.getPetType())) {
            wrapper.like(PmsPetPost::getPetBreed, query.getPetType());
        }
        if (StringUtils.hasText(query.getPetName())) {
            wrapper.like(PmsPetPost::getPetName, query.getPetName());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword();
            wrapper.and(w -> w.like(PmsPetPost::getTitle, keyword)
                    .or().like(PmsPetPost::getContent, keyword));
        }
        if (query.getAuditStatus() != null) {
            wrapper.eq(PmsPetPost::getAuditStatus, query.getAuditStatus());
        }

        applyStatusScope(wrapper, query);
        applyOrder(wrapper, query);
        return wrapper;
    }

    /**
     * 状态口径，逐条对齐老实现（老代码此处用的是裸数字 0/1/2/3/-1）：
     * <ul>
     *   <li>显式指定 status —— 按指定值过滤（回收站就是靠传 -1 实现的）；</li>
     *   <li>查某人发布的 —— 待审核/已发布/已完成/已下架（不含已删除、已拒绝）；</li>
     *   <li>公开列表 —— 已发布/已完成/已下架。</li>
     * </ul>
     * 这三条口径都属于「对外可见行为」，因此原样保留，不做「顺手修正」——
     * 例如公开列表包含「已下架」看起来可疑，但前端可能依赖它做展示，
     * 改动它属于产品决策而非重构。
     */
    private void applyStatusScope(LambdaQueryWrapper<PmsPetPost> wrapper, PetQueryRequestDto query) {
        if (query.getStatus() != null) {
            wrapper.eq(PmsPetPost::getStatus, query.getStatus());
        } else if (query.getUserId() != null) {
            wrapper.in(PmsPetPost::getStatus,
                    PetPostStatus.PENDING_AUDIT.getCode(),
                    PetPostStatus.PUBLISHED.getCode(),
                    PetPostStatus.COMPLETED.getCode(),
                    PetPostStatus.OFFLINE.getCode());
        } else {
            wrapper.in(PmsPetPost::getStatus,
                    PetPostStatus.PUBLISHED.getCode(),
                    PetPostStatus.COMPLETED.getCode(),
                    PetPostStatus.OFFLINE.getCode());
        }
        // userId 条件是独立于状态口径的，老实现也是无条件应用
        if (query.getUserId() != null) {
            wrapper.eq(PmsPetPost::getUserId, query.getUserId());
        }
    }

    /**
     * 排序。老实现只在 {@code orderBy} 非空时排序，否则完全不加 ORDER BY。
     *
     * <p>注意控制器传的 {@code "creat_time"} 是个拼写错误（少了个 e），
     * 它不等于 "viewCount"，所以会走创建时间分支 —— 结果恰好是期望的。
     * 这里保留同样的判断逻辑，因此行为与老实现完全一致，
     * 包括这个拼写错误带来的效果。
     */
    private void applyOrder(LambdaQueryWrapper<PmsPetPost> wrapper, PetQueryRequestDto query) {
        if (query.getOrderBy() == null) {
            return;
        }
        boolean asc = "asc".equals(query.getOrder());
        if ("viewCount".equals(query.getOrderBy())) {
            wrapper.orderBy(true, asc, PmsPetPost::getViewCount);
        } else {
            wrapper.orderBy(true, asc, PmsPetPost::getCreateTime);
        }
    }

    private Enrichment enrich(List<PmsPetPost> posts) {
        Long viewerId = UserContext.getUserId();
        Map<Long, UserSimpleDto> authors = loadAuthors(
                posts.stream().map(PmsPetPost::getUserId).distinct().toList());

        if (viewerId == null) {
            return new Enrichment(authors, Collections.emptySet(), Collections.emptySet());
        }

        List<Long> postIds = posts.stream().map(PmsPetPost::getId).toList();
        String targetType = targetTypeCodec.write(BizTargetType.PET_POST, LegacyDialect.INTERACTION);

        Set<Long> favorited = favoriteRecordMapper.selectList(new LambdaQueryWrapper<FavoriteRecord>()
                        .eq(FavoriteRecord::getUserId, viewerId)
                        .eq(FavoriteRecord::getTargetType, targetType)
                        .in(FavoriteRecord::getTargetId, postIds))
                .stream().map(FavoriteRecord::getTargetId).collect(Collectors.toSet());

        Set<Long> liked = likeRecordMapper.selectList(new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getUserId, viewerId)
                        .eq(LikeRecord::getTargetType, targetType)
                        .in(LikeRecord::getTargetId, postIds))
                .stream().map(LikeRecord::getTargetId).collect(Collectors.toSet());

        return new Enrichment(authors, favorited, liked);
    }

    private Map<Long, UserSimpleDto> loadAuthors(List<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, this::toSimpleDto));
    }

    private UserSimpleDto loadAuthor(Long authorId, Long currentUserId) {
        User user = userMapper.selectById(authorId);
        if (user == null) {
            return null;
        }
        UserSimpleDto dto = toSimpleDto(user);
        // 与老实现一致：只在确实关注时置 true，未关注时保持 null
        if (currentUserId != null) {
            Follow follow = followMapper.selectOne(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getFollowerId, currentUserId)
                    .eq(Follow::getFollowingId, authorId));
            if (follow != null) {
                dto.setIsFollow(true);
            }
        }
        return dto;
    }

    private UserSimpleDto toSimpleDto(User user) {
        return UserSimpleDto.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .build();
    }

    /** 补齐当前用户视角的点赞/收藏态；未登录时保持 null（与老实现一致） */
    private void fillViewerState(PetDetailDto detail, Long postId, Long currentUserId) {
        detail.setIsLiked(null);
        detail.setIsFavorite(null);
        if (currentUserId == null) {
            return;
        }
        String targetType = targetTypeCodec.write(BizTargetType.PET_POST, LegacyDialect.INTERACTION);
        detail.setIsLiked(likeRecordMapper.selectCount(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, currentUserId)
                .eq(LikeRecord::getTargetId, postId)
                .eq(LikeRecord::getTargetType, targetType)) > 0);
        detail.setIsFavorite(favoriteRecordMapper.selectCount(new LambdaQueryWrapper<FavoriteRecord>()
                .eq(FavoriteRecord::getUserId, currentUserId)
                .eq(FavoriteRecord::getTargetId, postId)
                .eq(FavoriteRecord::getTargetType, targetType)) > 0);
    }

    private void deleteFromOss(List<String> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        for (String image : images) {
            try {
                ossUtils.deleteFile(image);
            } catch (Exception e) {
                // 单张失败不影响其余图片的清理
                log.error("删除图片失败: {}", image, e);
            }
        }
    }

    /**
     * 缓存 key 与老实现保持一致（{@code pet:{id}}）。
     *
     * <p>这一点很重要：其它模块（审核、管理后台）在改动帖子后也会按同一个 key
     * 清缓存，只要 key 相同，新老实现混用期间缓存就不会错乱。
     */
    private String detailCacheKey(Long id) {
        return CacheUtil.buildKey("pet", String.valueOf(id));
    }

    private void evictDetailCache(Long id) {
        com.hongjie.pms.refactor.core.cache.AfterCommitCache.evict(
                () -> distributedCache.delete(detailCacheKey(id)));
    }

    private static Long asLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    /** 已装配好的关联数据，避免逐条查询 */
    private record Enrichment(Map<Long, UserSimpleDto> authors,
                              Set<Long> favoritedIds,
                              Set<Long> likedIds) {

        PetPostWithAuthor of(PmsPetPost post) {
            return new PetPostWithAuthor(
                    post,
                    authors.get(post.getUserId()),
                    favoritedIds.contains(post.getId()),
                    likedIds.contains(post.getId()));
        }
    }
}
