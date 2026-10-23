package com.hongjie.pms.refactor.petpost;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.utils.OssUtils;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.petpost.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.feed.service.FeedService;
import com.hongjie.pms.modules.audit.service.AuditService;
import com.hongjie.pms.refactor.audit.AuditCommandService;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import com.hongjie.pms.refactor.petpost.service.PmsPetPostServiceImpl;
import com.hongjie.pms.refactor.petpost.entity.PmsPetPost;
import com.hongjie.pms.refactor.petpost.mapper.PmsPetPostMapper;
import com.hongjie.pms.refactor.petpost.converter.PetPostDtoConverter;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetReviewPreviewTest {
    PmsPetPostMapper posts;DistributedCache cache;PmsPetPostServiceImpl service;PmsPetPost post;
    @BeforeEach void setup(){posts=mock(PmsPetPostMapper.class);cache=mock(DistributedCache.class);post=new PmsPetPost();post.setId(10L);post.setUserId(1L);post.setType(0);post.setTitle("pending");post.setAuditStatus(0);post.setStatus(0);when(posts.selectById(10L)).thenReturn(post);
        service=new PmsPetPostServiceImpl(posts,new PetPostDtoConverter(),mock(OssUtils.class),mock(UserMapper.class),mock(LikeRecordMapper.class),mock(FavoriteRecordMapper.class),mock(FollowMapper.class),cache,new TargetTypeCodec(),mock(FeedService.class),mock(AuditService.class),mock(ApplicationEventPublisher.class),mock(AuditCommandService.class));
    }
    @AfterEach void clear(){UserContext.clear();}
    @Test void ownerCanPreviewPendingWithoutSharedCache(){UserContext.setUser(new UserInfo(1L,"owner",0,null,null));assertThat(service.detail(10L,1L).getAuditStatus()).isZero();verifyNoInteractions(cache);verify(posts,never()).incrementViewCount(anyLong());}
    @Test void adminCanPreviewPendingWithoutSharedCache(){UserContext.setUser(new UserInfo(2L,"admin",1,null,null));assertThat(service.detail(10L,2L).getTitle()).isEqualTo("pending");verifyNoInteractions(cache);}
    @Test void unrelatedUserCannotPreviewEvenIfSharedCacheExists(){UserContext.setUser(new UserInfo(3L,"other",0,null,null));assertThatThrownBy(()->service.detail(10L,3L)).isInstanceOf(BusinessException.class);verifyNoInteractions(cache);}
    @Test void anonymousCannotPreview(){assertThatThrownBy(()->service.detail(10L,null)).isInstanceOf(BusinessException.class);verifyNoInteractions(cache);}
    @Test void ownerCanPreviewRejectedContent(){post.setAuditStatus(2);UserContext.setUser(new UserInfo(1L,"owner",0,null,null));assertThat(service.detail(10L,1L).getAuditStatus()).isEqualTo(2);verifyNoInteractions(cache);}
}
