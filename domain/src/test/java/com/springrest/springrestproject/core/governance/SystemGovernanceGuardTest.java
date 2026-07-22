package com.springrest.springrestproject.core.governance;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SystemGovernanceGuardTest {

    private final SystemGovernanceGuard guard = new SystemGovernanceGuard();

    @Test
    void assertNotReservedTableName_rejectsSysPrefix() {
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> guard.assertNotReservedTableName("sys_widgets"));
        assertEquals(ErrorCode.RESERVED_TABLE_PREFIX, ex.getErrorCode());
    }

    @Test
    void assertNotReservedTableName_caseInsensitive() {
        assertThrows(ApplicationException.class, () -> guard.assertNotReservedTableName("SYS_Widgets"));
    }

    @Test
    void assertNotReservedTableName_allowsOrdinaryName() {
        assertDoesNotThrow(() -> guard.assertNotReservedTableName("widgets"));
    }

    @Test
    void assertNotSystemTable_rejectsSysPrefix() {
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> guard.assertNotSystemTable("sys_app_users"));
        assertEquals(ErrorCode.SYSTEM_TABLE_PROTECTED, ex.getErrorCode());
    }

    @Test
    void assertNotSystemTable_allowsOrdinaryName() {
        assertDoesNotThrow(() -> guard.assertNotSystemTable("widgets"));
    }

    @Test
    void assertRowMutable_rejectsWhenRestricted() {
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> guard.assertRowMutable(true));
        assertEquals(ErrorCode.RESTRICTED_ROW_MUTATION, ex.getErrorCode());
    }

    @Test
    void assertRowMutable_allowsWhenNotRestricted() {
        assertDoesNotThrow(() -> guard.assertRowMutable(false));
    }

    @Test
    void assertNoRestrictedColumnWrite_rejectsIsRestrictedKey() {
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> guard.assertNoRestrictedColumnWrite(Set.of("name", "is_restricted")));
        assertEquals(ErrorCode.RESTRICTED_ROW_MUTATION, ex.getErrorCode());
    }

    @Test
    void assertNoRestrictedColumnWrite_allowsOtherKeys() {
        assertDoesNotThrow(() -> guard.assertNoRestrictedColumnWrite(Set.of("name", "email")));
    }
}
