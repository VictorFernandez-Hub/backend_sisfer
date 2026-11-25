package com.ferreteria.ferreteria.modulos.reporte.dto;

import java.math.BigDecimal;

/** Un producto del ranking de más vendidos. */
public record ProductoVendido(
        String codigo,
        String producto,
        String categoria,
        String unidad,
        BigDecimal cantidad,
        BigDecimal monto
) {}
