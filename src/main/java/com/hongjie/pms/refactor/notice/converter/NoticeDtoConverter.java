package com.hongjie.pms.refactor.notice.converter;

import com.hongjie.pms.modules.notice.dto.response.NoticeDetailDto;
import com.hongjie.pms.modules.notice.dto.response.NoticeListDto;
import com.hongjie.pms.modules.notice.entity.Notice;
import org.springframework.stereotype.Component;

/**
 * 公告 -> 对外 DTO 的显式转换。
 *
 * <p>替代老实现里的两个手写 builder 方法（{@code convertToListDto} /
 * {@code convertToDetailDto}）。
 *
 * <p>没有实现 {@code EntityConverter} 接口：那个接口的契约是「一个源对象映射成一个目标对象」，
 * 而公告 DTO 上还有一个<b>因浏览者而异</b>的 {@code isRead} 字段 ——
 * 它来自另一张表，不属于实体自身。与其为了套接口把 isRead 变成转换器的状态，
 * 不如显式把它作为入参。
 */
@Component
public class NoticeDtoConverter {

    public NoticeListDto toListDto(Notice notice, Boolean isRead) {
        return NoticeListDto.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .type(notice.getType())
                .priority(notice.getPriority())
                .status(notice.getStatus())
                .isTop(notice.getIsTop())
                .publishTime(notice.getPublishTime())
                .expireTime(notice.getExpireTime())
                .isRead(isRead)
                .build();
    }

    public NoticeDetailDto toDetailDto(Notice notice, Boolean isRead) {
        return NoticeDetailDto.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .type(notice.getType())
                .priority(notice.getPriority())
                .status(notice.getStatus())
                .isTop(notice.getIsTop())
                .publishTime(notice.getPublishTime())
                .expireTime(notice.getExpireTime())
                .createTime(notice.getCreateTime())
                .isRead(isRead)
                .build();
    }
}
