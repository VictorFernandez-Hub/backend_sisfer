-- =============================================================================
-- V2: Cambios del Grupo 5 del 17/10/2026 (ACTUALIZACION_BD_GRUPO5.sql)
-- Reescrito en sintaxis de MySQL 8: el original usaba "ADD COLUMN IF NOT EXISTS",
-- que solo existe en MariaDB, y rol_modulo.id_rol era INT, que no coincide con
-- rol.id_rol (TINYINT UNSIGNED) y hacía fallar la clave foránea.
-- =============================================================================

-- Usuario: DNI y contrato
ALTER TABLE usuario
  ADD COLUMN dni           VARCHAR(8)  NULL COMMENT 'DNI del trabajador' AFTER telefono,
  ADD COLUMN tipo_contrato VARCHAR(20) NULL DEFAULT 'INDETERMINADO' COMMENT 'INDETERMINADO o PLAZO_FIJO' AFTER estado,
  ADD COLUMN fec_inicio    DATE        NULL COMMENT 'Inicio del contrato' AFTER tipo_contrato,
  ADD COLUMN fec_fin       DATE        NULL COMMENT 'Fin del contrato a plazo fijo' AFTER fec_inicio;

ALTER TABLE usuario
  ADD CONSTRAINT uk_usuario_dni UNIQUE (dni);

-- Proveedor: teléfono fijo y anexo
ALTER TABLE proveedor
  ADD COLUMN telefono_fijo VARCHAR(15) NULL AFTER telefono,
  ADD COLUMN anexo         VARCHAR(10) NULL AFTER telefono_fijo;

-- Producto: marca de promoción
ALTER TABLE producto
  ADD COLUMN es_promocion TINYINT(1) NOT NULL DEFAULT 0 AFTER precio_venta;

-- Módulos que ve cada rol en el menú
CREATE TABLE rol_modulo (
  id_rol  TINYINT UNSIGNED NOT NULL COMMENT 'Rol',
  modulo  VARCHAR(30)      NOT NULL COMMENT 'Módulo del menú. Ejemplo: PRODUCTOS',
  PRIMARY KEY (id_rol, modulo),
  CONSTRAINT fk_rolmodulo_rol FOREIGN KEY (id_rol) REFERENCES rol (id_rol)
) ENGINE=InnoDB COMMENT='Módulos visibles para cada rol';
