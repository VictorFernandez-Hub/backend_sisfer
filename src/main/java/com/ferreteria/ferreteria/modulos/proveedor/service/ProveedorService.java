package com.ferreteria.ferreteria.modulos.proveedor.service;

import com.ferreteria.ferreteria.modulos.proveedor.dto.ProveedorDTO;

import java.util.List;

public interface ProveedorService {
    List<ProveedorDTO> listarTodos();
    List<ProveedorDTO> listarActivos();
    ProveedorDTO obtenerPorId(Long id);
    ProveedorDTO guardar(ProveedorDTO proveedorDTO);
    ProveedorDTO actualizar(Long id, ProveedorDTO proveedorDTO);
    void cambiarEstado(Long id, String nuevoEstado);

    void eliminar(Long id);
}
