package com.hongjie.pms.modules.community.comment.application;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.entity.Comment;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.mapper.CommentMapper;
import com.hongjie.pms.modules.community.comment.api.dto.request.CommentCreateRequest;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.modules.community.like.infrastructure.persistence.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.community.comment.application.service.PmsCommentServiceImpl;
import com.hongjie.pms.modules.community.comment.application.converter.CommentDtoConverter;
import com.hongjie.pms.modules.community.comment.api.event.CommentNotificationEvent;
import com.hongjie.pms.shared.core.event.DomainEventPublisher;
import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.extension.target.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CommentLinkTest {
    @ParameterizedTest @CsvSource({"pet_post,/pet/100","pet_activity,/activity/100","daily,/daily/100"})
    void replyNotificationContainsTargetAndNewCommentAnchor(String type,String detail){
        var comments=mock(CommentMapper.class);var events=mock(DomainEventPublisher.class);var target=mock(Commentable.class);
        when(target.ownerId(100L)).thenReturn(null);
        when(target.supportTypes()).thenReturn(Set.of(BizTargetType.resolve(type)));when(target.detailLink(100L)).thenReturn(detail);
        var parent=new Comment();parent.setUserId(2L);when(comments.selectById(5L)).thenReturn(parent);
        when(comments.insert(any(Comment.class))).thenAnswer(call->{((Comment)call.getArgument(0)).setId(7L);return 1;});
        var service=new PmsCommentServiceImpl(comments,mock(UserMapper.class),mock(LikeRecordMapper.class),
                new TargetHandlerRegistry(List.of(target)),new TargetTypeCodec(),events,new CommentDtoConverter());
        var request=new CommentCreateRequest();request.setTargetType(type);request.setTargetId(100L);request.setContent("reply");request.setParentId(5L);
        UserContext.setUser(new UserInfo(1L,"actor",0,null,null));
        try {
            service.createComment(request);
            var capture=org.mockito.ArgumentCaptor.forClass(CommentNotificationEvent.class);verify(events).publish(capture.capture());
            assertThat(capture.getValue().getLink()).isEqualTo(detail+"?commentId=7");
            verify(target).incrementCommentCount(100L);
        } finally {UserContext.clear();}
    }
}
