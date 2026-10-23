package com.hongjie.pms.refactor.audit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuditMySqlTestDatabaseTest {
    @Test void acceptsOnlyNamedLocalTestDatabase() {
        assertThatCode(() -> AuditMySqlTestDatabase.requireLocalTestDatabase("jdbc:mysql://127.0.0.1:3306/pet_system_test?serverTimezone=Asia/Shanghai"))
                .doesNotThrowAnyException();
        assertThatCode(() -> AuditMySqlTestDatabase.requireLocalTestDatabase("jdbc:mysql://localhost:3306/pet_system_test"))
                .doesNotThrowAnyException();
    }
    @Test void rejectsBusinessDatabasesAndRemoteServers() {
        for (String url : new String[]{null,"jdbc:mysql://127.0.0.1:3306/pet_system",
                "jdbc:mysql://111.229.254.252:3306/pet_system_test",
                "jdbc:mysql://localhost:3306/pet_system_test_other",
                "jdbc:mysql://localhost:3306/pet_system_test/other"}) {
            assertThatThrownBy(() -> AuditMySqlTestDatabase.requireLocalTestDatabase(url)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
