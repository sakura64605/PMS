package com.hongjie.pms.refactor.search;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.search.dto.SearchResultItem;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.petpost.entity.FavoriteRecord;
import com.hongjie.pms.modules.petpost.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.activity.entity.ActivitySignup;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchEnrichmentTest {
    UserMapper users=mock(UserMapper.class);LikeRecordMapper likes=mock(LikeRecordMapper.class);
    FavoriteRecordMapper favorites=mock(FavoriteRecordMapper.class);ActivitySignupMapper signups=mock(ActivitySignupMapper.class);
    SearchEnrichment enrichment=new SearchEnrichment(users,likes,favorites,signups);
    @BeforeAll static void metadata(){
        for(Class<?> type:List.of(LikeRecord.class,FavoriteRecord.class,ActivitySignup.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"search-test"),type);
    }
    SearchResultItem item(String type,long id){
        return SearchResultItem.builder().type(type).id(id).user(UserSimpleDto.builder().userId(8L).build())
                .isLiked(false).isFavorite(false).isSignedUp(0).build();
    }
    @Test void batchesCurrentAuthorsLikesFavoritesAndSignupsWithoutCrossTypeCollision(){
        var author=new User();author.setId(8L);author.setUserName("newName");author.setNickName("newNick");author.setAvatar("newAvatar");
        when(users.selectBatchIds(List.of(8L))).thenReturn(List.of(author));
        when(likes.selectList(any())).thenAnswer(inv->{
            LambdaQueryWrapper<LikeRecord> query=inv.getArgument(0);
            assertThat(query.getSqlSegment()).contains("user_id","target_id","target_type");
            assertThat(query.getParamNameValuePairs().values()).contains(7L,"pet_post","daily","pet_activity");
            return List.of(LikeRecord.builder().userId(7L).targetId(1L).targetType("daily").build());
        });
        when(favorites.selectList(any())).thenReturn(List.of(FavoriteRecord.builder().userId(7L).targetId(1L).targetType("pet_post").build()));
        ActivitySignup signed=new ActivitySignup();signed.setActivityId(1L);signed.setStatus(3);
        when(signups.selectList(any())).thenReturn(List.of(signed));
        var pet=item("pet",1);var daily=item("daily",1);var activity=item("activity",1);
        enrichment.enrich(List.of(pet,daily,activity),7L);
        assertThat(pet.getIsLiked()).isFalse();assertThat(daily.getIsLiked()).isTrue();
        assertThat(pet.getIsFavorite()).isTrue();assertThat(activity.getIsSignedUp()).isEqualTo(1);
        assertThat(pet.getUser().getNickname()).isEqualTo("newNick");
        verify(users).selectBatchIds(anyCollection());verify(likes).selectList(any());verify(favorites).selectList(any());verify(signups).selectList(any());
    }
    @Test void anonymousDoesNotQueryPersonalRecordsAndMissingAuthorUsesPlaceholder(){
        when(users.selectBatchIds(anyCollection())).thenReturn(List.of());var item=item("pet",1);
        enrichment.enrich(List.of(item),null);assertThat(item.getUser().getUsername()).isEqualTo("已注销");
        verifyNoInteractions(likes,favorites,signups);
    }
    @Test void cancelledSignupIsNotSignedAndEmptyGroupsDoNotQuery(){
        when(users.selectBatchIds(anyCollection())).thenReturn(List.of());when(likes.selectList(any())).thenReturn(List.of());
        ActivitySignup cancelled=new ActivitySignup();cancelled.setActivityId(1L);cancelled.setStatus(2);
        when(signups.selectList(any())).thenReturn(List.of(cancelled));var item=item("activity",1);
        enrichment.enrich(List.of(item),7L);
        assertThat(item.getIsSignedUp()).isZero();verifyNoInteractions(favorites);
    }
    @Test void emptyResultsDoNotQueryDatabase(){enrichment.enrich(List.of(),7L);verifyNoInteractions(users,likes,favorites,signups);}
}
