package com.ferreteria.ferreteria.modulos.caja.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Entrada (INGRESO) o salida (EGRESO) de efectivo que no es una venta. */
public record MovimientoRequest(
        @NotBlank(message = "Indique si entra o sale dinero") String tipo,
        @NotNull(message = "Indique el monto") BigDecimal monto,
        @NotBlank(message = "Indique el motivo")
        @Size(max = 200, message = "El motivo admite hasta 200 caracteres") String motivo
) {}
