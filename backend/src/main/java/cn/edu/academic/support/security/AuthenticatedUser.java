package cn.edu.academic.support.security;

import java.security.Principal;

public record AuthenticatedUser(Long id, String loginName, String displayName, String accountType,
                                Integer tokenVersion) implements Principal {
    @Override
    public String getName() {
        return loginName;
    }
}

