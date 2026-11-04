package com.ferreteria.ferreteria.modulos.venta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Convierte un monto en soles al texto que va en el comprobante: "SON: DIEZ CON 50/100 SOLES". */
final class MontoEnLetras {

    private static final String[] UNIDADES = {
            "", "UN", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE", "DIEZ",
            "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE",
            "VEINTE", "VEINTIÚN", "VEINTIDÓS", "VEINTITRÉS", "VEINTICUATRO", "VEINTICINCO", "VEINTISÉIS",
            "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"
    };
    private static final String[] DECENAS = {
            "", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"
    };
    private static final String[] CENTENAS = {
            "", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
            "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"
    };

    private MontoEnLetras() {}

    static String convertir(BigDecimal monto) {
        BigDecimal m = monto.setScale(2, RoundingMode.HALF_UP);
        long entero = m.longValue();
        int centimos = m.subtract(BigDecimal.valueOf(entero)).movePointRight(2).intValue();
        String moneda = entero == 1 ? "SOL" : "SOLES";
        return String.format("%s CON %02d/100 %s", entero == 0 ? "CERO" : enLetras(entero), centimos, moneda);
    }

    private static String enLetras(long n) {
        if (n >= 1_000_000) {
            long millones = n / 1_000_000;
            String texto = millones == 1 ? "UN MILLÓN" : enLetras(millones) + " MILLONES";
            return n % 1_000_000 == 0 ? texto : texto + " " + enLetras(n % 1_000_000);
        }
        if (n >= 1_000) {
            long miles = n / 1_000;
            String texto = miles == 1 ? "MIL" : tresCifras((int) miles) + " MIL";
            return n % 1_000 == 0 ? texto : texto + " " + tresCifras((int) (n % 1_000));
        }
        return tresCifras((int) n);
    }

    private static String tresCifras(int n) {
        if (n == 100) {
            return "CIEN";
        }
        String centena = CENTENAS[n / 100];
        int resto = n % 100;
        if (resto == 0) {
            return centena;
        }
        String decena;
        if (resto < 30) {
            decena = UNIDADES[resto];
        } else {
            decena = DECENAS[resto / 10] + (resto % 10 == 0 ? "" : " Y " + UNIDADES[resto % 10]);
        }
        return centena.isEmpty() ? decena : centena + " " + decena;
    }
}
