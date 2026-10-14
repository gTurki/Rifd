package com.example.rifd.Config;

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

    private static final String[] ALL_ROLES = {"FIELD", "OPERATOR", "SUPERVISOR"};

    // false = everything permitAll (plan B for the demo if tokens misbehave)
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
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }

        http.authorizeHttpRequests(a -> a
                        .requestMatchers("/ws", "/ws/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // external sources (sensors, radio gateway, visitor app) push alerts without a token
                        .requestMatchers(HttpMethod.POST, "/api/v1/alerts", "/api/v1/alerts/batch").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/simulation/reset").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/accessibility/assets/*/status").hasRole("SUPERVISOR")

                        // field staff: own tasks / notifications / location (ownership is checked in the services)
                        .requestMatchers(HttpMethod.GET, "/api/v1/tasks", "/api/v1/tasks/*",
                                "/api/v1/notifications", "/api/v1/staff/*").hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.POST, "/api/v1/tasks/*/accept", "/api/v1/tasks/*/reject")
                        .hasAnyRole(ALL_ROLES)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/tasks/*/status", "/api/v1/staff/*/location",
                                "/api/v1/notifications/*/read").hasAnyRole(ALL_ROLES)

                        // everything else = control room
                        .anyRequest().hasAnyRole("OPERATOR", "SUPERVISOR"))
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));

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
