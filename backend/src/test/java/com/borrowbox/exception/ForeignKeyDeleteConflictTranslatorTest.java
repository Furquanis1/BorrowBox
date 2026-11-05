package com.borrowbox.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ForeignKeyDeleteConflictTranslatorTest {

    private final ForeignKeyDeleteConflictTranslator translator =
            new ForeignKeyDeleteConflictTranslator();

    @Test
    void translatesForeignKeyDeleteConflict() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "delete failed",
                new IllegalStateException("wrapped",
                        new SQLException("parent row referenced", "23000", 1451)));

        RuntimeException translated = translator.translate(exception);

        assertThat(translated)
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(ForeignKeyDeleteConflictTranslator.CONFLICT_MESSAGE);
    }

    @Test
    void translatesForeignKeyDeleteConflictWithRowIsReferencedCode() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "delete failed",
                new SQLException("parent row referenced", "23000", 1217));

        RuntimeException translated = translator.translate(exception);

        assertThat(translated)
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(ForeignKeyDeleteConflictTranslator.CONFLICT_MESSAGE);
    }

    @Test
    void preservesRowIsReferencedCodeWithWrongSqlState() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "delete failed",
                new SQLException("unexpected state", "HY000", 1217));

        assertThat(translator.translate(exception)).isSameAs(exception);
    }

    @Test
    void preservesDuplicateKeyViolation() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate",
                new SQLException("duplicate entry", "23000", 1062));

        assertThat(translator.translate(exception)).isSameAs(exception);
    }

    @Test
    void preservesNotNullViolation() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "not null",
                new SQLException("column cannot be null", "23000", 1048));

        assertThat(translator.translate(exception)).isSameAs(exception);
    }

    @Test
    void preservesForeignKeyCodeWithWrongSqlState() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "delete failed",
                new SQLException("unexpected state", "HY000", 1451));

        assertThat(translator.translate(exception)).isSameAs(exception);
    }

    @Test
    void preservesIntegrityFailureWithoutSqlEvidence() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "opaque failure",
                new IllegalStateException("no SQL cause"));

        assertThat(translator.translate(exception)).isSameAs(exception);
    }
}
