package com.ferreteria.ferreteria.modulos.venta.controller;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import com.ferreteria.ferreteria.modulos.venta.dto.AnularVentaRequest;
import com.ferreteria.ferreteria.modulos.venta.dto.CobrarVentaRequest;
import com.ferreteria.ferreteria.modulos.venta.dto.ConfirmarVentaRequest;
import com.ferreteria.ferreteria.modulos.venta.dto.OpcionVenta;
import com.ferreteria.ferreteria.modulos.venta.dto.VentaDetalle;
import com.ferreteria.ferreteria.modulos.venta.dto.VentaRequest;
import com.ferreteria.ferreteria.modulos.venta.dto.VentaResumen;
import com.ferreteria.ferreteria.modulos.venta.service.ComprobantePdfService;
import com.ferreteria.ferreteria.modulos.venta.service.VentaService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ventas: registrar en borrador, editar el borrador, confirmar (baja el stock, llena el kardex
 * y emite boleta o factura) o anular.
 * Registran: VENDEDOR y ADMIN_NEGOCIO. Consulta: también GERENTE (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final ComprobantePdfService comprobantePdf;
    private final BitacoraService bitacora;

    public VentaController(VentaService ventaService, ComprobantePdfService comprobantePdf, BitacoraService bitacora) {
        this.ventaService = ventaService;
        this.comprobantePdf = comprobantePdf;
        this.bitacora = bitacora;
    }

    /**
     * GET /api/ventas                                   -> últimas 500 ventas
     * GET /api/ventas?desde=2026-10-01&hasta=2026-10-31 -> ventas de ese rango
     */
    @GetMapping
    public ResponseEntity<List<VentaResumen>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        // No se anota en bitácora: la pantalla recarga la lista tras cada acción y solo generaba filas de ruido
        return ResponseEntity.ok(ventaService.listar(desde, hasta));
    }

    @GetMapping("/metodos")
    public ResponseEntity<List<OpcionVenta>> metodos() {
        return ResponseEntity.ok(ventaService.metodos());
    }

    @GetMapping("/comprobantes")
    public ResponseEntity<List<OpcionVenta>> tiposComprobante() {
        return ResponseEntity.ok(ventaService.tiposComprobante());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VentaDetalle> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ventaService.obtener(id));
    }

    /**
     * Voucher en PDF: el comprobante si la venta se confirmó, o la proforma si sigue en borrador.
     * Lo descargan los mismos roles que consultan ventas.
     */
    @GetMapping("/{id}/comprobante.pdf")
    public ResponseEntity<byte[]> comprobantePdf(@PathVariable Long id) {
        VentaDetalle v = ventaService.obtener(id);
        if (v.comprobante() == null && !"BORRADOR".equals(v.estado())) {
            throw new IllegalArgumentException("Esta venta se anuló en borrador y no tiene comprobante");
        }
        String nombre = (v.comprobante() != null ? v.comprobante().numeroCompleto() : "Proforma-" + v.numero()) + ".pdf";
        bitacora.registrar("VENTAS", "DESCARGAR", id, "Descargó el PDF " + nombre + " de la venta " + v.numero());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre).build().toString())
                .body(comprobantePdf.generar(v, ventaService.tasaIgv()));
    }

    /**
     * Venta de mostrador: revisa todo antes de guardar, para no dejar borradores de intentos fallidos.
     * Si aun así el procedimiento rechaza la venta (otro la ganó por el stock), se quita el borrador recién creado.
     */
    @PostMapping("/cobrar")
    public ResponseEntity<VentaDetalle> cobrar(@Valid @RequestBody CobrarVentaRequest request) {
        ventaService.validarCobro(request.venta(), request.idTipoComp());
        VentaDetalle borrador = ventaService.registrar(request.venta());
        try {
            ventaService.confirmar(borrador.idVenta(), request.idTipoComp());
        } catch (RuntimeException e) {
            ventaService.eliminarBorrador(borrador.idVenta());
            throw e;
        }
        VentaDetalle v = ventaService.obtener(borrador.idVenta());
        bitacora.registrar("VENTAS", "CONFIRMAR", "Cobró la venta " + v.numero() + " y emitió " + v.comprobante().numeroCompleto());
        return new ResponseEntity<>(v, HttpStatus.CREATED);
    }

    @PostMapping
    public ResponseEntity<VentaDetalle> registrar(@Valid @RequestBody VentaRequest request) {
        VentaDetalle creada = ventaService.registrar(request);
        bitacora.registrar("VENTAS", "CREAR", "Registró la venta " + creada.numero() + " en borrador");
        return new ResponseEntity<>(creada, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VentaDetalle> actualizar(@PathVariable Long id, @Valid @RequestBody VentaRequest request) {
        VentaDetalle v = ventaService.actualizar(id, request);
        bitacora.registrar("VENTAS", "EDITAR", "Editó la venta " + v.numero() + " en borrador");
        return ResponseEntity.ok(v);
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<VentaDetalle> confirmar(@PathVariable Long id,
                                                  @Valid @RequestBody ConfirmarVentaRequest request) {
        ventaService.confirmar(id, request.idTipoComp());
        VentaDetalle v = ventaService.obtener(id);
        String comp = v.comprobante() != null ? v.comprobante().numeroCompleto() : "";
        bitacora.registrar("VENTAS", "CONFIRMAR", "Confirmó la venta " + v.numero() + " y emitió " + comp);
        return ResponseEntity.ok(v);
    }

    @PostMapping("/{id}/anular")
    public ResponseEntity<VentaDetalle> anular(@PathVariable Long id,
                                               @Valid @RequestBody(required = false) AnularVentaRequest request) {
        String motivo = request != null ? request.motivo() : null;
        ventaService.anular(id, motivo);
        VentaDetalle v = ventaService.obtener(id);
        String detalle = "Anuló la venta " + v.numero();
        if (motivo != null && !motivo.isBlank()) {
            detalle += ". Motivo: " + motivo.trim();
        }
        bitacora.registrar("VENTAS", "ANULAR", detalle.length() > 300 ? detalle.substring(0, 300) : detalle);
        return ResponseEntity.ok(v);
    }

    // ----------------------------------------------------------- errores

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> manejarValidaciones(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errores);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarReglas(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> manejarNoEncontrado(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> manejarIntegridad(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", "La operación viola una restricción de la base de datos."));
    }
}
