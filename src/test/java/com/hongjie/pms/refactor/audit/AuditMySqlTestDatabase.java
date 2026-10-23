package com.hongjie.pms.refactor.audit;

/** 真实数据库写测试的地址门禁；禁止接收服务器地址和业务库名。 */
final class AuditMySqlTestDatabase {
    private AuditMySqlTestDatabase() {}
    static void requireLocalTestDatabase(String url) {
        if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):3306/pet_system_test(?:\\?.*)?")) {
            throw new IllegalArgumentException("审核写测试只允许本机 3306 的 pet_system_test 数据库");
        }
    }
}
