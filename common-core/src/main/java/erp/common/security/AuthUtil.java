package erp.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;

@Component
public class AuthUtil {

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public Long currentUserId() {
        return getCurrentUser().userId();
    }

    public boolean hasRole(Role role) {
        return getCurrentUser().roles().contains(role);
    }
}
