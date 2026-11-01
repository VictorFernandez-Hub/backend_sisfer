package com.ferreteria.ferreteria.modulos.cliente.repository;

import com.ferreteria.ferreteria.modulos.cliente.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;


public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    @Query("SELECT c FROM Cliente c WHERE c.num_doc = :numDoc")
    Optional<Cliente> findByNumDoc(@Param("numDoc") String numDoc);

    @Query("SELECT COUNT(c) > 0 FROM Cliente c WHERE c.num_doc = :numDoc")
    boolean existsByNumDoc(@Param("numDoc") String numDoc);

    List<Cliente> findByEstado(String estado);
}