package com.hongjie.pms.architecture.support;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.ibatis.session.SqlSessionFactory;


/**
 * 等价性测试的 MyBatis 支撑：直接架一个指向测试库的 {@link SqlSessionFactory}。
 *
 * <p><b>为什么不走 Spring 上下文</b>：完整启动这个应用需要 MySQL、Redis(Redisson)、
 * RocketMQ、Elasticsearch、Qdrant 五样东西，而其中四样在本地都没有。
 * 但被验证的服务本身只依赖 MySQL 加上几个可 mock 的协作者（缓存、事件发布、推送）。
 * 与其为了跑一个查询测试去凑齐整套基础设施，不如手工架 MyBatis —— 启动更快、
 * 失败原因更清晰，也不会因为某个无关的 {@code @PostConstruct} 而整体起不来。
 *
 * <p><b>配置需与生产一致</b>：{@code mapUnderscoreToCamelCase} 与分页拦截器
 * 都必须和 {@code application.yaml} / {@code MybatisPlusConfig} 保持一样，
 * 否则测试通过而线上失败。生产用的是 MyBatis-Plus 默认值（下划线转驼峰默认开启）
 * 加一个 {@code PaginationInnerInterceptor(DbType.MYSQL)}，此处照搬。
 *
 * <p><b>凭据不落仓库</b>：库地址与账号从环境变量读取，密码<b>必须</b>由
 * {@code PMS_TEST_DB_PASSWORD} 提供。未设置时 {@link #configured()} 返回 false，
 * 测试通过 JUnit 的 assumption 跳过而不是失败 —— 这样在没有测试库的机器上
 * （例如 CI 或同事的电脑）不会因为缺凭据而红。
 */
public final class MyBatisTestSupport {

    private static final String DEFAULT_URL =
            "jdbc:mysql://127.0.0.1:3306/pet_system_test"
                    + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
                    + "&allowMultiQueries=true";

    private MyBatisTestSupport() {
    }

    /** 是否具备运行等价性测试的条件（至少要给出密码） */
    public static boolean configured() {
        return env("PMS_TEST_DB_PASSWORD") != null;
    }

    /** 未配置时给测试用的跳过原因 */
    public static String skipReason() {
        return "未设置环境变量 PMS_TEST_DB_PASSWORD，跳过需要真实数据库的等价性测试。"
                + "用法：PMS_TEST_DB_PASSWORD=xxx mvn test";
    }

    public static String jdbcUrl() {
        return env("PMS_TEST_DB_URL") != null ? env("PMS_TEST_DB_URL") : DEFAULT_URL;
    }

    /**
     * 建立一个 {@code SqlSessionFactory}。
     *
     * @param mapperTypes 需要注册的 Mapper（注册后 MyBatis-Plus 才会解析实体元信息）
     */
    public static SqlSessionFactory createFactory(Class<?>... mapperTypes) {
        try {
            MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setUrl(jdbcUrl());
            dataSource.setUser(env("PMS_TEST_DB_USER") != null ? env("PMS_TEST_DB_USER") : "root");
            dataSource.setPassword(env("PMS_TEST_DB_PASSWORD"));

            // 与生产一致：MyBatis-Plus 默认开启下划线转驼峰
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            for (Class<?> mapperType : mapperTypes) {
                configuration.addMapper(mapperType);
            }

            // 与 MybatisPlusConfig 一致：没有它 selectPage 不会真正分页
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setConfiguration(configuration);
            factoryBean.setPlugins(interceptor);
            return factoryBean.getObject();
        } catch (Exception e) {
            throw new IllegalStateException("构建测试用 SqlSessionFactory 失败", e);
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }
}
