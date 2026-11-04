package com.ferreteria.ferreteria.modulos.kardex.controller;

import com.ferreteria.ferreteria.modulos.kardex.model.KardexMovimiento;
import com.ferreteria.ferreteria.modulos.kardex.repository.KardexRepository;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta del Kardex (CU007 del ERS).
 * Solo lectura: el Kardex se llena solo cuando se confirma una compra,
 * una venta o un ajuste (regla RC002 y RN004).
 */
@RestController
@RequestMapping("/api/kardex")
@CrossOrigin(origins = "http://localhost:4200")
public class KardexController {

    private final KardexRepository kardexRepository;
    private final BitacoraService bitacora;

    public KardexController(KardexRepository kardexRepository, BitacoraService bitacora) {
        this.kardexRepository = kardexRepository;
        this.bitacora = bitacora;
    }

    /**
     * GET /api/kardex                  -> últimos 500 movimientos
     * GET /api/kardex?codigo=HER-001   -> historial de ese producto
     */
    @GetMapping
    public ResponseEntity<List<KardexMovimiento>> listar(
            @RequestParam(required = false) String codigo) {

        List<KardexMovimiento> movimientos;
        if (codigo != null && !codigo.isBlank()) {
            String c = codigo.trim();
            movimientos = kardexRepository.findByCodigoIgnoreCaseOrderByFecMovDescIdKardexDesc(c);
            bitacora.registrar("KARDEX", "CONSULTAR", "Consultó el kardex del producto " + c);
        } else {
            movimientos = kardexRepository.findTop500ByOrderByFecMovDescIdKardexDesc();
            bitacora.registrar("KARDEX", "CONSULTAR", "Consultó el kardex general");
        }
        return ResponseEntity.ok(movimientos);
    }
}
