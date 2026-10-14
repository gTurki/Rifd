package com.example.rifd.Security;

import com.example.rifd.Api.ApiException;
import com.example.rifd.Enum.StaffRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

// who is calling. With security.enabled=false there is no token: everything is empty and the
// actorStaffId sent in the request is used (same behaviour as before Security).
@Component
public class CurrentUser {

    public Optional<Long> staffId() {
        return claim("staffId").map(v -> v instanceof Number n ? n.longValue() : Long.valueOf(v.toString()));
    }

    public Optional<StaffRole> role() {
        return claim("role").map(v -> StaffRole.valueOf(v.toString()));
    }

    public boolean isField() {
        return role().filter(r -> r == StaffRole.FIELD).isPresent();
    }

    // the token wins over whatever the request says
    public Long actorId(Long requested) {
        return staffId().orElse(requested);
    }

    // FIELD users may only act for themselves (their tasks, their location, their notifications)
    public void requireSelfIfField(Long staffId) {
        if (isField() && !Objects.equals(this.staffId().orElse(null), staffId)) {
            throw new ApiException("You can only act on your own tasks and profile");
        }
    }

    private Optional<Object> claim(String name) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return Optional.ofNullable(jwt.getToken().getClaim(name));
        }
        return Optional.empty();
    }
}
