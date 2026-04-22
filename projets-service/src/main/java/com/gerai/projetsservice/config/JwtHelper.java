package com.gerai.projetsservice.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class JwtHelper {

    public Long getEmployeeId(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) throw new RuntimeException("JWT introuvable");
        Object val = jwt.getClaim("employee_id");
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        throw new RuntimeException("Claim employee_id absent du JWT — configurer Keycloak");
    }

    public String getEmail(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    public List<String> getRoles(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) return List.of();
        Map<String, Object> ra = jwt.getClaim("realm_access");
        if (ra == null) return List.of();
        @SuppressWarnings("unchecked")
        Collection<String> roles = (Collection<String>) ra.get("roles");
        return roles != null ? List.copyOf(roles) : List.of();
    }

    public boolean isChef(Authentication auth)      { return getRoles(auth).contains("CHEF");  }
    public boolean isRh(Authentication auth)        { return getRoles(auth).contains("RH");    }
    public boolean isAdmin(Authentication auth)     { return getRoles(auth).contains("ADMIN"); }
    public boolean isAdminOrRh(Authentication auth) { return isRh(auth) || isAdmin(auth);      }

    private Jwt extractJwt(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken t) return t.getToken();
        return null;
    }
}