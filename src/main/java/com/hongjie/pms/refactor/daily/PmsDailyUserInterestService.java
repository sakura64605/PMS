package com.hongjie.pms.refactor.daily;
import com.hongjie.pms.refactor.daily.api.DailyUserInterestService;
import com.hongjie.pms.modules.daily.mapper.*;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @Primary
public class PmsDailyUserInterestService implements DailyUserInterestService {
    private final DailyInterest interest;
    public PmsDailyUserInterestService(DailyInterest interest){
        this.interest=interest;
    }
    @Override public void updateInterest(Long actor,Long post,String action){interest.record(actor,post,action);}
    @Override public Map<String,Double> getUserInterest(Long actor){return interest.read(actor);}
    @Override public List<Long> getTopTopics(Long actor,int limit){return interest.top(actor,limit);}
}
