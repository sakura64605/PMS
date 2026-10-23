package com.hongjie.pms.refactor.feed;

import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedAssemblerTest {
    @Test void overlappingIdsKeepDistinctSourcesAndLoadAuthorsOnce(){
        var pets=mock(PetPostMapper.class);var activities=mock(ActivityMapper.class);var users=mock(UserMapper.class);
        var pet=new PetPost();pet.setId(10L);pet.setTitle("pet");pet.setImages(List.of("pet-cover"));
        var activity=new Activity();activity.setId(10L);activity.setTitle("activity");activity.setImages(List.of("activity-cover"));
        var author=new User();author.setId(2L);author.setUserName("current-name");
        when(pets.selectBatchIds(any())).thenReturn(List.of(pet));when(activities.selectBatchIds(any())).thenReturn(List.of(activity));when(users.selectBatchIds(any())).thenReturn(List.of(author));
        var result=new FeedAssembler(pets,activities,users).assemble(List.of(FeedServiceTest.key("activity",10),FeedServiceTest.key("pet",10)));
        assertThat(result).extracting(row->row.getTitle()).containsExactly("activity","pet");assertThat(result).extracting(row->row.getCoverImage()).containsExactly("activity-cover","pet-cover");
        verify(users,times(1)).selectBatchIds(any());verify(users,never()).selectById(any());
    }
}
