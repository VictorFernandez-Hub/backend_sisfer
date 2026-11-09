-- =============================================================================
-- V8: Proveedor principal del producto
--
-- V3 ya agregaba esta columna, pero una base local que ya tenía tablas y no
-- tenía historial de Flyway se marcó en la versión 4 y V3 nunca corrió.
-- Sin id_proveedor, GET /api/productos/activos responde 500.
-- En Aiven la columna ya existe: este script no la vuelve a crear.
-- =============================================================================

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'producto'
                  AND COLUMN_NAME = 'id_proveedor');
SET @sql := IF(@existe = 0,
  'ALTER TABLE producto ADD COLUMN id_proveedor INT UNSIGNED NULL COMMENT ''Proveedor principal del producto'' AFTER id_unidad',
  'DO 0');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'producto'
                  AND CONSTRAINT_NAME = 'fk_producto_proveedor');
SET @sql := IF(@existe = 0,
  'ALTER TABLE producto ADD CONSTRAINT fk_producto_proveedor FOREIGN KEY (id_proveedor) REFERENCES proveedor (id_proveedor)',
  'DO 0');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'producto'
                  AND INDEX_NAME = 'idx_producto_proveedor');
SET @sql := IF(@existe = 0,
  'CREATE INDEX idx_producto_proveedor ON producto (id_proveedor)',
  'DO 0');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;
