package com.hongjie.pms.refactor.audit;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.UserInfo;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证真实 Spring 事务同步，不声称代替 MySQL 行锁集成测试。 */
class AuditTransactionTest {
    @BeforeEach void user(){AuditCommandServiceTest.metadata();UserContext.setUser(new UserInfo(1L,"admin",1,null,null));}
    @AfterEach void clear(){UserContext.clear();}
    static class Probe {
        final List<AuditChanged> delivered=new ArrayList<>();
        @TransactionalEventListener public void changed(AuditChanged event){delivered.add(event);}
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import(AuditCommandService.class)
    static class Config {
        @Bean Probe probe(){return new Probe();}
        @Bean PlatformTransactionManager transactionManager(){return new AbstractPlatformTransactionManager(){
            protected Object doGetTransaction(){return new Object();}
            protected void doBegin(Object tx,TransactionDefinition definition){}
            protected void doCommit(DefaultTransactionStatus status){}
            protected void doRollback(DefaultTransactionStatus status){}
        };}
    }
    private void run(boolean fail){
        var target=mock(AuditWriteTarget.class);when(target.type()).thenReturn("adopt");
        when(target.load(anyLong())).thenReturn(new AuditWriteTarget.Snapshot(2L,"title","pet_post"));
        var locks=mock(AuditWriteMapper.class);when(locks.pending(anyString(),anyLong())).thenReturn(List.of(AuditCommandServiceTest.record()));
        var records=mock(AuditRecordMapper.class);when(records.update(isNull(),any())).thenReturn(1);
        if(fail)doThrow(new IllegalStateException("target write failed")).when(target).decide(2L,1);
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withBean(AuditWriteTarget.class,()->target).withBean(AuditWriteMapper.class,()->locks)
                .withBean(AuditRecordMapper.class,()->records).run(context->{
                    assertThat(context).hasNotFailed();
                    var service=context.getBean(AuditCommandService.class);
                    if(fail){
                        assertThatThrownBy(()->service.batchApprove(List.of(1L,2L),"adopt")).isInstanceOf(IllegalStateException.class);
                        assertThat(context.getBean(Probe.class).delivered).isEmpty();
                    }else{
                        service.approve(1L,"adopt");
                        assertThat(context.getBean(Probe.class).delivered).hasSize(1);
                    }
                });
    }
    @Test void commitDeliversEvent(){run(false);}
    @Test void laterBatchFailureSuppressesEarlierEvent(){run(true);}
}
