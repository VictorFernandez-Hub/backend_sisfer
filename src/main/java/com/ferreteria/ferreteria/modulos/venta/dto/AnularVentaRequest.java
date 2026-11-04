package com.ferreteria.ferreteria.modulos.venta.dto;

import jakarta.validation.constraints.Size;

/** Motivo de la anulación. Es obligatorio cuando la venta ya estaba confirmada. */
public record AnularVentaRequest(
        @Size(max = 150, message = "El motivo admite hasta 150 caracteres") String motivo
) {}
