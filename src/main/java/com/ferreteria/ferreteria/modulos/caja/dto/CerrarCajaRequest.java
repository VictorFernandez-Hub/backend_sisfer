package com.ferreteria.ferreteria.modulos.caja.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Efectivo contado al cerrar. Si no cuadra con lo esperado, la observación es obligatoria. */
public record CerrarCajaRequest(
        @NotNull(message = "Indique el efectivo que contó en la caja") BigDecimal efectivoContado,
        @Size(max = 300, message = "La observación admite hasta 300 caracteres") String observacion
) {}
