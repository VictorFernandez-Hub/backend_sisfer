package com.ferreteria.ferreteria.modulos.cobro.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Celular de Yape/Plin, cuentas bancarias y qué medios ve el vendedor. */
@Service
public class CobroService {

    private static final Set<String> BANCOS = Set.of("BCP", "BBVA", "INTERBANK", "SCOTIABANK");

    private final JdbcTemplate jdbc;

    public CobroService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lo que el mostrador necesita para cobrar: celular y cuentas que ya tienen número. */
    public Map<String, Object> datosParaVenta() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("celular", celular());
        datos.put("cuentas", jdbc.queryForList("""
                SELECT banco, numero_cuenta AS numeroCuenta, cci
                FROM cuenta_banco
                WHERE estado = 'ACTIVO' AND numero_cuenta <> '' AND cci <> ''
                ORDER BY FIELD(banco, 'BCP', 'BBVA', 'INTERBANK', 'SCOTIABANK')
                """));
        return datos;
    }

    public Map<String, Object> configuracion() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("celular", celular());
        datos.put("metodos", jdbc.queryForList(
                "SELECT id_metodo AS id, codigo, nombre, estado FROM metodo ORDER BY id_metodo"));
        datos.put("cuentas", jdbc.queryForList("""
                SELECT banco, numero_cuenta AS numeroCuenta, cci, estado
                FROM cuenta_banco
                ORDER BY FIELD(banco, 'BCP', 'BBVA', 'INTERBANK', 'SCOTIABANK')
                """));
        return datos;
    }

    @Transactional
    public void guardarCelular(String celular) {
        String limpio = celular == null ? "" : celular.trim();
        if (!limpio.matches("[0-9]{9}")) {
            throw new IllegalArgumentException("El celular de Yape y Plin debe tener 9 números.");
        }
        int filas = jdbc.update("UPDATE parametro SET valor = ? WHERE codigo = 'CELULAR_COBRO'", limpio);
        if (filas == 0) {
            jdbc.update("INSERT INTO parametro (codigo, valor, descripcion) VALUES ('CELULAR_COBRO', ?, 'Celular para Yape y Plin')", limpio);
        }
    }

    @Transactional
    public void guardarCuenta(String banco, String numeroCuenta, String cci, String estado) {
        String codigo = banco == null ? "" : banco.trim().toUpperCase();
        if (!BANCOS.contains(codigo)) {
            throw new IllegalArgumentException("El banco debe ser BCP, BBVA, Interbank o Scotiabank.");
        }
        String cuenta = numeroCuenta == null ? "" : numeroCuenta.trim();
        String interbancario = cci == null ? "" : cci.trim();
        if (cuenta.isEmpty() || interbancario.isEmpty()) {
            throw new IllegalArgumentException("Escriba el número de cuenta y el CCI.");
        }
        if (cuenta.length() > 30 || interbancario.length() > 25) {
            throw new IllegalArgumentException("El número de cuenta o el CCI es demasiado largo.");
        }
        String vigente = "INACTIVO".equalsIgnoreCase(estado) ? "INACTIVO" : "ACTIVO";
        int filas = jdbc.update(
                "UPDATE cuenta_banco SET numero_cuenta = ?, cci = ?, estado = ? WHERE banco = ?",
                cuenta, interbancario, vigente, codigo);
        if (filas == 0) {
            jdbc.update("INSERT INTO cuenta_banco (banco, numero_cuenta, cci, estado) VALUES (?, ?, ?, ?)",
                    codigo, cuenta, interbancario, vigente);
        }
    }

    @Transactional
    public void cambiarMetodo(long id, String estado) {
        String codigo = jdbc.query("SELECT codigo FROM metodo WHERE id_metodo = ?",
                rs -> rs.next() ? rs.getString(1) : null, id);
        if (codigo == null) {
            throw new IllegalArgumentException("Ese medio de pago no existe.");
        }
        if ("EFECTIVO".equals(codigo)) {
            throw new IllegalArgumentException("Efectivo siempre está disponible. Es el cobro del mostrador.");
        }
        String vigente = "INACTIVO".equalsIgnoreCase(estado) ? "INACTIVO" : "ACTIVO";
        jdbc.update("UPDATE metodo SET estado = ? WHERE id_metodo = ?", vigente, id);
    }

    private String celular() {
        List<String> filas = jdbc.queryForList(
                "SELECT valor FROM parametro WHERE codigo = 'CELULAR_COBRO'", String.class);
        return filas.isEmpty() || filas.get(0) == null ? "" : filas.get(0).trim();
    }
}
