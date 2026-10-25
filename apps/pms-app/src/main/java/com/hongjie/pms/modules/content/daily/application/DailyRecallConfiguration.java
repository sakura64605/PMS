package com.hongjie.pms.modules.content.daily.application;
import org.springframework.context.annotation.*;
import java.util.*;
import java.util.function.BiFunction;

@Configuration(proxyBeanMethods=false)
public class DailyRecallConfiguration {
    @Bean DailyRecallSource interestRecall(DailyDataMapper data,DailyInterest interest){
        return source("interest",(actor,limit)->{var topics=interest.top(actor,3);return topics.isEmpty()?List.of():data.interested(topics,limit);});
    }
    @Bean DailyRecallSource followRecall(DailyDataMapper data){return source("follow",(actor,limit)->actor==null?List.of():data.followed(actor,limit));}
    @Bean DailyRecallSource hotRecall(DailyDataMapper data){return source("hot",(actor,limit)->data.hot(limit));}
    @Bean DailyRecallSource freshRecall(DailyDataMapper data){return source("fresh",(actor,limit)->data.fresh(limit));}
    private DailyRecallSource source(String name,BiFunction<Long,Integer,List<Long>> query){
        return new DailyRecallSource(){public String name(){return name;}public List<Long> recall(Long actor,int limit){return query.apply(actor,limit);}};
    }
}
