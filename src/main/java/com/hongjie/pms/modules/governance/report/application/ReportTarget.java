package com.hongjie.pms.modules.governance.report.application;

import com.hongjie.pms.shared.core.enums.BizTargetType;
import java.util.Collection;
import java.util.Map;

/** 举报处置能力独立于通用目标摘要，避免默认空实现吞掉处置动作。 */
public interface ReportTarget {
    BizTargetType type();
    String description();
    boolean lock(Long id);
    Map<Long,String> titles(Collection<Long> ids);
    void accept(Long id);
}
