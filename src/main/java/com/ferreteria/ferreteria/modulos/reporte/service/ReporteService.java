package com.ferreteria.ferreteria.modulos.reporte.service;

import com.ferreteria.ferreteria.modulos.reporte.dto.AlertaStock;
import com.ferreteria.ferreteria.modulos.reporte.dto.ProductoVendido;
import com.ferreteria.ferreteria.modulos.reporte.dto.RangoReporte;
import com.ferreteria.ferreteria.modulos.reporte.dto.ResumenReporte;
import com.ferreteria.ferreteria.modulos.reporte.dto.VentaPorMetodo;
import com.ferreteria.ferreteria.modulos.reporte.dto.VentaPorVendedor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reporte de ventas de solo lectura, más las alertas de stock.
 * Lo vendido es siempre lo CONFIRMADA: un borrador no se cobró y una anulada ya no cuenta.
 * El periodo se filtra por v.fec_venta, igual que el listado de Ventas.
 */
@Service
public class ReporteService {

    private static final int LIMITE_MAXIMO = 50;

    private final JdbcTemplate jdbc;

    public ReporteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Sin fechas se usa el mes actual. Si llega solo una, la otra se completa con el mismo mes.
     */
    public RangoReporte rango(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = LocalDate.now();
        LocalDate d = desde != null ? desde : (hasta != null ? hasta : hoy).withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : (desde != null ? desde : hoy).withDayOfMonth(1).plusMonths(1).minusDays(1);
        if (d.isAfter(h)) {
            throw new IllegalArgumentException("La fecha \"Desde\" no puede ser posterior a \"Hasta\".");
        }
        return new RangoReporte(d, h);
    }

    public ResumenReporte resumen(RangoReporte r) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN v.estado = 'CONFIRMADA' THEN v.tot_total END), 0) AS vendido,
                       COALESCE(SUM(CASE WHEN v.estado = 'CONFIRMADA' THEN 1 ELSE 0 END), 0) AS num_ventas,
                       COALESCE(SUM(CASE WHEN v.estado = 'ANULADA' AND cp.id_venta IS NOT NULL THEN 1 ELSE 0 END), 0) AS num_anuladas,
                       COALESCE(SUM(CASE WHEN v.estado = 'ANULADA' AND cp.id_venta IS NOT NULL THEN v.tot_total END), 0) AS monto_anuladas
                FROM venta v
                LEFT JOIN comprobante cp ON cp.id_venta = v.id_venta
                WHERE v.fec_venta >= ? AND v.fec_venta < ?
                """,
                (rs, i) -> {
                    BigDecimal vendido = rs.getBigDecimal("vendido");
                    long numVentas = rs.getLong("num_ventas");
                    BigDecimal ticket = numVentas == 0
                            ? BigDecimal.ZERO.setScale(2)
                            : vendido.divide(BigDecimal.valueOf(numVentas), 2, RoundingMode.HALF_UP);
                    return new ResumenReporte(r.desde(), r.hasta(), vendido.setScale(2, RoundingMode.HALF_UP),
                            numVentas, ticket, rs.getLong("num_anuladas"),
                            rs.getBigDecimal("monto_anuladas").setScale(2, RoundingMode.HALF_UP));
                },
                inicio(r), fin(r));
    }

    public List<VentaPorMetodo> porMetodo(RangoReporte r) {
        return jdbc.query("""
                SELECT m.nombre AS metodo, COUNT(*) AS ventas, SUM(v.tot_total) AS monto
                FROM venta v
                JOIN metodo m ON m.id_metodo = v.id_metodo
                WHERE v.estado = 'CONFIRMADA' AND v.fec_venta >= ? AND v.fec_venta < ?
                GROUP BY m.id_metodo, m.nombre
                ORDER BY monto DESC, m.nombre
                """,
                (rs, i) -> new VentaPorMetodo(rs.getString("metodo"), rs.getLong("ventas"), rs.getBigDecimal("monto")),
                inicio(r), fin(r));
    }

    public List<ProductoVendido> topProductos(RangoReporte r, Integer limite) {
        int n = limite == null ? 10 : Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        return jdbc.query("""
                SELECT pr.codigo, pr.nombre, c.nombre AS categoria, un.codigo AS unidad,
                       SUM(l.cantidad) AS cantidad, SUM(l.tot_linea) AS monto
                FROM linea_venta l
                JOIN venta v ON v.id_venta = l.id_venta
                JOIN producto pr ON pr.id_producto = l.id_producto
                JOIN categoria c ON c.id_categoria = pr.id_categoria
                JOIN unidad un ON un.id_unidad = pr.id_unidad
                WHERE v.estado = 'CONFIRMADA' AND v.fec_venta >= ? AND v.fec_venta < ?
                GROUP BY pr.id_producto, pr.codigo, pr.nombre, c.nombre, un.codigo
                ORDER BY monto DESC, cantidad DESC, pr.codigo
                LIMIT ?
                """,
                (rs, i) -> new ProductoVendido(rs.getString("codigo"), rs.getString("nombre"),
                        rs.getString("categoria"), rs.getString("unidad"),
                        rs.getBigDecimal("cantidad"), rs.getBigDecimal("monto")),
                inicio(r), fin(r), n);
    }

    public List<VentaPorVendedor> porVendedor(RangoReporte r) {
        return jdbc.query("""
                SELECT TRIM(CONCAT(u.nombres, ' ', u.apellidos)) AS vendedor, u.cuenta,
                       COUNT(*) AS ventas, SUM(v.tot_total) AS monto
                FROM venta v
                JOIN usuario u ON u.id_usuario = v.id_usuario
                WHERE v.estado = 'CONFIRMADA' AND v.fec_venta >= ? AND v.fec_venta < ?
                GROUP BY u.id_usuario, u.nombres, u.apellidos, u.cuenta
                ORDER BY monto DESC, vendedor
                """,
                (rs, i) -> new VentaPorVendedor(rs.getString("vendedor"), rs.getString("cuenta"),
                        rs.getLong("ventas"), rs.getBigDecimal("monto")),
                inicio(r), fin(r));
    }

    /** No depende de fechas: muestra cómo está el stock ahora. Primero lo agotado. */
    public List<AlertaStock> alertasStock() {
        return jdbc.query("""
                SELECT codigo, nombre, categoria, marca, stock_actual, stock_minimo, nivel_alerta
                FROM v_alerta_stock
                ORDER BY CASE nivel_alerta WHEN 'AGOTADO' THEN 0 ELSE 1 END, nombre
                """,
                (rs, i) -> new AlertaStock(rs.getString("codigo"), rs.getString("nombre"),
                        rs.getString("categoria"), rs.getString("marca"),
                        rs.getBigDecimal("stock_actual"), rs.getBigDecimal("stock_minimo"),
                        rs.getString("nivel_alerta")));
    }

    private LocalDateTime inicio(RangoReporte r) {
        return r.desde().atStartOfDay();
    }

    private LocalDateTime fin(RangoReporte r) {
        return r.hasta().plusDays(1).atStartOfDay();
    }
}
