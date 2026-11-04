package com.ferreteria.ferreteria.modulos.seguridad.service;

import com.ferreteria.ferreteria.modulos.usuario.model.RolModulo;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.RolModuloRepository;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final RolModuloRepository rolModuloRepository;

    public UserDetailsServiceImpl(UsuarioRepository usuarioRepository, RolModuloRepository rolModuloRepository) {
        this.usuarioRepository = usuarioRepository;
        this.rolModuloRepository = rolModuloRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String cuenta) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCuenta(cuenta)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con cuenta: " + cuenta));

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (usuario.getRol() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getCodigo().toUpperCase()));

            // Módulos asignados al rol en "Roles y accesos": MOD_VENTAS, MOD_COMPRAS, etc.
            // Así un rol creado desde la pantalla funciona sin tocar el código (ver SecurityConfig).
            for (RolModulo rm : rolModuloRepository.findByIdRol(usuario.getRol().getIdRol())) {
                authorities.add(new SimpleGrantedAuthority("MOD_" + rm.getModulo().toUpperCase()));
            }
        }

        boolean activo = usuario.getEstado() != null
                && usuario.getEstado().equalsIgnoreCase("ACTIVO");
        boolean bloqueado = usuario.getEstado() != null
                && usuario.getEstado().equalsIgnoreCase("BLOQUEADO");

        return new User(
                usuario.getCuenta(),
                usuario.getHashClave(),
                activo,
                true,
                true,
                !bloqueado,
                authorities
        );
    }
}
