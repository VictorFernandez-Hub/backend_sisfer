package com.ferreteria.ferreteria.modulos.usuario.model;

import java.io.Serializable;
import java.util.Objects;

public class RolModuloId implements Serializable {
    private Short idRol;
    private String modulo;

    public RolModuloId() {}

    public RolModuloId(Short idRol, String modulo) {
        this.idRol = idRol;
        this.modulo = modulo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolModuloId)) return false;
        RolModuloId that = (RolModuloId) o;
        return Objects.equals(idRol, that.idRol) && Objects.equals(modulo, that.modulo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idRol, modulo);
    }
}