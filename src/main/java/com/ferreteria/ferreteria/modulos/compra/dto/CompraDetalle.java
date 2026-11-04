package com.ferreteria.ferreteria.modulos.compra.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Una compra con sus productos, para ver el detalle. */
public record CompraDetalle(
        Long idCompra,
        String numero,
        LocalDate fecCompra,
        Long idProveedor,
        String proveedor,
        String tipoDoc,
        String numDoc,
        BigDecimal totSubtotal,
        BigDecimal totIgv,
        BigDecimal totTotal,
        String estado,
        String observacion,
        String usuario,
        List<Linea> lineas
) {
    public record Linea(
            Long idProducto,
            String codigo,
            String producto,
            String unidad,
            BigDecimal cantidad,
            BigDecimal costoUnit,
            BigDecimal totLinea
    ) {}
}
