package pe.rutafija.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile(RestoreInspectionEnvironment.PROFILE)
class RestoreInspectionSecurity {
    @Bean
    SecurityFilterChain restoreInspectionChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health/liveness", "/actuator/health/readiness")
                        .permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
