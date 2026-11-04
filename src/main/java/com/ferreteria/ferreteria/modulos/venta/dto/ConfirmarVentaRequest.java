package com.ferreteria.ferreteria.modulos.venta.dto;

import jakarta.validation.constraints.NotNull;

/** Al confirmar se elige el comprobante: boleta (BOL) o factura (FAC). */
public record ConfirmarVentaRequest(
        @NotNull(message = "Seleccione el tipo de comprobante") Long idTipoComp
) {}
