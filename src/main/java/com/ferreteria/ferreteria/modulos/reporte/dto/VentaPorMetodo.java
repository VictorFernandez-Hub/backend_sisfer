package com.ferreteria.ferreteria.modulos.reporte.dto;

import java.math.BigDecimal;

/** Lo cobrado con un medio de pago en el periodo (cierre de caja). */
public record VentaPorMetodo(
        String metodo,
        long ventas,
        BigDecimal monto
) {}
