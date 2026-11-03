package com.ferreteria.ferreteria.modulos.producto.service.impl;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.producto.dto.CatalogoDTO;
import com.ferreteria.ferreteria.modulos.producto.dto.ProductoDTO;
import com.ferreteria.ferreteria.modulos.producto.model.Categoria;
import com.ferreteria.ferreteria.modulos.producto.model.Marca;
import com.ferreteria.ferreteria.modulos.producto.model.Producto;
import com.ferreteria.ferreteria.modulos.producto.model.Unidad;
import com.ferreteria.ferreteria.modulos.producto.repository.CategoriaRepository;
import com.ferreteria.ferreteria.modulos.producto.repository.MarcaRepository;
import com.ferreteria.ferreteria.modulos.producto.repository.ProductoRepository;
import com.ferreteria.ferreteria.modulos.producto.repository.UnidadRepository;
import com.ferreteria.ferreteria.modulos.producto.service.ProductoService;
import com.ferreteria.ferreteria.modulos.proveedor.model.Proveedor;
import com.ferreteria.ferreteria.modulos.proveedor.repository.ProveedorRepository;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;
    private final UnidadRepository unidadRepository;
    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final JdbcTemplate jdbc;
    private final BitacoraService bitacora;

    public ProductoServiceImpl(ProductoRepository productoRepository,
                               CategoriaRepository categoriaRepository,
                               MarcaRepository marcaRepository,
                               UnidadRepository unidadRepository,
                               ProveedorRepository proveedorRepository,
                               UsuarioRepository usuarioRepository,
                               JdbcTemplate jdbc,
                               BitacoraService bitacora){
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.marcaRepository = marcaRepository;
        this.unidadRepository = unidadRepository;
        this.proveedorRepository = proveedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.jdbc = jdbc;
        this.bitacora = bitacora;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoDTO> listarTodos() {
        Set<Long> conMovimientos = productosConMovimientos();
        return productoRepository.findAllConRelaciones().stream()
                .map(p -> convertirADTO(p, conMovimientos))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoDTO> listarActivos() {
        Set<Long> conMovimientos = productosConMovimientos();
        return productoRepository.findByEstadoConRelaciones("ACTIVO").stream()
                .map(p -> convertirADTO(p, conMovimientos))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoDTO obtenerPorId(Long id) {
        Producto producto = productoRepository.findByIdConRelaciones(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        ProductoDTO dto = convertirADTO(producto);
        dto.setEliminable(!tieneMovimientos(id));
        return dto;
    }

    @Override
    @Transactional
    public ProductoDTO guardar(ProductoDTO dto) {
        normalizar(dto);
        validarPrecios(dto.getPrecio_compra(), dto.getPrecio_venta());

        if (productoRepository.existsByCodigo(dto.getCodigo())) {
            throw new IllegalArgumentException("Ya existe un producto con el código: " + dto.getCodigo());
        }

        Producto producto = new Producto();
        producto.setCodigo(dto.getCodigo());
        producto.setNombre(dto.getNombre());
        producto.setDescripcion(dto.getDescripcion());
        producto.setCategoria(buscarCategoriaActiva(dto.getId_categoria()));
        producto.setMarca(buscarMarcaActiva(dto.getId_marca()));
        producto.setUnidad(buscarUnidad(dto.getId_unidad()));
        producto.setProveedor(buscarProveedorActivo(dto.getId_proveedor()));
        // Sin precios al registrar: quedan en 0 hasta la primera compra
        producto.setPrecio_compra(dto.getPrecio_compra() != null ? dto.getPrecio_compra() : BigDecimal.ZERO);
        producto.setPrecio_venta(dto.getPrecio_venta() != null ? dto.getPrecio_venta() : BigDecimal.ZERO);
        producto.setStock_minimo(dto.getStock_minimo());
        producto.setEstado(dto.getEstado() != null ? dto.getEstado() : "ACTIVO");
        // stock_actual NO se asigna con JPA: si hay stock inicial entra como ajuste + kardex

        Producto guardado = productoRepository.saveAndFlush(producto);

        BigDecimal stockInicial = dto.getStock_inicial();
        if (stockInicial != null && stockInicial.compareTo(BigDecimal.ZERO) > 0) {
            registrarStockInicial(guardado, stockInicial);
            guardado.setStock_actual(stockInicial); // Solo para la respuesta; JPA no escribe esta columna
        }

        bitacora.registrar("PRODUCTOS", "CREAR", "Creó el producto " + guardado.getCodigo()
                + (guardado.getStock_actual() != null && guardado.getStock_actual().signum() > 0
                        ? " con stock inicial " + guardado.getStock_actual().stripTrailingZeros().toPlainString()
                        : ""));
        return convertirADTO(guardado);
    }

    @Override
    @Transactional
    public ProductoDTO actualizar(Long id, ProductoDTO dto) {
        normalizar(dto);
        validarPrecios(dto.getPrecio_compra(), dto.getPrecio_venta());

        Producto producto = productoRepository.findByIdConRelaciones(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (productoRepository.existsByCodigoEnOtroProducto(dto.getCodigo(), id)) {
            throw new IllegalArgumentException("El código " + dto.getCodigo() + " ya está en uso por otro producto.");
        }

        // Solo se exige que esté ACTIVA si se cambia a otra categoría/marca.
        // Así un producto antiguo puede editarse aunque su categoría actual se haya inactivado.
        if (!Objects.equals(producto.getCategoria().getId_categoria(), dto.getId_categoria())) {
            producto.setCategoria(buscarCategoriaActiva(dto.getId_categoria()));
        }
        if (!Objects.equals(producto.getMarca().getId_marca(), dto.getId_marca())) {
            producto.setMarca(buscarMarcaActiva(dto.getId_marca()));
        }
        if (!Objects.equals(producto.getUnidad().getId_unidad(), dto.getId_unidad())) {
            producto.setUnidad(buscarUnidad(dto.getId_unidad()));
        }

        StringBuilder c = new StringBuilder();
        Long proveedorActual = producto.getProveedor() != null ? producto.getProveedor().getId_proveedor() : null;
        if (!Objects.equals(proveedorActual, dto.getId_proveedor())) {
            Proveedor nuevo = buscarProveedorActivo(dto.getId_proveedor());
            c.append("Proveedor: ")
             .append(producto.getProveedor() != null ? producto.getProveedor().getRazon_social() : "(sin proveedor)")
             .append(" → ").append(nuevo.getRazon_social()).append("; ");
            producto.setProveedor(nuevo);
        }
        if (!java.util.Objects.equals(producto.getNombre(), dto.getNombre()))
            c.append("Nombre: ").append(producto.getNombre()).append(" → ").append(dto.getNombre()).append("; ");
        if (!java.util.Objects.equals(producto.getCodigo(), dto.getCodigo()))
            c.append("Código: ").append(producto.getCodigo()).append(" → ").append(dto.getCodigo()).append("; ");
        // Si no se envían precios se conservan los actuales
        if (dto.getPrecio_compra() == null) dto.setPrecio_compra(producto.getPrecio_compra());
        if (dto.getPrecio_venta() == null) dto.setPrecio_venta(producto.getPrecio_venta());
        // Regla del negocio: el precio de venta solo lo cambia el administrador del negocio
        if (producto.getPrecio_venta().compareTo(dto.getPrecio_venta()) != 0 && !esAdminNegocio()) {
            throw new IllegalArgumentException("Solo el administrador del negocio puede cambiar el precio de venta.");
        }
        if (producto.getPrecio_compra().compareTo(dto.getPrecio_compra()) != 0)
            c.append("Precio de Compra: ").append(producto.getPrecio_compra()).append(" → ").append(dto.getPrecio_compra()).append("; ");
        if (producto.getPrecio_venta().compareTo(dto.getPrecio_venta()) != 0)
            c.append("Precio de Venta: ").append(producto.getPrecio_venta()).append(" → ").append(dto.getPrecio_venta()).append("; ");
        if (producto.getStock_minimo().compareTo(dto.getStock_minimo()) != 0)
            c.append("Stock mínimo: ").append(producto.getStock_minimo()).append(" → ").append(dto.getStock_minimo()).append("; ");
        if (!java.util.Objects.equals(producto.getEstado(), dto.getEstado()))
            c.append("Estado: ").append(producto.getEstado()).append(" → ").append(dto.getEstado()).append("; ");

        producto.setCodigo(dto.getCodigo());
        producto.setNombre(dto.getNombre());
        producto.setDescripcion(dto.getDescripcion());
        producto.setPrecio_compra(dto.getPrecio_compra());
        producto.setPrecio_venta(dto.getPrecio_venta());
        producto.setStock_minimo(dto.getStock_minimo());
        // stock_actual se ignora aunque venga en el JSON

        if (dto.getEstado() != null && !dto.getEstado().isBlank()) {
            if (!dto.getEstado().matches("^(ACTIVO|INACTIVO)$")) {
                throw new IllegalArgumentException("El estado debe ser ACTIVO o INACTIVO");
            }
            producto.setEstado(dto.getEstado());
        }

        Producto actualizado = productoRepository.save(producto);
        bitacora.registrar("PRODUCTOS", "EDITAR",
                "Editó " + dto.getCodigo() + ". " + (c.length() == 0 ? "Sin cambios." : c.toString()));
        return convertirADTO(actualizado);
    }

    @Override
    @Transactional
    public void cambiarEstado(Long id, String nuevoEstado) {
        if (nuevoEstado == null || !nuevoEstado.toUpperCase().matches("^(ACTIVO|INACTIVO)$")) {
            throw new IllegalArgumentException("El estado debe ser ACTIVO o INACTIVO");
        }
        Producto producto = buscarNoEliminado(id);
        String estado = nuevoEstado.toUpperCase();
        if (estado.equals(producto.getEstado())) return;
        producto.setEstado(estado);
        productoRepository.save(producto);
        bitacora.registrar("PRODUCTOS", "ACTIVO".equals(estado) ? "REACTIVAR" : "DAR_BAJA",
                ("ACTIVO".equals(estado) ? "Reactivó" : "Dio de baja (INACTIVO)") + " el producto " + producto.getCodigo());
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Producto producto = buscarNoEliminado(id);

        // Primero se desactiva y luego se borra desde "Ver inactivos": evita borrar algo en uso por error
        if (!"INACTIVO".equals(producto.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se pueden borrar productos INACTIVOS. Desactive " + producto.getCodigo() + " primero.");
        }

        boolean tieneStock = producto.getStock_actual() != null
                && producto.getStock_actual().compareTo(BigDecimal.ZERO) > 0;

        // Con stock o historial no se puede borrar la fila sin romper el kardex, las compras
        // y las ventas: se marca ELIMINADO, desaparece de la aplicación y el historial queda intacto.
        if (tieneStock || tieneMovimientos(id)) {
            producto.setEstado("ELIMINADO");
            productoRepository.save(producto);
            bitacora.registrar("PRODUCTOS", "ELIMINAR", "Eliminó el producto " + producto.getCodigo()
                    + " (se conserva su historial de kardex)");
            return;
        }

        // Sin movimientos (registro hecho por error): se borra de verdad
        try {
            productoRepository.delete(producto);
            productoRepository.flush(); // Fuerza el DELETE aquí para capturar una FK no prevista
            bitacora.registrar("PRODUCTOS", "ELIMINAR", "Eliminó el producto " + producto.getCodigo());
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "No se pudo eliminar el producto porque otra tabla lo referencia.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String siguienteCodigo() {
        // Menor número libre, contando también los ELIMINADOS: su código sigue ocupado en la base
        Pattern numeroFinal = Pattern.compile("(\\d+)$");
        Set<Integer> usados = new HashSet<>();
        for (String codigo : productoRepository.findTodosLosCodigos()) {
            Matcher m = numeroFinal.matcher(codigo);
            if (m.find()) usados.add(Integer.parseInt(m.group(1)));
        }
        int n = 1;
        while (usados.contains(n)) n++;
        return String.format("HER-%03d", n);
    }

    private Producto buscarNoEliminado(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        if ("ELIMINADO".equals(producto.getEstado())) {
            throw new ResourceNotFoundException("Producto no encontrado con ID: " + id);
        }
        return producto;
    }

    // =========================================================================
    // CATÁLOGOS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoDTO> listarCategorias() {
        return categoriaRepository.findByEstadoOrderByNombreAsc("ACTIVO").stream()
                .map(c -> new CatalogoDTO(c.getId_categoria(), null, c.getNombre()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoDTO> listarMarcas() {
        return marcaRepository.findByEstadoOrderByNombreAsc("ACTIVO").stream()
                .map(m -> new CatalogoDTO(m.getId_marca(), null, m.getNombre()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoDTO> listarUnidades() {
        return unidadRepository.findAllByOrderByNombreAsc().stream()
                .map(u -> new CatalogoDTO(u.getId_unidad(), u.getCodigo(), u.getNombre()))
                .toList();
    }

    // =========================================================================
    // MÉTODOS PRIVADOS
    // =========================================================================

    private Categoria buscarCategoriaActiva(Integer id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));
        if (!"ACTIVO".equals(categoria.getEstado())) {
            throw new IllegalArgumentException("La categoría '" + categoria.getNombre() + "' está inactiva");
        }
        return categoria;
    }

    private Marca buscarMarcaActiva(Integer id) {
        Marca marca = marcaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada con ID: " + id));
        if (!"ACTIVO".equals(marca.getEstado())) {
            throw new IllegalArgumentException("La marca '" + marca.getNombre() + "' está inactiva");
        }
        return marca;
    }

    private Unidad buscarUnidad(Integer id) {
        return unidadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida no encontrada con ID: " + id));
    }

    private Proveedor buscarProveedorActivo(Long id) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado con ID: " + id));
        if (!"ACTIVO".equals(proveedor.getEstado())) {
            throw new IllegalArgumentException("El proveedor '" + proveedor.getRazon_social() + "' está inactivo");
        }
        return proveedor;
    }

    /**
     * Da ingreso al stock inicial igual que sp_confirmar_ajuste: ajuste CONFIRMADO,
     * su línea de ENTRADA, el movimiento en kardex y la suma en producto.
     * Corre dentro de la transacción de guardar(): si algo falla, no se crea el producto.
     * No se llama al procedimiento porque su START TRANSACTION cerraría la transacción de Spring.
     */
    private void registrarStockInicial(Producto producto, BigDecimal cantidad) {
        Integer idUsuario = obtenerIdUsuarioActual();
        Long idProducto = producto.getId_producto();

        // El número definitivo (AJ-0001) depende del id, así que primero va uno temporal
        String numeroTemporal = "TMP-" + UUID.randomUUID().toString().substring(0, 12);
        KeyHolder llave = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ajuste (id_usuario, numero, motivo, observacion, estado) " +
                    "VALUES (?, ?, 'OTRO', ?, 'CONFIRMADO')", Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idUsuario);
            ps.setString(2, numeroTemporal);
            ps.setString(3, "Stock inicial al registrar el producto " + producto.getCodigo());
            return ps;
        }, llave);
        long idAjuste = llave.getKey().longValue();

        jdbc.update("UPDATE ajuste SET numero = ? WHERE id_ajuste = ?",
                String.format("AJ-%04d", idAjuste), idAjuste);
        jdbc.update("INSERT INTO linea_ajuste (id_ajuste, id_producto, tipo, cantidad) VALUES (?, ?, 'ENTRADA', ?)",
                idAjuste, idProducto, cantidad);
        jdbc.update("INSERT INTO kardex (id_producto, id_usuario, tipo_mov, documento, id_doc, cantidad, " +
                    "costo_unit, stock_ant, stock_nuevo, observacion) " +
                    "VALUES (?, ?, 'AJUSTE_ENTRADA', 'AJUSTE', ?, ?, ?, 0, ?, 'Stock inicial')",
                idProducto, idUsuario, idAjuste, cantidad, producto.getPrecio_compra(), cantidad);
        jdbc.update("UPDATE producto SET stock_actual = ? WHERE id_producto = ?", cantidad, idProducto);
    }

    // Toda tabla que referencia al producto con un movimiento de inventario
    private static final String SQL_PRODUCTOS_CON_MOVIMIENTOS =
            "SELECT id_producto FROM kardex " +
            "UNION SELECT id_producto FROM linea_compra " +
            "UNION SELECT id_producto FROM linea_venta " +
            "UNION SELECT id_producto FROM linea_ajuste";

    /** Ids de productos con historial: una sola consulta para todo el listado */
    private Set<Long> productosConMovimientos() {
        return new HashSet<>(jdbc.queryForList(SQL_PRODUCTOS_CON_MOVIMIENTOS, Long.class));
    }

    private boolean tieneMovimientos(Long idProducto) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (" + SQL_PRODUCTOS_CON_MOVIMIENTOS + ") m WHERE m.id_producto = ?",
                Integer.class, idProducto);
        return total != null && total > 0;
    }

    private Integer obtenerIdUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalArgumentException("No hay un usuario autenticado en la sesión");
        }
        return usuarioRepository.findByCuenta(auth.getName())
                .map(Usuario::getIdUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario de sesión no encontrado: " + auth.getName()));
    }

    private boolean esAdminNegocio() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ADMIN_NEGOCIO".equals(a.getAuthority()) || "ROLE_ADMIN_NEGOCIO".equals(a.getAuthority()));
    }

    /** Regla de negocio: no se vende por debajo del costo */
    private void validarPrecios(BigDecimal precioCompra, BigDecimal precioVenta) {
        if (precioCompra != null && precioVenta != null && precioVenta.compareTo(precioCompra) < 0) {
            throw new IllegalArgumentException("El precio de venta no puede ser menor al precio de compra");
        }
    }

    private void normalizar(ProductoDTO dto) {
        if (dto.getCodigo() != null) dto.setCodigo(dto.getCodigo().trim().toUpperCase());
        if (dto.getNombre() != null) dto.setNombre(dto.getNombre().trim());
        if (dto.getDescripcion() != null) dto.setDescripcion(dto.getDescripcion().trim());
    }

    /** Misma lógica que la vista v_alerta_stock */
    private String calcularNivelStock(BigDecimal stockActual, BigDecimal stockMinimo) {
        BigDecimal actual = (stockActual != null) ? stockActual : BigDecimal.ZERO;
        BigDecimal minimo = (stockMinimo != null) ? stockMinimo : BigDecimal.ZERO;
        if (actual.compareTo(BigDecimal.ZERO) <= 0) return "AGOTADO";
        if (actual.compareTo(minimo) <= 0) return "STOCK_MINIMO";
        return "OK";
    }

    private ProductoDTO convertirADTO(Producto producto, Set<Long> conMovimientos) {
        ProductoDTO dto = convertirADTO(producto);
        dto.setEliminable(!conMovimientos.contains(producto.getId_producto()));
        return dto;
    }

    private ProductoDTO convertirADTO(Producto producto) {
        ProductoDTO dto = new ProductoDTO();
        dto.setId_producto(producto.getId_producto());
        dto.setCodigo(producto.getCodigo());
        dto.setNombre(producto.getNombre());
        dto.setDescripcion(producto.getDescripcion());

        dto.setId_categoria(producto.getCategoria().getId_categoria());
        dto.setCategoria(producto.getCategoria().getNombre());
        dto.setId_marca(producto.getMarca().getId_marca());
        dto.setMarca(producto.getMarca().getNombre());
        dto.setId_unidad(producto.getUnidad().getId_unidad());
        dto.setUnidad(producto.getUnidad().getNombre());
        dto.setUnidad_codigo(producto.getUnidad().getCodigo());
        if (producto.getProveedor() != null) {
            dto.setId_proveedor(producto.getProveedor().getId_proveedor());
            dto.setProveedor(producto.getProveedor().getRazon_social());
        }

        dto.setPrecio_compra(producto.getPrecio_compra());
        dto.setPrecio_venta(producto.getPrecio_venta());
        dto.setStock_actual(producto.getStock_actual());
        dto.setStock_minimo(producto.getStock_minimo());
        dto.setNivel_stock(calcularNivelStock(producto.getStock_actual(), producto.getStock_minimo()));

        dto.setEstado(producto.getEstado());
        dto.setFec_registro(producto.getFec_registro());
        dto.setFec_modifica(producto.getFec_modifica());
        return dto;
    }
}
