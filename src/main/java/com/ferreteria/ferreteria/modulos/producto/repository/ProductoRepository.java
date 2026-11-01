package com.ferreteria.ferreteria.modulos.producto.repository;

import com.ferreteria.ferreteria.modulos.producto.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // JOIN FETCH trae categoría, marca y unidad en una sola consulta (evita N+1)
    // LEFT JOIN en proveedor: los productos antiguos pueden no tenerlo
    @Query("SELECT p FROM Producto p JOIN FETCH p.categoria JOIN FETCH p.marca JOIN FETCH p.unidad " +
           "LEFT JOIN FETCH p.proveedor ORDER BY p.nombre")
    List<Producto> findAllConRelaciones();

    @Query("SELECT p FROM Producto p JOIN FETCH p.categoria JOIN FETCH p.marca JOIN FETCH p.unidad " +
           "LEFT JOIN FETCH p.proveedor WHERE p.estado = :estado ORDER BY p.nombre")
    List<Producto> findByEstadoConRelaciones(@Param("estado") String estado);

    @Query("SELECT p FROM Producto p JOIN FETCH p.categoria JOIN FETCH p.marca JOIN FETCH p.unidad " +
           "LEFT JOIN FETCH p.proveedor WHERE p.id_producto = :id")
    Optional<Producto> findByIdConRelaciones(@Param("id") Long id);

    // Equivale a la restricción unq_producto_codigo
    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.codigo = :codigo")
    boolean existsByCodigo(@Param("codigo") String codigo);

    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.codigo = :codigo AND p.id_producto <> :id")
    boolean existsByCodigoEnOtroProducto(@Param("codigo") String codigo, @Param("id") Long id);
}
