package com.ferreteria.ferreteria.modulos.producto.model;

import jakarta.persistence.*;

@Entity
@Table(name = "categoria")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria")
    private Integer id_categoria;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String descripcion = "";

    @Column(name = "estado", nullable = false, length = 10)
    private String estado = "ACTIVO";

    public Categoria() {}

    public Integer getId_categoria() { return id_categoria; }
    public void setId_categoria(Integer id_categoria) { this.id_categoria = id_categoria; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = (descripcion != null) ? descripcion : ""; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
