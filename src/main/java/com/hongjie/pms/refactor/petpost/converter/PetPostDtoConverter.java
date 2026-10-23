package com.hongjie.pms.refactor.petpost.converter;

import com.hongjie.pms.refactor.core.converter.EntityConverter;
import com.hongjie.pms.modules.petpost.dto.PetDetailDto;
import com.hongjie.pms.modules.petpost.dto.response.PetListResponseDto;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.refactor.petpost.dto.PetPostWithAuthor;
import com.hongjie.pms.refactor.petpost.entity.PmsPetPost;
import org.springframework.stereotype.Component;

/**
 * 新模型 -> 对外 DTO 的显式转换器。
 *
 * <p>替代老实现里出现多次的
 * {@code PetListResponseDto.builder().id(...).type(...)....build()} 手写装配。
 * 与那些手写装配相比，这里的差别不只是「写一次」：转换是纯函数、可逐字段断言、
 * 且实体新增字段不会自动泄漏到响应里（出参是显式白名单）。
 *
 * <p><b>对外字段名保持不变</b>：DTO 里的 {@code petType} / {@code petAge} 继续沿用，
 * 尽管实体侧已经改成语义正确的 {@code petBreed} / {@code petAgeText}。
 * 前端不需要做任何改动。
 *
 * <p><b>相对老实现补齐了两个字段</b>（不是改结构，是把已有字段填上）：
 * <ul>
 *   <li>{@code petCategory} —— 老 {@code list()} 与 {@code detail()} 都没设置它，
 *       导致列表/详情里这个字段恒为 null，前端拿不到物种分类；</li>
 *   <li>{@code updateTime} —— 老 {@code list()} 同样漏了它。</li>
 * </ul>
 */
@Component
public class PetPostDtoConverter implements EntityConverter<PetPostWithAuthor, PetListResponseDto> {

    @Override
    public PetListResponseDto toView(PetPostWithAuthor source) {
        PmsPetPost post = source.post();
        return PetListResponseDto.builder()
                .id(post.getId())
                .type(post.getType())
                .title(post.getTitle())
                .petName(post.getPetName())
                .petType(post.getPetBreed())
                .petAge(post.getPetAgeText())
                .petGender(post.getPetGender())
                .petCategory(post.getPetCategory())
                .images(post.getImages())
                .viewCount(post.getViewCount())
                .status(post.getStatus())
                .auditStatus(post.getAuditStatus())
                .createTime(post.getCreateTime())
                .updateTime(post.getUpdateTime())
                .user(source.author())
                .shareCount(post.getShareCount())
                .commentCount(post.getCommentCount())
                .likeCount(post.getLikeCount())
                .build();
    }

    /** 详情 DTO：比列表多出正文与联系方式，并带上当前用户的点赞/收藏态 */
    public PetDetailDto toDetail(PetPostWithAuthor source) {
        PmsPetPost post = source.post();
        return PetDetailDto.builder()
                .id(post.getId())
                .type(post.getType())
                .title(post.getTitle())
                .content(post.getContent())
                .images(post.getImages())
                .petGender(post.getPetGender())
                .petAge(post.getPetAgeText())
                .petType(post.getPetBreed())
                .petCategory(post.getPetCategory())
                .petName(post.getPetName())
                .contactPhone(post.getContactPhone())
                .contactWechat(post.getContactWechat())
                .address(post.getAddress())
                .viewCount(post.getViewCount())
                .status(post.getStatus())
                .createTime(post.getCreateTime())
                .updateTime(post.getUpdateTime())
                .user(source.author())
                .shareCount(post.getShareCount())
                .commentCount(post.getCommentCount())
                .likeCount(post.getLikeCount())
                .isLiked(source.likedByViewer())
                .isFavorite(source.favoritedByViewer())
                .build();
    }


    /**
     * 新模型 -> 老实体投影。
     *
     * <p><b>迁移期的适配器</b>：老事件 {@code PetPostPublishedEvent} 的字段类型是
     * 老实体 {@code PetPost}（它被 ES 同步监听器消费）。本轮不换表，
     * 两者指向的是同一张物理表，所以做一次字段投影就能继续复用这条链路。
     *
     * <p>等到 search 模块也重构完、事件改成携带新模型时，这个方法即可删除。
     * 让它单独存在而不是散在 Service 里，是为了明确标记「这是临时适配」。
     */
    public PetPost toLegacyEntity(PmsPetPost post) {
        PetPost legacy = new PetPost();
        legacy.setId(post.getId());
        legacy.setUserId(post.getUserId());
        legacy.setType(post.getType());
        legacy.setTitle(post.getTitle());
        legacy.setContent(post.getContent());
        legacy.setImages(post.getImages());
        legacy.setPetGender(post.getPetGender());
        legacy.setPetAge(post.getPetAgeText());
        legacy.setPetType(post.getPetBreed());
        legacy.setPetCategory(post.getPetCategory());
        legacy.setPetName(post.getPetName());
        legacy.setContactPhone(post.getContactPhone());
        legacy.setContactWechat(post.getContactWechat());
        legacy.setAddress(post.getAddress());
        legacy.setStatus(post.getStatus());
        legacy.setAuditStatus(post.getAuditStatus());
        legacy.setViewCount(post.getViewCount());
        legacy.setLikeCount(post.getLikeCount());
        legacy.setCommentCount(post.getCommentCount());
        legacy.setShareCount(post.getShareCount());
        legacy.setCreateTime(post.getCreateTime());
        legacy.setUpdateTime(post.getUpdateTime());
        return legacy;
    }
}
