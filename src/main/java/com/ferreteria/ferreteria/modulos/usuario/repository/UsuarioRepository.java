package com.ferreteria.ferreteria.modulos.usuario.repository;

import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


import java.util.List;
import java.util.Optional;


public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByCuenta(String cuenta);
    Optional<Usuario> findByEmail(String email);

    // Consulta con JOIN FETCH para traer el Rol cargado
    @Query("SELECT DISTINCT u FROM Usuario u LEFT JOIN FETCH u.rol")
    List<Usuario> findAllConRol();

    java.util.Optional<Usuario> findByDni(String dni);
}