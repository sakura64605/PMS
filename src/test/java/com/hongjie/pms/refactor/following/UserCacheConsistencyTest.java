package com.hongjie.pms.refactor.following;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.common.utils.*;
import com.hongjie.pms.modules.following.entity.Follow;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.user.entity.*;
import com.hongjie.pms.modules.user.mapper.*;
import com.hongjie.pms.modules.user.service.Impl.*;
import com.hongjie.pms.modules.user.dto.request.UserUpdateRequestDto;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.redisson.api.RBloomFilter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class UserCacheConsistencyTest {
    UserMapper users=mock(UserMapper.class); FollowMapper follows=mock(FollowMapper.class);
    AvatarHistoryMapper histories=mock(AvatarHistoryMapper.class); DistributedCache cache=mock(DistributedCache.class);
    OssUtils oss=mock(OssUtils.class); UserServiceImpl service; AvatarServiceImpl avatars; User owner;
    @BeforeAll static void metadata(){ for(Class<?> t:List.of(User.class,Follow.class,AvatarHistory.class))
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"user-cache-test"),t); }
    @BeforeEach @SuppressWarnings("unchecked") void setup(){
        service=new UserServiceImpl(users,mock(JWTUtils.class),histories,follows,cache,mock(RBloomFilter.class));
        avatars=new AvatarServiceImpl(cache,oss,users,histories);
        owner=new User(); owner.setId(10L); owner.setUserName("owner"); owner.setAvatar("old");
        owner.setPhone("123"); owner.setEmail("owner@example.com"); owner.setTags(List.of("tag"));
        owner.setPrivacySettings(Map.of("phone",true,"email",true,"tags",true)); owner.setFollowerCount(1);
        when(users.selectById(10L)).thenReturn(owner); UserContext.setUser(new UserInfo(1L,"A",0,null,null));
    }
    @AfterEach void clear(){UserContext.clear();TransactionSynchronizationManager.clear();}
    @Test void viewersAndAnonymousHaveIndependentStateAndIgnoreCache(){
        when(follows.selectOne(any())).thenReturn(new Follow(),(Follow)null);
        assertThat(service.getUserProfileInfo(10L).getUser().getIsFollow()).isTrue();
        UserContext.setUser(new UserInfo(2L,"B",0,null,null));
        assertThat(service.getUserProfileInfo(10L).getUser().getIsFollow()).isFalse();
        UserContext.clear(); assertThat(service.getUserProfileInfo(10L).getUser().getIsFollow()).isFalse();
        verify(follows,times(2)).selectOne(any()); verify(users,times(3)).selectById(10L); verifyNoInteractions(cache);
    }
    @Test void freshPrivacyAvatarCountersAndFollowOnNextRequest(){
        when(follows.selectOne(any())).thenReturn(new Follow(),(Follow)null);
        var before=service.getUserProfileInfo(10L); assertThat(before.getPhone()).isEqualTo("123");
        owner.setPrivacySettings(Map.of("phone",false));owner.setAvatar("new");owner.setFollowerCount(7);
        var after=service.getUserProfileInfo(10L);
        assertThat(after.getPhone()).isNull();assertThat(after.getEmail()).isNull();assertThat(after.getTags()).isNull();
        assertThat(after.getUser().getAvatar()).isEqualTo("new");assertThat(after.getFollowerCount()).isEqualTo(7);
        assertThat(after.getUser().getIsFollow()).isFalse();assertThat(before.getUser().getAvatar()).isEqualTo("old");verifyNoInteractions(cache);
    }
    @Test void nullPrivacyAndMissingFlagsArePrivate(){
        owner.setPrivacySettings(null);var p=service.getUserProfileInfo(10L);
        assertThat(p.getPhone()).isNull();assertThat(p.getEmail()).isNull();assertThat(p.getTags()).isNull();
        var map=new HashMap<String,Boolean>();map.put("phone",null);owner.setPrivacySettings(map);
        assertThat(service.getUserProfileInfo(10L).getPhone()).isNull();
    }
    @Test void missingUserAndInvalidIdsAreControlledErrors(){
        assertThatThrownBy(()->service.getUserProfileInfo(99L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.getUserProfileInfo(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.getUserProfileInfo(0L)).isInstanceOf(BusinessException.class);verifyNoInteractions(cache,follows);
    }
    void begin(){TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();}
    void commit(){verifyNoInteractions(cache);TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);}
    @Test void profileEditEvictsAfterCommit(){
        when(users.update(any(User.class),any())).thenReturn(1);begin();service.updateUserInfo(10L,UserUpdateRequestDto.builder().build());commit();verify(cache).delete("user_10");
    }
    @Test void avatarUploadEvictsAfterCommit(){
        UserContext.setUser(new UserInfo(10L,"owner",0,null,null));when(oss.uploadAvatar(any(),eq(10L))).thenReturn("new");when(histories.selectList(any())).thenReturn(List.of());
        begin();avatars.uploadAvatar(new MockMultipartFile("file",new byte[]{1}));commit();verify(cache).delete("user_10");
    }
    @Test void historySwitchEvictsAfterCommit(){
        UserContext.setUser(new UserInfo(10L,"owner",0,null,null));var h=new AvatarHistory();h.setId(5L);h.setUserId(10L);h.setAvatarUrl("history");when(histories.selectById(5L)).thenReturn(h);
        begin();assertThat(avatars.switchToHistoryAvatar(5L)).isEqualTo("history");commit();verify(cache).delete("user_10");
    }
    @Test void rolledBackAvatarDoesNotEvict(){
        UserContext.setUser(new UserInfo(10L,"owner",0,null,null));when(oss.uploadAvatar(any(),eq(10L))).thenReturn("new");when(histories.selectList(any())).thenReturn(List.of());
        begin();avatars.uploadAvatar(new MockMultipartFile("file",new byte[]{1}));TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));verifyNoInteractions(cache);
    }
}
