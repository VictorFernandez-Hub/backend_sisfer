package com.ferreteria.ferreteria.modulos.ajuste.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Un ajuste de un solo producto, confirmado al guardarlo. */
public record AjusteRequest(
        @NotNull(message = "Seleccione un producto") Long idProducto,
        @NotBlank(message = "Indique si es entrada o salida") String tipo,
        @NotNull(message = "Indique la cantidad")
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0") BigDecimal cantidad,
        @NotBlank(message = "Indique el motivo") String motivo,
        @NotBlank(message = "Explique por qué se hace el ajuste")
        @Size(max = 300, message = "La observación admite hasta 300 caracteres") String observacion,
        BigDecimal costoUnit
) {}
