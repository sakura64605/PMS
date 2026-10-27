package com.hongjie.pms.modules.governance.report.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hongjie.pms.modules.scheduling.application.DelayTaskService;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.entity.Comment;
import com.hongjie.pms.modules.community.comment.infrastructure.persistence.mapper.CommentMapper;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ReportTargetsTest {
    @BeforeAll
    static void metadata() {
        for (var type : new Class<?>[] {Activity.class, User.class, Comment.class})
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), "report-target-test"),
                    type);
    }

    @Test
    void activityUsesDefinedOfflineStatus() {
        var mapper = mock(ActivityMapper.class);
        var locks = mock(ReportWriteMapper.class);
        new ReportTargets().activityReportTarget(mapper, locks).accept(10L);
        @SuppressWarnings("rawtypes")
        var wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), wrapper.capture());
        wrapper.getValue().getSqlSet();
        assertThat(wrapper.getValue().getParamNameValuePairs())
                .containsValue(3)
                .doesNotContainValue(4);
    }

    @Test
    void commentPunishmentIsPersistedAndDoesNotShortenExistingMute() {
        var comments = mock(CommentMapper.class);
        var users = mock(UserMapper.class);
        var locks = mock(ReportWriteMapper.class);
        var tasks = mock(DelayTaskService.class);
        var comment = new Comment();
        comment.setUserId(2L);
        when(comments.selectById(10L)).thenReturn(comment);
        when(comments.update(isNull(), any())).thenReturn(1);
        var until = LocalDateTime.now().plusDays(3);
        var user = new User();
        user.setId(2L);
        user.setMuteEndTime(until);
        when(locks.lockUser(2L)).thenReturn(user);
        new ReportTargets().commentReportTarget(comments, users, locks, tasks).accept(10L);
        verify(users)
                .update(
                        isNull(),
                        argThat(
                                wrapper ->
                                        ((LambdaUpdateWrapper<?>) wrapper)
                                                .getSqlSet()
                                                .contains("is_muted")));
        verify(tasks).addTask("MUTED_PUNISHMENT_END", 2L, until);
    }

    @Test
    void alreadyRemovedCommentDoesNotRepeatPunishment() {
        var comments = mock(CommentMapper.class);
        var users = mock(UserMapper.class);
        var locks = mock(ReportWriteMapper.class);
        var tasks = mock(DelayTaskService.class);
        when(comments.selectById(10L)).thenReturn(new Comment());
        new ReportTargets().commentReportTarget(comments, users, locks, tasks).accept(10L);
        verifyNoInteractions(users, locks, tasks);
    }
}
