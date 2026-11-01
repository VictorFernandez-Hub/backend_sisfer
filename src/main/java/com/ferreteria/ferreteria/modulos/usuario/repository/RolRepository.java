package com.ferreteria.ferreteria.modulos.usuario.repository;
import com.ferreteria.ferreteria.modulos.usuario.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

    public interface RolRepository extends JpaRepository<Rol, Short> {
    Optional<Rol> findByCodigo(String codigo);
}
