package com.ferreteria.ferreteria.modulos.cobro.controller;

import com.ferreteria.ferreteria.modulos.cobro.service.CobroService;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Datos que el mostrador muestra al cobrar y que el administrador puede cambiar. */
@RestController
@RequestMapping("/api/cobros")
public class CobroController {

    private final CobroService cobroService;
    private final BitacoraService bitacora;

    public CobroController(CobroService cobroService, BitacoraService bitacora) {
        this.cobroService = cobroService;
        this.bitacora = bitacora;
    }

    @GetMapping("/datos")
    public Map<String, Object> datos() {
        return cobroService.datosParaVenta();
    }

    @GetMapping
    public Map<String, Object> configuracion() {
        return cobroService.configuracion();
    }

    @PutMapping("/celular")
    public ResponseEntity<Void> celular(@RequestBody Map<String, String> body) {
        cobroService.guardarCelular(body.get("celular"));
        bitacora.registrar("VENTAS", "EDITAR", "Actualizó el celular de Yape y Plin");
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cuentas/{banco}")
    public ResponseEntity<Void> cuenta(@PathVariable String banco, @RequestBody Map<String, String> body) {
        cobroService.guardarCuenta(banco, body.get("numeroCuenta"), body.get("cci"), body.get("estado"));
        bitacora.registrar("VENTAS", "EDITAR", "Actualizó la cuenta " + banco);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/metodos/{id}/estado")
    public ResponseEntity<Void> metodo(@PathVariable long id, @RequestParam String estado) {
        cobroService.cambiarMetodo(id, estado);
        bitacora.registrar("VENTAS", "EDITAR", "Puso el medio de pago " + id + " en " + estado);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> reglas(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
    }
}
