package com.ferreteria.ferreteria.modulos.proveedor.repository;

import com.ferreteria.ferreteria.modulos.proveedor.model.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;


public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    // Equivale a la restricción unq_proveedor_doc (tipo_doc, num_doc)
    @Query("SELECT COUNT(p) > 0 FROM Proveedor p WHERE p.tipo_doc = :tipoDoc AND p.num_doc = :numDoc")
    boolean existsByDocumento(@Param("tipoDoc") String tipoDoc, @Param("numDoc") String numDoc);

    // Igual que la anterior, pero ignorando al proveedor que se está editando
    @Query("SELECT COUNT(p) > 0 FROM Proveedor p WHERE p.tipo_doc = :tipoDoc AND p.num_doc = :numDoc AND p.id_proveedor <> :id")
    boolean existsByDocumentoEnOtroProveedor(@Param("tipoDoc") String tipoDoc,
                                             @Param("numDoc") String numDoc,
                                             @Param("id") Long id);

    @Query("SELECT p FROM Proveedor p ORDER BY p.razon_social")
    List<Proveedor> findAllOrdenados();

    @Query("SELECT p FROM Proveedor p WHERE p.estado = :estado ORDER BY p.razon_social")
    List<Proveedor> findByEstado(@Param("estado") String estado);
}
