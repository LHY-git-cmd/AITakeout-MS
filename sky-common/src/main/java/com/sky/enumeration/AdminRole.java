package com.sky.enumeration;

/** 管理端权威角色。 */
public enum AdminRole {
    SUPER_ADMIN,
    ADMIN;

    public static AdminRole fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            return ADMIN;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return ADMIN;
        }
    }
}
