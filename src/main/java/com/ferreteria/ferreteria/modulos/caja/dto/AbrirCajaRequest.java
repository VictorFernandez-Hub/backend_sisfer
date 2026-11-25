package com.ferreteria.ferreteria.modulos.caja.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Fondo inicial (sencillo) con el que el vendedor empieza el turno y quién se lo entregó.
 * idEntregadoPor es obligatorio si el fondo es mayor que cero.
 */
public record AbrirCajaRequest(
        @NotNull(message = "Indique el monto con el que abre la caja") BigDecimal montoApertura,
        Integer idEntregadoPor,
        @Size(max = 200, message = "La nota admite hasta 200 caracteres") String observacion
) {}
