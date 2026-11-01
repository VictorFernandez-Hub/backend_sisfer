package com.ferreteria.ferreteria.modulos.usuario.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "rol_modulo")
@IdClass(RolModuloId.class)
public class RolModulo {
    @Id
    @Column(name = "id_rol")
    private Short idRol;

    @Id
    private String modulo;
}