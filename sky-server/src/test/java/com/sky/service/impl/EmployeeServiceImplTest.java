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

        employeeService.save(request);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).insert(captor.capture());
        assertEquals(AdminRole.ADMIN.name(), captor.getValue().getRole());
    }
}
