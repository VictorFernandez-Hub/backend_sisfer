package com.ferreteria.ferreteria.modulos.venta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Venta de mostrador: se registra y se confirma en un solo paso. */
public record CobrarVentaRequest(
        @NotNull(message = "Faltan los datos de la venta") @Valid VentaRequest venta,
        @NotNull(message = "Seleccione el tipo de comprobante") Long idTipoComp
) {}
