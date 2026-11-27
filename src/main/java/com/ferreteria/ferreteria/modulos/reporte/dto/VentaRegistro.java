package com.ferreteria.ferreteria.modulos.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una venta del periodo, confirmada o anulada, con su boleta o factura.
 * Los borradores no aparecen: nunca se cobraron.
 */
public record VentaRegistro(
        String numero,
        LocalDateTime fecha,
        String comprobante,
        String cliente,
        String vendedor,
        String metodo,
        BigDecimal total,
        String estado
) {}
