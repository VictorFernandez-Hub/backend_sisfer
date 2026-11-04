package com.ferreteria.ferreteria.config;

import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import jakarta.servlet.http.HttpServletResponse;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, BitacoraService bitacora) throws Exception {
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

                // Seguridad: administrador del sistema o un rol con el módulo asignado en "Roles y accesos"
                .requestMatchers("/api/usuarios/**", "/usuarios/**")
                    .hasAnyAuthority("ADMIN_SISTEMA", "ROLE_ADMIN_SISTEMA", "MOD_USUARIOS")
                .requestMatchers("/api/roles/**", "/roles/**")
                    .hasAnyAuthority("ADMIN_SISTEMA", "ROLE_ADMIN_SISTEMA", "MOD_ROLES")
                // Aviso de "ingresó al módulo": lo envía el frontend de cualquier usuario con sesión
                .requestMatchers(HttpMethod.POST, "/api/bitacora/ingreso").authenticated()
                .requestMatchers("/api/bitacora/**")
                    .hasAnyAuthority("ADMIN_SISTEMA", "ROLE_ADMIN_SISTEMA", "MOD_BITACORA")

                .requestMatchers("/api/clientes/**").authenticated()
                .requestMatchers("/api/productos/**", "/api/inventario/**").authenticated()
                .requestMatchers("/api/proveedores/**").authenticated()

                // MOD_xxx = módulo asignado al rol en "Roles y accesos" (UserDetailsServiceImpl).
                // Da acceso completo al módulo; así funcionan los roles creados desde la pantalla.
                // El gerente solo consulta ventas (permiso VTA_VER). El administrador del sistema tiene acceso total.
                .requestMatchers(HttpMethod.GET, "/api/ventas/**")
                    .hasAnyAuthority("ADMIN_SISTEMA", "ROLE_ADMIN_SISTEMA", "ADMIN_NEGOCIO", "ROLE_ADMIN_NEGOCIO", "VENDEDOR", "ROLE_VENDEDOR", "GERENTE", "ROLE_GERENTE", "MOD_VENTAS")
                .requestMatchers("/api/ventas/**")
                    .hasAnyAuthority("ADMIN_SISTEMA", "ROLE_ADMIN_SISTEMA", "ADMIN_NEGOCIO", "ROLE_ADMIN_NEGOCIO", "VENDEDOR", "ROLE_VENDEDOR", "MOD_VENTAS")
                // El gerente solo consulta las compras; registrar, confirmar y anular queda para los demás
                .requestMatchers(HttpMethod.GET, "/api/compras/**")
                    .hasAnyAuthority("ADMIN_SISTEMA", "ROLE_ADMIN_SISTEMA", "ADMIN_NEGOCIO", "ROLE_ADMIN_NEGOCIO", "ALMACENERO", "ROLE_ALMACENERO", "GERENTE", "ROLE_GERENTE", "MOD_COMPRAS")
                .requestMatchers("/api/compras/**")
                    .hasAnyAuthority("ADMIN_SISTEMA", "ROLE_ADMIN_SISTEMA", "ADMIN_NEGOCIO", "ROLE_ADMIN_NEGOCIO", "ALMACENERO", "ROLE_ALMACENERO", "MOD_COMPRAS")
                .requestMatchers("/api/reportes/**")
                    .hasAnyAuthority("ADMIN_SISTEMA", "ROLE_ADMIN_SISTEMA", "ADMIN_NEGOCIO", "ROLE_ADMIN_NEGOCIO", "GERENTE", "ROLE_GERENTE")

                .anyRequest().authenticated()
            )
            // Un usuario con sesión que intenta usar algo que su rol no permite queda en la bitácora
            .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, denegado) -> {
                String ruta = request.getRequestURI();
                String[] partes = ruta.split("/");
                String modulo = partes.length > 2 ? partes[2].toUpperCase() : "SISTEMA";
                bitacora.registrarFallo(modulo, "ACCESO_DENEGADO", "",
                        "Intentó " + request.getMethod() + " " + ruta + " sin permiso");
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            }));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Local: Angular en cualquier puerto de localhost. Producción: CORS_ALLOWED_ORIGINS con la URL del frontend
        configuration.setAllowedOriginPatterns(allowedOrigins);
        // PATCH lo usa /api/productos/{id}/estado (activar/desactivar)
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}