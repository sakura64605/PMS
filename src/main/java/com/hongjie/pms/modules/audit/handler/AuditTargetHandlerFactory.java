package com.hongjie.pms.modules.audit.handler;

import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuditTargetHandlerFactory {

    private final Map<String, AuditTargetHandler> handlerMap = new HashMap<>();

    public AuditTargetHandlerFactory(List<AuditTargetHandler> handlers) {
        for (AuditTargetHandler handler : handlers) {
            for (String targetType : handler.getTargetTypes()) {
                handlerMap.put(targetType, handler);
            }
        }
    }

    public AuditTargetHandler getHandler(String targetType) {
        AuditTargetHandler handler = handlerMap.get(targetType);
        if (handler == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的类型: " + targetType);
        }
        return handler;
    }
}