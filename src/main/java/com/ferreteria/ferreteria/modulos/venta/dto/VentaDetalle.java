package com.ferreteria.ferreteria.modulos.venta.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Una venta con sus productos y su comprobante (si ya se confirmó). */
public record VentaDetalle(
        Long idVenta,
        String numero,
        LocalDateTime fecVenta,
        Long idCliente,
        String cliente,
        String tipoDocCliente,
        String numDocCliente,
        String direccionCliente,
        Long idMetodo,
        String metodo,
        BigDecimal totSubtotal,
        BigDecimal totIgv,
        BigDecimal totTotal,
        String estado,
        String observacion,
        String usuario,
        Comprobante comprobante,
        List<Linea> lineas
) {
    public record Linea(
            Long idProducto,
            String codigo,
            String producto,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precioUnit,
            BigDecimal totLinea
    ) {}

    /** Boleta o factura (no se envía a SUNAT). */
    public record Comprobante(
            String tipo,
            String nombre,
            String serie,
            Long numero,
            String numeroCompleto,
            LocalDateTime fecEmision,
            String estado
    ) {}

    public VentaDetalle conLineas(Comprobante comp, List<Linea> lista) {
        return new VentaDetalle(idVenta, numero, fecVenta, idCliente, cliente, tipoDocCliente, numDocCliente,
                direccionCliente, idMetodo, metodo, totSubtotal, totIgv, totTotal, estado, observacion,
                usuario, comp, lista);
    }
}
