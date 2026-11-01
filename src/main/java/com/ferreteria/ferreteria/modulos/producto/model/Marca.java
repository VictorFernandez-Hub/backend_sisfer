package com.ferreteria.ferreteria.modulos.producto.model;

import jakarta.persistence.*;

@Entity
@Table(name = "marca")
public class Marca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_marca")
    private Integer id_marca;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado = "ACTIVO";

    public Marca() {}

    public Integer getId_marca() { return id_marca; }
    public void setId_marca(Integer id_marca) { this.id_marca = id_marca; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
