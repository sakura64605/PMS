package com.hongjie.pms.modules.notice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.notice.dto.request.NoticeRequestDto;
import com.hongjie.pms.modules.notice.dto.response.NoticeDetailDto;
import com.hongjie.pms.modules.notice.dto.response.NoticeListDto;

public interface NoticeService {
    
    // 管理员操作
    NoticeDetailDto create(NoticeRequestDto request);
    NoticeDetailDto update(NoticeRequestDto request);
    void delete(Long id);
    void publish(Long id);
    void unpublish(Long id);
    IPage<NoticeListDto> listForAdmin(Integer pageNum, Integer pageSize, Integer status, String keyword);
    
    // 用户端
    IPage<NoticeListDto> listForUser(Integer pageNum, Integer pageSize);
    NoticeDetailDto getByIdForUser(Long id);
    int getUnreadCount();
}