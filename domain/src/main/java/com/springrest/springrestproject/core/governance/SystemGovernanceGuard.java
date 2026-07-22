package com.springrest.springrestproject.core.governance;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SystemGovernanceGuard {

    private static final String RESERVED_PREFIX = "sys_";

    public void assertNotReservedTableName(String tableName) {
        if (tableName != null && tableName.toLowerCase().startsWith(RESERVED_PREFIX)) {
            throw new ApplicationException(ErrorCode.RESERVED_TABLE_PREFIX, tableName);
        }
    }

    public void assertNotSystemTable(String tableName) {
        if (tableName != null && tableName.toLowerCase().startsWith(RESERVED_PREFIX)) {
            throw new ApplicationException(ErrorCode.SYSTEM_TABLE_PROTECTED, tableName);
        }
    }

    public void assertRowMutable(boolean currentIsRestricted) {
        if (currentIsRestricted) {
            throw new ApplicationException(ErrorCode.RESTRICTED_ROW_MUTATION);
        }
    }

    public void assertNoRestrictedColumnWrite(Set<String> payloadKeys) {
        if (payloadKeys != null && payloadKeys.contains("is_restricted")) {
            throw new ApplicationException(ErrorCode.RESTRICTED_ROW_MUTATION);
        }
    }
}
