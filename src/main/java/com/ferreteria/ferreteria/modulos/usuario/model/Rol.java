package com.ferreteria.ferreteria.modulos.usuario.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rol")
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Short idRol;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    @Column(name = "es_tecnico")
    private Boolean esTecnico;

    @Column(length = 10)
    private String estado;

    @Column(name = "fec_registro")
    private LocalDateTime fecRegistro;
}