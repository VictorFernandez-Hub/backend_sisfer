-- Celular de la ferretería para mostrar en Yape y Plin.
-- Cuentas de los cuatro bancos. El administrador escribe los números reales.

INSERT INTO parametro (codigo, valor, descripcion)
SELECT 'CELULAR_COBRO', '', 'Celular de la ferreteria para Yape y Plin. 9 digitos'
WHERE NOT EXISTS (SELECT 1 FROM parametro WHERE codigo = 'CELULAR_COBRO');

CREATE TABLE IF NOT EXISTS cuenta_banco (
  id_cuenta      INT UNSIGNED NOT NULL AUTO_INCREMENT,
  banco          VARCHAR(20)  NOT NULL COMMENT 'BCP, BBVA, INTERBANK o SCOTIABANK',
  numero_cuenta  VARCHAR(30)  NOT NULL DEFAULT '' COMMENT 'Numero de cuenta de la ferreteria',
  cci            VARCHAR(25)  NOT NULL DEFAULT '' COMMENT 'Codigo de cuenta interbancario',
  estado         VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
  PRIMARY KEY (id_cuenta),
  UNIQUE KEY unq_cuenta_banco (banco),
  CONSTRAINT chk_cuenta_banco CHECK (banco IN ('BCP', 'BBVA', 'INTERBANK', 'SCOTIABANK')),
  CONSTRAINT chk_cuenta_estado CHECK (estado IN ('ACTIVO', 'INACTIVO'))
) ENGINE=InnoDB COMMENT='Cuentas a las que el cliente puede transferir';

INSERT INTO cuenta_banco (banco, numero_cuenta, cci)
SELECT banco, '', '' FROM (
  SELECT 'BCP' AS banco UNION ALL
  SELECT 'BBVA' UNION ALL
  SELECT 'INTERBANK' UNION ALL
  SELECT 'SCOTIABANK'
) AS semilla
WHERE NOT EXISTS (SELECT 1 FROM cuenta_banco c WHERE c.banco = semilla.banco);
