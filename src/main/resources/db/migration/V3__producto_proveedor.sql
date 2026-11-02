-- =============================================================================
-- V3: Proveedor principal del producto (ACTUALIZACION_PRODUCTO_PROVEEDOR.sql)
-- Opcional en la base para no romper productos antiguos; la aplicación lo exige al registrar.
-- =============================================================================

ALTER TABLE producto
  ADD COLUMN id_proveedor INT UNSIGNED NULL COMMENT 'Proveedor principal del producto' AFTER id_unidad,
  ADD CONSTRAINT fk_producto_proveedor
    FOREIGN KEY (id_proveedor) REFERENCES proveedor (id_proveedor);

CREATE INDEX idx_producto_proveedor ON producto (id_proveedor);
