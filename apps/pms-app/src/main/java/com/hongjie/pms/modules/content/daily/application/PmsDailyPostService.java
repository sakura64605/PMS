package com.hongjie.pms.modules.content.daily.application;
import com.hongjie.pms.modules.content.daily.api.service.DailyPostService;
import com.hongjie.pms.modules.content.daily.api.service.*;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.entity.DailyPost;
import com.hongjie.pms.modules.content.daily.api.dto.*;
import com.hongjie.pms.modules.community.like.api.service.LikeService;
import com.hongjie.pms.modules.community.like.api.dto.request.LikeRequest;
import com.hongjie.pms.platform.base.core.UserContext;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.List;

/** Adapter for existing concrete-type injections; no public operation falls back to legacy business code. */
@Service @Primary
public class PmsDailyPostService implements DailyPostService {
    private final DailyCommands commands;private final DailyQueries queries;private final DailyAssembler assembler;
    private final DailyRecommendation recommendation;private final LikeService likes;
    public PmsDailyPostService(DailyCommands commands, DailyQueries queries, DailyAssembler assembler, DailyRecommendation recommendation, LikeService likes){

        this.commands=commands;this.queries=queries;this.assembler=assembler;this.recommendation=recommendation;this.likes=likes;
    }
    @Override public DailyPostDto publish(PublishDailyRequest request){return commands.publish(request);}
    @Override public DailyPostDto getDetail(Long id){return queries.detail(id);}
    @Override public void delete(Long id){commands.delete(id);}
    @Override public boolean like(Long id){var request=new LikeRequest();request.setTargetId(id);request.setTargetType("daily");return likes.like(request).getIsLiked();}
    @Override public IPage<DailyPostDto> getRecommendFeed(Integer page,Integer size){return recommendation.recommend(UserContext.getUserId(),page,size);}
    @Override public DailyPostDto convertToDto(DailyPost post,Long actor){return assembler.batch(List.of(post),actor).get(0);}
    @Override public List<DailyPostDto> batchConvertToDto(List<DailyPost> posts,Long actor){return assembler.batch(posts,actor);}
}
