package com.hongjie.pms.modules.content.daily.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.content.daily.api.dto.DailyPostDto;
import com.hongjie.pms.modules.content.daily.infrastructure.persistence.mapper.DailyPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DailyQueries {
    private final DailyPostMapper posts;
    private final DailyDataMapper writes;
    private final DailyAssembler assembler;

    @Transactional
    public DailyPostDto detail(Long id) {
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR);
        var post = posts.selectById(id);
        if (post == null || !Objects.equals(post.getStatus(), 1))
            throw new BusinessException(ErrorCode.NOT_FOUND);
        Long actor = UserContext.getUserId();
        boolean publicPost = Objects.equals(post.getAuditStatus(), 1);
        if (!publicPost
                && (actor == null || !actor.equals(post.getUserId()) && !UserContext.isAdmin()))
            throw new BusinessException(ErrorCode.NOT_FOUND);
        if (publicPost) {
            if (writes.view(id) != 1) throw new BusinessException(ErrorCode.NOT_FOUND);
            post.setViewCount(Objects.requireNonNullElse(post.getViewCount(), 0) + 1);
        }
        return assembler.batch(List.of(post), actor).get(0);
    }
}
