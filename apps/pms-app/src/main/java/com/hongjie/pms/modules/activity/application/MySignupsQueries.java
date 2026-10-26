package com.hongjie.pms.modules.activity.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.activity.api.dto.request.MySignupsRequest;
import com.hongjie.pms.modules.activity.api.dto.response.MySignupResponse;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MySignupsQueries {
    private final MySignupsMapper signups;
    private final ActivityMapper activities;
    private final ActivityDtoAssembler assembler;

    public Map<String,Object> summary() { return signups.summary(actor(), LocalDateTime.now()); }

    @Transactional(readOnly = true)
    public IPage<MySignupResponse> list(MySignupsRequest input) {
        Long actor=actor();
        var request=input==null?new MySignupsRequest():input;
        LocalDateTime now=LocalDateTime.now();
        var query=new QueryWrapper<ActivitySignup>().eq("s.user_id",actor).in("s.status",List.of(1,3,4));
        if(request.getStatus()!=null) {
            if(!Set.of(0,1,2,3).contains(request.getStatus())) invalid();
            query.eq("a.status",request.getStatus());
        }
        if(request.getSignupStatus()!=null) {
            if(!Set.of(1,3,4).contains(request.getSignupStatus())) invalid();
            query.eq("s.status",request.getSignupStatus());
        }
        String range=request.getTimeRange();
        if(StringUtils.hasText(range) && !"all".equals(range)) {
            if("upcoming".equals(range)) query.gt("a.start_time",now).eq("a.deleted",0).eq("a.audit_status",1).eq("a.status",0);
            else if("past".equals(range)) query.and(q->q.eq("a.status",2).or().le("a.end_time",now));
            else invalid();
        }
        if(StringUtils.hasText(request.getKeyword())) {
            String keyword=request.getKeyword().trim();
            if(keyword.length()>100) invalid();
            query.and(q->q.like("a.title",keyword).or().like("a.location",keyword)
                .or().like("u.nick_name",keyword).or().like("u.user_name",keyword)
                .or().like("s.remark",keyword).or().like("s.real_name",keyword));
        }
        String sort=Objects.requireNonNullElse(request.getSort(),"startTime_asc");
        String column=switch(sort) {
            case "startTime_asc","startTime_desc" -> "a.start_time";
            case "signupTime_asc","signupTime_desc" -> "s.create_time";
            case "participants_desc" -> "a.current_people";
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR);
        };
        query.orderBy(true,sort.endsWith("_asc"),column).orderByDesc("s.id");
        IPage<ActivitySignup> page=signups.page(request.toPage(),query);
        if(page.getRecords().isEmpty()) return page.convert(s->null);
        var rows=activities.selectBatchIds(page.getRecords().stream().map(ActivitySignup::getActivityId).toList())
            .stream().collect(Collectors.toMap(Activity::getId,a->a));
        var users=assembler.users(rows.values().stream().map(Activity::getUserId).toList());
        var self=assembler.user(assembler.users(List.of(actor)),actor);
        return page.convert(s->{
            Activity a=rows.get(s.getActivityId());
            boolean available=Objects.equals(a.getDeleted(),0)&&Objects.equals(a.getAuditStatus(),1)&&Set.of(0,1,2).contains(a.getStatus());
            boolean cancel=Objects.equals(a.getDeleted(),0)&&!Set.of(1,2).contains(a.getStatus())&&a.getStartTime()!=null&&a.getStartTime().isAfter(now)&&Objects.equals(s.getStatus(),1);
            return new MySignupResponse(assembler.list(a,assembler.user(users,a.getUserId()),true),assembler.signup(s,self),s.getCheckInTime(),available,cancel);
        });
    }
    private Long actor() {
        Long id=UserContext.getUserId();
        if(id==null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return id;
    }
    private void invalid() { throw new BusinessException(ErrorCode.PARAM_ERROR); }
}
