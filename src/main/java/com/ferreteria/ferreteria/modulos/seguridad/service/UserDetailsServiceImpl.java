package com.ferreteria.ferreteria.modulos.seguridad.service;

import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UserDetailsServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String cuenta) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCuenta(cuenta)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con cuenta: " + cuenta));

        List<SimpleGrantedAuthority> authorities = (usuario.getRol() != null)
                ? List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getCodigo().toUpperCase()))
                : Collections.emptyList();

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