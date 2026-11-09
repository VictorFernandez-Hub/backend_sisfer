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

    // Próximo código libre (HER-001...), contando los productos ELIMINADOS
    String siguienteCodigo();

    // Catálogos para los combos del formulario (solo activos) y para la lista completa
    List<CatalogoDTO> listarCategorias();
    List<CatalogoDTO> listarMarcas();
    List<CatalogoDTO> listarUnidades();
    List<CatalogoDTO> listarCategoriasTodas();
    List<CatalogoDTO> listarMarcasTodas();
    List<CatalogoDTO> listarUnidadesTodas();

    CatalogoDTO crearCategoria(String nombre);
    CatalogoDTO crearMarca(String nombre);
    CatalogoDTO crearUnidad(String codigo, String nombre, boolean entera);

    void cambiarEstadoCategoria(Integer id, String estado);
    void cambiarEstadoMarca(Integer id, String estado);
    void cambiarEstadoUnidad(Integer id, String estado);
}
