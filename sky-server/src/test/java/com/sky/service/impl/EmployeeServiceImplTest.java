package com.sky.service.impl;

import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import com.sky.enumeration.AdminRole;
import com.sky.dto.EmployeeDTO;
import com.sky.mapper.EmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(employeeService, "passwordEncoder", passwordEncoder);
    }

    @Test
    void shouldUpgradeLegacyMd5AfterSuccessfulLogin() {
        String legacyHash = DigestUtils.md5DigestAsHex("123456".getBytes(StandardCharsets.UTF_8));
        Employee employee = Employee.builder().id(1L).username("admin").password(legacyHash).status(1).build();
        when(employeeMapper.getByUsername("admin")).thenReturn(employee);

        EmployeeLoginDTO request = new EmployeeLoginDTO();
        request.setUsername("admin");
        request.setPassword("123456");

        assertEquals(employee, employeeService.login(request));

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).update(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertTrue(passwordEncoder.matches("123456", captor.getValue().getPassword()));
    }

    @Test
    void shouldKeepExistingBcryptHash() {
        Employee employee = Employee.builder().id(1L).username("admin")
                .password(passwordEncoder.encode("123456")).status(1).build();
        when(employeeMapper.getByUsername("admin")).thenReturn(employee);

        EmployeeLoginDTO request = new EmployeeLoginDTO();
        request.setUsername("admin");
        request.setPassword("123456");

        employeeService.login(request);

        verify(employeeMapper, never()).update(org.mockito.ArgumentMatchers.any(Employee.class));
    }

    @Test
    void newEmployeesDefaultToOrdinaryAdminRole() {
        EmployeeDTO request = new EmployeeDTO();
        request.setUsername("operator");
        request.setName("Operator");
        request.setInitialPassword("Test-only-password-2026!");

        employeeService.save(request);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).insert(captor.capture());
        assertEquals(AdminRole.ADMIN.name(), captor.getValue().getRole());
        assertTrue(passwordEncoder.matches(request.getInitialPassword(), captor.getValue().getPassword()));
    }

    @Test
    void refusesMissingOrShortInitialPasswordBeforeWriting() {
        EmployeeDTO request = new EmployeeDTO();
        assertThrows(com.sky.exception.BaseException.class, () -> employeeService.save(request));
        request.setInitialPassword("short");
        assertThrows(com.sky.exception.BaseException.class, () -> employeeService.save(request));
        verify(employeeMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void editingEmployeeDoesNotChangePassword() {
        EmployeeDTO request = new EmployeeDTO();
        request.setId(7L);
        request.setInitialPassword("Test-only-password-2026!");
        employeeService.update(request);
        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).update(captor.capture());
        assertNull(captor.getValue().getPassword());
    }

    @Test
    void employeeResponseAndDiagnosticStringDoNotExposePassword() throws Exception {
        Employee employee = Employee.builder().id(7L).password("test-hash-sensitive").build();
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(employee);
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("test-hash-sensitive"));
        org.junit.jupiter.api.Assertions.assertFalse(employee.toString().contains("test-hash-sensitive"));
    }

    @Test
    void changesOnlyTheAuthenticatedEmployeesPassword() {
        com.sky.context.BaseContext.setCurrentId(7L);
        try {
            when(employeeMapper.getById(7L)).thenReturn(Employee.builder().id(7L)
                    .password(passwordEncoder.encode("legacy-test-password")).build());
            com.sky.dto.EmployeePasswordDTO request = new com.sky.dto.EmployeePasswordDTO();
            request.setOldPassword("legacy-test-password");
            request.setNewPassword("new-test-password-2026!");
            employeeService.changePassword(request);
            ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
            verify(employeeMapper).update(captor.capture());
            assertEquals(7L, captor.getValue().getId());
            assertTrue(passwordEncoder.matches(request.getNewPassword(), captor.getValue().getPassword()));
        } finally { com.sky.context.BaseContext.removeCurrentId(); }
    }

    @Test
    void wrongOldPasswordCannotModifyAnAccount() {
        com.sky.context.BaseContext.setCurrentId(7L);
        try {
            when(employeeMapper.getById(7L)).thenReturn(Employee.builder().id(7L)
                    .password(passwordEncoder.encode("legacy-test-password")).build());
            com.sky.dto.EmployeePasswordDTO request = new com.sky.dto.EmployeePasswordDTO();
            request.setOldPassword("incorrect-test-password");
            request.setNewPassword("new-test-password-2026!");
            assertThrows(com.sky.exception.PasswordErrorException.class, () -> employeeService.changePassword(request));
            verify(employeeMapper, never()).update(org.mockito.ArgumentMatchers.any());
        } finally { com.sky.context.BaseContext.removeCurrentId(); }
    }

    @Test
    void passwordChangeRequiresAuthenticatedContext() {
        com.sky.context.BaseContext.removeCurrentId();
        assertThrows(com.sky.exception.BaseException.class,
                () -> employeeService.changePassword(new com.sky.dto.EmployeePasswordDTO()));
        verify(employeeMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }
}
