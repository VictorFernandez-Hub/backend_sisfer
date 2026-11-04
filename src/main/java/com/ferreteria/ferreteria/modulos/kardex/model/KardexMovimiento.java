package com.ferreteria.ferreteria.modulos.kardex.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un movimiento del Kardex (una entrada o una salida de un producto).
 *
 * Se lee desde la vista v_kardex de la base SISFER, que ya une el kardex
 * con el producto y con el usuario responsable. Es SOLO LECTURA: los
 * movimientos los escriben automáticamente los procedimientos
 * sp_confirmar_compra, sp_confirmar_venta y sp_confirmar_ajuste.
 */
@Data
@Entity
@Immutable
@Table(name = "v_kardex")
public class KardexMovimiento {

    @Id
    @Column(name = "id_kardex")
    private Long idKardex;

    @Column(name = "codigo")
    private String codigo;

    @Column(name = "producto")
    private String producto;

    @Column(name = "fec_mov")
    private LocalDateTime fecMov;

    // ENTRADA_COMPRA, SALIDA_VENTA, AJUSTE_ENTRADA, AJUSTE_SALIDA,
    // ANULACION_COMPRA o ANULACION_VENTA
    @Column(name = "tipo_mov")
    private String tipoMov;

    // De dónde viene el movimiento: COMPRA, VENTA o AJUSTE
    @Column(name = "documento")
    private String documento;

    // Número de esa compra, venta o ajuste
    @Column(name = "id_doc")
    private Long idDoc;

    @Column(name = "cantidad")
    private BigDecimal cantidad;

    @Column(name = "stock_ant")
    private BigDecimal stockAnt;

    @Column(name = "stock_nuevo")
    private BigDecimal stockNuevo;

    @Column(name = "costo_unit")
    private BigDecimal costoUnit;

    // Nombre completo del usuario responsable
    @Column(name = "usuario")
    private String usuario;
}
