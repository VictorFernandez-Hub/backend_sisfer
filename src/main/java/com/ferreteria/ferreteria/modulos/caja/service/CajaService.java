package com.ferreteria.ferreteria.modulos.caja.service;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.caja.dto.CajaDetalle;
import com.ferreteria.ferreteria.modulos.caja.dto.CajaFila;
import com.ferreteria.ferreteria.modulos.caja.dto.EntregadorFondo;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.dao.DuplicateKeyException;
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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Caja del turno de ventas.
 *
 * Reglas del negocio:
 * - Cada vendedor trabaja con su propia caja y solo puede tener una abierta.
 * - Para cobrar (confirmar una venta) hay que tener la caja abierta y del día. Una proforma
 *   (borrador) no mueve dinero y se puede preparar sin caja.
 * - La caja abre con el sencillo que recibe el vendedor (puede ser S/ 0.00).
 * - Las entradas y salidas de efectivo que no son ventas se anotan con su motivo. No puede
 *   salir más efectivo del que hay.
 * - Al cerrar se cuenta el efectivo. Esperado = fondo + ventas en efectivo + entradas - salidas.
 *   Si lo contado no cuadra, se explica el sobrante o faltante. El cierre no se puede deshacer.
 * - Yape, Plin, transferencia, tarjeta y crédito no entran al cajón: se muestran para cuadrar
 *   con el celular y el banco, pero no forman parte del efectivo esperado.
 * - El administrador del negocio ve todas las cajas y puede cerrar la que un vendedor dejó abierta.
 *   El gerente solo consulta. El vendedor ve únicamente sus cajas.
 */
@Service
public class CajaService {

    private static final Set<String> ROLES_SUPERVISOR = Set.of(
            "ROLE_ADMIN_NEGOCIO", "ADMIN_NEGOCIO", "ROLE_ADMIN_SISTEMA", "ADMIN_SISTEMA");
    private static final Set<String> ROLES_CONSULTA_TOTAL = Set.of("ROLE_GERENTE", "GERENTE");
    private static final BigDecimal FONDO_MAXIMO_POR_DEFECTO = new BigDecimal("2000.00");
    private static final BigDecimal MOVIMIENTO_MAXIMO = new BigDecimal("50000.00");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarioRepository;

    public CajaService(JdbcTemplate jdbc, UsuarioRepository usuarioRepository) {
        this.jdbc = jdbc;
        this.usuarioRepository = usuarioRepository;
    }

    // ------------------------------------------------------------------ consultas

    /** La caja abierta del usuario de la sesión, si tiene una. */
    public Optional<CajaDetalle> actual() {
        return idCajaAbierta(idUsuarioActual()).map(this::armarDetalle);
    }

    public CajaDetalle obtener(Long idCaja) {
        CajaDetalle d = armarDetalle(idCaja);
        if (!puedeVerTodas() && !d.usuario().equalsIgnoreCase(cuentaActual())) {
            throw new IllegalArgumentException("Solo puede consultar sus propias cajas");
        }
        return d;
    }

    /** Historial. El vendedor solo ve sus cajas; el administrador y el gerente ven todas. */
    public List<CajaFila> listar(LocalDate desde, LocalDate hasta, String estado) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id_caja, c.numero, u.cuenta, CONCAT(u.nombres, ' ', u.apellidos) AS nombre,
                       c.fec_apertura, c.fec_cierre, c.monto_apertura, ue.cuenta AS cuenta_entrega, c.estado,
                       c.efectivo_esperado, c.efectivo_contado, c.diferencia,
                       (SELECT COUNT(*) FROM venta v WHERE v.id_caja = c.id_caja AND v.estado = 'CONFIRMADA') AS num_ventas,
                       (SELECT COALESCE(SUM(v.tot_total), 0) FROM venta v
                         WHERE v.id_caja = c.id_caja AND v.estado = 'CONFIRMADA') AS total_ventas
                FROM caja c
                JOIN usuario u ON u.id_usuario = c.id_usuario
                LEFT JOIN usuario ue ON ue.id_usuario = c.id_usuario_entrega
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (!puedeVerTodas()) {
            sql.append(" AND c.id_usuario = ?");
            params.add(idUsuarioActual());
        }
        if (desde != null) {
            sql.append(" AND c.fec_apertura >= ?");
            params.add(desde.atStartOfDay());
        }
        if (hasta != null) {
            sql.append(" AND c.fec_apertura < ?");
            params.add(hasta.plusDays(1).atStartOfDay());
        }
        if ("ABIERTA".equals(estado) || "CERRADA".equals(estado)) {
            sql.append(" AND c.estado = ?");
            params.add(estado);
        }
        sql.append(" ORDER BY c.id_caja DESC LIMIT 300");

        return jdbc.query(sql.toString(), (rs, i) -> new CajaFila(
                rs.getLong("id_caja"),
                rs.getString("numero"),
                rs.getString("cuenta"),
                rs.getString("nombre"),
                rs.getObject("fec_apertura", LocalDateTime.class),
                rs.getObject("fec_cierre", LocalDateTime.class),
                rs.getBigDecimal("monto_apertura"),
                rs.getString("cuenta_entrega"),
                rs.getInt("num_ventas"),
                rs.getBigDecimal("total_ventas"),
                rs.getBigDecimal("efectivo_esperado"),
                rs.getBigDecimal("efectivo_contado"),
                rs.getBigDecimal("diferencia"),
                rs.getString("estado")), params.toArray());
    }

    public BigDecimal fondoMaximo() {
        List<String> v = jdbc.queryForList("SELECT valor FROM parametro WHERE codigo = 'CAJA_FONDO_MAXIMO'", String.class);
        try {
            return v.isEmpty() ? FONDO_MAXIMO_POR_DEFECTO : new BigDecimal(v.get(0).trim());
        } catch (NumberFormatException e) {
            return FONDO_MAXIMO_POR_DEFECTO;
        }
    }

    /**
     * Quiénes pueden entregar el fondo inicial: cuentas activas del negocio (no las de TI).
     * El vendedor no se lo entrega a sí mismo; el administrador del negocio sí puede,
     * porque es quien guarda el dinero.
     */
    public List<EntregadorFondo> entregadores() {
        Integer idUsuario = idUsuarioActual();
        return jdbc.query("""
                SELECT u.id_usuario, u.cuenta, CONCAT(u.nombres, ' ', u.apellidos) AS nombre, r.nombre AS rol, r.codigo AS codigo_rol
                FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
                WHERE u.estado = 'ACTIVO' AND r.es_tecnico = 0 AND (u.id_usuario <> ? OR ?)
                ORDER BY r.codigo = 'VENDEDOR', u.nombres, u.apellidos
                """, (rs, i) -> new EntregadorFondo(rs.getInt("id_usuario"), rs.getString("cuenta"),
                rs.getString("nombre"), rs.getString("rol"), rs.getString("codigo_rol")), idUsuario, esSupervisor());
    }

    // ------------------------------------------------------------------ apertura

    @Transactional
    public CajaDetalle abrir(BigDecimal montoApertura, Integer idEntregadoPor, String observacion) {
        Integer idUsuario = idUsuarioActual();
        BigDecimal monto = soles(montoApertura, "El fondo inicial");
        if (monto.signum() < 0) {
            throw new IllegalArgumentException("El fondo inicial no puede ser negativo");
        }
        BigDecimal maximo = fondoMaximo();
        if (monto.compareTo(maximo) > 0) {
            throw new IllegalArgumentException("El fondo inicial no puede pasar de S/ " + maximo
                    + ". Revise que el monto esté bien escrito");
        }
        // Sin fondo no hay nada que entregar; con fondo, alguien tiene que responder por ese dinero
        Integer entrega = null;
        if (monto.signum() > 0) {
            if (idEntregadoPor == null) {
                throw new IllegalArgumentException("Indique quién le entregó el fondo inicial de S/ " + monto);
            }
            boolean valido = entregadores().stream().anyMatch(e -> e.idUsuario().equals(idEntregadoPor));
            if (!valido) {
                throw new IllegalArgumentException("La persona que entregó el fondo debe ser un usuario activo del negocio"
                        + " y distinto de quien abre la caja");
            }
            entrega = idEntregadoPor;
        }
        final Integer idEntrega = entrega;

        Optional<Long> abierta = idCajaAbierta(idUsuario);
        if (abierta.isPresent()) {
            throw new IllegalArgumentException("Ya tiene la caja " + numeroDe(abierta.get())
                    + " abierta. Ciérrela antes de abrir otra");
        }

        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO caja (id_usuario, monto_apertura, id_usuario_entrega, obs_apertura, estado)
                        VALUES (?, ?, ?, ?, 'ABIERTA')
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, idUsuario);
                ps.setBigDecimal(2, monto);
                ps.setObject(3, idEntrega, Types.INTEGER);
                ps.setString(4, recortar(texto(observacion), 200));
                return ps;
            }, keys);
        } catch (DuplicateKeyException e) {
            // Dos pestañas abriendo a la vez: el índice único deja pasar solo una
            throw new IllegalArgumentException("Ya tiene una caja abierta. Actualice la pantalla");
        }
        Number clave = keys.getKey();
        if (clave == null) {
            throw new IllegalStateException("No se pudo abrir la caja");
        }
        long idCaja = clave.longValue();
        jdbc.update("UPDATE caja SET numero = ? WHERE id_caja = ?", String.format("C-%04d", idCaja), idCaja);
        return armarDetalle(idCaja);
    }

    // ------------------------------------------------------------------ movimientos

    @Transactional
    public CajaDetalle registrarMovimiento(String tipo, BigDecimal monto, String motivo) {
        Integer idUsuario = idUsuarioActual();
        String t = texto(tipo).toUpperCase();
        if (!"INGRESO".equals(t) && !"EGRESO".equals(t)) {
            throw new IllegalArgumentException("El movimiento debe ser INGRESO o EGRESO");
        }
        BigDecimal m = soles(monto, "El monto");
        if (m.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
        if (m.compareTo(MOVIMIENTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("El monto es demasiado alto. Revise que esté bien escrito");
        }
        String mot = texto(motivo);
        if (mot.length() < 5) {
            throw new IllegalArgumentException("Explique el motivo del movimiento (mínimo 5 caracteres)");
        }

        Long idCaja = idCajaAbierta(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException("No tiene una caja abierta"));
        bloquear(idCaja);

        if ("EGRESO".equals(t)) {
            BigDecimal disponible = efectivoEsperado(idCaja);
            if (m.compareTo(disponible) > 0) {
                throw new IllegalArgumentException("No puede sacar S/ " + m + ": en la caja debería haber S/ "
                        + disponible + " en efectivo");
            }
        }

        jdbc.update("""
                INSERT INTO caja_movimiento (id_caja, id_usuario, tipo, monto, motivo)
                VALUES (?, ?, ?, ?, ?)
                """, idCaja, idUsuario, t, m, recortar(mot, 200));
        return armarDetalle(idCaja);
    }

    // ------------------------------------------------------------------ cierre

    /**
     * Arqueo y cierre. El vendedor cierra la suya; el administrador del negocio puede cerrar
     * la de cualquiera (por ejemplo, la que quedó abierta de un turno anterior).
     */
    @Transactional
    public CajaDetalle cerrar(Long idCaja, BigDecimal efectivoContado, String observacion) {
        Integer idUsuario = idUsuarioActual();
        Map<String, Object> caja = bloquear(idCaja);
        if (!"ABIERTA".equals(caja.get("estado"))) {
            throw new IllegalArgumentException("La caja " + caja.get("numero") + " ya está cerrada");
        }
        int duenio = ((Number) caja.get("id_usuario")).intValue();
        if (duenio != idUsuario && !esSupervisor()) {
            throw new IllegalArgumentException("Solo el responsable de la caja o el administrador del negocio puede cerrarla");
        }

        BigDecimal contado = soles(efectivoContado, "El efectivo contado");
        if (contado.signum() < 0) {
            throw new IllegalArgumentException("El efectivo contado no puede ser negativo");
        }

        BigDecimal esperado = efectivoEsperado(idCaja);
        BigDecimal diferencia = contado.subtract(esperado);
        String obs = texto(observacion);
        if (diferencia.signum() != 0 && obs.length() < 10) {
            String tipo = diferencia.signum() > 0 ? "sobrante" : "faltante";
            throw new IllegalArgumentException("Hay un " + tipo + " de S/ " + diferencia.abs()
                    + ". Explique el motivo en la observación (mínimo 10 caracteres)");
        }
        if (duenio != idUsuario) {
            obs = recortar(("[Cerrada por " + cuentaActual() + "] " + obs).trim(), 300);
        }

        Map<String, Object> ventas = jdbc.queryForMap("""
                SELECT COUNT(*) AS n, COALESCE(SUM(tot_total), 0) AS total
                FROM venta WHERE id_caja = ? AND estado = 'CONFIRMADA'
                """, idCaja);

        jdbc.update("""
                UPDATE caja
                SET estado = 'CERRADA', fec_cierre = NOW(), id_usuario_cierre = ?,
                    efectivo_esperado = ?, efectivo_contado = ?, diferencia = ?,
                    total_ventas = ?, num_ventas = ?, obs_cierre = ?
                WHERE id_caja = ? AND estado = 'ABIERTA'
                """, idUsuario, esperado, contado, diferencia,
                ventas.get("total"), ((Number) ventas.get("n")).intValue(), recortar(obs, 300), idCaja);
        return armarDetalle(idCaja);
    }

    // ------------------------------------------------------------------ uso desde Ventas

    /**
     * Caja en la que se cobrará la venta. Si no hay caja abierta, o es de un día anterior,
     * se avisa con un mensaje claro y la venta no se cobra.
     */
    public Long cajaParaCobrar(Integer idUsuario) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT id_caja, numero, fec_apertura FROM caja WHERE id_usuario = ? AND estado = 'ABIERTA'", idUsuario);
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("Abra su caja antes de cobrar. Vaya a Caja e indique el fondo inicial del turno");
        }
        Map<String, Object> c = filas.get(0);
        LocalDateTime apertura = aFecha(c.get("fec_apertura"));
        if (apertura != null && apertura.toLocalDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Su caja " + c.get("numero") + " está abierta desde el "
                    + apertura.format(FECHA) + ". Ciérrela y abra una nueva para el turno de hoy");
        }
        return ((Number) c.get("id_caja")).longValue();
    }

    // ------------------------------------------------------------------ ayudas

    private CajaDetalle armarDetalle(Long idCaja) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT c.*, u.cuenta, CONCAT(u.nombres, ' ', u.apellidos) AS nombre, uc.cuenta AS cuenta_cierre,
                       ue.cuenta AS cuenta_entrega, CONCAT(ue.nombres, ' ', ue.apellidos) AS nombre_entrega
                FROM caja c
                JOIN usuario u ON u.id_usuario = c.id_usuario
                LEFT JOIN usuario uc ON uc.id_usuario = c.id_usuario_cierre
                LEFT JOIN usuario ue ON ue.id_usuario = c.id_usuario_entrega
                WHERE c.id_caja = ?
                """, idCaja);
        if (filas.isEmpty()) {
            throw new ResourceNotFoundException("La caja " + idCaja + " no existe");
        }
        Map<String, Object> c = filas.get(0);
        boolean abierta = "ABIERTA".equals(c.get("estado"));

        List<CajaDetalle.PorMetodo> porMetodo = jdbc.query("""
                SELECT m.codigo, m.nombre, COUNT(*) AS n, SUM(v.tot_total) AS total
                FROM venta v JOIN metodo m ON m.id_metodo = v.id_metodo
                WHERE v.id_caja = ? AND v.estado = 'CONFIRMADA'
                GROUP BY m.id_metodo, m.codigo, m.nombre
                ORDER BY total DESC
                """, (rs, i) -> new CajaDetalle.PorMetodo(rs.getString("codigo"), rs.getString("nombre"),
                rs.getInt("n"), rs.getBigDecimal("total")), idCaja);

        List<CajaDetalle.Movimiento> movimientos = jdbc.query("""
                SELECT mv.id_movimiento, mv.tipo, mv.monto, mv.motivo, mv.fec_registro, u.cuenta
                FROM caja_movimiento mv JOIN usuario u ON u.id_usuario = mv.id_usuario
                WHERE mv.id_caja = ?
                ORDER BY mv.id_movimiento
                """, (rs, i) -> new CajaDetalle.Movimiento(rs.getLong("id_movimiento"), rs.getString("tipo"),
                rs.getBigDecimal("monto"), rs.getString("motivo"),
                rs.getObject("fec_registro", LocalDateTime.class), rs.getString("cuenta")), idCaja);

        List<CajaDetalle.Venta> ventas = jdbc.query("""
                SELECT v.id_venta, v.numero, v.fec_venta, cl.nombres, m.nombre AS metodo,
                       cp.serie, cp.numero AS num_comp, v.tot_total, v.estado
                FROM venta v
                JOIN cliente cl ON cl.id_cliente = v.id_cliente
                JOIN metodo m ON m.id_metodo = v.id_metodo
                LEFT JOIN comprobante cp ON cp.id_venta = v.id_venta
                WHERE v.id_caja = ?
                ORDER BY v.id_venta DESC
                """, (rs, i) -> {
                    String serie = rs.getString("serie");
                    Long num = rs.getObject("num_comp", Long.class);
                    return new CajaDetalle.Venta(rs.getLong("id_venta"), rs.getString("numero"),
                            rs.getObject("fec_venta", LocalDateTime.class), rs.getString("nombres"),
                            rs.getString("metodo"),
                            serie == null || num == null ? "" : serie + "-" + String.format("%08d", num),
                            rs.getBigDecimal("tot_total"), rs.getString("estado"));
                }, idCaja);

        BigDecimal totalVentas = BigDecimal.ZERO;
        BigDecimal efectivo = BigDecimal.ZERO;
        int numVentas = 0;
        for (CajaDetalle.PorMetodo p : porMetodo) {
            totalVentas = totalVentas.add(p.total());
            numVentas += p.ventas();
            if ("EFECTIVO".equals(p.codigo())) {
                efectivo = efectivo.add(p.total());
            }
        }
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal egresos = BigDecimal.ZERO;
        for (CajaDetalle.Movimiento mv : movimientos) {
            if ("INGRESO".equals(mv.tipo())) ingresos = ingresos.add(mv.monto());
            else egresos = egresos.add(mv.monto());
        }
        int anuladas = (int) ventas.stream().filter(v -> "ANULADA".equals(v.estado())).count();

        BigDecimal apertura = (BigDecimal) c.get("monto_apertura");
        // Abierta: se calcula al momento. Cerrada: vale lo que se guardó en el arqueo
        BigDecimal esperado = abierta
                ? apertura.add(efectivo).add(ingresos).subtract(egresos)
                : (BigDecimal) c.get("efectivo_esperado");
        LocalDateTime fecApertura = aFecha(c.get("fec_apertura"));

        return new CajaDetalle(
                idCaja,
                (String) c.get("numero"),
                (String) c.get("cuenta"),
                (String) c.get("nombre"),
                fecApertura,
                apertura,
                (String) c.get("cuenta_entrega"),
                (String) c.get("nombre_entrega"),
                (String) c.get("obs_apertura"),
                aFecha(c.get("fec_cierre")),
                (String) c.get("cuenta_cierre"),
                (String) c.get("estado"),
                abierta && fecApertura != null && fecApertura.toLocalDate().isBefore(LocalDate.now()),
                numVentas,
                anuladas,
                totalVentas,
                efectivo,
                ingresos,
                egresos,
                esperado,
                (BigDecimal) c.get("efectivo_contado"),
                (BigDecimal) c.get("diferencia"),
                (String) c.get("obs_cierre"),
                porMetodo,
                movimientos,
                ventas);
    }

    /** Fondo + ventas en efectivo + entradas - salidas, con lo registrado hasta este momento. */
    private BigDecimal efectivoEsperado(Long idCaja) {
        BigDecimal v = jdbc.queryForObject("""
                SELECT c.monto_apertura
                     + COALESCE((SELECT SUM(v.tot_total) FROM venta v JOIN metodo m ON m.id_metodo = v.id_metodo
                                 WHERE v.id_caja = c.id_caja AND v.estado = 'CONFIRMADA' AND m.codigo = 'EFECTIVO'), 0)
                     + COALESCE((SELECT SUM(monto) FROM caja_movimiento WHERE id_caja = c.id_caja AND tipo = 'INGRESO'), 0)
                     - COALESCE((SELECT SUM(monto) FROM caja_movimiento WHERE id_caja = c.id_caja AND tipo = 'EGRESO'), 0)
                FROM caja c WHERE c.id_caja = ?
                """, BigDecimal.class, idCaja);
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Bloquea la fila de la caja hasta el fin de la transacción (dos cierres a la vez, un retiro y un cierre...). */
    private Map<String, Object> bloquear(Long idCaja) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT id_caja, numero, id_usuario, estado FROM caja WHERE id_caja = ? FOR UPDATE", idCaja);
        if (filas.isEmpty()) {
            throw new ResourceNotFoundException("La caja " + idCaja + " no existe");
        }
        return filas.get(0);
    }

    private Optional<Long> idCajaAbierta(Integer idUsuario) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id_caja FROM caja WHERE id_usuario = ? AND estado = 'ABIERTA'", Long.class, idUsuario);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    private String numeroDe(Long idCaja) {
        return jdbc.queryForObject("SELECT numero FROM caja WHERE id_caja = ?", String.class, idCaja);
    }

    private BigDecimal soles(BigDecimal valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(campo + " admite hasta 2 decimales (céntimos)");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime aFecha(Object valor) {
        if (valor == null) return null;
        if (valor instanceof LocalDateTime l) return l;
        if (valor instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        return LocalDateTime.parse(valor.toString().replace(' ', 'T'));
    }

    public boolean esSupervisor() {
        return tieneAlguna(ROLES_SUPERVISOR);
    }

    private boolean puedeVerTodas() {
        return esSupervisor() || tieneAlguna(ROLES_CONSULTA_TOTAL);
    }

    private boolean tieneAlguna(Set<String> roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (roles.contains(a.getAuthority())) return true;
        }
        return false;
    }

    private String cuentaActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "" : auth.getName();
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

    private String texto(String s) {
        return s == null ? "" : s.trim();
    }

    private String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
