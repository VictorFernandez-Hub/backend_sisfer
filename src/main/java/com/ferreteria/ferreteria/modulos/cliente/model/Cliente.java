package com.ferreteria.ferreteria.modulos.cliente.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Long id_cliente;

    @Column(name = "tipo_doc", nullable = false, length = 3)
    private String tipo_doc = "DNI";

    @Column(name = "num_doc", nullable = false, length = 11)
    private String num_doc;

    @Column(name = "nombres", nullable = false, length = 150)
    private String nombres;

    @Column(name = "telefono", nullable = false, length = 9)
    private String telefono = "";

    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion = "";

    @Column(name = "email", nullable = false, length = 120)
    private String email = "";

    @Column(name = "estado", nullable = false, length = 10)
    private String estado = "ACTIVO";

    @Column(name = "fec_registro", insertable = false, updatable = false)
    private LocalDateTime fec_registro;

    public Cliente() {}

    public Long getId_cliente() { return id_cliente; }
    public void setId_cliente(Long id_cliente) { this.id_cliente = id_cliente; }

    public String getTipo_doc() { return tipo_doc; }
    public void setTipo_doc(String tipo_doc) { this.tipo_doc = tipo_doc; }

    public String getNum_doc() { return num_doc; }
    public void setNum_doc(String num_doc) { this.num_doc = num_doc; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = (telefono != null) ? telefono : ""; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = (direccion != null) ? direccion : ""; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = (email != null) ? email : ""; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFec_registro() { return fec_registro; }
    public void setFec_registro(LocalDateTime fec_registro) { this.fec_registro = fec_registro; }
}