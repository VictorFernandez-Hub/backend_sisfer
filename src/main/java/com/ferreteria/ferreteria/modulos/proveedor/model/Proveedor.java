package com.ferreteria.ferreteria.modulos.proveedor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "proveedor")
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor")
    private Long id_proveedor;

    // Usuario que registró al proveedor (se asigna una sola vez, al crear)
    @Column(name = "id_usuario", nullable = false, updatable = false)
    private Integer id_usuario;

    @Column(name = "tipo_doc", nullable = false, length = 10)
    private String tipo_doc = "RUC";

    @Column(name = "num_doc", nullable = false, length = 15)
    private String num_doc;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razon_social;

    @Column(name = "nom_comercial", nullable = false, length = 150)
    private String nom_comercial = "";

    @Column(name = "telefono", nullable = false, length = 20)
    private String telefono = "";

    @Column(name = "email", nullable = false, length = 120)
    private String email = "";

    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion = "";

    @Column(name = "contacto", nullable = false, length = 100)
    private String contacto = "";

    @Column(name = "estado", nullable = false, length = 10)
    private String estado = "ACTIVO";

    @Column(name = "fec_registro", insertable = false, updatable = false)
    private LocalDateTime fec_registro;

    public Proveedor() {}

    public Long getId_proveedor() { return id_proveedor; }
    public void setId_proveedor(Long id_proveedor) { this.id_proveedor = id_proveedor; }

    public Integer getId_usuario() { return id_usuario; }
    public void setId_usuario(Integer id_usuario) { this.id_usuario = id_usuario; }

    public String getTipo_doc() { return tipo_doc; }
    public void setTipo_doc(String tipo_doc) { this.tipo_doc = tipo_doc; }

    public String getNum_doc() { return num_doc; }
    public void setNum_doc(String num_doc) { this.num_doc = num_doc; }

    public String getRazon_social() { return razon_social; }
    public void setRazon_social(String razon_social) { this.razon_social = razon_social; }

    public String getNom_comercial() { return nom_comercial; }
    public void setNom_comercial(String nom_comercial) { this.nom_comercial = (nom_comercial != null) ? nom_comercial : ""; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = (telefono != null) ? telefono : ""; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = (email != null) ? email : ""; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = (direccion != null) ? direccion : ""; }

    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = (contacto != null) ? contacto : ""; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFec_registro() { return fec_registro; }
    public void setFec_registro(LocalDateTime fec_registro) { this.fec_registro = fec_registro; }
}
