package com.hongjie.pms.refactor.daily;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.daily.dto.DailyPostDto;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.refactor.core.page.PageQuery;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class DailyRecommendation {
    private final List<DailyRecallSource> sources;
    private final DailyPostMapper posts;
    private final FollowMapper follows;
    private final DailyInterest interest;
    private final DailyAssembler assembler;
    public DailyRecommendation(List<DailyRecallSource> sources,DailyPostMapper posts,FollowMapper follows,DailyInterest interest,DailyAssembler assembler){
        Set<String> names=new HashSet<>();for(var source:sources)if(!names.add(source.name()))throw new IllegalStateException("Duplicate daily recall: "+source.name());
        this.sources=List.copyOf(sources);this.posts=posts;this.follows=follows;this.interest=interest;this.assembler=assembler;
    }
    public IPage<DailyPostDto> recommend(Long actor,Integer number,Integer size){
        if(actor!=null&&!Objects.equals(actor,UserContext.getUserId()))throw new BusinessException(ErrorCode.FORBIDDEN);
        var query=PageQuery.of(number==null?null:number.longValue(),size==null?20L:size.longValue());
        Set<Long> ids=new LinkedHashSet<>();for(var source:sources)ids.addAll(source.recall(actor,100));
        var result=new Page<DailyPostDto>(query.normalizedPageNum(),query.normalizedPageSize(),0);
        if(ids.isEmpty())return result;
        var candidates=posts.selectBatchIds(ids).stream().filter(post->Objects.equals(post.getStatus(),1)&&Objects.equals(post.getAuditStatus(),1)).toList();
        var topics=assembler.topicMap(candidates.stream().map(DailyPost::getId).toList());
        var profile=interest.read(actor);
        Set<Long> following=actor==null?Set.of():new HashSet<>(follows.getFollowingIds(actor));
        LocalDateTime now=LocalDateTime.now();
        Map<Long,Double> scores=new HashMap<>();
        for(var post:candidates){
            double match=topics.getOrDefault(post.getId(),List.of()).stream()
                    .mapToDouble(topic->profile.getOrDefault("topic_"+topic.getId(),0.0)).max().orElse(0.5);
            double hot=Math.log1p(Math.max(0,Objects.requireNonNullElse(post.getLikeCount(),0)))/Math.log(1000)*0.6
                    +Math.log1p(Math.max(0,Objects.requireNonNullElse(post.getViewCount(),0)))/Math.log(10000)*0.4;
            double age=post.getCreateTime()==null?24:Math.max(0,Duration.between(post.getCreateTime(),now).toMinutes()/60.0);
            scores.put(post.getId(),Math.min(match,1)*0.4+Math.min(hot,1)*0.3+Math.max(0,1-age/24)*0.2+(following.contains(post.getUserId())?0.1:0));
        }
        var ranked=candidates.stream().sorted(Comparator.<DailyPost>comparingDouble(post->scores.get(post.getId())).reversed()
                .thenComparing(DailyPost::getId,Comparator.reverseOrder())).toList();
        result.setTotal(ranked.size());
        long offset=(query.normalizedPageNum()-1)*query.normalizedPageSize();
        if(offset>=ranked.size())return result;
        var page=ranked.subList((int)offset,(int)Math.min(offset+query.normalizedPageSize(),ranked.size()));
        // Assemble live viewer state only after ranking; no DTO page is shared through Redis.
        result.setRecords(assembler.batch(page,actor));return result;
    }
}
