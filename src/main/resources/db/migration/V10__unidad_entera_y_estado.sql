-- La unidad dice si se vende entera (caja, unidad) o con decimales (metro, kilo).
-- También se puede inactivar, igual que categoría y marca, sin borrar las que ya usan los productos.

ALTER TABLE unidad
  ADD COLUMN entera TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1 = no admite decimales' AFTER nombre,
  ADD COLUMN estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVO' COMMENT 'ACTIVO se ofrece al registrar un producto' AFTER entera;

UPDATE unidad SET entera = 0 WHERE codigo IN ('M', 'KG', 'LT');

ALTER TABLE unidad
  ADD CONSTRAINT chk_unidad_estado CHECK (estado IN ('ACTIVO', 'INACTIVO'));
