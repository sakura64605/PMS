package com.hongjie.pms.modules.governance.report.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.cache.DistributedCache;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.utils.RedisScanUtil;
import com.hongjie.pms.modules.governance.report.infrastructure.persistence.mapper.ReportRecordMapper;
import com.hongjie.pms.modules.governance.report.api.service.ReportService;
import com.hongjie.pms.modules.governance.report.api.controller.ReportController;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.shared.extension.target.TargetTypeCodec;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises Spring proxies and after-commit delivery; does not simulate database row locks. */
class ReportTransactionTest {
    static class Transactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;

        protected Object doGetTransaction() {
            return new Object();
        }

        protected void doBegin(Object tx, TransactionDefinition definition) {}

        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({PmsReportServiceImpl.class, ReportTargetChangedListener.class, ReportController.class})
    static class Config {
        @Bean
        Transactions transactionManager() {
            return new Transactions();
        }

        @Bean
        TargetTypeCodec codec() {
            return new TargetTypeCodec();
        }
    }

    @BeforeEach
    void setup() {
        ReportServiceTest.metadata();
        UserContext.setUser(new UserInfo(1L, "admin", 1, null, null));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private void run(boolean failure, boolean cacheFailure) {
        var records = mock(ReportRecordMapper.class);
        var row =
                new com.hongjie.pms.modules.governance.report.infrastructure.persistence.entity
                        .ReportRecord();
        row.setId(100L);
        row.setTargetId(10L);
        row.setTargetType("pet");
        row.setStatus(0);
        when(records.selectById(100L)).thenReturn(row);
        when(records.update(isNull(), any())).thenReturn(1);
        var target = mock(ReportTarget.class);
        when(target.type()).thenReturn(BizTargetType.PET_POST);
        when(target.lock(10L)).thenReturn(true);
        var cache = mock(DistributedCache.class);
        var proxy = mock(com.hongjie.pms.platform.cache.StringRedisTemplateProxy.class);
        var scan = mock(RedisScanUtil.class);
        if (cacheFailure)
            doThrow(new IllegalStateException("cache unavailable")).when(cache).delete(anyString());
        doAnswer(
                        call -> {
                            verifyNoInteractions(cache, scan);
                            if (failure) throw new IllegalStateException("target write failed");
                            return null;
                        })
                .when(target)
                .accept(10L);
        var legacy = mock(ReportService.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withBean("legacyReportService", ReportService.class, () -> legacy)
                .withBean(ReportTarget.class, () -> target)
                .withBean(ReportRecordMapper.class, () -> records)
                .withBean(UserMapper.class, () -> mock(UserMapper.class))
                .withBean(ReportWriteMapper.class, () -> mock(ReportWriteMapper.class))
                .withBean("distributedCache", DistributedCache.class, () -> cache)
                .withBean(
                        "stringRedisTemplateProxy",
                        com.hongjie.pms.platform.cache.StringRedisTemplateProxy.class,
                        () -> proxy)
                .withBean(RedisScanUtil.class, () -> scan)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            var service = context.getBean(ReportService.class);
                            assertThat(service).isInstanceOf(PmsReportServiceImpl.class);
                            var tx = context.getBean(Transactions.class);
                            if (failure) {
                                assertThatThrownBy(() -> service.handle(100L, 1L, 1, "done"))
                                        .isInstanceOf(IllegalStateException.class);
                                assertThat(tx.rollbacks).isEqualTo(1);
                                assertThat(tx.commits).isZero();
                                verifyNoInteractions(cache, scan);
                            } else {
                                context.getBean(ReportController.class).handle(100L, 1, "done");
                                assertThat(tx.commits).isEqualTo(1);
                                assertThat(tx.rollbacks).isZero();
                                verify(cache).delete("pet_10");
                                verify(scan).scanAndDelete("petList_*");
                            }
                            verifyNoInteractions(legacy, proxy);
                        });
    }

    @Test
    void primaryServiceCommitsBeforeInvalidatingCache() {
        run(false, false);
    }

    @Test
    void targetFailureRollsBackAndSuppressesCacheInvalidation() {
        run(true, false);
    }

    @Test
    void cacheFailureDoesNotUndoCommittedDecisionOrSkipOtherInvalidations() {
        run(false, true);
    }
}
