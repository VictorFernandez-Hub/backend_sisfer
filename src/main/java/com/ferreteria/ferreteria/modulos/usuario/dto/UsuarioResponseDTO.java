package com.ferreteria.ferreteria.modulos.usuario.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioResponseDTO {
    private Integer idUsuario;
    private String cuenta;
    private String nombresCompleto;
    private String email;
    private String telefono;
    private String dni;
    private String estado;
    private List<String> roles;
    private String codigoRol;
    private String tipoContrato;
    private java.time.LocalDate fecInicio;
    private java.time.LocalDate fecFin;
}