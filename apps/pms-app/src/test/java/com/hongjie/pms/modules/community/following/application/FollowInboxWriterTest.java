package com.hongjie.pms.modules.community.following.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.community.feed.infrastructure.persistence.entity.UserInbox;
import com.hongjie.pms.modules.community.feed.infrastructure.persistence.mapper.UserInboxMapper;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.PetPostMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FollowInboxWriterTest {
    @BeforeAll
    static void metadata() {
        for (Class<?> type : List.of(UserInbox.class, PetPost.class, Activity.class))
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), "inbox-test"), type);
    }

    @Test
    void historyUsesCorrectSourceTitleCoverTypeAndCombinedLimit() {
        var inbox = mock(UserInboxMapper.class);
        var pets = mock(PetPostMapper.class);
        var activities = mock(ActivityMapper.class);
        var time = LocalDateTime.of(2026, 1, 1, 0, 0);
        List<PetPost> posts = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            var p = new PetPost();
            p.setId((long) i);
            p.setType(i % 2);
            p.setTitle("pet" + i);
            p.setImages(List.of("pet-cover"));
            p.setCreateTime(time.plusMinutes(i));
            posts.add(p);
        }
        var activity = new Activity();
        activity.setId(999L);
        activity.setTitle("activity");
        activity.setImages(List.of("activity-cover"));
        activity.setCreateTime(time.plusDays(1));
        when(pets.selectList(any())).thenReturn(posts);
        when(activities.selectList(any())).thenReturn(List.of(activity));
        new FollowInboxWriter(inbox, pets, activities).populate(2L, FollowServiceTest.user(1));
        var captured = ArgumentCaptor.forClass(UserInbox.class);
        verify(inbox, times(20)).insert(captured.capture());
        var rows = captured.getAllValues();
        assertThat(rows.get(0).getPostType()).isEqualTo("activity");
        assertThat(rows.get(0).getCoverImage()).isEqualTo("activity-cover");
        assertThat(rows.get(1).getTitle()).isEqualTo("pet20");
        assertThat(rows.get(1).getPostType()).isEqualTo("pet");
        assertThat(rows.get(2).getPostType()).isEqualTo("pet");
        assertThat(rows.get(1).getCoverImage()).isEqualTo("pet-cover");
        verify(pets, never()).selectById(any());
        verify(activities, never()).selectById(any());
    }
}
