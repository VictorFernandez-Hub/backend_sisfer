-- =============================================================================
-- V13: Caja (apertura y cierre del turno de ventas)
--
-- Cada vendedor abre su caja al empezar el turno con el sencillo que recibe
-- (fondo inicial). Mientras la caja está abierta puede cobrar ventas; cada venta
-- confirmada queda unida a esa caja (venta.id_caja).
-- Durante el turno se anotan las entradas y salidas de efectivo que no son ventas
-- (sencillo extra, pago de un servicio, retiro para el banco).
-- Al cerrar, el vendedor cuenta el efectivo y el sistema calcula lo que debería haber:
--   fondo inicial + ventas en efectivo + entradas - salidas.
-- La diferencia (sobrante o faltante) queda registrada con su explicación.
-- =============================================================================

CREATE TABLE IF NOT EXISTS caja (
  id_caja           INT UNSIGNED   NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del turno de caja',
  numero            VARCHAR(20)    NOT NULL DEFAULT '' COMMENT 'Numero visible. Ejemplo: C-0001',
  id_usuario        INT UNSIGNED   NOT NULL COMMENT 'Vendedor responsable de la caja',
  fec_apertura      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Inicio del turno',
  monto_apertura    DECIMAL(12,2)  NOT NULL DEFAULT 0.00 COMMENT 'Sencillo con el que empieza, en soles',
  obs_apertura      VARCHAR(200)   NOT NULL DEFAULT '' COMMENT 'Nota al abrir',
  fec_cierre        DATETIME       NULL COMMENT 'Fin del turno. Vacio mientras esta abierta',
  id_usuario_cierre INT UNSIGNED   NULL COMMENT 'Quien cerro la caja (el vendedor o el administrador)',
  efectivo_esperado DECIMAL(12,2)  NULL COMMENT 'Lo que deberia haber en efectivo al cerrar',
  efectivo_contado  DECIMAL(12,2)  NULL COMMENT 'Lo que se conto al cerrar',
  diferencia        DECIMAL(12,2)  NULL COMMENT 'Contado - esperado. Positivo sobra, negativo falta',
  total_ventas      DECIMAL(12,2)  NULL COMMENT 'Total vendido en el turno (todos los medios)',
  num_ventas        INT UNSIGNED   NULL COMMENT 'Ventas confirmadas en el turno',
  obs_cierre        VARCHAR(300)   NOT NULL DEFAULT '' COMMENT 'Explicacion del cierre o de la diferencia',
  estado            VARCHAR(10)    NOT NULL DEFAULT 'ABIERTA' COMMENT 'ABIERTA o CERRADA',
  -- Solo una caja abierta por vendedor: para una caja cerrada vale NULL y no choca con el indice unico
  usuario_abierta   INT UNSIGNED AS (IF(estado = 'ABIERTA', id_usuario, NULL)) STORED,
  PRIMARY KEY (id_caja),
  UNIQUE KEY unq_caja_abierta_usuario (usuario_abierta),
  KEY idx_caja_usuario (id_usuario, fec_apertura),
  KEY idx_caja_apertura (fec_apertura),
  CONSTRAINT fk_caja_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_caja_usuario_cierre FOREIGN KEY (id_usuario_cierre) REFERENCES usuario (id_usuario),
  CONSTRAINT chk_caja_estado CHECK (estado IN ('ABIERTA', 'CERRADA')),
  CONSTRAINT chk_caja_apertura CHECK (monto_apertura >= 0)
) ENGINE=InnoDB COMMENT='Turno de caja de un vendedor: apertura, ventas y arqueo de cierre';

CREATE TABLE IF NOT EXISTS caja_movimiento (
  id_movimiento  INT UNSIGNED   NOT NULL AUTO_INCREMENT,
  id_caja        INT UNSIGNED   NOT NULL COMMENT 'Caja en la que entro o salio el dinero',
  id_usuario     INT UNSIGNED   NOT NULL COMMENT 'Quien lo registro',
  tipo           VARCHAR(10)    NOT NULL COMMENT 'INGRESO o EGRESO de efectivo',
  monto          DECIMAL(12,2)  NOT NULL COMMENT 'En soles, siempre positivo',
  motivo         VARCHAR(200)   NOT NULL COMMENT 'Por que entro o salio el dinero',
  fec_registro   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_movimiento),
  KEY idx_movimiento_caja (id_caja),
  CONSTRAINT fk_movimiento_caja FOREIGN KEY (id_caja) REFERENCES caja (id_caja),
  CONSTRAINT fk_movimiento_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT chk_movimiento_tipo CHECK (tipo IN ('INGRESO', 'EGRESO')),
  CONSTRAINT chk_movimiento_monto CHECK (monto > 0)
) ENGINE=InnoDB COMMENT='Entradas y salidas de efectivo de la caja que no son ventas';

-- Las ventas confirmadas desde ahora quedan unidas a la caja en la que se cobraron.
-- Las ventas anteriores a este cambio quedan sin caja (NULL).
ALTER TABLE venta
  ADD COLUMN id_caja INT UNSIGNED NULL COMMENT 'Caja en la que se cobro la venta' AFTER id_usuario,
  ADD KEY idx_venta_caja (id_caja),
  ADD CONSTRAINT fk_venta_caja FOREIGN KEY (id_caja) REFERENCES caja (id_caja);

-- Monto maximo del fondo inicial: evita errores de tipeo (un 5000 en vez de 50.00)
INSERT INTO parametro (codigo, valor, descripcion)
SELECT 'CAJA_FONDO_MAXIMO', '2000.00', 'Monto maximo del fondo inicial de caja, en soles'
WHERE NOT EXISTS (SELECT 1 FROM parametro WHERE codigo = 'CAJA_FONDO_MAXIMO');
