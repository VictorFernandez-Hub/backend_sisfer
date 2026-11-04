package com.ferreteria.ferreteria.modulos.compra.service;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.compra.dto.CompraDetalle;
import com.ferreteria.ferreteria.modulos.compra.dto.CompraRequest;
import com.ferreteria.ferreteria.modulos.compra.dto.CompraResumen;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Compras (RF del ERS: ingreso de mercadería).
 * Se guarda primero en BORRADOR. Al confirmar, el procedimiento sp_confirmar_compra
 * sube el stock y escribe el kardex (así el stock nunca se mueve desde Java).
 */
@Service
public class CompraService {

    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarioRepository;

    public CompraService(JdbcTemplate jdbc, UsuarioRepository usuarioRepository) {
        this.jdbc = jdbc;
        this.usuarioRepository = usuarioRepository;
    }

    // ------------------------------------------------------------------ consultas

    public List<CompraResumen> listar() {
        return jdbc.query("""
                SELECT c.id_compra, c.numero, c.fec_compra, p.razon_social, c.tipo_doc, c.num_doc,
                       c.tot_total, c.estado, u.cuenta
                FROM compra c
                JOIN proveedor p ON p.id_proveedor = c.id_proveedor
                JOIN usuario u ON u.id_usuario = c.id_usuario
                ORDER BY c.id_compra DESC
                LIMIT 500
                """,
                (rs, i) -> new CompraResumen(
                        rs.getLong("id_compra"),
                        rs.getString("numero"),
                        rs.getObject("fec_compra", LocalDate.class),
                        rs.getString("razon_social"),
                        rs.getString("tipo_doc"),
                        rs.getString("num_doc"),
                        rs.getBigDecimal("tot_total"),
                        rs.getString("estado"),
                        rs.getString("cuenta")));
    }

    public CompraDetalle obtener(Long id) {
        List<CompraDetalle> cab = jdbc.query("""
                SELECT c.id_compra, c.numero, c.fec_compra, c.id_proveedor, p.razon_social,
                       c.tipo_doc, c.num_doc, c.tot_subtotal, c.tot_igv, c.tot_total,
                       c.estado, c.observacion, u.cuenta
                FROM compra c
                JOIN proveedor p ON p.id_proveedor = c.id_proveedor
                JOIN usuario u ON u.id_usuario = c.id_usuario
                WHERE c.id_compra = ?
                """,
                (rs, i) -> new CompraDetalle(
                        rs.getLong("id_compra"),
                        rs.getString("numero"),
                        rs.getObject("fec_compra", LocalDate.class),
                        rs.getLong("id_proveedor"),
                        rs.getString("razon_social"),
                        rs.getString("tipo_doc"),
                        rs.getString("num_doc"),
                        rs.getBigDecimal("tot_subtotal"),
                        rs.getBigDecimal("tot_igv"),
                        rs.getBigDecimal("tot_total"),
                        rs.getString("estado"),
                        rs.getString("observacion"),
                        rs.getString("cuenta"),
                        null),
                id);
        if (cab.isEmpty()) {
            throw new ResourceNotFoundException("La compra " + id + " no existe");
        }

        List<CompraDetalle.Linea> lineas = jdbc.query("""
                SELECT l.id_producto, pr.codigo, pr.nombre, un.codigo AS unidad,
                       l.cantidad, l.costo_unit, l.tot_linea
                FROM linea_compra l
                JOIN producto pr ON pr.id_producto = l.id_producto
                JOIN unidad un ON un.id_unidad = pr.id_unidad
                WHERE l.id_compra = ?
                ORDER BY l.id_linea_compra
                """,
                (rs, i) -> new CompraDetalle.Linea(
                        rs.getLong("id_producto"),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getString("unidad"),
                        rs.getBigDecimal("cantidad"),
                        rs.getBigDecimal("costo_unit"),
                        rs.getBigDecimal("tot_linea")),
                id);

        CompraDetalle c = cab.get(0);
        return new CompraDetalle(c.idCompra(), c.numero(), c.fecCompra(), c.idProveedor(), c.proveedor(),
                c.tipoDoc(), c.numDoc(), c.totSubtotal(), c.totIgv(), c.totTotal(), c.estado(),
                c.observacion(), c.usuario(), lineas);
    }

    // ------------------------------------------------------------------ registrar

    @Transactional
    public CompraDetalle registrar(CompraRequest req) {
        Integer idUsuario = idUsuarioActual();

        // El proveedor debe existir y estar ACTIVO
        Integer provActivo = jdbc.queryForObject(
                "SELECT COUNT(*) FROM proveedor WHERE id_proveedor = ? AND estado = 'ACTIVO'",
                Integer.class, req.idProveedor());
        if (provActivo == null || provActivo == 0) {
            throw new IllegalArgumentException("El proveedor no existe o está inactivo");
        }

        // Los productos deben existir y estar ACTIVOS (y no repetirse)
        java.util.Set<Long> vistos = new java.util.HashSet<>();
        for (CompraRequest.LineaRequest l : req.lineas()) {
            if (!vistos.add(l.idProducto())) {
                throw new IllegalArgumentException("Un producto está repetido en la compra. Sume las cantidades en una sola línea");
            }
            Integer ok = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM producto WHERE id_producto = ? AND estado = 'ACTIVO'",
                    Integer.class, l.idProducto());
            if (ok == null || ok == 0) {
                throw new IllegalArgumentException("Un producto de la lista no existe o está inactivo");
            }
        }

        // Totales en soles
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CompraRequest.LineaRequest l : req.lineas()) {
            subtotal = subtotal.add(totalLinea(l));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal igv = subtotal.multiply(tasaIgv()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(igv);

        // Cabecera (con un número temporal; luego se pone OC-0001, OC-0002...)
        String temporal = "TMP-" + UUID.randomUUID().toString().substring(0, 12);
        KeyHolder keys = new GeneratedKeyHolder();
        final BigDecimal fSub = subtotal;
        final BigDecimal fIgv = igv;
        final BigDecimal fTot = total;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO compra (id_proveedor, id_usuario, numero, fec_compra, tipo_doc, num_doc,
                                        tot_subtotal, tot_igv, tot_total, estado, observacion)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'BORRADOR', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, req.idProveedor());
            ps.setInt(2, idUsuario);
            ps.setString(3, temporal);
            ps.setObject(4, req.fecCompra());
            ps.setString(5, texto(req.tipoDoc()));
            ps.setString(6, texto(req.numDoc()));
            ps.setBigDecimal(7, fSub);
            ps.setBigDecimal(8, fIgv);
            ps.setBigDecimal(9, fTot);
            ps.setString(10, texto(req.observacion()));
            return ps;
        }, keys);

        Number clave = keys.getKey();
        if (clave == null) {
            throw new IllegalStateException("No se pudo registrar la compra");
        }
        long idCompra = clave.longValue();
        jdbc.update("UPDATE compra SET numero = ? WHERE id_compra = ?",
                String.format("OC-%04d", idCompra), idCompra);

        // Líneas
        for (CompraRequest.LineaRequest l : req.lineas()) {
            jdbc.update("""
                    INSERT INTO linea_compra (id_compra, id_producto, cantidad, costo_unit, tot_linea)
                    VALUES (?, ?, ?, ?, ?)
                    """, idCompra, l.idProducto(), l.cantidad(), l.costoUnit(), totalLinea(l));
        }
        return obtener(idCompra);
    }

    // ------------------------------------------------------------------ confirmar / anular

    /** No lleva @Transactional: el procedimiento maneja su propia transacción. */
    public void confirmar(Long id) {
        Integer idUsuario = idUsuarioActual();
        obtener(id); // 404 si no existe
        try {
            jdbc.execute((ConnectionCallback<Object>) con -> {
                try (CallableStatement cs = con.prepareCall("{call sp_confirmar_compra(?, ?)}")) {
                    cs.setLong(1, id);
                    cs.setInt(2, idUsuario);
                    cs.execute();
                    return null;
                } catch (SQLException e) {
                    // Si el procedimiento falló a mitad, dejamos la conexión limpia
                    try (Statement st = con.createStatement()) {
                        st.execute("ROLLBACK");
                    } catch (SQLException ignorado) {
                        // no hay nada más que hacer
                    }
                    throw e;
                }
            });
        } catch (DataAccessException e) {
            Throwable causa = NestedExceptionUtils.getMostSpecificCause(e);
            String mensaje = causa.getMessage() != null ? causa.getMessage() : "No se pudo confirmar la compra";
            throw new IllegalArgumentException(mensaje);
        }
    }

    @Transactional
    public void anular(Long id) {
        obtener(id); // 404 si no existe
        int filas = jdbc.update(
                "UPDATE compra SET estado = 'ANULADA' WHERE id_compra = ? AND estado = 'BORRADOR'", id);
        if (filas == 0) {
            throw new IllegalArgumentException("Solo se puede anular una compra en BORRADOR");
        }
    }

    // ------------------------------------------------------------------ ayudas

    private BigDecimal totalLinea(CompraRequest.LineaRequest l) {
        return l.cantidad().multiply(l.costoUnit()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal tasaIgv() {
        List<String> v = jdbc.queryForList("SELECT valor FROM parametro WHERE codigo = 'IGV_TASA'", String.class);
        try {
            return v.isEmpty() ? new BigDecimal("0.18") : new BigDecimal(v.get(0));
        } catch (NumberFormatException e) {
            return new BigDecimal("0.18");
        }
    }

    private String texto(String s) {
        return s == null ? "" : s.trim();
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
