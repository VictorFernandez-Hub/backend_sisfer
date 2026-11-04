package com.ferreteria.ferreteria.modulos.venta.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MontoEnLetrasTest {

    private String letras(String monto) {
        return MontoEnLetras.convertir(new BigDecimal(monto));
    }

    @Test
    void convierteMontosDelMostrador() {
        assertEquals("CIENTO CINCUENTA Y CUATRO CON 30/100 SOLES", letras("154.30"));
        assertEquals("CIEN CON 00/100 SOLES", letras("100"));
        assertEquals("UN CON 50/100 SOL", letras("1.50"));
        assertEquals("CERO CON 90/100 SOLES", letras("0.90"));
        assertEquals("VEINTIÚN CON 00/100 SOLES", letras("21"));
        assertEquals("TREINTA Y UN CON 05/100 SOLES", letras("31.05"));
        assertEquals("DOSCIENTOS DIECISÉIS CON 00/100 SOLES", letras("216"));
    }

    @Test
    void convierteMilesYMillones() {
        assertEquals("MIL CON 00/100 SOLES", letras("1000"));
        assertEquals("MIL OCHOCIENTOS NOVENTA CON 00/100 SOLES", letras("1890"));
        assertEquals("VEINTIÚN MIL CIEN CON 00/100 SOLES", letras("21100"));
        assertEquals("CIENTO UN MIL UN CON 00/100 SOLES", letras("101001"));
        assertEquals("UN MILLÓN DOSCIENTOS MIL CON 00/100 SOLES", letras("1200000"));
    }
}
