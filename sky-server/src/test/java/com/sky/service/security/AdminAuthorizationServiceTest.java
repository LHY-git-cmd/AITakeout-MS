package com.sky.service.security;

import com.sky.entity.Employee;
import com.sky.enumeration.AdminPermission;
import com.sky.enumeration.AdminRole;
import com.sky.exception.PermissionDeniedException;
import com.sky.mapper.EmployeeMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuthorizationServiceTest {

    private final EmployeeMapper mapper = mock(EmployeeMapper.class);
    private final AdminAuthorizationService service = new AdminAuthorizationService(mapper);

    @Test
    void roleComesFromDatabaseField() {
        when(mapper.getById(1L)).thenReturn(Employee.builder()
                .id(1L).username("not-admin").role("SUPER_ADMIN").build());

        assertEquals(AdminRole.SUPER_ADMIN, service.resolveRole(1L));
    }

    @Test
    void ordinaryAdminCanReadButCannotWriteEmployeesOrKnowledge() {
        service.require(AdminRole.ADMIN, AdminPermission.EMPLOYEE_READ);
        service.require(AdminRole.ADMIN, AdminPermission.KNOWLEDGE_READ);

        assertThrows(PermissionDeniedException.class,
                () -> service.require(AdminRole.ADMIN, AdminPermission.EMPLOYEE_WRITE));
        assertThrows(PermissionDeniedException.class,
                () -> service.require(AdminRole.ADMIN, AdminPermission.KNOWLEDGE_WRITE));
    }

    @Test
    void disabledAdministratorCannotBeResolved() {
        when(mapper.getById(2L)).thenReturn(Employee.builder()
                .id(2L).role("SUPER_ADMIN").status(0).build());

        assertThrows(PermissionDeniedException.class, () -> service.resolveRole(2L));
    }

    @Test
    void superAdminHasEveryRegisteredPermission() {
        for (AdminPermission permission : AdminPermission.values()) {
            service.require(AdminRole.SUPER_ADMIN, permission);
        }
    }
}
