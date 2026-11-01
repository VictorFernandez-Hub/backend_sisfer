package com.ferreteria.ferreteria.modulos.producto.model;

import jakarta.persistence.*;

@Entity
@Table(name = "unidad")
public class Unidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_unidad")
    private Integer id_unidad;

    @Column(name = "codigo", nullable = false, length = 10)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 40)
    private String nombre;

    public Unidad() {}

    public Integer getId_unidad() { return id_unidad; }
    public void setId_unidad(Integer id_unidad) { this.id_unidad = id_unidad; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
