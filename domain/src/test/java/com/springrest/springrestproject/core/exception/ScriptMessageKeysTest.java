package com.springrest.springrestproject.core.exception;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScriptMessageKeysTest {

    private final ReloadableResourceBundleMessageSource messageSource = buildMessageSource();

    private static ReloadableResourceBundleMessageSource buildMessageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages/messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Test
    void scriptInvalidPayload_resolvesInDefaultAndTurkishLocale() {
        String defaultMessage = messageSource.getMessage("error.script_invalid_payload", null, Locale.ENGLISH);
        String turkishMessage = messageSource.getMessage("error.script_invalid_payload", null, new Locale("tr"));

        assertEquals("The script payload is invalid or exceeds the allowed size.", defaultMessage);
        assertEquals("Betik verisi geçersiz veya izin verilen boyutu aşıyor.", turkishMessage);
    }

    @Test
    void scriptQueryFailed_resolvesInDefaultAndTurkishLocale() {
        String defaultMessage = messageSource.getMessage("error.script_query_failed", new Object[]{"boom"}, Locale.ENGLISH);
        String turkishMessage = messageSource.getMessage("error.script_query_failed", new Object[]{"boom"}, new Locale("tr"));

        assertEquals("The script execution failed: boom", defaultMessage);
        assertEquals("Betik çalıştırması başarısız oldu: boom", turkishMessage);
    }

    @Test
    void scriptDebugSessionAlreadyActive_resolvesInDefaultAndTurkishLocale() {
        String defaultMessage = messageSource.getMessage("error.script_debug_session_already_active", null, Locale.ENGLISH);
        String turkishMessage = messageSource.getMessage("error.script_debug_session_already_active", null, new Locale("tr"));

        assertEquals("A script debug session is already active on port 4242. Wait for it to finish or detach Chrome DevTools before starting another.", defaultMessage);
        assertEquals("Zaten 4242 portunda aktif bir betik hata ayıklama oturumu var. Başka bir tane başlatmadan önce mevcut oturumun bitmesini bekleyin veya Chrome DevTools bağlantısını kesin.", turkishMessage);
    }
}
