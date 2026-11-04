package com.ferreteria.ferreteria.modulos.compra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Datos que llegan del frontend para registrar una compra en BORRADOR. */
public record CompraRequest(
        @NotNull(message = "Seleccione un proveedor") Long idProveedor,
        @NotNull(message = "Indique la fecha de la compra") LocalDate fecCompra,
        @Size(max = 20, message = "El tipo de documento admite hasta 20 caracteres") String tipoDoc,
        @Size(max = 30, message = "El número de documento admite hasta 30 caracteres") String numDoc,
        @Size(max = 300, message = "La observación admite hasta 300 caracteres") String observacion,
        @NotEmpty(message = "Agregue al menos un producto") @Valid List<LineaRequest> lineas
) {
    public record LineaRequest(
            @NotNull(message = "Seleccione un producto") Long idProducto,
            @NotNull(message = "Indique la cantidad")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0") BigDecimal cantidad,
            @NotNull(message = "Indique el costo unitario")
            @DecimalMin(value = "0.00", message = "El costo no puede ser negativo") BigDecimal costoUnit
    ) {}
}
