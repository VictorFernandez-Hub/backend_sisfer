package com.ferreteria.ferreteria.modulos.reporte.controller;

import com.ferreteria.ferreteria.modulos.reporte.dto.AlertaStock;
import com.ferreteria.ferreteria.modulos.reporte.dto.ProductoVendido;
import com.ferreteria.ferreteria.modulos.reporte.dto.RangoReporte;
import com.ferreteria.ferreteria.modulos.reporte.dto.ResumenReporte;
import com.ferreteria.ferreteria.modulos.reporte.dto.VentaPorMetodo;
import com.ferreteria.ferreteria.modulos.reporte.dto.VentaPorVendedor;
import com.ferreteria.ferreteria.modulos.reporte.service.ReportePdfService;
import com.ferreteria.ferreteria.modulos.reporte.service.ReporteService;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Reporte de ventas de solo lectura: resumen, medios de pago, productos más vendidos, vendedores
 * y alertas de stock.
 * Acceso: ADMIN_SISTEMA, ADMIN_NEGOCIO y GERENTE (ver SecurityConfig). Es la gente que ve las ventas
 * de todo el negocio; el vendedor ve sus propias ventas en el módulo Ventas.
 * Todos aceptan ?desde=yyyy-MM-dd&hasta=yyyy-MM-dd. Sin fechas se usa el mes actual.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReporteService reporteService;
    private final ReportePdfService reportePdf;
    private final BitacoraService bitacora;

    public ReporteController(ReporteService reporteService, ReportePdfService reportePdf, BitacoraService bitacora) {
        this.reporteService = reporteService;
        this.reportePdf = reportePdf;
        this.bitacora = bitacora;
    }

    @GetMapping("/resumen")
    public ResponseEntity<ResumenReporte> resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reporteService.resumen(reporteService.rango(desde, hasta)));
    }

    @GetMapping("/por-metodo")
    public ResponseEntity<List<VentaPorMetodo>> porMetodo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reporteService.porMetodo(reporteService.rango(desde, hasta)));
    }

    @GetMapping("/top-productos")
    public ResponseEntity<List<ProductoVendido>> topProductos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer limite) {
        return ResponseEntity.ok(reporteService.topProductos(reporteService.rango(desde, hasta), limite));
    }

    @GetMapping("/por-vendedor")
    public ResponseEntity<List<VentaPorVendedor>> porVendedor(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reporteService.porVendedor(reporteService.rango(desde, hasta)));
    }

    /** El stock de ahora: no depende de fechas. */
    @GetMapping("/alertas-stock")
    public ResponseEntity<List<AlertaStock>> alertasStock() {
        return ResponseEntity.ok(reporteService.alertasStock());
    }

    /** Resumen, medios de pago, más vendidos y vendedores en un PDF que se abre en el navegador. */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        RangoReporte r = reporteService.rango(desde, hasta);
        byte[] pdf = reportePdf.generar(r, reporteService.resumen(r), reporteService.porMetodo(r),
                reporteService.topProductos(r, 10), reporteService.porVendedor(r));
        String nombre = "Reporte-ventas-" + r.desde() + "_" + r.hasta() + ".pdf";
        bitacora.registrar("REPORTES", "DESCARGAR",
                "Descargó el reporte de ventas del " + r.desde().format(FECHA) + " al " + r.hasta().format(FECHA));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nombre).build().toString())
                .body(pdf);
    }

    // ----------------------------------------------------------- errores

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarReglas(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> manejarFormato(MethodArgumentTypeMismatchException ex) {
        String mensaje = "limite".equals(ex.getName())
                ? "El límite debe ser un número."
                : "La fecha \"" + ex.getName() + "\" debe tener el formato AAAA-MM-DD.";
        return ResponseEntity.badRequest().body(Map.of("mensaje", mensaje));
    }
}
