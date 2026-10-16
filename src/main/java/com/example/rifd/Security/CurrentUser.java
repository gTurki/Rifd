package com.example.rifd.Security;

import com.example.rifd.Api.ApiException;
import com.example.rifd.Enum.StaffRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

// who is calling. With security.enabled=false there is no token: everything is empty and the
// actorStaffId sent in the request is used (same behaviour as before Security).
@Component
public class CurrentUser {

    public Optional<Long> staffId() {
        return claim("staffId").map(v -> v instanceof Number n ? n.longValue() : Long.valueOf(v.toString()));
    }

    // empty for a visitor token (role VISITOR is not a staff role)
    public Optional<StaffRole> role() {
        return claim("role").map(Object::toString)
                .filter(r -> Arrays.stream(StaffRole.values()).anyMatch(s -> s.name().equals(r)))
                .map(StaffRole::valueOf);
    }

    // signed-in visitor (visitor page); empty for staff tokens and anonymous calls
    public Optional<Long> visitorId() {
        return claim("visitorId").map(v -> v instanceof Number n ? n.longValue() : Long.valueOf(v.toString()));
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
