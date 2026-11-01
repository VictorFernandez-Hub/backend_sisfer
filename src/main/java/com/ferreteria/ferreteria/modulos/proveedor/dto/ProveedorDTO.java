package com.ferreteria.ferreteria.modulos.proveedor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class ProveedorDTO {

    private Long id_proveedor;

    @NotBlank(message = "El tipo de documento es obligatorio")
    @Pattern(regexp = "^(RUC|DNI|CE|PAS)$", message = "El tipo de documento debe ser RUC, DNI, CE o PAS")
    private String tipo_doc;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 15, message = "El número de documento no puede superar los 15 caracteres")
    private String num_doc;

    @NotBlank(message = "La razón social es obligatoria")
    @Size(max = 150, message = "La razón social no puede superar los 150 caracteres")
    private String razon_social;

    @Size(max = 150, message = "El nombre comercial no puede superar los 150 caracteres")
    private String nom_comercial;

    @Pattern(regexp = "^$|^[0-9]{6,15}$", message = "El teléfono debe estar vacío o tener entre 6 y 15 dígitos")
    private String telefono;

    @Email(message = "Debe ingresar un formato de correo válido")
    @Size(max = 120, message = "El email no puede superar los 120 caracteres")
    private String email;

    @Size(max = 200, message = "La dirección no puede superar los 200 caracteres")
    private String direccion;

    @Size(max = 100, message = "El contacto no puede superar los 100 caracteres")
    private String contacto;

    @Pattern(regexp = "^(ACTIVO|INACTIVO)$", message = "El estado debe ser ACTIVO o INACTIVO")
    private String estado;

    private LocalDateTime fec_registro;

    public ProveedorDTO() {
    }

    // Getters y Setters
    public Long getId_proveedor() { return id_proveedor; }
    public void setId_proveedor(Long id_proveedor) { this.id_proveedor = id_proveedor; }

    public String getTipo_doc() { return tipo_doc; }
    public void setTipo_doc(String tipo_doc) { this.tipo_doc = tipo_doc; }

    public String getNum_doc() { return num_doc; }
    public void setNum_doc(String num_doc) { this.num_doc = num_doc; }

    public String getRazon_social() { return razon_social; }
    public void setRazon_social(String razon_social) { this.razon_social = razon_social; }

    public String getNom_comercial() { return nom_comercial; }
    public void setNom_comercial(String nom_comercial) { this.nom_comercial = nom_comercial; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFec_registro() { return fec_registro; }
    public void setFec_registro(LocalDateTime fec_registro) { this.fec_registro = fec_registro; }
}
