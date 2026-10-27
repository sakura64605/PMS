package com.hongjie.pms.modules.content.petpost.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.cache.DistributedCache;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.platform.utils.OssUtils;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.modules.community.like.infrastructure.persistence.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.community.following.infrastructure.persistence.mapper.FollowMapper;
import com.hongjie.pms.modules.community.feed.api.service.FeedService;
import com.hongjie.pms.modules.governance.audit.api.service.AuditService;
import com.hongjie.pms.modules.governance.audit.application.AuditCommandService;
import com.hongjie.pms.shared.extension.target.TargetTypeCodec;
import com.hongjie.pms.modules.content.petpost.application.service.PmsPetPostServiceImpl;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PmsPetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PmsPetPostMapper;
import com.hongjie.pms.modules.content.petpost.application.converter.PetPostDtoConverter;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetReviewPreviewTest {
    PmsPetPostMapper posts;
    DistributedCache cache;
    PmsPetPostServiceImpl service;
    PmsPetPost post;

    @BeforeEach
    void setup() {
        posts = mock(PmsPetPostMapper.class);
        cache = mock(DistributedCache.class);
        post = new PmsPetPost();
        post.setId(10L);
        post.setUserId(1L);
        post.setType(0);
        post.setTitle("pending");
        post.setAuditStatus(0);
        post.setStatus(0);
        when(posts.selectById(10L)).thenReturn(post);
        service =
                new PmsPetPostServiceImpl(
                        posts,
                        new PetPostDtoConverter(),
                        mock(OssUtils.class),
                        mock(UserMapper.class),
                        mock(LikeRecordMapper.class),
                        mock(FavoriteRecordMapper.class),
                        mock(FollowMapper.class),
                        cache,
                        new TargetTypeCodec(),
                        mock(FeedService.class),
                        mock(AuditService.class),
                        mock(ApplicationEventPublisher.class),
                        mock(AuditCommandService.class));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void ownerCanPreviewPendingWithoutSharedCache() {
        UserContext.setUser(new UserInfo(1L, "owner", 0, null, null));
        assertThat(service.detail(10L, 1L).getAuditStatus()).isZero();
        verifyNoInteractions(cache);
        verify(posts, never()).incrementViewCount(anyLong());
    }

    @Test
    void adminCanPreviewPendingWithoutSharedCache() {
        UserContext.setUser(new UserInfo(2L, "admin", 1, null, null));
        assertThat(service.detail(10L, 2L).getTitle()).isEqualTo("pending");
        verifyNoInteractions(cache);
    }

    @Test
    void unrelatedUserCannotPreviewEvenIfSharedCacheExists() {
        UserContext.setUser(new UserInfo(3L, "other", 0, null, null));
        assertThatThrownBy(() -> service.detail(10L, 3L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(cache);
    }

    @Test
    void anonymousCannotPreview() {
        assertThatThrownBy(() -> service.detail(10L, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(cache);
    }

    @Test
    void ownerCanPreviewRejectedContent() {
        post.setAuditStatus(2);
        UserContext.setUser(new UserInfo(1L, "owner", 0, null, null));
        assertThat(service.detail(10L, 1L).getAuditStatus()).isEqualTo(2);
        verifyNoInteractions(cache);
    }

    @Test
    void cachedDetailUsesCurrentCountersAndResetsAnonymousViewerState() {
        post.setAuditStatus(1);
        post.setStatus(1);
        post.setLikeCount(4);
        post.setCommentCount(3);
        var cached =
                com.hongjie.pms.modules.content.petpost.api.dto.PetDetailDto.builder()
                        .id(10L)
                        .title("cached")
                        .likeCount(1)
                        .commentCount(0)
                        .isLiked(true)
                        .isFavorite(true)
                        .build();
        when(cache.get(
                        "pet_10",
                        com.hongjie.pms.modules.content.petpost.api.dto.PetDetailDto.class))
                .thenReturn(cached);
        var result = service.detail(10L, null);
        assertThat(result.getLikeCount()).isEqualTo(4);
        assertThat(result.getCommentCount()).isEqualTo(3);
        assertThat(result.getIsLiked()).isNull();
        assertThat(result.getIsFavorite()).isNull();
    }

    @Test
    void staleDetailVersionIsRebuiltFromCurrentRow() {
        post.setAuditStatus(1);
        post.setStatus(1);
        post.setTitle("new title");
        post.setUpdateTime(java.time.LocalDateTime.of(2026, 10, 24, 12, 0));
        var cached =
                com.hongjie.pms.modules.content.petpost.api.dto.PetDetailDto.builder()
                        .id(10L)
                        .title("old title")
                        .updateTime(post.getUpdateTime().minusMinutes(1))
                        .build();
        when(cache.get(
                        "pet_10",
                        com.hongjie.pms.modules.content.petpost.api.dto.PetDetailDto.class))
                .thenReturn(cached);
        assertThat(service.detail(10L, null).getTitle()).isEqualTo("new title");
    }
}
