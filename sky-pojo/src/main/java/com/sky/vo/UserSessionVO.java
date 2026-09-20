package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;

/** Access credentials and the authenticated user. Refresh token is for cookie plumbing only. */
public record UserSessionVO(AuthenticatedUser user, String accessToken,
                            @JsonIgnore String refreshToken) implements Serializable {
    /** Deliberately excludes credentials and high-risk identity fields. */
    public record AuthenticatedUser(Long id, String name, String phone, String avatar) implements Serializable { }
}
