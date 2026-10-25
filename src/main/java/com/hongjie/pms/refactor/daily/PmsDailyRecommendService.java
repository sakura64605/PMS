package com.hongjie.pms.refactor.daily;
import com.hongjie.pms.modules.daily.service.*;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.daily.dto.DailyPostDto;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service @Primary
public class PmsDailyRecommendService extends DailyRecommendService {
    private final DailyRecommendation recommendation;private final DailyInterest interest;
    public PmsDailyRecommendService(DailyRecallService recall,DailyRankService rank,DailyPostMapper posts,DailyPostService service,
            DailyUserInterestService legacyInterest,RedisTemplate<String,Object> redis,DailyCacheInvalidator cache,
            DailyRecommendation recommendation,DailyInterest interest){
        super(recall,rank,posts,service,legacyInterest,redis,cache);this.recommendation=recommendation;this.interest=interest;
    }
    @Override public IPage<DailyPostDto> recommend(Long actor,Integer page,Integer size){return recommendation.recommend(actor,page,size);}
    @Override public void recordAction(Long actor,Long id,String type){interest.record(actor,id,type);}
}
