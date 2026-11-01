package com.ferreteria.ferreteria.modulos.usuario.repository;

import com.ferreteria.ferreteria.modulos.usuario.model.RolModulo;
import com.ferreteria.ferreteria.modulos.usuario.model.RolModuloId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface RolModuloRepository extends JpaRepository<RolModulo, RolModuloId> {
    List<RolModulo> findByIdRol(Short idRol);

    @Modifying
    @Transactional
    void deleteByIdRol(Short idRol);
}