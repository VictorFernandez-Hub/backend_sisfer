package com.ferreteria.ferreteria.modulos.caja.dto;

/** Persona que puede entregar el fondo inicial de una caja (lista del formulario de apertura). */
public record EntregadorFondo(Integer idUsuario, String cuenta, String nombre, String rol, String codigoRol) {}
