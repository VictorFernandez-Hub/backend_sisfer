package com.ferreteria.ferreteria.modulos.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequestDTO {

    @NotBlank(message = "El usuario/cuenta es obligatorio")
    private String cuenta;

    @NotBlank(message = "La contraseña es obligatoria")
    private String clave;

    public LoginRequestDTO() {}

    public String getCuenta() { return cuenta; }
    public void setCuenta(String cuenta) { this.cuenta = cuenta; }

    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
}