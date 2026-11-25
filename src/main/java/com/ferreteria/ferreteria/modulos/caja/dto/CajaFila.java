package com.ferreteria.ferreteria.modulos.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una fila del historial de cajas. */
public record CajaFila(
        Long idCaja,
        String numero,
        String usuario,
        String nombreUsuario,
        LocalDateTime fecApertura,
        LocalDateTime fecCierre,
        BigDecimal montoApertura,
        String entregadoPor,
        Integer numVentas,
        BigDecimal totalVentas,
        BigDecimal efectivoEsperado,
        BigDecimal efectivoContado,
        BigDecimal diferencia,
        String estado
) {}
