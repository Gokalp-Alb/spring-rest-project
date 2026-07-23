package com.springrest.springrestproject.core.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScriptHooksErrorCodeTest {

    @Test
    void newErrorCodesHaveExpectedStatusesAndMessageKeys() {
        assertEquals(HttpStatus.FORBIDDEN, ErrorCode.SCRIPT_EXECUTION_DISABLED.getHttpStatus());
        assertEquals("error.script_execution_disabled", ErrorCode.SCRIPT_EXECUTION_DISABLED.getMessageKey());

        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.SCRIPT_HOOK_EXECUTION_FAILED.getHttpStatus());
        assertEquals("error.script_hook_execution_failed", ErrorCode.SCRIPT_HOOK_EXECUTION_FAILED.getMessageKey());

        assertEquals(HttpStatus.BAD_GATEWAY, ErrorCode.SCRIPT_CACHE_FAILED.getHttpStatus());
        assertEquals("error.script_cache_failed", ErrorCode.SCRIPT_CACHE_FAILED.getMessageKey());

        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.SCRIPT_INVALID_ASSOCIATION.getHttpStatus());
        assertEquals("error.script_invalid_association", ErrorCode.SCRIPT_INVALID_ASSOCIATION.getMessageKey());

        assertEquals(HttpStatus.CONFLICT, ErrorCode.SCRIPT_ALREADY_EXISTS_FOR_TARGET.getHttpStatus());
        assertEquals("error.script_already_exists_for_target", ErrorCode.SCRIPT_ALREADY_EXISTS_FOR_TARGET.getMessageKey());
    }
}
