package com.hongjie.pms.modules.governance.report.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.governance.report.infrastructure.persistence.entity.ReportRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReportRecordMapper extends BaseMapper<ReportRecord> {
}