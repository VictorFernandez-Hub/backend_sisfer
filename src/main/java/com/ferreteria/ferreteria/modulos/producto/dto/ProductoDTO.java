package com.ferreteria.ferreteria.modulos.producto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductoDTO {

    private Long id_producto;

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 30, message = "El código no puede superar los 30 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "El código solo admite letras, números y guiones (ej. HER-001)")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    private String nombre;

    @Size(max = 300, message = "La descripción no puede superar los 300 caracteres")
    private String descripcion;

    // ----- Relaciones (se envían como IDs) -----
    @NotNull(message = "La categoría es obligatoria")
    private Integer id_categoria;

    @NotNull(message = "La marca es obligatoria")
    private Integer id_marca;

    @NotNull(message = "La unidad de medida es obligatoria")
    private Integer id_unidad;

    @NotNull(message = "El proveedor es obligatorio")
    private Long id_proveedor;

    // ----- Solo lectura (para mostrar en la tabla) -----
    private String proveedor;
    private String categoria;
    private String marca;
    private String unidad;
    private String unidad_codigo;
    private Boolean unidad_entera;

    // ----- Precios en soles (opcionales: el de compra lo actualiza sp_confirmar_compra) -----
    @DecimalMin(value = "0.00", message = "El precio de compra no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El precio de compra admite máximo 2 decimales")
    private BigDecimal precio_compra;

    @DecimalMin(value = "0.00", message = "El precio de venta no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El precio de venta admite máximo 2 decimales")
    private BigDecimal precio_venta;

    // ----- Stock -----
    private BigDecimal stock_actual; // Solo lectura: lo mueven compras, ventas y ajustes

    // Solo al crear: se registra como ajuste de entrada para que el kardex cuadre
    @DecimalMin(value = "0.000", message = "El stock inicial no puede ser negativo")
    @Digits(integer = 9, fraction = 3, message = "El stock inicial admite máximo 3 decimales")
    private BigDecimal stock_inicial;

    @NotNull(message = "El stock mínimo es obligatorio")
    @DecimalMin(value = "0.000", message = "El stock mínimo no puede ser negativo")
    @Digits(integer = 9, fraction = 3, message = "El stock mínimo admite máximo 3 decimales")
    private BigDecimal stock_minimo;

    private String nivel_stock; // OK, STOCK_MINIMO o AGOTADO (igual que v_alerta_stock)

    // Solo lectura: false si tiene movimientos (kardex, compras, ventas o ajustes) y solo puede desactivarse
    private Boolean eliminable;

    @Pattern(regexp = "^(ACTIVO|INACTIVO)$", message = "El estado debe ser ACTIVO o INACTIVO")
    private String estado;

    private LocalDateTime fec_registro;
    private LocalDateTime fec_modifica;

    public ProductoDTO() {
    }

    // Getters y Setters
    public Long getId_producto() { return id_producto; }
    public void setId_producto(Long id_producto) { this.id_producto = id_producto; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Integer getId_categoria() { return id_categoria; }
    public void setId_categoria(Integer id_categoria) { this.id_categoria = id_categoria; }

    public Integer getId_marca() { return id_marca; }
    public void setId_marca(Integer id_marca) { this.id_marca = id_marca; }

    public Integer getId_unidad() { return id_unidad; }
    public void setId_unidad(Integer id_unidad) { this.id_unidad = id_unidad; }

    public Long getId_proveedor() { return id_proveedor; }
    public void setId_proveedor(Long id_proveedor) { this.id_proveedor = id_proveedor; }

    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }

    public String getUnidad_codigo() { return unidad_codigo; }
    public void setUnidad_codigo(String unidad_codigo) { this.unidad_codigo = unidad_codigo; }

    public Boolean getUnidad_entera() { return unidad_entera; }
    public void setUnidad_entera(Boolean unidad_entera) { this.unidad_entera = unidad_entera; }

    public BigDecimal getPrecio_compra() { return precio_compra; }
    public void setPrecio_compra(BigDecimal precio_compra) { this.precio_compra = precio_compra; }

    public BigDecimal getPrecio_venta() { return precio_venta; }
    public void setPrecio_venta(BigDecimal precio_venta) { this.precio_venta = precio_venta; }

    public BigDecimal getStock_actual() { return stock_actual; }
    public void setStock_actual(BigDecimal stock_actual) { this.stock_actual = stock_actual; }

    public BigDecimal getStock_inicial() { return stock_inicial; }
    public void setStock_inicial(BigDecimal stock_inicial) { this.stock_inicial = stock_inicial; }

    public BigDecimal getStock_minimo() { return stock_minimo; }
    public void setStock_minimo(BigDecimal stock_minimo) { this.stock_minimo = stock_minimo; }

    public String getNivel_stock() { return nivel_stock; }
    public void setNivel_stock(String nivel_stock) { this.nivel_stock = nivel_stock; }

    public Boolean getEliminable() { return eliminable; }
    public void setEliminable(Boolean eliminable) { this.eliminable = eliminable; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFec_registro() { return fec_registro; }
    public void setFec_registro(LocalDateTime fec_registro) { this.fec_registro = fec_registro; }

    public LocalDateTime getFec_modifica() { return fec_modifica; }
    public void setFec_modifica(LocalDateTime fec_modifica) { this.fec_modifica = fec_modifica; }
}
