package com.ferreteria.ferreteria.modulos.producto.service;

import com.ferreteria.ferreteria.modulos.producto.dto.CatalogoDTO;
import com.ferreteria.ferreteria.modulos.producto.dto.ProductoDTO;

import java.util.List;

public interface ProductoService {
    List<ProductoDTO> listarTodos();
    List<ProductoDTO> listarActivos();
    ProductoDTO obtenerPorId(Long id);
    ProductoDTO guardar(ProductoDTO productoDTO);
    ProductoDTO actualizar(Long id, ProductoDTO productoDTO);
    void cambiarEstado(Long id, String nuevoEstado);

    void eliminar(Long id);

    // Catálogos para los combos del formulario
    List<CatalogoDTO> listarCategorias();
    List<CatalogoDTO> listarMarcas();
    List<CatalogoDTO> listarUnidades();
}
