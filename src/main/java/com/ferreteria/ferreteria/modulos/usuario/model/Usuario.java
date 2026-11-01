package com.ferreteria.ferreteria.modulos.usuario.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(nullable = false, unique = true, length = 50)
    private String cuenta;

    @Column(name = "hash_clave", nullable = false, length = 255)
    private String hashClave;

    @Column(length = 80)
    private String nombres;

    @Column(length = 80)
    private String apellidos;

    @Column(length = 120)
    private String email;

    @Column(length = 20)
    private String telefono;

    @Column(length = 8)
    private String dni;

    @Column(name = "tipo_contrato", length = 20)
    private String tipoContrato;

    @Column(name = "fec_inicio")
    private java.time.LocalDate fecInicio;

    @Column(name = "fec_fin")
    private java.time.LocalDate fecFin;

    @Column(length = 10)
    private String estado;

    @Column(name = "fec_acceso")
    private LocalDateTime fecAcceso;

    @Column(name = "num_intentos")
    private Short numIntentos;

    @Column(name = "fec_registro")
    private LocalDateTime fecRegistro;

    @Column(name = "fec_modifica")
    private LocalDateTime fecModifica;

    // Relación directa con la tabla 'rol' a través de la clave foránea 'id_rol'
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_rol")
    private Rol rol;
}