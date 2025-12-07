package erp.approvalrequest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import erp.common.security.AbstractSecurityConfig;
import erp.common.security.HeaderAuthFilter;

@Configuration
@EnableMethodSecurity
public class ApprovalRequestSecurityConfig extends AbstractSecurityConfig {

    @Override
    protected String[] permitAllPatterns() {
        return new String[] {"/actuator/**"};
    }

    @Override
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            HeaderAuthFilter headerAuthFilter,
            AccessDeniedHandler accessDeniedHandler,
            AuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler)
                        .authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(permitAllPatterns()).permitAll()
                        .requestMatchers(HttpMethod.POST, "/approvals/**").hasAnyRole("EMPLOYEE", "APPROVER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/approvals/**").hasAnyRole("EMPLOYEE", "APPROVER", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
