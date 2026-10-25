package com.hongjie.pms.modules.content.daily.application;
import com.hongjie.pms.modules.content.daily.api.service.DailyRecommendService;
import com.hongjie.pms.modules.content.daily.api.service.*;
import com.hongjie.pms.modules.content.daily.api.dto.DailyPostDto;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service @Primary
public class PmsDailyRecommendService implements DailyRecommendService {
    private final DailyRecommendation recommendation;private final DailyInterest interest;
    public PmsDailyRecommendService(DailyRecommendation recommendation, DailyInterest interest){
        this.recommendation=recommendation;this.interest=interest;
    }
    @Override public IPage<DailyPostDto> recommend(Long actor,Integer page,Integer size){return recommendation.recommend(actor,page,size);}
    @Override public void recordAction(Long actor,Long id,String type){interest.record(actor,id,type);}
}
