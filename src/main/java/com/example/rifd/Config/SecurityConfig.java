package com.example.rifd.Config;

import com.example.rifd.Security.JwtService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String FIELD = "FIELD";
    private static final String OPERATOR = "OPERATOR";
    private static final String SUPERVISOR = "SUPERVISOR";
    private static final String VISITOR = JwtService.VISITOR_ROLE; // visitor page accounts (not staff)
    private static final String[] ALL_ROLES = {FIELD, OPERATOR, SUPERVISOR};
    private static final String[] CONTROL_ROOM = {OPERATOR, SUPERVISOR};

    private static final String API = "/api/v1";

    // false = everything permitAll (local dev / plan B). On the server: SECURITY_ENABLED=true
    @Value("${security.enabled:false}")
    private boolean enabled;

    private final SecretKey secretKey;

    public SecurityConfig(@Value("${security.jwt.secret:change-me-to-a-long-random-secret-at-least-32-chars}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 bytes for HS256");
        }
        this.secretKey = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    // "role": "OPERATOR" -> authority ROLE_OPERATOR
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeJson(res, 401, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> writeJson(res, 403, "Access denied")));

        if (!enabled) {
            // everything open, but a token that IS sent is still read: the visitor page needs to know who is signed in
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                    .oauth2ResourceServer(o -> o
                            .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                            .authenticationEntryPoint((req, res, ex) -> writeJson(res, 401, "Invalid or expired token")));
            return http.build();
        }

        http.authorizeHttpRequests(a -> a

                        // ---------------- public ----------------
                        .requestMatchers("/ws", "/ws/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, API + "/auth/login").permitAll()
                        // The controller exists only when camera-demo.enabled=true (local showcase).
                        .requestMatchers(HttpMethod.POST, API + "/demo/camera-alerts").permitAll()
                        // external sources (sensors, radio gateway, staff app) push alerts without a token
                        .requestMatchers(HttpMethod.POST, API + "/alerts", API + "/alerts/batch", API + "/alerts/media",
                                API + "/alerts/preview", API + "/alerts/confirm").permitAll()
                        // visitor app: sign up / sign in
                        .requestMatchers(HttpMethod.POST, API + "/visitor-auth/register", API + "/visitor-auth/login").permitAll()
                        // visitor app: reporting needs a signed-in visitor; "mine" before the public /{code} below
                        .requestMatchers(HttpMethod.POST, API + "/visitor-reports", API + "/visitor-reports/media",
                                API + "/visitor-reports/preview", API + "/visitor-reports/confirm").hasRole(VISITOR)
                        .requestMatchers(HttpMethod.GET, API + "/visitor-reports/mine").hasRole(VISITOR)
                        // track by the random code (public)
                        .requestMatchers(HttpMethod.GET, API + "/visitor-reports/*").permitAll()
                        // visitor app: map zones + accessible routes and their alternatives (public information)
                        .requestMatchers(HttpMethod.GET,
                                API + "/zones",
                                API + "/accessibility/routes",
                                API + "/accessibility/routes/*/alternative").permitAll()

                        // ---------------- supervisor only ----------------
                        .requestMatchers(HttpMethod.POST, API + "/simulation/reset").hasRole(SUPERVISOR)
                        .requestMatchers(HttpMethod.PATCH, API + "/accessibility/assets/*/status").hasRole(SUPERVISOR)
                        .requestMatchers(HttpMethod.POST, API + "/staff").hasRole(SUPERVISOR)
                        .requestMatchers(HttpMethod.DELETE, API + "/staff/*").hasRole(SUPERVISOR)

                        // ---------------- every role (FIELD: own data only, checked in the services) ----------------
                        .requestMatchers(HttpMethod.GET, API + "/auth/me").hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.PATCH, API + "/auth/me").hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.POST, API + "/auth/me/password").hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.GET, API + "/tasks", API + "/tasks/*").hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.POST, API + "/tasks/*/accept", API + "/tasks/*/reject").hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.PATCH, API + "/tasks/*/status").hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.GET, API + "/notifications").hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.PATCH, API + "/notifications/*/read").hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.GET, API + "/staff/*").hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.PATCH, API + "/staff/*/location").hasAnyRole(ALL_ROLES)

                        // ---------------- control room: incidents ----------------
                        .requestMatchers(HttpMethod.GET,
                                API + "/incidents",
                                API + "/incidents/*",
                                API + "/incidents/*/activity",
                                API + "/incidents/*/recommendations",
                                API + "/incidents/*/candidates",
                                API + "/incidents/*/tasks").hasAnyRole(CONTROL_ROOM)
                        .requestMatchers(HttpMethod.POST,
                                API + "/incidents/*/acknowledge",
                                API + "/incidents/*/resolve",
                                API + "/incidents/*/false-alarm",
                                API + "/incidents/*/reopen",
                                API + "/incidents/*/confirm-duplicate",
                                API + "/incidents/*/not-duplicate",
                                API + "/incidents/*/recommendations/regenerate").hasAnyRole(CONTROL_ROOM)
                        .requestMatchers(HttpMethod.PATCH,
                                API + "/incidents/*/severity",
                                API + "/incidents/*/status").hasAnyRole(CONTROL_ROOM)

                        // ---------------- control room: alerts ----------------
                        .requestMatchers(HttpMethod.GET,
                                API + "/alerts",
                                API + "/alerts/*",
                                API + "/alerts/*/attachment").hasAnyRole(CONTROL_ROOM)
                        .requestMatchers(HttpMethod.POST, API + "/alerts/*/dismiss").hasAnyRole(CONTROL_ROOM)

                        // ---------------- control room: recommendations / tasks ----------------
                        .requestMatchers(HttpMethod.POST,
                                API + "/recommendations/*/accept",
                                API + "/recommendations/*/reject").hasAnyRole(CONTROL_ROOM)
                        .requestMatchers(HttpMethod.POST,
                                API + "/tasks",              // manual assignment
                                API + "/tasks/*/cancel").hasAnyRole(CONTROL_ROOM)

                        // ---------------- control room: staff ----------------
                        .requestMatchers(HttpMethod.GET, API + "/staff").hasAnyRole(CONTROL_ROOM)
                        .requestMatchers(HttpMethod.PATCH, API + "/staff/*/status").hasAnyRole(CONTROL_ROOM)

                        // ---------------- control room: accessibility ----------------
                        .requestMatchers(HttpMethod.GET, API + "/accessibility/assets").hasAnyRole(CONTROL_ROOM)
                        .requestMatchers(HttpMethod.POST, API + "/accessibility/routes/*/publish").hasAnyRole(CONTROL_ROOM)

                        // ---------------- control room: telegram reporters ----------------
                        .requestMatchers(HttpMethod.GET,
                                API + "/telegram/reporters",
                                API + "/telegram/reporters/*").hasAnyRole(CONTROL_ROOM)
                        .requestMatchers(HttpMethod.POST,
                                API + "/telegram/reporters/*/block",
                                API + "/telegram/reporters/*/unblock").hasAnyRole(CONTROL_ROOM)

                        // ---------------- control room: simulation ----------------
                        .requestMatchers(HttpMethod.POST, API + "/simulation/start").hasAnyRole(CONTROL_ROOM)
                        .requestMatchers(HttpMethod.GET, API + "/simulation/status").hasAnyRole(CONTROL_ROOM)

                        // ---------------- control room: read-only ----------------
                        .requestMatchers(HttpMethod.GET,
                                API + "/activity",
                                API + "/dashboard/stats",
                                API + "/events",
                                API + "/playbooks").hasAnyRole(CONTROL_ROOM)

                        // anything not listed above is closed: a forgotten endpoint fails safe
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        // bad / expired token -> same JSON shape
                        .authenticationEntryPoint((req, res, ex) -> writeJson(res, 401, "Invalid or expired token")));

        return http.build();
    }

    private static void writeJson(jakarta.servlet.http.HttpServletResponse res, int status, String message)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
