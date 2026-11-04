package com.ferreteria.ferreteria.modulos.venta.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una fila del listado de ventas. */
public record VentaResumen(
        Long idVenta,
        String numero,
        LocalDateTime fecVenta,
        String cliente,
        String docCliente,
        String metodo,
        String comprobante,
        BigDecimal totTotal,
        String estado,
        String usuario
) {}
