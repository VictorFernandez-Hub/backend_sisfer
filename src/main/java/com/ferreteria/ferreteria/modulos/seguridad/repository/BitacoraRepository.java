package com.ferreteria.ferreteria.modulos.seguridad.repository;

import com.ferreteria.ferreteria.modulos.seguridad.model.Bitacora;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BitacoraRepository extends JpaRepository<Bitacora, Integer> {
    List<Bitacora> findAllByOrderByFecEventoDesc();
}