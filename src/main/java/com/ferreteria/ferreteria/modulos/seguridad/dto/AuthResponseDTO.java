package com.ferreteria.ferreteria.modulos.seguridad.dto;

import java.util.List;

public class AuthResponseDTO {

    private String cuenta;
    private String mensaje;
    private List<String> roles;

    public AuthResponseDTO() {}

    public AuthResponseDTO(String cuenta, String mensaje, List<String> roles) {
        this.cuenta = cuenta;
        this.mensaje = mensaje;
        this.roles = roles;
    }

    public String getCuenta() { return cuenta; }
    public void setCuenta(String cuenta) { this.cuenta = cuenta; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}