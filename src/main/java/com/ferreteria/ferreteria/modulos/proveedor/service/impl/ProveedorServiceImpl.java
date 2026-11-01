package com.ferreteria.ferreteria.modulos.proveedor.service.impl;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.proveedor.dto.ProveedorDTO;
import com.ferreteria.ferreteria.modulos.proveedor.model.Proveedor;
import com.ferreteria.ferreteria.modulos.proveedor.repository.ProveedorRepository;
import com.ferreteria.ferreteria.modulos.proveedor.service.ProveedorService;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;

import java.util.List;

@Service
public class ProveedorServiceImpl implements ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraService bitacora;

    public ProveedorServiceImpl(ProveedorRepository proveedorRepository,
                            UsuarioRepository usuarioRepository,
                            BitacoraService bitacora) {
    this.proveedorRepository = proveedorRepository;
    this.usuarioRepository = usuarioRepository;
    this.bitacora = bitacora;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ProveedorDTO> listarTodos() {
        return proveedorRepository.findAllOrdenados().stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProveedorDTO> listarActivos() {
        return proveedorRepository.findByEstado("ACTIVO").stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProveedorDTO obtenerPorId(Long id) {
        return convertirADTO(buscarProveedor(id));
    }

    @Override
    @Transactional
    public ProveedorDTO guardar(ProveedorDTO dto) {
        normalizar(dto);
        validarRestriccionesProveedor(dto.getTipo_doc(), dto.getNum_doc());

        if (proveedorRepository.existsByDocumento(dto.getTipo_doc(), dto.getNum_doc())) {
            throw new IllegalArgumentException("Ya existe un proveedor registrado con " + dto.getTipo_doc() + " " + dto.getNum_doc());
        }

        Proveedor proveedor = convertirAEntidad(dto);
        proveedor.setId_usuario(obtenerIdUsuarioActual()); // FK obligatoria: quién lo registró
        Proveedor guardado = proveedorRepository.save(proveedor);
        bitacora.registrar("PROVEEDORES", "CREAR", "Creó el proveedor " + guardado.getRazon_social());
        return convertirADTO(guardado);
    }

    @Override
    @Transactional
    public ProveedorDTO actualizar(Long id, ProveedorDTO dto) {
        normalizar(dto);
        validarRestriccionesProveedor(dto.getTipo_doc(), dto.getNum_doc());

    Proveedor proveedor = buscarProveedor(id);

    if (proveedorRepository.existsByDocumentoEnOtroProveedor(dto.getTipo_doc(), dto.getNum_doc(), id)) {
        throw new IllegalArgumentException("El documento " + dto.getTipo_doc() + " " + dto.getNum_doc() + " ya está en uso por otro proveedor.");
    }

    StringBuilder c = new StringBuilder();
    if (!java.util.Objects.equals(proveedor.getRazon_social(), dto.getRazon_social()))
        c.append("Razón Social: ").append(proveedor.getRazon_social()).append(" → ").append(dto.getRazon_social()).append("; ");
    if (!java.util.Objects.equals(proveedor.getNom_comercial(), dto.getNom_comercial()))
        c.append("Nombre Comercial: ").append(proveedor.getNom_comercial()).append(" → ").append(dto.getNom_comercial()).append("; ");
    if (!java.util.Objects.equals(proveedor.getNum_doc(), dto.getNum_doc()))
        c.append("Documento: ").append(proveedor.getNum_doc()).append(" → ").append(dto.getNum_doc()).append("; ");
    if (!java.util.Objects.equals(proveedor.getTelefono(), dto.getTelefono()))
        c.append("Teléfono: ").append(proveedor.getTelefono()).append(" → ").append(dto.getTelefono()).append("; ");
    if (!java.util.Objects.equals(proveedor.getEmail(), dto.getEmail()))
        c.append("Correo: ").append(proveedor.getEmail()).append(" → ").append(dto.getEmail()).append("; ");
    if (!java.util.Objects.equals(proveedor.getDireccion(), dto.getDireccion()))
        c.append("Dirección: ").append(proveedor.getDireccion()).append(" → ").append(dto.getDireccion()).append("; ");
    if (!java.util.Objects.equals(proveedor.getContacto(), dto.getContacto()))
        c.append("Contacto: ").append(proveedor.getContacto()).append(" → ").append(dto.getContacto()).append("; ");
    if (!java.util.Objects.equals(proveedor.getEstado(), dto.getEstado()))
        c.append("Estado: ").append(proveedor.getEstado()).append(" → ").append(dto.getEstado()).append("; ");

    proveedor.setTipo_doc(dto.getTipo_doc());
    proveedor.setNum_doc(dto.getNum_doc());
    proveedor.setRazon_social(dto.getRazon_social());
    proveedor.setNom_comercial(dto.getNom_comercial());
    proveedor.setTelefono(dto.getTelefono());
    proveedor.setEmail(dto.getEmail());
    proveedor.setDireccion(dto.getDireccion());
    proveedor.setContacto(dto.getContacto());

    if (dto.getEstado() != null && !dto.getEstado().isBlank()) {
        if (!dto.getEstado().matches("^(ACTIVO|INACTIVO)$")) {
            throw new IllegalArgumentException("El estado debe ser ACTIVO o INACTIVO");
        }
        proveedor.setEstado(dto.getEstado());
    }

    Proveedor actualizado = proveedorRepository.save(proveedor);
    bitacora.registrar("PROVEEDORES", "EDITAR",
        "Editó a " + dto.getRazon_social() + ". " + (c.length() == 0 ? "Sin cambios." : c.toString()));
    return convertirADTO(actualizado);
    }
    
    @Override
    @Transactional
    public void cambiarEstado(Long id, String nuevoEstado) {
        if (nuevoEstado == null || !nuevoEstado.toUpperCase().matches("^(ACTIVO|INACTIVO)$")) {
            throw new IllegalArgumentException("El estado debe ser ACTIVO o INACTIVO");
        }
        Proveedor proveedor = buscarProveedor(id);
        proveedor.setEstado(nuevoEstado.toUpperCase());
        proveedorRepository.save(proveedor);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Proveedor proveedor = buscarProveedor(id);
        try {
            proveedorRepository.delete(proveedor);
            bitacora.registrar("PROVEEDORES", "ELIMINAR", "Eliminó el proveedor " + proveedor.getRazon_social());
            proveedorRepository.flush(); // Fuerza el DELETE aquí para capturar la FK de compra
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "No se puede eliminar el proveedor porque tiene compras registradas. Cámbielo a INACTIVO.");
        }
    }

    // =========================================================================
    // MÉTODOS PRIVADOS
    // =========================================================================

    private Proveedor buscarProveedor(Long id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado con ID: " + id));
    }

    /**
     * Valida el número de documento según su tipo.
     * RUC: 11 dígitos y empieza en 10, 15, 17 o 20 (formato SUNAT).
     * DNI: 8 dígitos. CE y PAS: 6 a 12 caracteres alfanuméricos.
     */
    private void validarRestriccionesProveedor(String tipoDoc, String numDoc) {
        if (tipoDoc == null || numDoc == null) {
            throw new IllegalArgumentException("El tipo y número de documento son obligatorios");
        }
        if ("RUC".equals(tipoDoc)) {
            if (!numDoc.matches("^(10|15|17|20)[0-9]{9}$")) {
                throw new IllegalArgumentException("El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20");
            }
        } else if ("DNI".equals(tipoDoc)) {
            if (!numDoc.matches("^[0-9]{8}$")) {
                throw new IllegalArgumentException("El DNI debe contener exactamente 8 dígitos numéricos");
            }
        } else if ("CE".equals(tipoDoc) || "PAS".equals(tipoDoc)) {
            if (!numDoc.matches("^[A-Z0-9]{6,12}$")) {
                throw new IllegalArgumentException("El " + tipoDoc + " debe tener entre 6 y 12 caracteres alfanuméricos");
            }
        } else {
            throw new IllegalArgumentException("El tipo de documento debe ser RUC, DNI, CE o PAS");
        }
    }

    /** Quita espacios y pasa a mayúsculas los campos que lo necesitan */
    private void normalizar(ProveedorDTO dto) {
        if (dto.getTipo_doc() != null) dto.setTipo_doc(dto.getTipo_doc().trim().toUpperCase());
        if (dto.getNum_doc() != null) dto.setNum_doc(dto.getNum_doc().trim().toUpperCase());
        if (dto.getRazon_social() != null) dto.setRazon_social(dto.getRazon_social().trim());
        if (dto.getNom_comercial() != null) dto.setNom_comercial(dto.getNom_comercial().trim());
        if (dto.getEmail() != null) dto.setEmail(dto.getEmail().trim().toLowerCase());
    }

    /** Obtiene el id del usuario logueado a partir de la sesión de Spring Security */
    private Integer obtenerIdUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalArgumentException("No hay un usuario autenticado en la sesión");
        }
        return usuarioRepository.findByCuenta(auth.getName())
                .map(Usuario::getIdUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario de sesión no encontrado: " + auth.getName()));
    }

    private ProveedorDTO convertirADTO(Proveedor proveedor) {
        ProveedorDTO dto = new ProveedorDTO();
        dto.setId_proveedor(proveedor.getId_proveedor());
        dto.setTipo_doc(proveedor.getTipo_doc());
        dto.setNum_doc(proveedor.getNum_doc());
        dto.setRazon_social(proveedor.getRazon_social());
        dto.setNom_comercial(proveedor.getNom_comercial());
        dto.setTelefono(proveedor.getTelefono());
        dto.setEmail(proveedor.getEmail());
        dto.setDireccion(proveedor.getDireccion());
        dto.setContacto(proveedor.getContacto());
        dto.setEstado(proveedor.getEstado());
        dto.setFec_registro(proveedor.getFec_registro());
        return dto;
    }

    private Proveedor convertirAEntidad(ProveedorDTO dto) {
        Proveedor proveedor = new Proveedor();
        proveedor.setTipo_doc(dto.getTipo_doc());
        proveedor.setNum_doc(dto.getNum_doc());
        proveedor.setRazon_social(dto.getRazon_social());
        proveedor.setNom_comercial(dto.getNom_comercial());
        proveedor.setTelefono(dto.getTelefono());
        proveedor.setEmail(dto.getEmail());
        proveedor.setDireccion(dto.getDireccion());
        proveedor.setContacto(dto.getContacto());
        proveedor.setEstado(dto.getEstado() != null ? dto.getEstado() : "ACTIVO");
        return proveedor;
    }
}
