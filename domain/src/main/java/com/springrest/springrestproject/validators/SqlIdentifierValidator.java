package com.springrest.springrestproject.validators;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class SqlIdentifierValidator {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[\\p{L}_][\\p{L}\\p{N}_]*$");

    /**
     * Validates if the given string is a safe SQL identifier (table name, column name, relation name).
     * Throws an ApplicationException if the validation fails.
     *
     * @param identifier the SQL identifier to validate
     */
    public void validate(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "SQL identifier cannot be null or empty.");
        }
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "Invalid SQL identifier: " + identifier);
        }
    }
}
