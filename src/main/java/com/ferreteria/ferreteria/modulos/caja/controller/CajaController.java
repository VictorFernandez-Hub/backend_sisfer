package com.ferreteria.ferreteria.modulos.caja.controller;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.caja.dto.AbrirCajaRequest;
import com.ferreteria.ferreteria.modulos.caja.dto.CajaDetalle;
import com.ferreteria.ferreteria.modulos.caja.dto.CajaFila;
import com.ferreteria.ferreteria.modulos.caja.dto.CerrarCajaRequest;
import com.ferreteria.ferreteria.modulos.caja.dto.EntregadorFondo;
import com.ferreteria.ferreteria.modulos.caja.dto.MovimientoRequest;
import com.ferreteria.ferreteria.modulos.caja.service.CajaService;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Caja del turno de ventas: apertura, entradas y salidas de efectivo, arqueo y cierre.
 * Abren y cierran: quienes cobran ventas. Consulta: también el gerente (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final CajaService cajaService;
    private final BitacoraService bitacora;

    public CajaController(CajaService cajaService, BitacoraService bitacora) {
        this.cajaService = cajaService;
        this.bitacora = bitacora;
    }

    /** Caja abierta del usuario. "abierta": false si todavía no abrió su turno. */
    @GetMapping("/actual")
    public ResponseEntity<Map<String, Object>> actual() {
        Map<String, Object> r = new LinkedHashMap<>();
        CajaDetalle caja = cajaService.actual().orElse(null);
        r.put("abierta", caja != null);
        r.put("caja", caja);
        r.put("fondoMaximo", cajaService.fondoMaximo());
        return ResponseEntity.ok(r);
    }

    /** GET /api/caja?desde=2026-11-01&hasta=2026-11-30&estado=CERRADA */
    @GetMapping
    public ResponseEntity<List<CajaFila>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("\"Desde\" no puede ser posterior a \"Hasta\"");
        }
        return ResponseEntity.ok(cajaService.listar(desde, hasta, estado));
    }

    /** Personas que pueden entregar el fondo inicial (combo de la apertura). */
    @GetMapping("/entregadores")
    public ResponseEntity<List<EntregadorFondo>> entregadores() {
        return ResponseEntity.ok(cajaService.entregadores());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CajaDetalle> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(cajaService.obtener(id));
    }

    @PostMapping("/abrir")
    public ResponseEntity<CajaDetalle> abrir(@Valid @RequestBody AbrirCajaRequest request) {
        CajaDetalle c = cajaService.abrir(request.montoApertura(), request.idEntregadoPor(), request.observacion());
        StringBuilder detalle = new StringBuilder("Abrió la caja " + c.numero() + " con un fondo de S/ " + c.montoApertura());
        if (c.entregadoPor() != null) {
            detalle.append(", entregado por ").append(c.entregadoPor())
                   .append(" (").append(c.nombreEntregadoPor()).append(")");
        } else {
            detalle.append(" (sin fondo inicial)");
        }
        if (c.obsApertura() != null && !c.obsApertura().isBlank()) {
            detalle.append(". Nota: ").append(c.obsApertura());
        }
        bitacora.registrar("CAJA", "ABRIR", c.idCaja(),
                detalle.length() > 300 ? detalle.substring(0, 300) : detalle.toString());
        return new ResponseEntity<>(c, HttpStatus.CREATED);
    }

    @PostMapping("/movimientos")
    public ResponseEntity<CajaDetalle> movimiento(@Valid @RequestBody MovimientoRequest request) {
        CajaDetalle c = cajaService.registrarMovimiento(request.tipo(), request.monto(), request.motivo());
        String detalle = ("INGRESO".equalsIgnoreCase(request.tipo().trim()) ? "Ingresó" : "Retiró")
                + " S/ " + request.monto().setScale(2, java.math.RoundingMode.HALF_UP)
                + " en la caja " + c.numero() + ". Motivo: " + request.motivo().trim();
        bitacora.registrar("CAJA", "INGRESO".equalsIgnoreCase(request.tipo().trim()) ? "INGRESO" : "EGRESO",
                c.idCaja(), detalle.length() > 300 ? detalle.substring(0, 300) : detalle);
        return ResponseEntity.ok(c);
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<CajaDetalle> cerrar(@PathVariable Long id, @Valid @RequestBody CerrarCajaRequest request) {
        CajaDetalle c = cajaService.cerrar(id, request.efectivoContado(), request.observacion());
        BigDecimal dif = c.diferencia() == null ? BigDecimal.ZERO : c.diferencia();
        String cuadre = dif.signum() == 0 ? "cuadró"
                : (dif.signum() > 0 ? "sobrante de S/ " + dif : "faltante de S/ " + dif.abs());
        bitacora.registrar("CAJA", "CERRAR", c.idCaja(), "Cerró la caja " + c.numero()
                + ". Esperado S/ " + c.efectivoEsperado() + ", contado S/ " + c.efectivoContado() + " (" + cuadre + ")");
        return ResponseEntity.ok(c);
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
}
