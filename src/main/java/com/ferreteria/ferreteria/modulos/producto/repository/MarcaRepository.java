package com.ferreteria.ferreteria.modulos.producto.repository;

import com.ferreteria.ferreteria.modulos.producto.model.Marca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarcaRepository extends JpaRepository<Marca, Integer> {

    List<Marca> findByEstadoOrderByNombreAsc(String estado);
}
