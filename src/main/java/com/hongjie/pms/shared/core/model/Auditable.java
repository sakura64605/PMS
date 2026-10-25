package com.hongjie.pms.shared.core.model;

import java.time.LocalDateTime;

/**
 * 审计字段只读契约。
 *
 * <p>只声明读取能力，不强制写入 —— 老实体中不少表缺失 {@code updateTime}，
 * 若在此强制双字段会逼着改老实体。
 */
public interface Auditable {

    LocalDateTime getCreateTime();

    LocalDateTime getUpdateTime();
}
