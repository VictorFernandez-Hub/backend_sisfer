package com.ferreteria.ferreteria.modulos.producto.model;

import com.ferreteria.ferreteria.modulos.proveedor.model.Proveedor;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long id_producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_categoria", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_marca", nullable = false)
    private Marca marca;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_unidad", nullable = false)
    private Unidad unidad;

    // Proveedor principal. Puede ser null en productos registrados antes de este campo.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proveedor")
    private Proveedor proveedor;

    @Column(name = "codigo", nullable = false, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion = "";

    @Column(name = "precio_compra", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio_compra = BigDecimal.ZERO;

    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio_venta = BigDecimal.ZERO;

    // El stock SOLO lo mueven los procedimientos (sp_confirmar_compra / venta / ajuste).
    // Por eso JPA nunca lo inserta ni lo actualiza.
    @Column(name = "stock_actual", insertable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal stock_actual = BigDecimal.ZERO;

    @Column(name = "stock_minimo", nullable = false, precision = 12, scale = 3)
    private BigDecimal stock_minimo = BigDecimal.ZERO;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado = "ACTIVO";

    @Column(name = "fec_registro", insertable = false, updatable = false)
    private LocalDateTime fec_registro;

    // MySQL la actualiza sola (ON UPDATE CURRENT_TIMESTAMP)
    @Column(name = "fec_modifica", insertable = false, updatable = false)
    private LocalDateTime fec_modifica;

    public Producto() {}

    public Long getId_producto() { return id_producto; }
    public void setId_producto(Long id_producto) { this.id_producto = id_producto; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public Marca getMarca() { return marca; }
    public void setMarca(Marca marca) { this.marca = marca; }

    public Unidad getUnidad() { return unidad; }
    public void setUnidad(Unidad unidad) { this.unidad = unidad; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = (descripcion != null) ? descripcion : ""; }

    public BigDecimal getPrecio_compra() { return precio_compra; }
    public void setPrecio_compra(BigDecimal precio_compra) { this.precio_compra = precio_compra; }

    public BigDecimal getPrecio_venta() { return precio_venta; }
    public void setPrecio_venta(BigDecimal precio_venta) { this.precio_venta = precio_venta; }

    public BigDecimal getStock_actual() { return stock_actual; }
    public void setStock_actual(BigDecimal stock_actual) { this.stock_actual = stock_actual; }

    public BigDecimal getStock_minimo() { return stock_minimo; }
    public void setStock_minimo(BigDecimal stock_minimo) { this.stock_minimo = stock_minimo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFec_registro() { return fec_registro; }
    public void setFec_registro(LocalDateTime fec_registro) { this.fec_registro = fec_registro; }

    public LocalDateTime getFec_modifica() { return fec_modifica; }
    public void setFec_modifica(LocalDateTime fec_modifica) { this.fec_modifica = fec_modifica; }
}
