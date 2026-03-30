package com.hongjie.pms.modules.comment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.comment.dto.request.CommentCreateRequest;
import com.hongjie.pms.modules.comment.dto.response.CommentRespDto;
import jakarta.validation.Valid;

public interface CommentService {
    void createComment(@Valid CommentCreateRequest request);

    IPage<CommentRespDto> getCommentList(String targetType, Long targetId, Integer pageNum, Integer pageSize);
}
