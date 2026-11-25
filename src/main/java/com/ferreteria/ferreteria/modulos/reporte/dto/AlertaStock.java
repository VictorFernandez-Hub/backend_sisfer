package com.ferreteria.ferreteria.modulos.reporte.dto;

import java.math.BigDecimal;

/** Producto activo que se agotó o llegó a su stock mínimo (vista v_alerta_stock). */
public record AlertaStock(
        String codigo,
        String producto,
        String categoria,
        String marca,
        BigDecimal stockActual,
        BigDecimal stockMinimo,
        String nivel
) {}
