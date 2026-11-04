package com.ferreteria.ferreteria.modulos.compra.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una fila del listado de compras. */
public record CompraResumen(
        Long idCompra,
        String numero,
        LocalDate fecCompra,
        String proveedor,
        String tipoDoc,
        String numDoc,
        BigDecimal totTotal,
        String estado,
        String usuario
) {}
