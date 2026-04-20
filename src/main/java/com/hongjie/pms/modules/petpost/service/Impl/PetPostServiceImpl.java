package com.hongjie.pms.modules.petpost.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.annotation.CircuitBreaker;
import com.hongjie.pms.common.annotation.DistributedCacheable;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.CommentLikeTypes;
import com.hongjie.pms.common.enums.PostType;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.exception.SystemException;
import com.hongjie.pms.common.mq.CacheUpdateProducer;
import com.hongjie.pms.common.utils.OssUtils;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.modules.following.entity.Follow;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.petpost.dto.PetDetailDto;
import com.hongjie.pms.modules.petpost.service.PetPostService;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.petpost.dto.request.PetPostRequestDto;
import com.hongjie.pms.modules.petpost.dto.request.PetQueryRequestDto;
import com.hongjie.pms.modules.user.dto.response.AvatarUploadResponse;
import com.hongjie.pms.modules.petpost.dto.response.FavoriteResponseDto;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import com.hongjie.pms.modules.petpost.entity.FavoriteRecord;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.petpost.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.common.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetPostServiceImpl implements PetPostService {

    private final PetPostMapper petPostMapper;
    private final OssUtils ossUtils;
    private final UserMapper userMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final FavoriteRecordMapper favoriteRecordMapper;
    private final FollowMapper followMapper;
    private final CacheUpdateProducer cacheUpdateProducer;
    private final RedisTemplate redisTemplate;
    private final FeedService feedService;
    private final AuditService auditService;

    @Override
    public PetListResponseDto post(PetPostRequestDto request) {

        Long userId = UserContext.getUserId();

        PetPost petPost = PetPost.builder()
                .userId(userId)
                .type(request.getType())
                .title(request.getTitle())
                .content(request.getContent())
                .images(request.getImages())
                .petGender(request.getPetGender())
                .petAge(request.getPetAge())
                .petType(request.getPetType())
                .petName(request.getPetName())
                .contactPhone(request.getContactPhone())
                .contactWechat(request.getContactWechat())
                .address(request.getAddress())
                .status(1)
                .auditStatus(0)
                .viewCount(0)
                .build();

        petPostMapper.insert(petPost);

        auditService.submit(PostType.PET.getCode(), petPost.getId());

        // 获取用户信息
        User user = userMapper.selectById(userId);

        // 推送 Feed
        feedService.pushToFans(
                userId,
                petPost.getId(),
                PostType.PET.getCode(),
                petPost.getTitle(),
                petPost.getImages(),
                user.getUserName(),
                user.getAvatar(),
                petPost.getCreateTime()
        );

        return PetListResponseDto.builder()
                .id(petPost.getId())
                .type(petPost.getType())
                .title(petPost.getTitle())
                .petName(petPost.getPetName())
                .petType(petPost.getPetType())
                .petAge(petPost.getPetAge())
                .petGender(petPost.getPetGender())
                .images(petPost.getImages())
                .viewCount(petPost.getViewCount())
                .status(petPost.getStatus())
                .build();

    }

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
        // 1. 构建查询条件
        LambdaQueryWrapper<PetPost> wrapper = new LambdaQueryWrapper<>();

        // 类型筛选
        if (queryDto.getType() != null) {
            wrapper.eq(PetPost::getType, queryDto.getType());
        }

        // 性别筛选
        if (queryDto.getGender() != null) {
            wrapper.eq(PetPost::getPetGender, queryDto.getGender());
        }

        // 品种筛选
        if (StringUtils.hasText(queryDto.getPetType())) {
            wrapper.like(PetPost::getPetType, queryDto.getPetType());
        }

        // 状态筛选
        if (queryDto.getStatus() != null) {
            wrapper.eq(PetPost::getStatus, queryDto.getStatus());
        } else {
            wrapper.in(PetPost::getStatus, 1, 2, 3);
        }

        if (queryDto.getAuditStatus() != null) {
            wrapper.eq(PetPost::getAuditStatus, queryDto.getAuditStatus());
        }

        // 用户筛选（我的发布）
        if (queryDto.getUserId() != null) {
            wrapper.eq(PetPost::getUserId, queryDto.getUserId());
        }

        // 关键词搜索
        if (StringUtils.hasText(queryDto.getKeyword())) {
            wrapper.and(w -> w
                    .like(PetPost::getTitle, queryDto.getKeyword())
                    .or()
                    .like(PetPost::getContent, queryDto.getKeyword())
            );
        }

        // 宠物名搜索
        if (StringUtils.hasText(queryDto.getPetName())) {
            wrapper.like(PetPost::getPetName, queryDto.getPetName());
        }

        // 排序
        if (queryDto.getOrderBy() != null) {
            if ("viewCount".equals(queryDto.getOrderBy())) {
                if ("asc".equals(queryDto.getOrder())) {
                    wrapper.orderByAsc(PetPost::getViewCount);
                } else {
                    wrapper.orderByDesc(PetPost::getViewCount);
                }
            } else {
                if ("asc".equals(queryDto.getOrder())) {
                    wrapper.orderByAsc(PetPost::getCreateTime);
                } else {
                    wrapper.orderByDesc(PetPost::getCreateTime);
                }
            }
        }


        // 2. 分页查询
        Page<PetPost> page = new Page<>(queryDto.getPageNum(), queryDto.getPageSize());
        IPage<PetPost> petPage = petPostMapper.selectPage(page, wrapper);

        // 3. 如果查询结果为空，直接返回空分页
        if (petPage.getRecords() == null || petPage.getRecords().isEmpty()) {
            Page<PetListResponseDto> emptyPage = new Page<>(page.getCurrent(), page.getSize(), 0);
            emptyPage.setRecords(new ArrayList<>());
            return emptyPage;
        }

        // 4. 收集用户ID
        List<Long> userIds = petPage.getRecords().stream()
                .map(PetPost::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // 5. 批量查询用户信息 - 关键：先判断是否为空
        Map<Long, UserSimpleDto> userMap;
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(
                            User::getId,
                            user -> UserSimpleDto.builder()
                                    .userId(user.getId())
                                    .username(user.getUserName())
                                    .nickname(user.getNickName())
                                    .avatar(user.getAvatar())
                                    .build()
                    ));
        } else {
            userMap = new HashMap<>(); // 在else分支中初始化
        }

        // 6. 转换为 DTO
        List<PetListResponseDto> records = petPage.getRecords().stream()
                .map(pet -> {
                    UserSimpleDto user = userMap.get(pet.getUserId());
                    return PetListResponseDto.builder()
                            .id(pet.getId())
                            .type(pet.getType())
                            .title(pet.getTitle())
                            .petName(pet.getPetName())
                            .petType(pet.getPetType())
                            .petGender(pet.getPetGender())
                            .petAge(pet.getPetAge())
                            .images(pet.getImages())
                            .viewCount(pet.getViewCount())
                            .status(pet.getStatus())
                            .auditStatus(pet.getAuditStatus())
                            .createTime(pet.getCreateTime())
                            .user(user)
                            .commentCount(pet.getCommentCount())
                            .likeCount(pet.getLikeCount())
                            .shareCount(pet.getShareCount())
                            .build();
                })
                .collect(Collectors.toList());

        // 5. 返回分页结果
        Page<PetListResponseDto> resultPage = new Page<>(page.getCurrent(), page.getSize(), petPage.getTotal());
        resultPage.setRecords(records);

        return resultPage;
    }

    /**
     * 降级方法：返回空列表
     */
    public IPage<PetListResponseDto> fallbackGetPetList(PetQueryRequestDto queryDto) {
        log.warn("宠物列表熔断降级: pageNum={}, pageSize={}", queryDto.getPageNum(), queryDto.getPageSize());

        Page<PetListResponseDto> emptyPage = new Page<>(queryDto.getPageNum(), queryDto.getPageSize(), 0);
        emptyPage.setRecords(new ArrayList<>());
        return emptyPage;
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
//    @DistributedCacheable(
//            value = "pet",
//            key = "#petId",
//            ttl = 1800,
//            bloomFilter = false  // 不开启
//    )
    public PetDetailDto detail(Long id, Long currentUserId) {
        // 1. 查询宠物信息
        PetPost pet = petPostMapper.selectById(id);

        if (pet.getAuditStatus() == 2){
            throw new BusinessException(ErrorCode.AUDIT_REJECT);
        } else if (pet.getAuditStatus() == 0){
            throw new BusinessException(ErrorCode.AUDIT_WAITING);
        }

        if (pet == null) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        }

        // 2. 查询发布者信息
        User user = userMapper.selectById(pet.getUserId());

        UserSimpleDto userSimpleDto = UserSimpleDto.builder()
                .userId(user.getId())
                .username(user.getUserName())
                .nickname(user.getNickName())
                .avatar(user.getAvatar())
                .build();

        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getFollowerId, currentUserId);
        queryWrapper.eq(Follow::getFollowingId, pet.getUserId());
        Follow follow = followMapper.selectOne(queryWrapper);
        if (follow != null) {
            userSimpleDto.setIsFollow(true);
        }

        // 浏览次数增加（原子操作）
        if (pet.getStatus() == 1 && pet.getUserId() != currentUserId) {
            petPostMapper.incrementViewCount(id);
        }

        // 4. 构建详情DTO
        PetDetailDto detailDto = PetDetailDto.builder()
                .id(pet.getId())
                .type(pet.getType())
                .title(pet.getTitle())
                .content(pet.getContent())
                .images(pet.getImages())
                .petGender(pet.getPetGender())
                .petAge(pet.getPetAge())
                .petType(pet.getPetType())
                .petName(pet.getPetName())
                .contactPhone(pet.getContactPhone())
                .contactWechat(pet.getContactWechat())
                .address(pet.getAddress())
                .viewCount(pet.getViewCount())
                .status(pet.getStatus())
                .createTime(pet.getCreateTime())
                .updateTime(pet.getUpdateTime())
                .user(userSimpleDto)
                .shareCount(pet.getShareCount())
                .commentCount(pet.getCommentCount())
                .likeCount(pet.getLikeCount())
                .build();

        // 5. 获取互动数据
        if (currentUserId != null) {
            // 检查是否点赞
            log.info("currentUserId = {}" , currentUserId);
            LikeRecord likeRecord = likeRecordMapper.selectOne(new QueryWrapper<LikeRecord>().eq("user_id", currentUserId).eq("target_id", id).eq("target_type", CommentLikeTypes.PET_POST));
            detailDto.setIsLiked(likeRecord != null);

            log.info("currentUserId = {}" , currentUserId);
            // 检查是否收藏
            FavoriteRecord favoriteRecord = favoriteRecordMapper.selectOne(new QueryWrapper<FavoriteRecord>().eq("user_id", currentUserId).eq("target_id", id).eq("target_type", CommentLikeTypes.PET_POST));
            detailDto.setIsFavorite(favoriteRecord != null);
        }

        return detailDto;
    }

    /**
     * 降级方法
     */
    public PetDetailDto fallbackGetPetDetail(Long id, Long userId) {
        return fallbackGetPetDetail(id, userId, null);
    }

    public PetDetailDto fallbackGetPetDetail(Long id, Long userId, Exception e) {
        if (e != null) {
            log.error("宠物详情熔断降级: id={}, error={}", id, e.getMessage());
        }

        // 从缓存读取
        String cacheKey = "pet:fallback:" + id;
        PetDetailDto cached = (PetDetailDto) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 返回默认值
        return PetDetailDto.builder()
                .id(id)
                .title("宠物信息暂时不可用")
                .content("系统繁忙，请稍后再试")
                .status(0)
                .build();
    }

    @Override
    public PetListResponseDto update(PetPostRequestDto request) {
        Long userId = UserContext.getUserId();

        PetPost pet = petPostMapper.selectById(request.getId());

        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        }
        if(request.getTitle() != null){
            pet.setTitle(request.getTitle());
        }
        if(request.getContent() != null){
            pet.setContent(request.getContent());
        }
        if(request.getImages() != null){
            pet.setImages(request.getImages());
        }
        if(request.getPetGender() != null){
            pet.setPetGender(request.getPetGender());
        }
        if(request.getPetAge() != null){
            pet.setPetAge(request.getPetAge());
        }
        if(request.getPetType() != null){
            pet.setPetType(request.getPetType());
        }
        if(request.getPetName() != null){
            pet.setPetName(request.getPetName());
        }
        if(request.getContactPhone() != null){
            pet.setContactPhone(request.getContactPhone());
        }
        if(request.getContactWechat() != null){
            pet.setContactWechat(request.getContactWechat());
        }
        if(request.getAddress() != null){
            pet.setAddress(request.getAddress());
        }
        pet.setAuditStatus(0);
        petPostMapper.updateById(pet);

        cacheUpdateProducer.sendEvict("pet", String.valueOf(pet.getId()));

        return PetListResponseDto.builder()
                .id(pet.getId())
                .type(pet.getType())
                .title(pet.getTitle())
                .petName(pet.getPetName())
                .petType(pet.getPetType())
                .petAge(pet.getPetAge())
                .petGender(pet.getPetGender())
                .images(pet.getImages())
                .viewCount(pet.getViewCount())
                .build();
    }

    @Override
    public AvatarUploadResponse uploadImage(MultipartFile file) {
        Long userId = UserContext.getUserId();

        try{
            String newAvatarUrl = ossUtils.uploadAvatar(file, userId);
            log.info("图片上传成功: {}", newAvatarUrl);

            return AvatarUploadResponse.builder()
                    .avatarUrl(newAvatarUrl)
                    .message("图片上传成功")
                    .build();
        } catch (Exception e) {
            log.error("图片上传失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UPLOAD_FAIL);
        }

    }

    @Override
    public void delete(Long id) {
        QueryWrapper<PetPost> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        PetPost pet = petPostMapper.selectOne(queryWrapper);
        if (pet == null) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        }
        pet.setStatus(-1);
        petPostMapper.updateById(pet);
        cacheUpdateProducer.sendEvict("pet", String.valueOf(pet.getId()));
    }

    @Override
    public void offline(Long id) {
        QueryWrapper<PetPost> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        PetPost pet = petPostMapper.selectOne(queryWrapper);
        if (pet == null) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        }
        pet.setStatus(3);
        petPostMapper.updateById(pet);
    }

    @Override
    public void recover(Long id) {
        QueryWrapper<PetPost> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        PetPost pet = petPostMapper.selectOne(queryWrapper);
        if (pet == null) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        }
        pet.setStatus(1);
        petPostMapper.updateById(pet);
        cacheUpdateProducer.sendEvict("pet", String.valueOf(pet.getId()));
    }

    @Override
    @Transactional
    public void deleteReally(Long id) {

        Long userId = UserContext.getUserId();

        QueryWrapper<PetPost> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        PetPost pet = petPostMapper.selectOne(queryWrapper);

        if (!UserContext.isAdmin() && !pet.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (pet == null) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        }
        if(pet.getStatus() != -1){
            throw new BusinessException(ErrorCode.PET_STATUS_ERROR);
        }

        int result = petPostMapper.deleteById(id);
        if (result != 1) {
            throw new SystemException(ErrorCode.DB_ERROR);
        }

        // 2. 再删OSS（即使失败，数据库已删，可异步清理）
        try {
            deleteFromOss(pet.getImages());
        } catch (Exception e) {
            log.error("OSS删除失败，需要人工清理: {}", pet.getImages(), e);
            // 记录到失败队列，后续异步重试
        }
    }

    @Override
    public FavoriteResponseDto favorite(Long id) {
        QueryWrapper<PetPost> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        PetPost pet = petPostMapper.selectOne(queryWrapper);
        if (pet == null) {
            throw new BusinessException(ErrorCode.PET_NOT_FOUND);
        }
        FavoriteRecord favoriteRecord = favoriteRecordMapper.selectOne(new QueryWrapper<FavoriteRecord>().eq("user_id", UserContext.getUserId()).eq("target_id", id).eq("target_type", "pet_post"));
        if (favoriteRecord != null) {
            favoriteRecordMapper.deleteById(favoriteRecord);
            log.info("取消收藏成功");
            return FavoriteResponseDto.builder()
                    .isFavorited(false)
                    .build();
        }
        favoriteRecordMapper.insert(FavoriteRecord.builder()
                .userId(UserContext.getUserId())
                .targetId(id)
                .targetType("pet_post")
                .createTime(LocalDateTime.now())
                .build());
        log.info("收藏成功");
        return FavoriteResponseDto.builder()
                .isFavorited(true)
                .build();
    }

    @Override
    public IPage<PetListResponseDto> favoriteList(Integer pageNum, Integer pageSize) {
        Long userId = UserContext.getUserId();

        // 1. 分页查询收藏记录
        Page<FavoriteRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FavoriteRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FavoriteRecord::getUserId, userId)
                .orderByDesc(FavoriteRecord::getCreateTime);

        IPage<FavoriteRecord> favoritePage = favoriteRecordMapper.selectPage(page, wrapper);

        if (favoritePage.getRecords().isEmpty()) {
            Page<PetListResponseDto> emptyPage = new Page<>(pageNum, pageSize, 0);
            emptyPage.setRecords(new ArrayList<>());
            return emptyPage;
        }

        // 2. 提取宠物ID
        List<Long> petIds = favoritePage.getRecords().stream()
                .map(FavoriteRecord::getTargetId)
                .collect(Collectors.toList());

        // 3. 批量查询宠物信息
        List<PetPost> pets = petPostMapper.selectBatchIds(petIds);
        Map<Long, PetPost> petMap = pets.stream()
                .collect(Collectors.toMap(PetPost::getId, pet -> pet));

        // 4. 提取用户ID并批量查询用户信息
        List<Long> userIds = pets.stream()
                .map(PetPost::getUserId)
                .distinct()
                .collect(Collectors.toList());

        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, UserSimpleDto> userMap = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> UserSimpleDto.builder()
                                .userId(user.getId())
                                .username(user.getUserName())
                                .nickname(user.getNickName())
                                .avatar(user.getAvatar())
                                .build()
                ));

        // 5. 组装结果
        List<PetListResponseDto> petList = favoritePage.getRecords().stream()
                .map(record -> {
                    PetPost pet = petMap.get(record.getTargetId());
                    if (pet == null) return null;
                    UserSimpleDto userDto = userMap.get(pet.getUserId());
                    return PetListResponseDto.builder()
                            .id(pet.getId())
                            .type(pet.getType())
                            .title(pet.getTitle())
                            .petName(pet.getPetName())
                            .petType(pet.getPetType())
                            .petAge(pet.getPetAge())
                            .petGender(pet.getPetGender())
                            .images(pet.getImages())
                            .viewCount(pet.getViewCount())
                            .status(pet.getStatus())
                            .createTime(pet.getCreateTime())
                            .updateTime(pet.getUpdateTime())
                            .user(userDto)
                            .shareCount(pet.getShareCount())
                            .commentCount(pet.getCommentCount())
                            .likeCount(pet.getLikeCount())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 6. 按收藏顺序返回
        Page<PetListResponseDto> resultPage = new Page<>(pageNum, pageSize, favoritePage.getTotal());
        resultPage.setRecords(petList);
        return resultPage;
    }

    private void deleteFromOss(List<String> images) {
        if (images == null || images.isEmpty()) {
            return;
        }

        for (String image : images) {
            try {
                ossUtils.deleteFile(image);
            } catch (Exception e) {
                log.error("删除图片失败: {}", image, e);
                // 不抛出异常，继续删除其他图片
            }
        }
    }
}
