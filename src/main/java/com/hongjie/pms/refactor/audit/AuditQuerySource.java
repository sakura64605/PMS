package com.hongjie.pms.refactor.audit;

import com.hongjie.pms.modules.audit.dto.AuditListDto;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 按审核类型读取内容；与执行审核的写策略分离。 */
public interface AuditQuerySource {
    String type();
    List<Item> pending(String keyword);
    Map<Long, Item> findAll(Collection<Long> ids);
    record Item(Long ownerId, AuditListDto view) {
        public String historyTitle() {
            return "daily".equals(view.getTargetType()) ? view.getContent() : view.getTitle();
        }
    }
}
