package com.ferreteria.ferreteria.modulos.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Estado completo de un turno de caja: lo que se vendió por cada medio de pago,
 * las entradas y salidas de efectivo y el arqueo.
 * Con la caja abierta, efectivoEsperado se calcula al momento; al cerrarla queda guardado.
 */
public record CajaDetalle(
        Long idCaja,
        String numero,
        String usuario,
        String nombreUsuario,
        LocalDateTime fecApertura,
        BigDecimal montoApertura,
        String entregadoPor,
        String nombreEntregadoPor,
        String obsApertura,
        LocalDateTime fecCierre,
        String cerradoPor,
        String estado,
        boolean deOtroDia,
        int numVentas,
        int numAnuladas,
        BigDecimal totalVentas,
        BigDecimal ventasEfectivo,
        BigDecimal ingresos,
        BigDecimal egresos,
        BigDecimal efectivoEsperado,
        BigDecimal efectivoContado,
        BigDecimal diferencia,
        String obsCierre,
        List<PorMetodo> porMetodo,
        List<Movimiento> movimientos,
        List<Venta> ventas
) {
    public record PorMetodo(String codigo, String metodo, int ventas, BigDecimal total) {}

    public record Movimiento(Long idMovimiento, String tipo, BigDecimal monto, String motivo,
                             LocalDateTime fecha, String usuario) {}

    public record Venta(Long idVenta, String numero, LocalDateTime fecVenta, String cliente,
                        String metodo, String comprobante, BigDecimal total, String estado) {}
}
