package com.hongjie.pms.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.hongjie.pms.common.base.core.UpdateTimeContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void updateFill(MetaObject metaObject) {
        // 判断是否需要跳过更新时间
        if (UpdateTimeContext.shouldSkip()) {
            log.debug("跳过 updateTime 自动填充");
            return;  // 直接返回，不填充
        }

        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        log.debug("更新填充: updateTime");
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        this.setFieldValByName("createTime", LocalDateTime.now(), metaObject);
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }

}
