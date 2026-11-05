package com.borrowbox.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Set;

@Component
public class ForeignKeyDeleteConflictTranslator {

    static final String CONFLICT_MESSAGE =
            "Cannot delete because related records still reference this resource.";

    private static final Set<Integer> MYSQL_FOREIGN_KEY_DELETE_ERROR_CODES = Set.of(1217, 1451);
    private static final String SQL_STATE_INTEGRITY_CONSTRAINT_VIOLATION = "23000";

    public RuntimeException translate(DataIntegrityViolationException exception) {
        return hasForeignKeyDeleteEvidence(exception)
                ? new ResourceConflictException(CONFLICT_MESSAGE)
                : exception;
    }

    private boolean hasForeignKeyDeleteEvidence(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && MYSQL_FOREIGN_KEY_DELETE_ERROR_CODES.contains(sqlException.getErrorCode())
                    && SQL_STATE_INTEGRITY_CONSTRAINT_VIOLATION.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
