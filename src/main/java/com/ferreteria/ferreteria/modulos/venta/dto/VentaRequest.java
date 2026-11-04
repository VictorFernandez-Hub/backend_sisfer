package com.ferreteria.ferreteria.modulos.venta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos que llegan del frontend para registrar (o editar) una venta en BORRADOR.
 * El precio es opcional: si no viene se usa el precio_venta del producto.
 * Solo el ADMIN_NEGOCIO puede vender a un precio distinto (descuentos).
 */
public record VentaRequest(
        @NotNull(message = "Seleccione un cliente") Long idCliente,
        @NotNull(message = "Seleccione el medio de pago") Long idMetodo,
        @Size(max = 300, message = "La observación admite hasta 300 caracteres") String observacion,
        @NotEmpty(message = "Agregue al menos un producto") @Valid List<LineaRequest> lineas
) {
    public record LineaRequest(
            @NotNull(message = "Seleccione un producto") Long idProducto,
            @NotNull(message = "Indique la cantidad")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0") BigDecimal cantidad,
            @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0") BigDecimal precioUnit
    ) {}
}
