package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.service.interfaces.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatabaseManagementServiceTest {

    @Mock
    private IUserService userService;

    @InjectMocks
    private DatabaseManagementService databaseManagementService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testResetDatabase_WrongConfirm() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                databaseManagementService.resetDatabaseToDefault("wrong", 1L));
        assertEquals("Action aborted. You must pass exactly 'yes-reset-sandbox' to confirm.", ex.getMessage());
    }

    @Test
    void testResetDatabase_NotAdmin() {
        when(userService.getUserById(1L)).thenReturn(new com.springrest.springrestproject.dto.request.user.UserRequest(1L, "user", List.of(), "password"));

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                databaseManagementService.resetDatabaseToDefault("yes-reset-sandbox", 1L));
        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
    }
}
