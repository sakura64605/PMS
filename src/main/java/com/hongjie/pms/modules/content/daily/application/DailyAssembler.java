package com.hongjie.pms.modules.content.daily.application;
import com.hongjie.pms.modules.content.daily.api.dto.*;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.api.dto.UserSimpleDto;
import com.hongjie.pms.modules.community.like.infrastructure.persistence.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.community.following.infrastructure.persistence.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component @RequiredArgsConstructor
public class DailyAssembler {
    private final UserMapper users;
    private final LikeRecordMapper likes;
    private final FollowMapper follows;
    private final DailyTopicRelMapper relations;
    private final TopicMapper topics;
    public List<DailyPostDto> batch(List<DailyPost> posts,Long actor) {
        if(posts==null||posts.isEmpty())return List.of();
        var ids=posts.stream().map(DailyPost::getId).distinct().toList();
        var authors=posts.stream().map(DailyPost::getUserId).distinct().toList();
        Map<Long,User> userMap=users.selectBatchIds(authors).stream().collect(Collectors.toMap(User::getId,user->user));
        Set<Long> liked=actor==null?Set.of():likes.selectByUserAndTargetIds(actor,ids,"daily").stream().map(record->record.getTargetId()).collect(Collectors.toSet());
        Set<Long> followed=actor==null?Set.of():new HashSet<>(follows.selectFollowedIds(actor,authors));
        var topicMap=topicMap(ids);
        return posts.stream().map(post->{
            User user=userMap.get(post.getUserId());
            var author=UserSimpleDto.builder().userId(post.getUserId()).username(user==null?"已注销":user.getUserName())
                    .nickname(user==null?"已注销":user.getNickName()).avatar(user==null?null:user.getAvatar())
                    .isFollow(followed.contains(post.getUserId())).build();
            return DailyPostDto.builder().id(post.getId()).content(post.getContent()).images(post.getImages()).videoUrl(post.getVideoUrl())
                    .location(post.getLocation()).user(author).viewCount(post.getViewCount()).likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount()).createTime(post.getCreateTime()).isLiked(liked.contains(post.getId()))
                    .isFollowed(followed.contains(post.getUserId())).topics(topicMap.getOrDefault(post.getId(),List.of())).build();
        }).toList();
    }
    public Map<Long,List<TopicDto>> topicMap(List<Long> ids) {
        if(ids.isEmpty())return Map.of();
        var links=relations.selectByDailyIds(ids);
        var topicIds=links.stream().map(DailyTopicRel::getTopicId).distinct().toList();
        if(topicIds.isEmpty())return Map.of();
        Map<Long,Topic> entities=topics.selectBatchIds(topicIds).stream().filter(topic->Objects.equals(topic.getStatus(),1))
                .collect(Collectors.toMap(Topic::getId,topic->topic));
        Map<Long,List<TopicDto>> result=new HashMap<>();
        links.stream().sorted(Comparator.comparing(DailyTopicRel::getTopicId)).forEach(link->{
            Topic topic=entities.get(link.getTopicId());
            if(topic!=null)result.computeIfAbsent(link.getDailyId(),ignored->new ArrayList<>()).add(TopicDto.builder().id(topic.getId())
                    .name(topic.getName()).description(topic.getDescription()).postCount(topic.getPostCount()).hotScore(topic.getHotScore()).build());
        });
        return result;
    }
}
