package com.ferreteria.ferreteria.modulos.cliente.service;

import com.ferreteria.ferreteria.modulos.cliente.dto.ClienteDTO;
import java.util.List;

public interface ClienteService {

    List<ClienteDTO> listarTodos();

    List<ClienteDTO> listarActivos();

    ClienteDTO obtenerPorId(Long id);

    ClienteDTO guardar(ClienteDTO clienteDTO);

    ClienteDTO actualizar(Long id, ClienteDTO clienteDTO);

    void cambiarEstado(Long id, String nuevoEstado);
    
    void eliminar(Long id);
}