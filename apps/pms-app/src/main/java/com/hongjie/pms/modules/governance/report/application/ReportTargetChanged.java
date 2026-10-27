package com.hongjie.pms.modules.governance.report.application;

import com.hongjie.pms.shared.core.enums.BizTargetType;

public record ReportTargetChanged(BizTargetType type, Long targetId) {}
