package com.borrowbox.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application under the test profile where
 * spring.jpa.hibernate.ddl-auto=validate is active. Successful context
 * startup proves the mapped entities (users, communities, memberships,
 * categories) are aligned with the version-controlled schema.sql.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SchemaValidationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsWithSchemaValidation() {
        assertThat(applicationContext).isNotNull();
    }
}
