package com.ferreteria.ferreteria.modulos.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Totales del periodo. Vendido solo cuenta ventas CONFIRMADA.
 * Anuladas son las que llegaron a cobrarse (tienen comprobante) y luego se anularon.
 */
public record ResumenReporte(
        LocalDate desde,
        LocalDate hasta,
        BigDecimal totalVendido,
        long numVentas,
        long numAnuladas,
        BigDecimal montoAnuladas
) {}
