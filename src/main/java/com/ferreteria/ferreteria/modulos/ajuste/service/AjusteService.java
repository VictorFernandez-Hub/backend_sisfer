package com.ferreteria.ferreteria.modulos.ajuste.service;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.ajuste.dto.AjusteRequest;
import com.ferreteria.ferreteria.modulos.ajuste.dto.AjusteResultado;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Merma, hallazgo o corrección de conteo.
 * El borrador se confirma en el mismo paso llamando a sp_confirmar_ajuste,
 * que es quien mueve el stock y escribe el kardex.
 */
@Service
public class AjusteService {

    private static final Set<String> MOTIVOS = Set.of("MERMA", "HALLAZGO", "CORRECCION", "OTRO");

    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarioRepository;

    public AjusteService(JdbcTemplate jdbc, UsuarioRepository usuarioRepository) {
        this.jdbc = jdbc;
        this.usuarioRepository = usuarioRepository;
    }

    public AjusteResultado registrarYConfirmar(AjusteRequest req) {
        long id = insertarBorrador(req);
        try {
            confirmar(id);
        } catch (RuntimeException e) {
            jdbc.update("UPDATE ajuste SET estado = 'ANULADO' WHERE id_ajuste = ? AND estado = 'BORRADOR'", id);
            throw e;
        }
        List<AjusteResultado> filas = jdbc.query(
                "SELECT id_ajuste, numero, estado FROM ajuste WHERE id_ajuste = ?",
                (rs, i) -> new AjusteResultado(rs.getLong("id_ajuste"), rs.getString("numero"), rs.getString("estado")),
                id);
        return filas.get(0);
    }

    private long insertarBorrador(AjusteRequest req) {
        String tipo = req.tipo() == null ? "" : req.tipo().trim().toUpperCase();
        String motivo = req.motivo() == null ? "" : req.motivo().trim().toUpperCase();
        if (!"ENTRADA".equals(tipo) && !"SALIDA".equals(tipo)) {
            throw new IllegalArgumentException("El ajuste es de entrada o de salida");
        }
        if (!MOTIVOS.contains(motivo)) {
            throw new IllegalArgumentException("El motivo debe ser merma, hallazgo, corrección u otro");
        }
        if ("MERMA".equals(motivo) && !"SALIDA".equals(tipo)) {
            throw new IllegalArgumentException("Una merma es una salida de stock");
        }
        if ("HALLAZGO".equals(motivo) && !"ENTRADA".equals(tipo)) {
            throw new IllegalArgumentException("Un hallazgo es una entrada de stock");
        }
        if ("ENTRADA".equals(tipo) && (req.costoUnit() == null || req.costoUnit().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("La entrada necesita el costo de esas unidades, mayor que cero");
        }
        if (req.observacion() == null || req.observacion().isBlank()) {
            throw new IllegalArgumentException("Explique por qué se hace el ajuste");
        }

        Integer activo = jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto WHERE id_producto = ? AND estado = 'ACTIVO'",
                Integer.class, req.idProducto());
        if (activo == null || activo == 0) {
            throw new IllegalArgumentException("El producto no existe o está inactivo");
        }
        if ("SALIDA".equals(tipo)) {
            BigDecimal stock = jdbc.queryForObject(
                    "SELECT stock_actual FROM producto WHERE id_producto = ?",
                    BigDecimal.class, req.idProducto());
            if (stock == null || stock.compareTo(req.cantidad()) < 0) {
                throw new IllegalArgumentException("No hay stock suficiente para esta salida");
            }
        }

        Integer idUsuario = idUsuarioActual();
        String temporal = "TMP-" + UUID.randomUUID().toString().substring(0, 12);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ajuste (id_usuario, numero, motivo, observacion, estado) VALUES (?, ?, ?, ?, 'BORRADOR')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idUsuario);
            ps.setString(2, temporal);
            ps.setString(3, motivo);
            ps.setString(4, req.observacion().trim());
            return ps;
        }, keys);
        Number clave = keys.getKey();
        if (clave == null) {
            throw new IllegalStateException("No se pudo registrar el ajuste");
        }
        long id = clave.longValue();
        jdbc.update("UPDATE ajuste SET numero = ? WHERE id_ajuste = ?", String.format("AJ-%04d", id), id);
        try {
            jdbc.update("""
                    INSERT INTO linea_ajuste (id_ajuste, id_producto, tipo, cantidad, costo_unit)
                    VALUES (?, ?, ?, ?, ?)
                    """, id, req.idProducto(), tipo, req.cantidad(), "ENTRADA".equals(tipo) ? req.costoUnit() : null);
        } catch (RuntimeException e) {
            jdbc.update("DELETE FROM ajuste WHERE id_ajuste = ? AND estado = 'BORRADOR'", id);
            throw e;
        }
        return id;
    }

    private void confirmar(long id) {
        Integer idUsuario = idUsuarioActual();
        try {
            jdbc.execute((ConnectionCallback<Object>) con -> {
                try (CallableStatement cs = con.prepareCall("{call sp_confirmar_ajuste(?, ?)}")) {
                    cs.setLong(1, id);
                    cs.setInt(2, idUsuario);
                    cs.execute();
                    return null;
                } catch (SQLException e) {
                    try (Statement st = con.createStatement()) {
                        st.execute("ROLLBACK");
                    } catch (SQLException ignorado) {
                        // la conexión queda como estaba
                    }
                    throw e;
                }
            });
        } catch (DataAccessException e) {
            Throwable causa = NestedExceptionUtils.getMostSpecificCause(e);
            String mensaje = causa.getMessage() != null ? causa.getMessage() : "No se pudo confirmar el ajuste";
            throw new IllegalArgumentException(mensaje);
        }
    }

    private Integer idUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalArgumentException("No hay un usuario autenticado en la sesión");
        }
        return usuarioRepository.findByCuenta(auth.getName())
                .map(Usuario::getIdUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario de sesión no encontrado: " + auth.getName()));
    }
}
