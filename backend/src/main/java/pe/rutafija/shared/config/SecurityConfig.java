package pe.rutafija.shared.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.shared.security.JsonAccessDeniedHandler;
import pe.rutafija.shared.security.JsonAuthenticationEntryPoint;
import pe.rutafija.terms.application.AdminTermsGateAuthorizationManager;
import pe.rutafija.personidentity.application.PersonIdentityGateAuthorizationManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            AdminTermsGateAuthorizationManager adminTermsGateAuthorizationManager,
            PersonIdentityGateAuthorizationManager personIdentityGateAuthorizationManager,
            pe.rutafija.shared.security.SecurityDenialAuditFilter securityDenialAuditFilter
    ) throws Exception {
        http
                .addFilterAfter(securityDenialAuditFilter, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
                .cors(Customizer.withDefaults())
                // Bearer access tokens are sent in a header. The only credential cookie is
                // HttpOnly + SameSite=Strict and scoped to /api/v1/auth.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/onboarding/requests",
                                "/api/v1/onboarding/otp/verify",
                                "/api/v1/onboarding/otp/resend",
                                "/api/v1/onboarding/access/login",
                                "/api/v1/onboarding/access/logout"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/onboarding/applicant/requests",
                                "/api/v1/onboarding/applicant/requests/*/documentary-status").permitAll()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/onboarding/applicant/requests/*/documents").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/onboarding/applicant/requests/*/resubmit",
                                "/api/v1/onboarding/applicant/requests/*/reapply").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/admin-activation/claim",
                                "/api/v1/admin-activation/otp/begin",
                                "/api/v1/admin-activation/otp/resend",
                                "/api/v1/admin-activation/otp/verify",
                                "/api/v1/admin-activation/terms",
                                "/api/v1/admin-activation/complete").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/admin-activation/terms",
                                "/api/v1/admin-activation/status").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/otp/verify",
                                "/api/v1/auth/otp/resend",
                                "/api/v1/auth/conductor-recovery/**",
                                "/api/v1/auth/super-admin-recovery/challenges",
                                "/api/v1/auth/super-admin-recovery/challenges/**",
                                "/api/v1/mobile/auth/login",
                                "/api/v1/mobile/auth/refresh",
                                "/api/v1/mobile/auth/logout",
                                "/api/v1/mobile/auth/otp/verify",
                                "/api/v1/mobile/auth/otp/resend"
                        ).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/ws/operations").permitAll()
                        .requestMatchers("/api/v1/admin/terms/**").access(AuthorizationManagers.allOf(
                                AuthorityAuthorizationManager.hasRole("ADMIN"),
                                personIdentityGateAuthorizationManager))
                        .requestMatchers("/api/v1/**").access(AuthorizationManagers.allOf(
                                AuthenticatedAuthorizationManager.authenticated(),
                                adminTermsGateAuthorizationManager,
                                personIdentityGateAuthorizationManager
                        ))
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; frame-ancestors 'none'"
                        ))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.jwt().secretBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must contain valid Base64", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must decode to at least 32 bytes");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<pe.rutafija.shared.security.SecurityDenialAuditFilter> securityAuditRegistration(pe.rutafija.shared.security.SecurityDenialAuditFilter filter){
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);registration.setEnabled(false);return registration;
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(AppUserRepository userRepository) {
        return jwt -> {
            List<SimpleGrantedAuthority> authorities = List.of();
            try {
                UUID userId = UUID.fromString(jwt.getSubject());
                String organizationClaim = jwt.getClaimAsString("organizationId");
                UUID organizationId = organizationClaim == null ? null : UUID.fromString(organizationClaim);
                AppUser user = userRepository.findOneById(userId).orElse(null);
                Object sessionVersionClaim = jwt.getClaims().get("sessionVersion");
                boolean sessionVersionMatches = sessionVersionClaim instanceof Number version
                        ? user != null && user.getSessionVersion() == version.longValue()
                        : user != null && user.getSessionVersion() == 0;
                if (user != null
                        && user.isActive()
                        && user.isEmailVerified()
                        && sessionVersionMatches
                        && Objects.equals(user.getOrganizationId(), organizationId)
                        && (jwt.getClaimAsString("email") == null
                            || Objects.equals(user.getEmail(), jwt.getClaimAsString("email")))
                        && Objects.equals(user.getRole().name(), jwt.getClaimAsString("role"))
                        && (user.getOrganization() == null || user.getOrganization().isActive())) {
                    authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                }
            } catch (IllegalArgumentException | NullPointerException exception) {
                // Un claim malformado no recibe autoridades y será rechazado por el servicio protegido.
            }
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Correlation-ID", "Idempotency-Key", "X-Onboarding-Token",
                "X-Admin-Activation-Token"
        ));
        configuration.setExposedHeaders(List.of("X-Correlation-ID", "X-Idempotent-Replay"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
