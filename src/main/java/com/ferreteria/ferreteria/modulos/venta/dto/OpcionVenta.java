package com.ferreteria.ferreteria.modulos.venta.dto;

/** Opción para los combos de la venta: medio de pago o tipo de comprobante. */
public record OpcionVenta(
        Long id,
        String codigo,
        String nombre
) {}
