package com.hongjie.pms.refactor.search;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.refactor.support.MyBatisTestSupport;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.petpost.entity.*;
import com.hongjie.pms.modules.petpost.mapper.*;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.apache.ibatis.session.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="PMS_SEARCH_MYSQL_TESTS",matches="true")
class SearchProjectionMySqlTest {
    static SqlSessionFactory factory;
    SqlSession session;
    UserMapper users;PetPostMapper pets;LikeRecordMapper likes;FavoriteRecordMapper favorites;ActivitySignupMapper signups;
    User owner,actor;PetPost post;
    @BeforeAll static void factory(){
        Assumptions.assumeTrue(MyBatisTestSupport.configured());
        factory=MyBatisTestSupport.createFactory(UserMapper.class,PetPostMapper.class,LikeRecordMapper.class,FavoriteRecordMapper.class,ActivitySignupMapper.class);
    }
    @BeforeEach void fixture(){
        session=factory.openSession(false);
        users=session.getMapper(UserMapper.class);pets=session.getMapper(PetPostMapper.class);likes=session.getMapper(LikeRecordMapper.class);
        favorites=session.getMapper(FavoriteRecordMapper.class);signups=session.getMapper(ActivitySignupMapper.class);
        owner=user("owner");actor=user("actor");
        post=PetPost.builder().userId(owner.getId()).type(0).title("cat old").content("safe body").status(1).auditStatus(1)
                .contactPhone("13800000000").address("search-test").petName("cat").petType("cat").petAge("1")
                .images(List.of("first.png","second.png")).likeCount(2).commentCount(0).viewCount(0).build();
        pets.insert(post);
    }
    User user(String suffix){
        User user=new User();user.setUserName("search_"+suffix+"_"+UUID.randomUUID().toString().substring(0,12));
        user.setPassword("test-only");user.setNickName("current nickname");user.setAvatar("current-avatar");users.insert(user);return user;
    }
    @AfterEach void rollback(){if(session!=null){session.rollback();session.close();}}
    PmsUnifiedSearchService service(){
        SearchBackend backend=mock(SearchBackend.class);
        when(backend.search(any())).thenReturn(new SearchBackend.Page(1,List.of(new SearchBackend.Candidate("pet",post.getId()))));
        var source=new SearchSources().petSearchSource(pets);
        var projection=new SearchProjection(new SearchEnrichment(users,likes,favorites,signups),List.of(source));
        return new PmsUnifiedSearchService(backend,projection);
    }
    @Test void unchangedCandidateGetsFreshContentCountsAndScopedInteractions(){
        likes.insert(LikeRecord.builder().userId(actor.getId()).targetType("pet_post").targetId(post.getId()).build());
        favorites.insert(FavoriteRecord.builder().userId(owner.getId()).targetType("pet_post").targetId(post.getId()).build());
        var service=service();
        var first=service.search("cat",1,20,null,null,actor.getId()).getItems().get(0);
        assertThat(first.getIsLiked()).isTrue();assertThat(first.getIsFavorite()).isFalse();
        assertThat(first.getImages()).containsExactly("first.png");assertThat(first.getUser().getNickname()).isEqualTo("current nickname");
        pets.update(null,new LambdaUpdateWrapper<PetPost>().eq(PetPost::getId,post.getId()).set(PetPost::getTitle,"cat NEW").set(PetPost::getLikeCount,9));
        var second=service.search("cat",1,20,null,null,actor.getId()).getItems().get(0);
        assertThat(second.getTitle()).isEqualTo("cat NEW");assertThat(second.getLikeCount()).isEqualTo(9);
    }
    @Test void indexSourceUsesBoundedKeysetAndReloadsCurrentRows(){
        var source=new com.hongjie.pms.refactor.search.index.SearchIndexSources().petIndexSource(pets,users);
        assertThat(source.maxId()).isGreaterThanOrEqualTo(post.getId());
        assertThat(source.ids(post.getId()-1,post.getId())).containsExactly(post.getId());
        var doc=source.load(List.of(post.getId())).get(post.getId());
        assertThat(doc.getId()).isEqualTo("pet_"+post.getId());
        assertThat(doc.getImages()).containsExactly("first.png","second.png");
        assertThat(doc.getUserName()).isEqualTo(owner.getUserName());
        pets.update(null,new LambdaUpdateWrapper<PetPost>().eq(PetPost::getId,post.getId()).set(PetPost::getAuditStatus,0));
        assertThat(source.load(List.of(post.getId())).get(post.getId()).getAuditStatus()).isZero();
        pets.update(null,new LambdaUpdateWrapper<PetPost>().eq(PetPost::getId,post.getId()).set(PetPost::getStatus,-1));
        assertThat(source.load(List.of(post.getId()))).isEmpty();
    }
    @Test void currentDatabaseAuditAndDeletionOverrideStaleApprovedCandidate(){
        var service=service();
        pets.update(null,new LambdaUpdateWrapper<PetPost>().eq(PetPost::getId,post.getId()).set(PetPost::getAuditStatus,0));
        assertThat(service.search("cat",1,20,null,null,actor.getId()).getItems()).isEmpty();
        assertThat(service.search("cat",1,20,null,null,owner.getId()).getItems()).hasSize(1);
        pets.update(null,new LambdaUpdateWrapper<PetPost>().eq(PetPost::getId,post.getId()).set(PetPost::getStatus,-1));
        assertThat(service.search("cat",1,20,null,null,owner.getId()).getItems()).isEmpty();
    }
}
