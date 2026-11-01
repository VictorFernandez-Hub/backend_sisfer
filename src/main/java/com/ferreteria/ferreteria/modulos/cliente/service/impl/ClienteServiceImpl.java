package com.ferreteria.ferreteria.modulos.cliente.service.impl;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.cliente.dto.ClienteDTO;
import com.ferreteria.ferreteria.modulos.cliente.model.Cliente;
import com.ferreteria.ferreteria.modulos.cliente.repository.ClienteRepository;
import com.ferreteria.ferreteria.modulos.cliente.service.ClienteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;

import java.util.List;

@Service
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;
    private final BitacoraService bitacora;

    public ClienteServiceImpl(ClienteRepository clienteRepository, BitacoraService bitacora) {
    this.clienteRepository = clienteRepository;
    this.bitacora = bitacora;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteDTO> listarTodos() {
        return clienteRepository.findAll().stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteDTO> listarActivos() {
        return clienteRepository.findByEstado("ACTIVO").stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteDTO obtenerPorId(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));
        return convertirADTO(cliente);
    }

    @Override
    @Transactional
    public ClienteDTO guardar(ClienteDTO dto) {
        validarRestriccionesCliente(dto.getTipo_doc(), dto.getNum_doc(), dto.getTelefono());

        if (clienteRepository.existsByNumDoc(dto.getNum_doc())) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con el número de documento: " + dto.getNum_doc());
        }

        Cliente cliente = convertirAEntidad(dto);
        Cliente guardado = clienteRepository.save(cliente);
        bitacora.registrar("CLIENTES", "CREAR", "Creó el cliente " + dto.getNombres());
        return convertirADTO(guardado);
    }

    @Override
    @Transactional
    public ClienteDTO actualizar(Long id, ClienteDTO dto) {
    validarRestriccionesCliente(dto.getTipo_doc(), dto.getNum_doc(), dto.getTelefono());

    Cliente cliente = clienteRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));

    if (!cliente.getNum_doc().equals(dto.getNum_doc()) && clienteRepository.existsByNumDoc(dto.getNum_doc())) {
        throw new IllegalArgumentException("El número de documento " + dto.getNum_doc() + " ya está en uso por otro cliente.");
    }

    StringBuilder c = new StringBuilder();
    if (!java.util.Objects.equals(cliente.getNombres(), dto.getNombres()))
        c.append("Nombre: ").append(cliente.getNombres()).append(" → ").append(dto.getNombres()).append("; ");
    if (!java.util.Objects.equals(cliente.getTipo_doc(), dto.getTipo_doc()))
        c.append("Tipo de Documento: ").append(cliente.getTipo_doc()).append(" → ").append(dto.getTipo_doc()).append("; ");
    if (!java.util.Objects.equals(cliente.getNum_doc(), dto.getNum_doc()))
        c.append("Número de Documento: ").append(cliente.getNum_doc()).append(" → ").append(dto.getNum_doc()).append("; ");
    if (!java.util.Objects.equals(cliente.getTelefono(), dto.getTelefono()))
        c.append("Teléfono: ").append(cliente.getTelefono()).append(" → ").append(dto.getTelefono()).append("; ");
    if (!java.util.Objects.equals(cliente.getEmail(), dto.getEmail()))
        c.append("Correo: ").append(cliente.getEmail()).append(" → ").append(dto.getEmail()).append("; ");
    if (!java.util.Objects.equals(cliente.getDireccion(), dto.getDireccion()))
        c.append("Dirección: ").append(cliente.getDireccion()).append(" → ").append(dto.getDireccion()).append("; ");
    if (!java.util.Objects.equals(cliente.getEstado(), dto.getEstado()))
        c.append("Estado: ").append(cliente.getEstado()).append(" → ").append(dto.getEstado()).append("; ");

    cliente.setTipo_doc(dto.getTipo_doc());
    cliente.setNum_doc(dto.getNum_doc());
    cliente.setNombres(dto.getNombres());
    cliente.setTelefono(dto.getTelefono());
    cliente.setDireccion(dto.getDireccion());
    cliente.setEmail(dto.getEmail());

    if (dto.getEstado() != null && !dto.getEstado().isBlank()) {
        if (!dto.getEstado().matches("^(ACTIVO|INACTIVO)$")) {
            throw new IllegalArgumentException("El estado debe ser ACTIVO o INACTIVO");
        }
        cliente.setEstado(dto.getEstado());
    }

    Cliente actualizado = clienteRepository.save(cliente);
    bitacora.registrar("CLIENTES", "EDITAR",
        "Editó a " + dto.getNombres() + ". " + (c.length() == 0 ? "Sin cambios." : c.toString()));
    return convertirADTO(actualizado);
    }
    
    @Override
    @Transactional
    public void cambiarEstado(Long id, String nuevoEstado) {
        if (nuevoEstado == null || !nuevoEstado.toUpperCase().matches("^(ACTIVO|INACTIVO)$")) {
            throw new IllegalArgumentException("El estado debe ser ACTIVO o INACTIVO");
        }
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));
        cliente.setEstado(nuevoEstado.toUpperCase());
        clienteRepository.save(cliente);
    }

    /**
     * Valida las restricciones equivalentes a los CHECK de MySQL
     */
    private void validarRestriccionesCliente(String tipoDoc, String numDoc, String telefono) {
        // Validation para chk_cliente_tipodoc & chk_cliente_numdoc
        if ("DNI".equalsIgnoreCase(tipoDoc)) {
            if (numDoc == null || !numDoc.matches("^[0-9]{8}$")) {
                throw new IllegalArgumentException("Un cliente con DNI debe contener exactamente 8 dígitos numéricos");
            }
        } else if ("RUC".equalsIgnoreCase(tipoDoc)) {
            if (numDoc == null || !numDoc.matches("^[0-9]{11}$")) {
                throw new IllegalArgumentException("Un cliente con RUC debe contener exactamente 11 dígitos numéricos");
            }
        } else {
            throw new IllegalArgumentException("El tipo de documento debe ser únicamente DNI o RUC");
        }

        // Validation para chk_cliente_telefono
        if (telefono != null && !telefono.isBlank() && !telefono.matches("^[0-9]{9}$")) {
            throw new IllegalArgumentException("El teléfono debe estar vacío o contener exactamente 9 dígitos numéricos");
        }
    }

    private ClienteDTO convertirADTO(Cliente cliente) {
        ClienteDTO dto = new ClienteDTO();
        dto.setId_cliente(cliente.getId_cliente());
        dto.setTipo_doc(cliente.getTipo_doc());
        dto.setNum_doc(cliente.getNum_doc());
        dto.setNombres(cliente.getNombres());
        dto.setTelefono(cliente.getTelefono());
        dto.setDireccion(cliente.getDireccion());
        dto.setEmail(cliente.getEmail());
        dto.setEstado(cliente.getEstado());
        dto.setFec_registro(cliente.getFec_registro());
        return dto;
    }

    private Cliente convertirAEntidad(ClienteDTO dto) {
        Cliente cliente = new Cliente();
        cliente.setId_cliente(dto.getId_cliente());
        cliente.setTipo_doc(dto.getTipo_doc());
        cliente.setNum_doc(dto.getNum_doc());
        cliente.setNombres(dto.getNombres());
        cliente.setTelefono(dto.getTelefono());
        cliente.setDireccion(dto.getDireccion());
        cliente.setEmail(dto.getEmail());
        cliente.setEstado(dto.getEstado() != null ? dto.getEstado() : "ACTIVO");
        return cliente;
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));
        clienteRepository.delete(cliente);
        bitacora.registrar("CLIENTES", "ELIMINAR", "Eliminó el cliente " + id);
    }
}