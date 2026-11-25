package com.ferreteria.ferreteria.modulos.reporte.dto;

import java.time.LocalDate;

/** Periodo del reporte. Ambas fechas se incluyen completas. */
public record RangoReporte(LocalDate desde, LocalDate hasta) {}
