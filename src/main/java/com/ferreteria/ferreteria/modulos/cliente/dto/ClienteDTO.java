package com.ferreteria.ferreteria.modulos.cliente.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ClienteDTO {

    private Long id_cliente;

    @NotBlank(message = "El tipo de documento es obligatorio")
    @Pattern(regexp = "^(DNI|RUC)$", message = "El tipo de documento solo puede ser DNI o RUC")
    private String tipo_doc;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 11, message = "El número de documento no puede superar los 11 dígitos")
    private String num_doc;

    @NotBlank(message = "El nombre o razón social es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombres;

    @Pattern(regexp = "^$|^[0-9]{9}$", message = "El teléfono debe estar vacío o contener exactamente 9 dígitos numéricos")
    @Size(max = 9, message = "El teléfono no puede superar los 9 caracteres")
    private String telefono;

    @Size(max = 200, message = "La dirección no puede superar los 200 caracteres")
    private String direccion;

    @Email(message = "Debe ingresar un formato de correo válido")
    @Size(max = 120, message = "El email no puede superar los 120 caracteres")
    private String email;

    @Pattern(regexp = "^(ACTIVO|INACTIVO)$", message = "El estado debe ser ACTIVO o INACTIVO")
    private String estado;

    private LocalDateTime fec_registro;

    public ClienteDTO() {
    }

    // Getters y Setters
    public Long getId_cliente() {
        return id_cliente;
    }

    public void setId_cliente(Long id_cliente) {
        this.id_cliente = id_cliente;
    }

    public String getTipo_doc() {
        return tipo_doc;
    }

    public void setTipo_doc(String tipo_doc) {
        this.tipo_doc = tipo_doc;
    }

    public String getNum_doc() {
        return num_doc;
    }

    public void setNum_doc(String num_doc) {
        this.num_doc = num_doc;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFec_registro() {
        return fec_registro;
    }

    public void setFec_registro(LocalDateTime fec_registro) {
        this.fec_registro = fec_registro;
    }
}