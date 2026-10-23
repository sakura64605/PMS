package com.hongjie.pms.refactor.report;

import com.hongjie.pms.refactor.core.enums.BizTargetType;
public record ReportTargetChanged(BizTargetType type,Long targetId) {}
