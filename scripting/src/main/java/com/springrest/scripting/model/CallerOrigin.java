package com.springrest.scripting.model;

public enum CallerOrigin {
    USER_SUBMITTED,
    DB_HOOK,
    KAFKA_INBOUND_HOOK,
    KAFKA_OUTBOUND_HOOK
}
