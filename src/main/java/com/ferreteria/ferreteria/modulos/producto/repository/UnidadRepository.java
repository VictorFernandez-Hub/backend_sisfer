package com.ferreteria.ferreteria.modulos.producto.repository;

import com.ferreteria.ferreteria.modulos.producto.model.Unidad;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;


public interface UnidadRepository extends JpaRepository<Unidad, Integer> {

    List<Unidad> findAllByOrderByNombreAsc();
}
