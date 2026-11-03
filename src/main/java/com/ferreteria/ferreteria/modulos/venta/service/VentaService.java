package com.ferreteria.ferreteria.modulos.venta.service;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import com.ferreteria.ferreteria.modulos.venta.dto.OpcionVenta;
import com.ferreteria.ferreteria.modulos.venta.dto.VentaDetalle;
import com.ferreteria.ferreteria.modulos.venta.dto.VentaRequest;
import com.ferreteria.ferreteria.modulos.venta.dto.VentaResumen;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Ventas (RF del ERS: registrar venta y emitir comprobante).
 * Igual que en compras: se guarda primero en BORRADOR y al confirmar el procedimiento
 * sp_confirmar_venta revisa el stock, lo descuenta, escribe el kardex y emite la boleta o factura.
 *
 * El precio_venta del producto es el precio al público y YA INCLUYE IGV
 * (así se cobra en mostrador). Por eso el IGV se separa del total, no se suma.
 */
@Service
public class VentaService {

    private static final String ROL_ADMIN_NEGOCIO = "ROLE_ADMIN_NEGOCIO";
    private static final String DOC_CLIENTE_VARIOS = "00000000";
    // Estas unidades no se parten: no se vende medio martillo. Metro, kilo y litro sí admiten decimales
    private static final Set<String> UNIDADES_ENTERAS = Set.of("UND", "PQT", "CJ");
    // SUNAT exige identificar al comprador en boletas que pasan de este monto
    private static final BigDecimal TOPE_BOLETA_SIN_IDENTIFICAR = new BigDecimal("700.00");

    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraService bitacora;

    public VentaService(JdbcTemplate jdbc, UsuarioRepository usuarioRepository, BitacoraService bitacora) {
        this.jdbc = jdbc;
        this.usuarioRepository = usuarioRepository;
        this.bitacora = bitacora;
    }

    // ------------------------------------------------------------------ consultas

    /** Listado de ventas. Las fechas son opcionales (filtran por fecha de la venta). */
    public List<VentaResumen> listar(LocalDate desde, LocalDate hasta) {
        StringBuilder sql = new StringBuilder("""
                SELECT v.id_venta, v.numero, v.fec_venta, c.nombres, c.tipo_doc, c.num_doc,
                       m.nombre AS metodo, cp.serie, cp.numero AS num_comp,
                       v.tot_total, v.estado, u.cuenta
                FROM venta v
                JOIN cliente c ON c.id_cliente = v.id_cliente
                JOIN metodo m ON m.id_metodo = v.id_metodo
                JOIN usuario u ON u.id_usuario = v.id_usuario
                LEFT JOIN comprobante cp ON cp.id_venta = v.id_venta
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (desde != null) {
            sql.append(" AND v.fec_venta >= ?");
            params.add(desde.atStartOfDay());
        }
        if (hasta != null) {
            sql.append(" AND v.fec_venta < ?");
            params.add(hasta.plusDays(1).atStartOfDay());
        }
        sql.append(" ORDER BY v.id_venta DESC LIMIT 500");

        return jdbc.query(sql.toString(),
                (rs, i) -> new VentaResumen(
                        rs.getLong("id_venta"),
                        rs.getString("numero"),
                        rs.getObject("fec_venta", LocalDateTime.class),
                        rs.getString("nombres"),
                        rs.getString("tipo_doc") + " " + rs.getString("num_doc"),
                        rs.getString("metodo"),
                        numeroComprobante(rs.getString("serie"), rs.getObject("num_comp", Long.class)),
                        rs.getBigDecimal("tot_total"),
                        rs.getString("estado"),
                        rs.getString("cuenta")),
                params.toArray());
    }

    public VentaDetalle obtener(Long id) {
        List<VentaDetalle> cab = jdbc.query("""
                SELECT v.id_venta, v.numero, v.fec_venta, v.id_cliente, c.nombres, c.tipo_doc, c.num_doc,
                       c.direccion, v.id_metodo, m.nombre AS metodo, v.tot_subtotal, v.tot_igv, v.tot_total,
                       v.estado, v.observacion, u.cuenta
                FROM venta v
                JOIN cliente c ON c.id_cliente = v.id_cliente
                JOIN metodo m ON m.id_metodo = v.id_metodo
                JOIN usuario u ON u.id_usuario = v.id_usuario
                WHERE v.id_venta = ?
                """,
                (rs, i) -> new VentaDetalle(
                        rs.getLong("id_venta"),
                        rs.getString("numero"),
                        rs.getObject("fec_venta", LocalDateTime.class),
                        rs.getLong("id_cliente"),
                        rs.getString("nombres"),
                        rs.getString("tipo_doc"),
                        rs.getString("num_doc"),
                        rs.getString("direccion"),
                        rs.getLong("id_metodo"),
                        rs.getString("metodo"),
                        rs.getBigDecimal("tot_subtotal"),
                        rs.getBigDecimal("tot_igv"),
                        rs.getBigDecimal("tot_total"),
                        rs.getString("estado"),
                        rs.getString("observacion"),
                        rs.getString("cuenta"),
                        null,
                        null),
                id);
        if (cab.isEmpty()) {
            throw new ResourceNotFoundException("La venta " + id + " no existe");
        }

        List<VentaDetalle.Linea> lineas = jdbc.query("""
                SELECT l.id_producto, pr.codigo, pr.nombre, un.codigo AS unidad,
                       l.cantidad, l.precio_unit, l.tot_linea
                FROM linea_venta l
                JOIN producto pr ON pr.id_producto = l.id_producto
                JOIN unidad un ON un.id_unidad = pr.id_unidad
                WHERE l.id_venta = ?
                ORDER BY l.id_linea_venta
                """,
                (rs, i) -> new VentaDetalle.Linea(
                        rs.getLong("id_producto"),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getString("unidad"),
                        rs.getBigDecimal("cantidad"),
                        rs.getBigDecimal("precio_unit"),
                        rs.getBigDecimal("tot_linea")),
                id);

        List<VentaDetalle.Comprobante> comp = jdbc.query("""
                SELECT t.codigo, t.nombre, cp.serie, cp.numero, cp.fec_emision, cp.estado
                FROM comprobante cp
                JOIN tipo_comp t ON t.id_tipo_comp = cp.id_tipo_comp
                WHERE cp.id_venta = ?
                """,
                (rs, i) -> new VentaDetalle.Comprobante(
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getString("serie"),
                        rs.getLong("numero"),
                        numeroComprobante(rs.getString("serie"), rs.getLong("numero")),
                        rs.getObject("fec_emision", LocalDateTime.class),
                        rs.getString("estado")),
                id);

        return cab.get(0).conLineas(comp.isEmpty() ? null : comp.get(0), lineas);
    }

    /** Medios de pago activos (efectivo, Yape, Plin...). */
    public List<OpcionVenta> metodos() {
        return jdbc.query("SELECT id_metodo, codigo, nombre FROM metodo WHERE estado = 'ACTIVO' ORDER BY id_metodo",
                (rs, i) -> new OpcionVenta(rs.getLong("id_metodo"), rs.getString("codigo"), rs.getString("nombre")));
    }

    /** Tipos de comprobante: boleta y factura. */
    public List<OpcionVenta> tiposComprobante() {
        return jdbc.query("SELECT id_tipo_comp, codigo, nombre, serie FROM tipo_comp ORDER BY id_tipo_comp",
                (rs, i) -> new OpcionVenta(rs.getLong("id_tipo_comp"), rs.getString("codigo"),
                        rs.getString("nombre") + " (" + rs.getString("serie") + ")"));
    }

    // ------------------------------------------------------------------ registrar / editar

    @Transactional
    public VentaDetalle registrar(VentaRequest req) {
        Integer idUsuario = idUsuarioActual();
        validarCabecera(req);
        List<LineaCalculada> lineas = calcularLineas(req, Map.of());
        Totales t = totales(lineas);

        // Cabecera (con un número temporal; luego se pone V-0001, V-0002...)
        String temporal = "TMP-" + UUID.randomUUID().toString().substring(0, 12);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO venta (id_cliente, id_metodo, id_usuario, numero,
                                       tot_subtotal, tot_igv, tot_total, estado, observacion)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'BORRADOR', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, req.idCliente());
            ps.setLong(2, req.idMetodo());
            ps.setInt(3, idUsuario);
            ps.setString(4, temporal);
            ps.setBigDecimal(5, t.subtotal());
            ps.setBigDecimal(6, t.igv());
            ps.setBigDecimal(7, t.total());
            ps.setString(8, texto(req.observacion()));
            return ps;
        }, keys);

        Number clave = keys.getKey();
        if (clave == null) {
            throw new IllegalStateException("No se pudo registrar la venta");
        }
        long idVenta = clave.longValue();
        String numero = String.format("V-%04d", idVenta);
        jdbc.update("UPDATE venta SET numero = ? WHERE id_venta = ?", numero, idVenta);

        guardarLineas(idVenta, lineas);
        anotarCambiosDePrecio(numero, lineas);
        return obtener(idVenta);
    }

    /** Revisa una venta de mostrador antes de guardarla, para no dejar borradores de intentos fallidos. */
    public void validarCobro(VentaRequest req, Long idTipoComp) {
        idUsuarioActual();
        validarCabecera(req);
        Totales t = totales(calcularLineas(req, Map.of()));
        Map<String, Object> cliente = jdbc.queryForMap(
                "SELECT tipo_doc, num_doc FROM cliente WHERE id_cliente = ?", req.idCliente());
        validarComprobante(idTipoComp, (String) cliente.get("tipo_doc"), (String) cliente.get("num_doc"), t.total());
    }

    /** Quita un borrador creado en un cobro que no se pudo completar. Una venta confirmada nunca se borra. */
    @Transactional
    public void eliminarBorrador(Long id) {
        if ("BORRADOR".equals(estadoDe(id))) {
            jdbc.update("DELETE FROM linea_venta WHERE id_venta = ?", id);
            jdbc.update("DELETE FROM venta WHERE id_venta = ? AND estado = 'BORRADOR'", id);
        }
    }

    /** Solo se edita una venta en BORRADOR (por ejemplo, una proforma que el cliente cambió). */
    @Transactional
    public VentaDetalle actualizar(Long id, VentaRequest req) {
        idUsuarioActual();
        String estado = estadoDe(id);
        if (!"BORRADOR".equals(estado)) {
            throw new IllegalArgumentException("Solo se puede editar una venta en BORRADOR");
        }
        validarCabecera(req);
        // Un descuento ya autorizado por el administrador se conserva si el vendedor edita la proforma
        Map<Long, BigDecimal> preciosGuardados = new HashMap<>();
        jdbc.query("SELECT id_producto, precio_unit FROM linea_venta WHERE id_venta = ?",
                rs -> { preciosGuardados.put(rs.getLong("id_producto"), rs.getBigDecimal("precio_unit")); }, id);
        List<LineaCalculada> lineas = calcularLineas(req, preciosGuardados);
        Totales t = totales(lineas);

        jdbc.update("""
                UPDATE venta
                SET id_cliente = ?, id_metodo = ?, tot_subtotal = ?, tot_igv = ?, tot_total = ?, observacion = ?
                WHERE id_venta = ? AND estado = 'BORRADOR'
                """, req.idCliente(), req.idMetodo(), t.subtotal(), t.igv(), t.total(),
                texto(req.observacion()), id);
        jdbc.update("DELETE FROM linea_venta WHERE id_venta = ?", id);
        guardarLineas(id, lineas);
        VentaDetalle v = obtener(id);
        anotarCambiosDePrecio(v.numero(), lineas);
        return v;
    }

    // ------------------------------------------------------------------ confirmar / anular

    /**
     * Confirma la venta y emite el comprobante.
     * Antes de llamar al procedimiento se revisan las reglas para dar un mensaje claro
     * (qué producto no alcanza, si la factura necesita RUC, etc.).
     * No lleva @Transactional: el procedimiento maneja su propia transacción.
     */
    public void confirmar(Long id, Long idTipoComp) {
        Integer idUsuario = idUsuarioActual();
        VentaDetalle v = obtener(id); // 404 si no existe
        if (!"BORRADOR".equals(v.estado())) {
            throw new IllegalArgumentException("Solo se confirma una venta en BORRADOR");
        }

        validarComprobante(idTipoComp, v.tipoDocCliente(), v.numDocCliente(), v.totTotal());

        // Stock: se avisa qué producto no alcanza (el procedimiento lo vuelve a revisar con bloqueo)
        for (VentaDetalle.Linea l : v.lineas()) {
            Map<String, Object> p = jdbc.queryForMap(
                    "SELECT stock_actual, estado FROM producto WHERE id_producto = ?", l.idProducto());
            if (!"ACTIVO".equals(p.get("estado"))) {
                throw new IllegalArgumentException("El producto " + l.codigo() + " está inactivo y ya no se vende");
            }
            BigDecimal stock = (BigDecimal) p.get("stock_actual");
            if (stock.compareTo(l.cantidad()) < 0) {
                throw new IllegalArgumentException("Stock insuficiente de " + l.codigo() + " - " + l.producto()
                        + ": hay " + stock.stripTrailingZeros().toPlainString()
                        + " y se piden " + l.cantidad().stripTrailingZeros().toPlainString());
            }
        }

        llamarProcedimiento("{call sp_confirmar_venta(?, ?, ?)}", "No se pudo confirmar la venta", cs -> {
            cs.setLong(1, id);
            cs.setInt(2, idUsuario);
            cs.setLong(3, idTipoComp);
        });
    }

    /**
     * BORRADOR: se anula directamente (no movió stock).
     * CONFIRMADA: solo el administrador del negocio, con motivo. El procedimiento
     * sp_anular_venta devuelve el stock, anota ANULACION_VENTA en el kardex y anula el comprobante.
     */
    public void anular(Long id, String motivo) {
        Integer idUsuario = idUsuarioActual();
        String estado = estadoDe(id);
        String m = texto(motivo);

        if ("BORRADOR".equals(estado)) {
            jdbc.update("UPDATE venta SET estado = 'ANULADA' WHERE id_venta = ? AND estado = 'BORRADOR'", id);
            return;
        }
        if (!"CONFIRMADA".equals(estado)) {
            throw new IllegalArgumentException("La venta ya está anulada");
        }
        if (!esAdminNegocio()) {
            throw new IllegalArgumentException("Solo el administrador del negocio puede anular una venta confirmada");
        }
        if (m.length() < 5) {
            throw new IllegalArgumentException("Indique el motivo de la anulación (mínimo 5 caracteres)");
        }

        llamarProcedimiento("{call sp_anular_venta(?, ?, ?)}", "No se pudo anular la venta", cs -> {
            cs.setLong(1, id);
            cs.setInt(2, idUsuario);
            cs.setString(3, m);
        });
        jdbc.update("UPDATE venta SET observacion = LEFT(TRIM(CONCAT(observacion, ' [Anulada: ', ?, ']')), 300) WHERE id_venta = ?",
                m, id);
    }

    public boolean esAdminNegocio() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (ROL_ADMIN_NEGOCIO.equals(a.getAuthority()) || "ADMIN_NEGOCIO".equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ ayudas

    /** cambioPrecio: texto para la bitácora cuando el administrador acaba de cambiar el precio; si no, null. */
    private record LineaCalculada(Long idProducto, BigDecimal cantidad, BigDecimal precioUnit, BigDecimal totLinea,
                                  String cambioPrecio) {}

    private record Totales(BigDecimal subtotal, BigDecimal igv, BigDecimal total) {}

    private interface Parametros {
        void poner(CallableStatement cs) throws SQLException;
    }

    private void validarCabecera(VentaRequest req) {
        List<String> doc = jdbc.queryForList(
                "SELECT num_doc FROM cliente WHERE id_cliente = ? AND estado = 'ACTIVO'",
                String.class, req.idCliente());
        if (doc.isEmpty()) {
            throw new IllegalArgumentException("El cliente no existe o está inactivo");
        }
        List<String> metodo = jdbc.queryForList(
                "SELECT codigo FROM metodo WHERE id_metodo = ? AND estado = 'ACTIVO'",
                String.class, req.idMetodo());
        if (metodo.isEmpty()) {
            throw new IllegalArgumentException("El medio de pago no existe o está inactivo");
        }
        if ("CREDITO".equals(metodo.get(0)) && DOC_CLIENTE_VARIOS.equals(doc.get(0))) {
            throw new IllegalArgumentException("El crédito interno se da a un cliente identificado. Elija al cliente o cambie el medio de pago");
        }
    }

    /** Reglas del comprobante: factura solo con RUC y boletas altas con el comprador identificado. */
    private void validarComprobante(Long idTipoComp, String tipoDocCliente, String numDocCliente, BigDecimal total) {
        List<String> tipo = jdbc.queryForList(
                "SELECT codigo FROM tipo_comp WHERE id_tipo_comp = ?", String.class, idTipoComp);
        if (tipo.isEmpty()) {
            throw new IllegalArgumentException("El tipo de comprobante no existe");
        }
        if ("FAC".equals(tipo.get(0)) && !"RUC".equals(tipoDocCliente)) {
            throw new IllegalArgumentException("Para emitir factura el cliente debe tener RUC. Use boleta o cambie el cliente");
        }
        if (DOC_CLIENTE_VARIOS.equals(numDocCliente) && total.compareTo(TOPE_BOLETA_SIN_IDENTIFICAR) > 0) {
            throw new IllegalArgumentException("Una boleta de más de S/ 700.00 debe llevar los datos del comprador. Elija o registre al cliente");
        }
    }

    /**
     * Revisa cada línea y fija su precio:
     * - el producto debe existir, estar ACTIVO y no repetirse;
     * - la cantidad no puede pasar el stock actual (se avisa desde el borrador);
     * - UND, PQT y CJ se venden en cantidades enteras;
     * - si no viene precio se usa el ya guardado en la proforma o el precio_venta;
     * - solo ADMIN_NEGOCIO puede cambiarlo, indicando el motivo en la observación.
     */
    private List<LineaCalculada> calcularLineas(VentaRequest req, Map<Long, BigDecimal> preciosGuardados) {
        boolean admin = esAdminNegocio();
        Set<Long> vistos = new HashSet<>();
        List<LineaCalculada> resultado = new ArrayList<>();

        for (VentaRequest.LineaRequest l : req.lineas()) {
            if (!vistos.add(l.idProducto())) {
                throw new IllegalArgumentException("Un producto está repetido en la venta. Sume las cantidades en una sola línea");
            }
            List<Map<String, Object>> filas = jdbc.queryForList("""
                    SELECT pr.codigo, pr.nombre, pr.precio_venta, pr.stock_actual, un.codigo AS unidad
                    FROM producto pr
                    JOIN unidad un ON un.id_unidad = pr.id_unidad
                    WHERE pr.id_producto = ? AND pr.estado = 'ACTIVO'
                    """, l.idProducto());
            if (filas.isEmpty()) {
                throw new IllegalArgumentException("Un producto de la lista no existe o está inactivo");
            }
            Map<String, Object> p = filas.get(0);

            BigDecimal cantidad = l.cantidad().stripTrailingZeros();
            if (cantidad.scale() > 3) {
                throw new IllegalArgumentException("La cantidad de " + p.get("codigo") + " admite hasta 3 decimales");
            }
            if (cantidad.scale() > 0 && UNIDADES_ENTERAS.contains(p.get("unidad"))) {
                throw new IllegalArgumentException(p.get("codigo") + " - " + p.get("nombre")
                        + " se vende en cantidades enteras");
            }

            BigDecimal stock = (BigDecimal) p.get("stock_actual");
            if (stock.compareTo(l.cantidad()) < 0) {
                throw new IllegalArgumentException("Stock insuficiente de " + p.get("codigo") + " - " + p.get("nombre")
                        + ": hay " + stock.stripTrailingZeros().toPlainString());
            }

            BigDecimal precioVigente = preciosGuardados.getOrDefault(l.idProducto(), (BigDecimal) p.get("precio_venta"));
            BigDecimal precio = l.precioUnit() == null ? precioVigente : l.precioUnit().setScale(2, RoundingMode.HALF_UP);
            String cambioPrecio = null;
            if (precio.compareTo(precioVigente) != 0) {
                if (!admin) {
                    throw new IllegalArgumentException("Solo el administrador del negocio puede cambiar el precio de "
                            + p.get("codigo"));
                }
                if (texto(req.observacion()).isEmpty()) {
                    throw new IllegalArgumentException("Indique en la observación el motivo del cambio de precio de "
                            + p.get("codigo"));
                }
                cambioPrecio = p.get("codigo") + ": S/ " + precioVigente + " a S/ " + precio;
            }
            if (precio.signum() <= 0) {
                throw new IllegalArgumentException("El producto " + p.get("codigo")
                        + " no tiene precio de venta. Solicite que lo registren antes de venderlo");
            }
            BigDecimal tot = l.cantidad().multiply(precio).setScale(2, RoundingMode.HALF_UP);
            if (tot.signum() <= 0) {
                throw new IllegalArgumentException("La cantidad de " + p.get("codigo") + " es demasiado pequeña");
            }
            resultado.add(new LineaCalculada(l.idProducto(), l.cantidad(), precio, tot, cambioPrecio));
        }
        return resultado;
    }

    /** Deja constancia de quién cambió un precio, en qué venta y de cuánto a cuánto. */
    private void anotarCambiosDePrecio(String numeroVenta, List<LineaCalculada> lineas) {
        List<String> cambios = lineas.stream().map(LineaCalculada::cambioPrecio).filter(Objects::nonNull).toList();
        if (!cambios.isEmpty()) {
            String detalle = "Cambió el precio en la venta " + numeroVenta + ". " + String.join("; ", cambios);
            bitacora.registrar("VENTAS", "CAMBIAR_PRECIO", detalle.length() > 300 ? detalle.substring(0, 300) : detalle);
        }
    }

    /** El total es la suma de las líneas (precios con IGV). Del total se separa la base y el IGV. */
    private Totales totales(List<LineaCalculada> lineas) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineaCalculada l : lineas) {
            total = total.add(l.totLinea());
        }
        total = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = total.divide(BigDecimal.ONE.add(tasaIgv()), 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotal);
        return new Totales(subtotal, igv, total);
    }

    private void guardarLineas(long idVenta, List<LineaCalculada> lineas) {
        for (LineaCalculada l : lineas) {
            jdbc.update("""
                    INSERT INTO linea_venta (id_venta, id_producto, cantidad, precio_unit, tot_linea)
                    VALUES (?, ?, ?, ?, ?)
                    """, idVenta, l.idProducto(), l.cantidad(), l.precioUnit(), l.totLinea());
        }
    }

    private String estadoDe(Long id) {
        List<String> estado = jdbc.queryForList("SELECT estado FROM venta WHERE id_venta = ?", String.class, id);
        if (estado.isEmpty()) {
            throw new ResourceNotFoundException("La venta " + id + " no existe");
        }
        return estado.get(0);
    }

    /** Llama a un procedimiento y, si falla a mitad, deja la conexión limpia (igual que en compras). */
    private void llamarProcedimiento(String llamada, String mensajePorDefecto, Parametros parametros) {
        try {
            jdbc.execute((ConnectionCallback<Object>) con -> {
                try (CallableStatement cs = con.prepareCall(llamada)) {
                    parametros.poner(cs);
                    cs.execute();
                    return null;
                } catch (SQLException e) {
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
            String mensaje = causa.getMessage() != null ? causa.getMessage() : mensajePorDefecto;
            throw new IllegalArgumentException(mensaje);
        }
    }

    private String numeroComprobante(String serie, Long numero) {
        if (serie == null || numero == null) {
            return "";
        }
        return serie + "-" + String.format("%08d", numero);
    }

    public BigDecimal tasaIgv() {
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
