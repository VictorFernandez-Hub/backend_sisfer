package com.ferreteria.ferreteria.modulos.reporte.dto;

import java.math.BigDecimal;

/** Lo vendido por cada usuario que registró ventas en el periodo. */
public record VentaPorVendedor(
        String vendedor,
        String cuenta,
        long ventas,
        BigDecimal monto
) {}
