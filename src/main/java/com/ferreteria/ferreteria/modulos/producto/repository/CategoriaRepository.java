package com.ferreteria.ferreteria.modulos.producto.repository;

import com.ferreteria.ferreteria.modulos.producto.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    List<Categoria> findByEstadoOrderByNombreAsc(String estado);
}
