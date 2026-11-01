package com.ferreteria.ferreteria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/roles/**").authenticated()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**", "/login", "/css/**", "/js/**", "/images/**", "/webjars/**")
                    .permitAll()

                .requestMatchers("/api/usuarios/**", "/usuarios/**", "/api/roles/**", "/roles/**", "/api/bitacora/**")
                    .hasAnyAuthority("ADMIN_SISTEMA", "ROLE_ADMIN_SISTEMA")

                .requestMatchers("/api/clientes/**").authenticated()
                .requestMatchers("/api/productos/**", "/api/inventario/**").authenticated()
                .requestMatchers("/api/proveedores/**").authenticated()

                .requestMatchers("/api/ventas/**")
                    .hasAnyAuthority("ADMIN_NEGOCIO", "ROLE_ADMIN_NEGOCIO", "VENDEDOR", "ROLE_VENDEDOR")
                .requestMatchers("/api/compras/**")
                    .hasAnyAuthority("ADMIN_NEGOCIO", "ROLE_ADMIN_NEGOCIO", "ALMACENERO", "ROLE_ALMACENERO")
                .requestMatchers("/api/reportes/**")
                    .hasAnyAuthority("ADMIN_NEGOCIO", "ROLE_ADMIN_NEGOCIO", "GERENTE", "ROLE_GERENTE")

                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Local: Angular en cualquier puerto de localhost. Producción: CORS_ALLOWED_ORIGINS con la URL del frontend
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}