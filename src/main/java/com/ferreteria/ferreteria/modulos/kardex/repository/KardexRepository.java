package com.ferreteria.ferreteria.modulos.kardex.repository;

import com.ferreteria.ferreteria.modulos.kardex.model.KardexMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KardexRepository extends JpaRepository<KardexMovimiento, Long> {

    // Últimos movimientos de todos los productos (los más recientes primero)
    List<KardexMovimiento> findTop500ByOrderByFecMovDescIdKardexDesc();

    // Historial de un solo producto, buscado por su código (ej. HER-001)
    List<KardexMovimiento> findByCodigoIgnoreCaseOrderByFecMovDescIdKardexDesc(String codigo);
}
