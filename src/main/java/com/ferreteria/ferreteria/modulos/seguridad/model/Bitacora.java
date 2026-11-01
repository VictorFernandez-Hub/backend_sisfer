package com.ferreteria.ferreteria.modulos.seguridad.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bitacora")
public class Bitacora {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bitacora")
    private Long idBitacora;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "fec_evento")
    private LocalDateTime fecEvento;

    private String modulo;
    private String accion;

    @Column(name = "tabla_obj")
    private String tablaObj;

    @Column(name = "id_registro")
    private String idRegistro;

    private String detalle;

    @Column(name = "ip_origen")
    private String ipOrigen;

    private Boolean exito;
}