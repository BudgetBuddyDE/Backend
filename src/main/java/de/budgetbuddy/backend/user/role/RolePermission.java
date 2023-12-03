package de.budgetbuddy.backend.user.role;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RolePermission {
    BASIC(1L, 100),
    SERVICE_ACCOUNT(2L, 200),
    ADMIN(3L, 1000);

    private final Long id;
    private final int permissions;

}
