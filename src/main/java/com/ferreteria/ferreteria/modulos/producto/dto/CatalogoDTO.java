package com.ferreteria.ferreteria.modulos.producto.dto;

/**
 * DTO genérico para llenar los combos del formulario de productos
 * (categorías, marcas y unidades de medida).
 */
public class CatalogoDTO {

    private Integer id;
    private String codigo; // Solo lo usan las unidades (UND, KG, M...)
    private String nombre;

    public CatalogoDTO() {}

    public CatalogoDTO(Integer id, String codigo, String nombre) {
        this.id = id;
        this.codigo = codigo;
        this.nombre = nombre;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
