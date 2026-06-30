package com.springrest.springrestproject.model.relation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum DeletePolicy {
    CASCADE("ON DELETE CASCADE"),
    SET_NULL("ON DELETE SET NULL"),
    RESTRICT("ON DELETE RESTRICT"),
    NO_ACTION("ON DELETE NO ACTION");

    private final String sql;

    @JsonCreator
    public static DeletePolicy fromValue(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String sanitized = text.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (DeletePolicy policy : DeletePolicy.values()) {
            if (policy.name().equals(sanitized)) {
                return policy;
            }
        }
        String allowed = java.util.Arrays.stream(DeletePolicy.values())
                .map(DeletePolicy::name)
                .collect(java.util.stream.Collectors.joining(", "));
        throw new ApplicationException(ErrorCode.INVALID_DELETE_POLICY, text, allowed);
    }
}
