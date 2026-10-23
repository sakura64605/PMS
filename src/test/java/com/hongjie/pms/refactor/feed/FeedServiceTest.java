package com.hongjie.pms.refactor.feed;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.feed.dto.FeedDto;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import com.hongjie.pms.refactor.following.FollowInboxWriter;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedServiceTest {
    FeedReadMapper reads;FeedWriteMapper writes;FeedAssembler assembler;FollowInboxWriter history;UserMapper users;PmsFeedServiceImpl service;
    @BeforeEach void setup(){
        UserContext.setUser(new UserInfo(1L,"viewer",0,null,null));reads=mock(FeedReadMapper.class);writes=mock(FeedWriteMapper.class);assembler=mock(FeedAssembler.class);history=mock(FollowInboxWriter.class);users=mock(UserMapper.class);
        service=new PmsFeedServiceImpl(reads,writes,assembler,history,users,new TargetTypeCodec());when(writes.lockAuthor(1L)).thenReturn(1L);when(writes.lockAuthor(2L)).thenReturn(2L);
    }
    @AfterEach void clear(){UserContext.clear();}
    static FeedKey key(String type,long id){var key=new FeedKey();key.setPostType(type);key.setPostId(id);key.setPosterId(2L);return key;}
    @Test void onlyActuallyReturnedKeysAreMarkedRead(){
        var pet=key("pet",10);var activity=key("activity",10);when(reads.total(1L)).thenReturn(2L);when(reads.page(1L,0,20)).thenReturn(List.of(pet,activity));
        when(assembler.assemble(any())).thenReturn(List.of(FeedDto.builder().postId(10L).postType("pet").build()));
        var result=service.getHomeFeed(null,null);assertThat(result.getTotal()).isEqualTo(2);verify(reads).markRead(1L,List.of(pet));
    }
    @Test void outOfRangePageKeepsTotalAndDoesNotLoadOrMark(){
        when(reads.total(1L)).thenReturn(2L);var result=service.getHomeFeed(2,20);assertThat(result.getTotal()).isEqualTo(2);assertThat(result.getRecords()).isEmpty();verifyNoInteractions(assembler);verify(reads,never()).markRead(anyLong(),any());
    }
    @Test void pageSizeIsCapped(){when(reads.total(1L)).thenReturn(500L);when(reads.page(1L,100,100)).thenReturn(List.of());when(assembler.assemble(any())).thenReturn(List.of());service.getHomeFeed(2,10000);verify(reads).page(1L,100,100);}
    @Test void anonymousReadIsRejected(){UserContext.clear();assertThatThrownBy(()->service.getHomeFeed(1,20)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.getUnreadCount()).isInstanceOf(BusinessException.class);verifyNoInteractions(reads);}
    @Test void petAliasesWriteCanonicalInboxType(){
        when(writes.fans(2L)).thenReturn(1);var time=LocalDateTime.now();service.pushToFans(2L,10L,"ADOPT","title",List.of("cover"),"author","avatar",time);
        var order=inOrder(writes);order.verify(writes).lockAuthor(2L);order.verify(writes).fans(2L);order.verify(writes).mode(2L,1,0);order.verify(writes).fanOut(2L,10L,"pet","author","avatar","title","cover",time);
    }
    @Test void bigAuthorsUsePullModeWithoutFanOut(){when(writes.fans(2L)).thenReturn(1001);service.pushToFans(2L,10L,"activity","title",null,"author",null,null);verify(writes).mode(2L,1001,1);verify(writes,never()).fanOut(anyLong(),anyLong(),anyString(),any(),any(),any(),any(),any());}
    @Test void unsupportedTargetsCannotCreateProjection(){assertThatThrownBy(()->service.pushToFans(2L,10L,"user","title",null,null,null,null)).isInstanceOf(BusinessException.class);verifyNoInteractions(writes);}
    @Test void legacyHistoryEntrypointUsesSharedWriterAndHonorsLimit(){var author=new User();author.setId(2L);when(users.selectById(2L)).thenReturn(author);service.pullHistoryPosts(1L,2L,3);verify(history).populate(1L,author,3);}
}
