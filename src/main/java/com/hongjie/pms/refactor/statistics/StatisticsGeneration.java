package com.hongjie.pms.refactor.statistics;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.statistics.mapper.DailyStatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor @Slf4j
public class StatisticsGeneration {
    private final StatisticsDayWriter writer;
    private final StatisticsFactMapper facts;
    private final DailyStatisticsMapper rows;
    private final StatisticsCalendar calendar;
    @Value("${pms.statistics.compensation-lookback-days:90}") private int lookback=90;
    @Value("${pms.statistics.compensation-batch-days:31}") private int batch=31;
    public void daily(){writer.write(calendar.today().minusDays(1),false);}
    public void regenerate(LocalDate date){admin();writer.write(date,true);}
    public void regenerateRange(LocalDate start,LocalDate end){
        admin();StatisticsDates.range(start,end,366);
        if(!end.isBefore(calendar.today()))throw new BusinessException(ErrorCode.PARAM_ERROR,"补录范围只能包含已结束日期");
        run(start,end,true,null);
    }
    public void compensate(){
        LocalDate end=calendar.today().minusDays(1);
        LocalDate first=facts.earliestBusinessDate(),snapshot=facts.earliestSnapshot();
        if(first==null||snapshot!=null&&snapshot.isBefore(first))first=snapshot;
        if(first==null||first.isAfter(end))return;
        LocalDate minimum=end.minusDays(Math.max(1,Math.min(3660,lookback))-1);
        LocalDate start=first.isBefore(minimum)?minimum:first;
        var existing=rows.selectByDateRange(start,end).stream().map(row->row.getStatDate()).collect(Collectors.toSet());
        run(start,end,false,existing);
    }
    private void run(LocalDate start,LocalDate end,boolean force,Set<LocalDate> existing){
        int attempts=0,failures=0;
        for(LocalDate date=start;!date.isAfter(end);date=date.plusDays(1)){
            if(existing!=null&&existing.contains(date))continue;
            if(!force&&attempts>=Math.max(1,Math.min(366,batch)))break;
            attempts++;
            try{writer.write(date,force);}catch(RuntimeException failure){failures++;log.error("统计日期写入失败: date={}",date,failure);}
        }
        if(failures>0)throw new IllegalStateException("Statistics failed dates: "+failures+" of "+attempts+"; successful dates were committed");
    }
    private void admin(){if(!UserContext.isAdmin())throw new BusinessException(ErrorCode.FORBIDDEN);}
}
