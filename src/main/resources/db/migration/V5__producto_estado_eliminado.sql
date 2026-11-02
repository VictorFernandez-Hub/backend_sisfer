-- =============================================================================
-- V5: Estado ELIMINADO para productos
-- INACTIVO  = pausa: se ve en "Ver inactivos", no se vende, se puede reactivar.
-- ELIMINADO = se borró desde "Ver inactivos" pero tenía stock o movimientos:
--             desaparece de todas las pantallas y se conserva solo para que el
--             kardex, las compras y las ventas sigan apuntando a un producto que existe.
-- =============================================================================

-- La restricción se quita solo si existe, para que la migración no falle en una
-- base donde se creó con otro nombre o no se creó.
SET @existe := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'producto'
                  AND CONSTRAINT_NAME = 'chk_producto_estado');
SET @sql := IF(@existe > 0, 'ALTER TABLE producto DROP CONSTRAINT chk_producto_estado', 'DO 0');
PREPARE quitar FROM @sql;
EXECUTE quitar;
DEALLOCATE PREPARE quitar;

ALTER TABLE producto
  ADD CONSTRAINT chk_producto_estado CHECK (estado IN ('ACTIVO','INACTIVO','ELIMINADO'));
