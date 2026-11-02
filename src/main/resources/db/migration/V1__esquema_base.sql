-- =============================================================================
-- V1: Esquema base de SISFER (tomado de SISFER_mysql8.sql, Grupo 5, 27/09/2026)
-- Tablas, vistas, procedimientos y datos iniciales.
-- No crea la base ni los usuarios de MySQL: eso depende de cada servidor.
-- En una base existente (Aiven) no se ejecuta: Flyway la marca como versión 4.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 2) SEGURIDAD
--    El profesor pidio no mezclar al administrador de sistemas (TI)
--    con el administrador del negocio (dueno de compras, stock y ventas).
--    Por eso hay roles separados y una tabla de permisos.
-- -----------------------------------------------------------------------------

-- Lista de roles. Ejemplo: vendedor, almacenero, gerente.
CREATE TABLE rol (
  id_rol          TINYINT UNSIGNED      NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del rol',
  codigo          VARCHAR(20)           NOT NULL COMMENT 'Codigo fijo. Ejemplo: VENDEDOR',
  nombre          VARCHAR(80)           NOT NULL COMMENT 'Nombre que se muestra en pantalla',
  descripcion     VARCHAR(200)          NOT NULL DEFAULT '' COMMENT 'Que puede hacer este rol',
  es_tecnico      TINYINT(1)            NOT NULL DEFAULT 0 COMMENT '1 = rol de TI. 0 = rol de la ferreteria',
  estado          VARCHAR(10)           NOT NULL DEFAULT 'ACTIVO' COMMENT 'ACTIVO se usa. INACTIVO ya no se asigna',
  fec_registro    DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha en que se creo el rol',
  PRIMARY KEY (id_rol),
  CONSTRAINT chk_rol_estado CHECK (estado IN ('ACTIVO','INACTIVO')),
  CONSTRAINT chk_rol_tecnico CHECK (es_tecnico IN (0,1))
) ENGINE=InnoDB COMMENT='Roles del sistema. Separa al personal de TI del personal de la ferreteria';

ALTER TABLE rol
  ADD CONSTRAINT unq_rol_codigo UNIQUE (codigo);

-- Cada permiso es una accion concreta. No se guardan varios permisos en un solo campo.
CREATE TABLE permiso (
  id_permiso      SMALLINT UNSIGNED     NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del permiso',
  codigo          VARCHAR(20)           NOT NULL COMMENT 'Codigo fijo. Ejemplo: VTA_REG',
  modulo          VARCHAR(20)           NOT NULL COMMENT 'Modulo: SEGURIDAD, COMPRA, VENTA, INVENTARIO',
  descripcion     VARCHAR(150)          NOT NULL DEFAULT '' COMMENT 'Accion que permite hacer',
  PRIMARY KEY (id_permiso)
) ENGINE=InnoDB COMMENT='Permisos uno por uno. Asi no se mezcla varias acciones en la misma columna';

ALTER TABLE permiso
  ADD CONSTRAINT unq_permiso_codigo UNIQUE (codigo);

-- Une un rol con los permisos que le tocan.
-- Se llama acceso para no juntar los nombres de las dos tablas.
CREATE TABLE acceso (
  id_rol          TINYINT UNSIGNED      NOT NULL COMMENT 'Rol al que se le da el permiso',
  id_permiso      SMALLINT UNSIGNED     NOT NULL COMMENT 'Permiso que se le da',
  PRIMARY KEY (id_rol, id_permiso),
  CONSTRAINT fk_acceso_rol
    FOREIGN KEY (id_rol) REFERENCES rol (id_rol),
  CONSTRAINT fk_acceso_permiso
    FOREIGN KEY (id_permiso) REFERENCES permiso (id_permiso)
) ENGINE=InnoDB COMMENT='Que puede hacer cada rol. Un rol puede tener varios permisos';

-- Personas que entran al sistema. Cada cuenta tiene un solo rol.
-- Si alguien hace de TI y de dueno, se crean dos cuentas. No un superusuario.
CREATE TABLE usuario (
  id_usuario      INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del usuario',
  id_rol          TINYINT UNSIGNED      NOT NULL COMMENT 'Rol de esta cuenta',
  cuenta          VARCHAR(50)           NOT NULL COMMENT 'Usuario para iniciar sesion',
  hash_clave      VARCHAR(255)          NOT NULL COMMENT 'Clave encriptada. Nunca se guarda en texto plano',
  nombres         VARCHAR(80)           NOT NULL COMMENT 'Nombres de la persona',
  apellidos       VARCHAR(80)           NOT NULL COMMENT 'Apellidos de la persona',
  email           VARCHAR(120)          NOT NULL DEFAULT '' COMMENT 'Correo. Si no tiene, se deja vacio',
  telefono        VARCHAR(20)           NOT NULL DEFAULT '' COMMENT 'Telefono. Si no tiene, se deja vacio',
  estado          VARCHAR(10)           NOT NULL DEFAULT 'ACTIVO' COMMENT 'ACTIVO entra. INACTIVO o BLOQUEADO no entra',
  fec_acceso      DATETIME              NOT NULL DEFAULT '1000-01-01 00:00:00' COMMENT 'Ultima vez que entro. Fecha 1000 si nunca entro',
  num_intentos    TINYINT UNSIGNED      NOT NULL DEFAULT 0 COMMENT 'Veces seguidas que fallo la clave',
  fec_registro    DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha en que se creo la cuenta',
  fec_modifica    DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Fecha del ultimo cambio',
  PRIMARY KEY (id_usuario),
  CONSTRAINT fk_usuario_rol
    FOREIGN KEY (id_rol) REFERENCES rol (id_rol),
  CONSTRAINT chk_usuario_estado CHECK (estado IN ('ACTIVO','INACTIVO','BLOQUEADO'))
) ENGINE=InnoDB COMMENT='Cuentas para entrar al sistema. La clave se guarda encriptada';

ALTER TABLE usuario
  ADD CONSTRAINT unq_usuario_cuenta UNIQUE (cuenta);
CREATE INDEX idx_usuario_rol ON usuario (id_rol);

-- Historial de lo que hace cada usuario. Sirve para auditoria.
CREATE TABLE bitacora (
  id_bitacora     BIGINT UNSIGNED       NOT NULL AUTO_INCREMENT COMMENT 'Numero del evento',
  id_usuario      INT UNSIGNED          NOT NULL COMMENT 'Quien hizo la accion',
  fec_evento      DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Cuando ocurrio',
  modulo          VARCHAR(20)           NOT NULL COMMENT 'En que modulo paso. Ejemplo: VENTA',
  accion          VARCHAR(20)           NOT NULL COMMENT 'Que hizo. Ejemplo: CONFIRMAR',
  tabla_obj       VARCHAR(20)           NOT NULL DEFAULT '' COMMENT 'Tabla que se toco',
  id_registro     VARCHAR(20)           NOT NULL DEFAULT '' COMMENT 'Numero del registro que se toco',
  detalle         VARCHAR(300)          NOT NULL DEFAULT '' COMMENT 'Nota corta de lo ocurrido',
  ip_origen       VARCHAR(45)           NOT NULL DEFAULT '' COMMENT 'Desde que IP se conecto',
  exito           TINYINT(1)            NOT NULL DEFAULT 1 COMMENT '1 = salio bien. 0 = fallo',
  PRIMARY KEY (id_bitacora),
  CONSTRAINT fk_bitacora_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT chk_bitacora_exito CHECK (exito IN (0,1))
) ENGINE=InnoDB COMMENT='Registro de acciones para saber quien hizo cada cambio';

CREATE INDEX idx_bitacora_fecha ON bitacora (fec_evento);
CREATE INDEX idx_bitacora_usuario ON bitacora (id_usuario);

-- -----------------------------------------------------------------------------
-- 3) DATOS BASICOS DE LA FERRETERIA
--    Categorias, marcas, unidades, proveedores, clientes y productos.
-- -----------------------------------------------------------------------------

CREATE TABLE categoria (
  id_categoria    SMALLINT UNSIGNED     NOT NULL AUTO_INCREMENT COMMENT 'Numero interno de la categoria',
  nombre          VARCHAR(80)           NOT NULL COMMENT 'Nombre. Ejemplo: Herramientas',
  descripcion     VARCHAR(200)          NOT NULL DEFAULT '' COMMENT 'Detalle opcional',
  estado          VARCHAR(10)           NOT NULL DEFAULT 'ACTIVO' COMMENT 'ACTIVO se usa en productos nuevos',
  PRIMARY KEY (id_categoria),
  CONSTRAINT chk_categoria_estado CHECK (estado IN ('ACTIVO','INACTIVO'))
) ENGINE=InnoDB COMMENT='Como se agrupan los productos. Ejemplo: herramientas o pintura';

ALTER TABLE categoria
  ADD CONSTRAINT unq_categoria_nombre UNIQUE (nombre);

CREATE TABLE marca (
  id_marca        SMALLINT UNSIGNED     NOT NULL AUTO_INCREMENT COMMENT 'Numero interno de la marca',
  nombre          VARCHAR(80)           NOT NULL COMMENT 'Nombre. Ejemplo: Truper',
  estado          VARCHAR(10)           NOT NULL DEFAULT 'ACTIVO' COMMENT 'ACTIVO se usa en productos nuevos',
  PRIMARY KEY (id_marca),
  CONSTRAINT chk_marca_estado CHECK (estado IN ('ACTIVO','INACTIVO'))
) ENGINE=InnoDB COMMENT='Marca del producto. Si no tiene marca se usa Generico';

ALTER TABLE marca
  ADD CONSTRAINT unq_marca_nombre UNIQUE (nombre);

CREATE TABLE unidad (
  id_unidad       TINYINT UNSIGNED      NOT NULL AUTO_INCREMENT COMMENT 'Numero interno de la unidad',
  codigo          VARCHAR(10)           NOT NULL COMMENT 'Codigo corto. Ejemplo: UND o KG',
  nombre          VARCHAR(40)           NOT NULL COMMENT 'Nombre. Ejemplo: Unidad o Kilogramo',
  PRIMARY KEY (id_unidad)
) ENGINE=InnoDB COMMENT='Como se mide el producto: unidad, metro, kilo, litro, caja';

ALTER TABLE unidad
  ADD CONSTRAINT unq_unidad_codigo UNIQUE (codigo);

CREATE TABLE proveedor (
  id_proveedor    INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del proveedor',
  id_usuario      INT UNSIGNED          NOT NULL COMMENT 'Usuario que lo registro',
  tipo_doc        VARCHAR(10)           NOT NULL DEFAULT 'RUC' COMMENT 'Tipo de documento. Casi siempre RUC',
  num_doc         VARCHAR(15)           NOT NULL COMMENT 'Numero de RUC o DNI',
  razon_social    VARCHAR(150)          NOT NULL COMMENT 'Nombre legal de la empresa',
  nom_comercial   VARCHAR(150)          NOT NULL DEFAULT '' COMMENT 'Nombre comercial si es distinto',
  telefono        VARCHAR(20)           NOT NULL DEFAULT '' COMMENT 'Telefono de contacto',
  email           VARCHAR(120)          NOT NULL DEFAULT '' COMMENT 'Correo de contacto',
  direccion       VARCHAR(200)          NOT NULL DEFAULT '' COMMENT 'Direccion',
  contacto        VARCHAR(100)          NOT NULL DEFAULT '' COMMENT 'Persona con quien se coordina',
  estado          VARCHAR(10)           NOT NULL DEFAULT 'ACTIVO' COMMENT 'Solo ACTIVO se puede usar en una compra',
  fec_registro    DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha de alta',
  PRIMARY KEY (id_proveedor),
  CONSTRAINT fk_proveedor_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT chk_proveedor_tipodoc CHECK (tipo_doc IN ('RUC','DNI','CE','PAS')),
  CONSTRAINT chk_proveedor_estado CHECK (estado IN ('ACTIVO','INACTIVO'))
) ENGINE=InnoDB COMMENT='Empresas que venden mercaderia a la ferreteria';

ALTER TABLE proveedor
  ADD CONSTRAINT unq_proveedor_doc UNIQUE (tipo_doc, num_doc);

-- Cliente de la venta. Solo DNI (8 numeros) o RUC (11 numeros).
-- El telefono, si se llena, debe tener 9 numeros.
CREATE TABLE cliente (
  id_cliente      INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del cliente',
  tipo_doc        VARCHAR(3)            NOT NULL DEFAULT 'DNI' COMMENT 'Solo DNI o RUC',
  num_doc         VARCHAR(11)           NOT NULL COMMENT 'DNI: 8 numeros. RUC: 11 numeros',
  nombres         VARCHAR(150)          NOT NULL COMMENT 'Nombre de la persona o razon social',
  telefono        VARCHAR(9)            NOT NULL DEFAULT '' COMMENT 'Celular de 9 numeros. Si no hay, se deja vacio',
  direccion       VARCHAR(200)          NOT NULL DEFAULT '' COMMENT 'Direccion',
  email           VARCHAR(120)          NOT NULL DEFAULT '' COMMENT 'Correo',
  estado          VARCHAR(10)           NOT NULL DEFAULT 'ACTIVO' COMMENT 'ACTIVO se puede usar en ventas',
  fec_registro    DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha de alta',
  PRIMARY KEY (id_cliente),
  CONSTRAINT chk_cliente_tipodoc CHECK (tipo_doc IN ('DNI','RUC')),
  CONSTRAINT chk_cliente_numdoc CHECK (
    (tipo_doc = 'DNI' AND num_doc REGEXP '^[0-9]{8}$')
    OR (tipo_doc = 'RUC' AND num_doc REGEXP '^[0-9]{11}$')
  ),
  CONSTRAINT chk_cliente_telefono CHECK (
    telefono = '' OR telefono REGEXP '^[0-9]{9}$'
  ),
  CONSTRAINT chk_cliente_estado CHECK (estado IN ('ACTIVO','INACTIVO'))
) ENGINE=InnoDB COMMENT='Cliente de la boleta o factura. DNI de 8 o RUC de 11. Telefono de 9';

CREATE INDEX idx_cliente_doc ON cliente (tipo_doc, num_doc);

-- Producto que se compra y se vende.
-- Los precios estan en soles. La moneda no se repite en cada producto
-- porque todo el sistema trabaja en soles.
CREATE TABLE producto (
  id_producto     INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del producto',
  id_categoria    SMALLINT UNSIGNED     NOT NULL COMMENT 'Categoria a la que pertenece',
  id_marca        SMALLINT UNSIGNED     NOT NULL COMMENT 'Marca. Si no tiene, se usa Generico',
  id_unidad       TINYINT UNSIGNED      NOT NULL COMMENT 'Unidad de medida',
  codigo          VARCHAR(30)           NOT NULL COMMENT 'Codigo interno. Ejemplo: HER-001',
  nombre          VARCHAR(150)          NOT NULL COMMENT 'Nombre que se muestra. Ejemplo: Martillo 16 oz',
  descripcion     VARCHAR(300)          NOT NULL DEFAULT '' COMMENT 'Detalle extra del producto',
  precio_compra   DECIMAL(12,2)         NOT NULL DEFAULT 0.00 COMMENT 'Ultimo costo de compra, en soles',
  precio_venta    DECIMAL(12,2)         NOT NULL DEFAULT 0.00 COMMENT 'Precio al publico, en soles',
  stock_actual    DECIMAL(12,3)         NOT NULL DEFAULT 0.000 COMMENT 'Cuantas unidades hay ahora. Lo cambian solo los procedimientos',
  stock_minimo    DECIMAL(12,3)         NOT NULL DEFAULT 0.000 COMMENT 'Si el stock baja de este numero, sale alerta',
  estado          VARCHAR(10)           NOT NULL DEFAULT 'ACTIVO' COMMENT 'INACTIVO = ya no se vende. No se borra el producto',
  fec_registro    DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha de alta',
  fec_modifica    DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Fecha del ultimo cambio',
  PRIMARY KEY (id_producto),
  CONSTRAINT fk_producto_categoria
    FOREIGN KEY (id_categoria) REFERENCES categoria (id_categoria),
  CONSTRAINT fk_producto_marca
    FOREIGN KEY (id_marca) REFERENCES marca (id_marca),
  CONSTRAINT fk_producto_unidad
    FOREIGN KEY (id_unidad) REFERENCES unidad (id_unidad),
  CONSTRAINT chk_producto_precios CHECK (precio_compra >= 0 AND precio_venta >= 0),
  CONSTRAINT chk_producto_stock CHECK (stock_actual >= 0 AND stock_minimo >= 0),
  CONSTRAINT chk_producto_estado CHECK (estado IN ('ACTIVO','INACTIVO'))
) ENGINE=InnoDB COMMENT='Productos de la ferreteria. Precios en soles. El stock lo mueven los procedimientos';

ALTER TABLE producto
  ADD CONSTRAINT unq_producto_codigo UNIQUE (codigo);
CREATE INDEX idx_producto_categoria ON producto (id_categoria);
CREATE INDEX idx_producto_marca ON producto (id_marca);
CREATE INDEX idx_producto_stock ON producto (stock_actual, stock_minimo);

CREATE TABLE metodo (
  id_metodo       TINYINT UNSIGNED      NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del medio de pago',
  codigo          VARCHAR(20)           NOT NULL COMMENT 'Codigo. Ejemplo: YAPE',
  nombre          VARCHAR(50)           NOT NULL COMMENT 'Nombre. Ejemplo: Yape',
  estado          VARCHAR(10)           NOT NULL DEFAULT 'ACTIVO' COMMENT 'ACTIVO se puede usar en una venta',
  PRIMARY KEY (id_metodo),
  CONSTRAINT chk_metodo_estado CHECK (estado IN ('ACTIVO','INACTIVO'))
) ENGINE=InnoDB COMMENT='Como paga el cliente: efectivo, Yape, Plin, transferencia, tarjeta';

ALTER TABLE metodo
  ADD CONSTRAINT unq_metodo_codigo UNIQUE (codigo);

-- Boleta y factura son simuladas. No se envia nada a SUNAT.
CREATE TABLE tipo_comp (
  id_tipo_comp    TINYINT UNSIGNED      NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del tipo',
  codigo          VARCHAR(10)           NOT NULL COMMENT 'BOL = boleta. FAC = factura',
  nombre          VARCHAR(40)           NOT NULL COMMENT 'Nombre que se muestra',
  serie           VARCHAR(6)            NOT NULL COMMENT 'Serie. Ejemplo: B001 o F001',
  num_actual      INT UNSIGNED          NOT NULL DEFAULT 0 COMMENT 'Ultimo numero emitido de esa serie',
  PRIMARY KEY (id_tipo_comp)
) ENGINE=InnoDB COMMENT='Tipos de comprobante simulados. No hay envio a SUNAT';

ALTER TABLE tipo_comp
  ADD CONSTRAINT unq_tipo_comp_codigo UNIQUE (codigo);

-- Datos que valen para todo el sistema. La moneda esta aqui, no en cada producto.
CREATE TABLE parametro (
  id_parametro    TINYINT UNSIGNED      NOT NULL AUTO_INCREMENT COMMENT 'Numero interno',
  codigo          VARCHAR(20)           NOT NULL COMMENT 'Nombre del dato. Ejemplo: MONEDA',
  valor           VARCHAR(50)           NOT NULL COMMENT 'Valor vigente. Ejemplo: PEN',
  descripcion     VARCHAR(150)          NOT NULL DEFAULT '' COMMENT 'Para que se usa',
  PRIMARY KEY (id_parametro)
) ENGINE=InnoDB COMMENT='Configuracion general. Aqui esta la moneda: soles (PEN / S/)';

ALTER TABLE parametro
  ADD CONSTRAINT unq_parametro_codigo UNIQUE (codigo);

-- -----------------------------------------------------------------------------
-- 4) COMPRAS
--    Primero se guarda el documento en BORRADOR.
--    Al confirmarlo, entra la mercaderia y se anota en el kardex.
-- -----------------------------------------------------------------------------

CREATE TABLE compra (
  id_compra       INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno de la compra',
  id_proveedor    INT UNSIGNED          NOT NULL COMMENT 'Proveedor que vendio la mercaderia',
  id_usuario      INT UNSIGNED          NOT NULL COMMENT 'Usuario que registro la compra',
  numero          VARCHAR(20)           NOT NULL COMMENT 'Numero interno. Ejemplo: OC-0001',
  fec_compra      DATE                  NOT NULL COMMENT 'Fecha de la factura del proveedor',
  fec_ingreso     DATETIME              NOT NULL DEFAULT '1000-01-01 00:00:00' COMMENT 'Cuando se confirmo. Fecha 1000 si sigue en borrador',
  tipo_doc        VARCHAR(20)           NOT NULL DEFAULT '' COMMENT 'Tipo de documento del proveedor',
  num_doc         VARCHAR(30)           NOT NULL DEFAULT '' COMMENT 'Numero de la factura del proveedor',
  tot_subtotal    DECIMAL(12,2)         NOT NULL DEFAULT 0.00 COMMENT 'Suma de las lineas, en soles',
  tot_igv         DECIMAL(12,2)         NOT NULL DEFAULT 0.00 COMMENT 'IGV, en soles',
  tot_total       DECIMAL(12,2)         NOT NULL DEFAULT 0.00 COMMENT 'Total a pagar, en soles',
  estado          VARCHAR(12)           NOT NULL DEFAULT 'BORRADOR' COMMENT 'BORRADOR, CONFIRMADA o ANULADA',
  observacion     VARCHAR(300)          NOT NULL DEFAULT '' COMMENT 'Nota',
  fec_registro    DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha de registro',
  PRIMARY KEY (id_compra),
  CONSTRAINT fk_compra_proveedor
    FOREIGN KEY (id_proveedor) REFERENCES proveedor (id_proveedor),
  CONSTRAINT fk_compra_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT chk_compra_estado CHECK (estado IN ('BORRADOR','CONFIRMADA','ANULADA'))
) ENGINE=InnoDB COMMENT='Cabecera de la compra. Al confirmarla sube el stock';

ALTER TABLE compra
  ADD CONSTRAINT unq_compra_numero UNIQUE (numero);
CREATE INDEX idx_compra_proveedor ON compra (id_proveedor);
CREATE INDEX idx_compra_fecha ON compra (fec_compra);

-- Productos de esa compra. El proveedor no se repite aqui porque ya esta en la cabecera.
CREATE TABLE linea_compra (
  id_linea_compra INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno de la linea',
  id_compra       INT UNSIGNED          NOT NULL COMMENT 'Compra a la que pertenece',
  id_producto     INT UNSIGNED          NOT NULL COMMENT 'Producto que entra',
  cantidad        DECIMAL(12,3)         NOT NULL COMMENT 'Cuantas unidades entran',
  costo_unit      DECIMAL(12,2)         NOT NULL COMMENT 'Costo de cada unidad, en soles',
  tot_linea       DECIMAL(12,2)         NOT NULL COMMENT 'cantidad por costo, en soles',
  PRIMARY KEY (id_linea_compra),
  CONSTRAINT fk_lincom_compra
    FOREIGN KEY (id_compra) REFERENCES compra (id_compra),
  CONSTRAINT fk_lincom_producto
    FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
  CONSTRAINT chk_lincom_cant CHECK (cantidad > 0),
  CONSTRAINT chk_lincom_costo CHECK (costo_unit >= 0)
) ENGINE=InnoDB COMMENT='Detalle de la compra. Un producto por linea';

CREATE INDEX idx_lincom_compra ON linea_compra (id_compra);
CREATE INDEX idx_lincom_producto ON linea_compra (id_producto);

-- -----------------------------------------------------------------------------
-- 5) VENTAS Y COMPROBANTE
--    Al confirmar la venta se revisa el stock.
--    Si alcanza, se descuenta, se anota el kardex y se emite la boleta o factura.
-- -----------------------------------------------------------------------------

CREATE TABLE venta (
  id_venta        INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno de la venta',
  id_cliente      INT UNSIGNED          NOT NULL COMMENT 'Cliente. Si es boleta rapida se usa Cliente varios',
  id_metodo       TINYINT UNSIGNED      NOT NULL COMMENT 'Como pago: efectivo, Yape, etc.',
  id_usuario      INT UNSIGNED          NOT NULL COMMENT 'Vendedor que registro la venta',
  numero          VARCHAR(20)           NOT NULL COMMENT 'Numero interno. Ejemplo: V-0001',
  fec_venta       DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha y hora de la venta',
  tot_subtotal    DECIMAL(12,2)         NOT NULL DEFAULT 0.00 COMMENT 'Suma de las lineas, en soles',
  tot_igv         DECIMAL(12,2)         NOT NULL DEFAULT 0.00 COMMENT 'IGV, en soles',
  tot_total       DECIMAL(12,2)         NOT NULL DEFAULT 0.00 COMMENT 'Total cobrado, en soles',
  estado          VARCHAR(12)           NOT NULL DEFAULT 'BORRADOR' COMMENT 'BORRADOR, CONFIRMADA o ANULADA',
  observacion     VARCHAR(300)          NOT NULL DEFAULT '' COMMENT 'Nota',
  fec_registro    DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha de registro',
  PRIMARY KEY (id_venta),
  CONSTRAINT fk_venta_cliente
    FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente),
  CONSTRAINT fk_venta_metodo
    FOREIGN KEY (id_metodo) REFERENCES metodo (id_metodo),
  CONSTRAINT fk_venta_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT chk_venta_estado CHECK (estado IN ('BORRADOR','CONFIRMADA','ANULADA'))
) ENGINE=InnoDB COMMENT='Cabecera de la venta. Al confirmarla baja el stock si hay unidades';

ALTER TABLE venta
  ADD CONSTRAINT unq_venta_numero UNIQUE (numero);
CREATE INDEX idx_venta_fecha ON venta (fec_venta);
CREATE INDEX idx_venta_cliente ON venta (id_cliente);

CREATE TABLE linea_venta (
  id_linea_venta  INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno de la linea',
  id_venta        INT UNSIGNED          NOT NULL COMMENT 'Venta a la que pertenece',
  id_producto     INT UNSIGNED          NOT NULL COMMENT 'Producto que se vende',
  cantidad        DECIMAL(12,3)         NOT NULL COMMENT 'Cuantas unidades se venden',
  precio_unit     DECIMAL(12,2)         NOT NULL COMMENT 'Precio de cada unidad, en soles',
  tot_linea       DECIMAL(12,2)         NOT NULL COMMENT 'cantidad por precio, en soles',
  PRIMARY KEY (id_linea_venta),
  CONSTRAINT fk_linven_venta
    FOREIGN KEY (id_venta) REFERENCES venta (id_venta),
  CONSTRAINT fk_linven_producto
    FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
  CONSTRAINT chk_linven_cant CHECK (cantidad > 0),
  CONSTRAINT chk_linven_precio CHECK (precio_unit >= 0)
) ENGINE=InnoDB COMMENT='Detalle de la venta. Un producto por linea';

CREATE INDEX idx_linven_venta ON linea_venta (id_venta);
CREATE INDEX idx_linven_producto ON linea_venta (id_producto);

CREATE TABLE comprobante (
  id_comprobante  INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del comprobante',
  id_venta        INT UNSIGNED          NOT NULL COMMENT 'Venta que origina este comprobante',
  id_tipo_comp    TINYINT UNSIGNED      NOT NULL COMMENT 'Boleta o factura',
  serie           VARCHAR(6)            NOT NULL COMMENT 'Serie impresa. Ejemplo: B001',
  numero          INT UNSIGNED          NOT NULL COMMENT 'Numero correlativo',
  fec_emision     DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha de emision',
  estado          VARCHAR(10)           NOT NULL DEFAULT 'EMITIDO' COMMENT 'EMITIDO o ANULADO',
  PRIMARY KEY (id_comprobante),
  CONSTRAINT fk_comp_venta
    FOREIGN KEY (id_venta) REFERENCES venta (id_venta),
  CONSTRAINT fk_comp_tipo
    FOREIGN KEY (id_tipo_comp) REFERENCES tipo_comp (id_tipo_comp),
  CONSTRAINT chk_comp_estado CHECK (estado IN ('EMITIDO','ANULADO'))
) ENGINE=InnoDB COMMENT='Boleta o factura simulada. Una venta genera un solo comprobante';

ALTER TABLE comprobante
  ADD CONSTRAINT unq_comp_venta UNIQUE (id_venta);
ALTER TABLE comprobante
  ADD CONSTRAINT unq_comp_serie_num UNIQUE (id_tipo_comp, serie, numero);

-- -----------------------------------------------------------------------------
-- 6) AJUSTES Y KARDEX
--    El ajuste sirve para merma, hallazgo o correccion de conteo.
--    El kardex guarda cada movimiento. Es la prueba de por que cambio el stock.
-- -----------------------------------------------------------------------------

CREATE TABLE ajuste (
  id_ajuste       INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno del ajuste',
  id_usuario      INT UNSIGNED          NOT NULL COMMENT 'Quien autorizo el ajuste',
  numero          VARCHAR(20)           NOT NULL COMMENT 'Numero interno. Ejemplo: AJ-0001',
  fec_ajuste      DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha del ajuste',
  motivo          VARCHAR(15)           NOT NULL COMMENT 'MERMA, HALLAZGO, CORRECCION u OTRO',
  observacion     VARCHAR(300)          NOT NULL COMMENT 'Por que se hace el ajuste. Es obligatorio',
  estado          VARCHAR(12)           NOT NULL DEFAULT 'BORRADOR' COMMENT 'BORRADOR, CONFIRMADO o ANULADO',
  PRIMARY KEY (id_ajuste),
  CONSTRAINT fk_ajuste_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT chk_ajuste_motivo CHECK (motivo IN ('MERMA','HALLAZGO','CORRECCION','OTRO')),
  CONSTRAINT chk_ajuste_estado CHECK (estado IN ('BORRADOR','CONFIRMADO','ANULADO'))
) ENGINE=InnoDB COMMENT='Ajuste de inventario cuando el stock fisico no coincide';

ALTER TABLE ajuste
  ADD CONSTRAINT unq_ajuste_numero UNIQUE (numero);

CREATE TABLE linea_ajuste (
  id_linea_ajuste INT UNSIGNED          NOT NULL AUTO_INCREMENT COMMENT 'Numero interno de la linea',
  id_ajuste       INT UNSIGNED          NOT NULL COMMENT 'Ajuste al que pertenece',
  id_producto     INT UNSIGNED          NOT NULL COMMENT 'Producto que se corrige',
  tipo            VARCHAR(10)           NOT NULL COMMENT 'ENTRADA suma. SALIDA resta',
  cantidad        DECIMAL(12,3)         NOT NULL COMMENT 'Cuantas unidades se corrigen',
  PRIMARY KEY (id_linea_ajuste),
  CONSTRAINT fk_linaju_ajuste
    FOREIGN KEY (id_ajuste) REFERENCES ajuste (id_ajuste),
  CONSTRAINT fk_linaju_producto
    FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
  CONSTRAINT chk_linaju_tipo CHECK (tipo IN ('ENTRADA','SALIDA')),
  CONSTRAINT chk_linaju_cant CHECK (cantidad > 0)
) ENGINE=InnoDB COMMENT='Detalle del ajuste. Un producto por linea';

CREATE INDEX idx_linaju_ajuste ON linea_ajuste (id_ajuste);

-- Historial de entradas y salidas. No se borra. Se consulta para el kardex.
CREATE TABLE kardex (
  id_kardex       BIGINT UNSIGNED       NOT NULL AUTO_INCREMENT COMMENT 'Numero del movimiento',
  id_producto     INT UNSIGNED          NOT NULL COMMENT 'Producto que se movio',
  id_usuario      INT UNSIGNED          NOT NULL COMMENT 'Quien hizo el movimiento',
  fec_mov         DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Cuando se movio',
  tipo_mov        VARCHAR(20)           NOT NULL COMMENT 'Si entro por compra, salio por venta o fue un ajuste',
  documento       VARCHAR(10)           NOT NULL COMMENT 'De donde sale: COMPRA, VENTA o AJUSTE',
  id_doc          INT UNSIGNED          NOT NULL COMMENT 'Numero de esa compra, venta o ajuste',
  cantidad        DECIMAL(12,3)         NOT NULL COMMENT 'Cuantas unidades se movieron',
  costo_unit      DECIMAL(12,2)         NOT NULL DEFAULT 0.00 COMMENT 'Costo o precio de referencia, en soles',
  stock_ant       DECIMAL(12,3)         NOT NULL COMMENT 'Stock que habia antes',
  stock_nuevo     DECIMAL(12,3)         NOT NULL COMMENT 'Stock que quedo despues',
  observacion     VARCHAR(300)          NOT NULL DEFAULT '' COMMENT 'Nota del movimiento',
  PRIMARY KEY (id_kardex),
  CONSTRAINT fk_kardex_producto
    FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
  CONSTRAINT fk_kardex_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT chk_kardex_tipomov CHECK (tipo_mov IN (
    'ENTRADA_COMPRA','SALIDA_VENTA','AJUSTE_ENTRADA','AJUSTE_SALIDA',
    'ANULACION_COMPRA','ANULACION_VENTA')),
  CONSTRAINT chk_kardex_doc CHECK (documento IN ('COMPRA','VENTA','AJUSTE'))
) ENGINE=InnoDB COMMENT='Historial de stock. Permite explicar por que subio o bajo cada producto';

CREATE INDEX idx_kardex_prod_fec ON kardex (id_producto, fec_mov);
CREATE INDEX idx_kardex_doc ON kardex (documento, id_doc);

-- -----------------------------------------------------------------------------
-- 7) VISTAS
--    Consultas listas para reportes. No guardan datos nuevos.
-- -----------------------------------------------------------------------------

-- Productos activos que ya llegaron al minimo o se acabaron.
CREATE OR REPLACE VIEW v_alerta_stock AS
SELECT
  p.id_producto,
  p.codigo,
  p.nombre,
  c.nombre AS categoria,
  m.nombre AS marca,
  p.stock_actual,
  p.stock_minimo,
  CASE
    WHEN p.stock_actual <= 0 THEN 'AGOTADO'
    WHEN p.stock_actual <= p.stock_minimo THEN 'STOCK_MINIMO'
    ELSE 'OK'
  END AS nivel_alerta
FROM producto AS p
JOIN categoria AS c ON c.id_categoria = p.id_categoria
JOIN marca AS m ON m.id_marca = p.id_marca
WHERE p.estado = 'ACTIVO'
  AND p.stock_actual <= p.stock_minimo;

-- Kardex para imprimir o mostrar en pantalla.
CREATE OR REPLACE VIEW v_kardex AS
SELECT
  k.id_kardex,
  p.codigo,
  p.nombre AS producto,
  k.fec_mov,
  k.tipo_mov,
  k.documento,
  k.id_doc,
  k.cantidad,
  k.stock_ant,
  k.stock_nuevo,
  k.costo_unit,
  CONCAT(u.nombres, ' ', u.apellidos) AS usuario
FROM kardex AS k
JOIN producto AS p ON p.id_producto = k.id_producto
JOIN usuario AS u ON u.id_usuario = k.id_usuario;

-- -----------------------------------------------------------------------------
-- 8) PROCEDIMIENTOS
--    La aplicacion NO cambia el stock a mano.
--    Guarda la compra, venta o ajuste en BORRADOR y luego llama al procedimiento.
--    Asi el stock y el kardex siempre coinciden.
-- -----------------------------------------------------------------------------

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_confirmar_compra $$
CREATE PROCEDURE sp_confirmar_compra (
  IN p_id_compra  INT UNSIGNED,
  IN p_id_usuario INT UNSIGNED
)
COMMENT 'Confirma una compra: entra mercaderia y se anota en el kardex'
BEGIN
  DECLARE v_estado     VARCHAR(12);
  DECLARE v_id_prod    INT UNSIGNED;
  DECLARE v_cant       DECIMAL(12,3);
  DECLARE v_costo      DECIMAL(12,2);
  DECLARE v_stock      DECIMAL(12,3);
  DECLARE v_done       INT DEFAULT 0;
  DECLARE cur CURSOR FOR
    SELECT id_producto, cantidad, costo_unit
    FROM linea_compra
    WHERE id_compra = p_id_compra;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

  START TRANSACTION;
    SELECT estado INTO v_estado
    FROM compra
    WHERE id_compra = p_id_compra
    FOR UPDATE;

    IF v_estado IS NULL THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La compra no existe';
    END IF;
    IF v_estado <> 'BORRADOR' THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Solo se confirma una compra en BORRADOR';
    END IF;

    OPEN cur;
    read_loop: LOOP
      FETCH cur INTO v_id_prod, v_cant, v_costo;
      IF v_done = 1 THEN
        LEAVE read_loop;
      END IF;

      SELECT stock_actual INTO v_stock
      FROM producto
      WHERE id_producto = v_id_prod
      FOR UPDATE;

      UPDATE producto
        SET stock_actual = stock_actual + v_cant,
            precio_compra = v_costo
      WHERE id_producto = v_id_prod;

      INSERT INTO kardex (
        id_producto, id_usuario, tipo_mov, documento, id_doc,
        cantidad, costo_unit, stock_ant, stock_nuevo, observacion
      ) VALUES (
        v_id_prod, p_id_usuario, 'ENTRADA_COMPRA', 'COMPRA', p_id_compra,
        v_cant, v_costo, v_stock, v_stock + v_cant, 'Ingreso por compra'
      );
    END LOOP;
    CLOSE cur;

    UPDATE compra
      SET estado = 'CONFIRMADA',
          fec_ingreso = NOW()
    WHERE id_compra = p_id_compra;
  COMMIT;
END $$

DROP PROCEDURE IF EXISTS sp_confirmar_venta $$
CREATE PROCEDURE sp_confirmar_venta (
  IN p_id_venta     INT UNSIGNED,
  IN p_id_usuario   INT UNSIGNED,
  IN p_id_tipo_comp TINYINT UNSIGNED
)
COMMENT 'Confirma una venta: revisa stock, descuenta, anota kardex y emite comprobante'
BEGIN
  DECLARE v_estado     VARCHAR(12);
  DECLARE v_id_prod    INT UNSIGNED;
  DECLARE v_cant       DECIMAL(12,3);
  DECLARE v_precio     DECIMAL(12,2);
  DECLARE v_stock      DECIMAL(12,3);
  DECLARE v_serie      VARCHAR(6);
  DECLARE v_num        INT UNSIGNED;
  DECLARE v_done       INT DEFAULT 0;
  DECLARE cur CURSOR FOR
    SELECT id_producto, cantidad, precio_unit
    FROM linea_venta
    WHERE id_venta = p_id_venta;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

  START TRANSACTION;
    SELECT estado INTO v_estado
    FROM venta
    WHERE id_venta = p_id_venta
    FOR UPDATE;

    IF v_estado IS NULL THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La venta no existe';
    END IF;
    IF v_estado <> 'BORRADOR' THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Solo se confirma una venta en BORRADOR';
    END IF;

    OPEN cur;
    read_loop: LOOP
      FETCH cur INTO v_id_prod, v_cant, v_precio;
      IF v_done = 1 THEN
        LEAVE read_loop;
      END IF;

      SELECT stock_actual INTO v_stock
      FROM producto
      WHERE id_producto = v_id_prod
      FOR UPDATE;

      -- No se vende si no hay unidades suficientes.
      IF v_stock < v_cant THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stock insuficiente para confirmar la venta';
      END IF;

      UPDATE producto
        SET stock_actual = stock_actual - v_cant
      WHERE id_producto = v_id_prod;

      INSERT INTO kardex (
        id_producto, id_usuario, tipo_mov, documento, id_doc,
        cantidad, costo_unit, stock_ant, stock_nuevo, observacion
      ) VALUES (
        v_id_prod, p_id_usuario, 'SALIDA_VENTA', 'VENTA', p_id_venta,
        v_cant, v_precio, v_stock, v_stock - v_cant, 'Salida por venta'
      );
    END LOOP;
    CLOSE cur;

    SELECT serie, num_actual + 1
      INTO v_serie, v_num
    FROM tipo_comp
    WHERE id_tipo_comp = p_id_tipo_comp
    FOR UPDATE;

    UPDATE tipo_comp
      SET num_actual = v_num
    WHERE id_tipo_comp = p_id_tipo_comp;

    INSERT INTO comprobante (id_venta, id_tipo_comp, serie, numero)
    VALUES (p_id_venta, p_id_tipo_comp, v_serie, v_num);

    UPDATE venta
      SET estado = 'CONFIRMADA'
    WHERE id_venta = p_id_venta;
  COMMIT;
END $$

DROP PROCEDURE IF EXISTS sp_confirmar_ajuste $$
CREATE PROCEDURE sp_confirmar_ajuste (
  IN p_id_ajuste  INT UNSIGNED,
  IN p_id_usuario INT UNSIGNED
)
COMMENT 'Confirma un ajuste: corrige el stock y lo deja anotado en el kardex'
BEGIN
  DECLARE v_estado     VARCHAR(12);
  DECLARE v_id_prod    INT UNSIGNED;
  DECLARE v_tipo       VARCHAR(10);
  DECLARE v_cant       DECIMAL(12,3);
  DECLARE v_stock      DECIMAL(12,3);
  DECLARE v_nuevo      DECIMAL(12,3);
  DECLARE v_mov        VARCHAR(20);
  DECLARE v_done       INT DEFAULT 0;
  DECLARE cur CURSOR FOR
    SELECT id_producto, tipo, cantidad
    FROM linea_ajuste
    WHERE id_ajuste = p_id_ajuste;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

  START TRANSACTION;
    SELECT estado INTO v_estado
    FROM ajuste
    WHERE id_ajuste = p_id_ajuste
    FOR UPDATE;

    IF v_estado IS NULL THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El ajuste no existe';
    END IF;
    IF v_estado <> 'BORRADOR' THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Solo se confirma un ajuste en BORRADOR';
    END IF;

    OPEN cur;
    read_loop: LOOP
      FETCH cur INTO v_id_prod, v_tipo, v_cant;
      IF v_done = 1 THEN
        LEAVE read_loop;
      END IF;

      SELECT stock_actual INTO v_stock
      FROM producto
      WHERE id_producto = v_id_prod
      FOR UPDATE;

      IF v_tipo = 'SALIDA' THEN
        IF v_stock < v_cant THEN
          ROLLBACK;
          SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stock insuficiente para el ajuste de salida';
        END IF;
        SET v_nuevo = v_stock - v_cant;
        SET v_mov = 'AJUSTE_SALIDA';
      ELSE
        SET v_nuevo = v_stock + v_cant;
        SET v_mov = 'AJUSTE_ENTRADA';
      END IF;

      UPDATE producto
        SET stock_actual = v_nuevo
      WHERE id_producto = v_id_prod;

      INSERT INTO kardex (
        id_producto, id_usuario, tipo_mov, documento, id_doc,
        cantidad, costo_unit, stock_ant, stock_nuevo, observacion
      ) VALUES (
        v_id_prod, p_id_usuario, v_mov, 'AJUSTE', p_id_ajuste,
        v_cant, 0, v_stock, v_nuevo, CONCAT('Ajuste ', v_tipo)
      );
    END LOOP;
    CLOSE cur;

    UPDATE ajuste
      SET estado = 'CONFIRMADO'
    WHERE id_ajuste = p_id_ajuste;
  COMMIT;
END $$

DELIMITER ;

-- -----------------------------------------------------------------------------
-- 9) DATOS INICIALES
--    Lo minimo para entrar al sistema y empezar a registrar maestros.
-- -----------------------------------------------------------------------------

INSERT INTO rol (codigo, nombre, descripcion, es_tecnico) VALUES
('ADMIN_SISTEMA', 'Administrador del sistema', 'Cuida usuarios, roles y la bitacora. No opera la ferreteria.', 1),
('ADMIN_NEGOCIO', 'Administrador del negocio', 'Registra proveedores, productos, compras, ajustes y reportes.', 0),
('ALMACENERO',    'Almacenero',                'Ingresa mercaderia, ve stock, kardex y hace ajustes.', 0),
('VENDEDOR',      'Vendedor',                  'Registra ventas y consulta stock.', 0),
('GERENTE',       'Gerente',                   'Solo ve reportes y alertas.', 0);

INSERT INTO permiso (codigo, modulo, descripcion) VALUES
('SEG_USUARIO',  'SEGURIDAD',  'Crear y cambiar usuarios'),
('SEG_ROL',      'SEGURIDAD',  'Crear y cambiar roles'),
('SEG_BITACORA', 'SEGURIDAD',  'Ver la bitacora'),
('PRV_CRUD',     'PROVEEDOR',  'Registrar proveedores'),
('PRD_CRUD',     'PRODUCTO',   'Registrar productos, categorias y marcas'),
('PRD_VER',      'PRODUCTO',   'Consultar productos y stock'),
('COM_REG',      'COMPRA',     'Registrar y confirmar compras'),
('COM_VER',      'COMPRA',     'Consultar compras'),
('INV_AJUSTE',   'INVENTARIO', 'Registrar ajustes'),
('INV_KARDEX',   'INVENTARIO', 'Consultar kardex'),
('INV_ALERTA',   'INVENTARIO', 'Ver alertas de stock minimo'),
('VTA_REG',      'VENTA',      'Registrar ventas y emitir comprobante'),
('VTA_VER',      'VENTA',      'Consultar ventas'),
('RPT_GERENCIA', 'REPORTE',    'Ver reportes de gerencia');

-- El administrador de sistemas solo maneja seguridad.
INSERT INTO acceso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol AS r
CROSS JOIN permiso AS p
WHERE r.codigo = 'ADMIN_SISTEMA'
  AND p.codigo IN ('SEG_USUARIO','SEG_ROL','SEG_BITACORA');

-- El administrador del negocio opera la ferreteria. No crea roles de TI.
INSERT INTO acceso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol AS r
CROSS JOIN permiso AS p
WHERE r.codigo = 'ADMIN_NEGOCIO'
  AND p.codigo <> 'SEG_ROL';

INSERT INTO acceso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol AS r
CROSS JOIN permiso AS p
WHERE r.codigo = 'ALMACENERO'
  AND p.codigo IN ('PRD_VER','PRV_CRUD','COM_REG','COM_VER','INV_AJUSTE','INV_KARDEX','INV_ALERTA');

INSERT INTO acceso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol AS r
CROSS JOIN permiso AS p
WHERE r.codigo = 'VENDEDOR'
  AND p.codigo IN ('PRD_VER','VTA_REG','VTA_VER');

INSERT INTO acceso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol AS r
CROSS JOIN permiso AS p
WHERE r.codigo = 'GERENTE'
  AND p.codigo IN ('PRD_VER','COM_VER','VTA_VER','INV_KARDEX','INV_ALERTA','RPT_GERENCIA');

INSERT INTO unidad (codigo, nombre) VALUES
('UND', 'Unidad'),
('M',   'Metro'),
('KG',  'Kilogramo'),
('LT',  'Litro'),
('PQT', 'Paquete'),
('CJ',  'Caja');

INSERT INTO categoria (nombre) VALUES
('Herramientas'),
('Materiales de construccion'),
('Accesorios electricos'),
('Plomeria'),
('Fijaciones y tornilleria'),
('Pinturas');

INSERT INTO marca (nombre) VALUES
('Pretul'),
('Stanley'),
('Truper'),
('Pavco'),
('Sika'),
('Generico');

INSERT INTO metodo (codigo, nombre) VALUES
('EFECTIVO',      'Efectivo'),
('YAPE',          'Yape'),
('PLIN',          'Plin'),
('TRANSFERENCIA', 'Transferencia'),
('TARJETA',       'Tarjeta'),
('CREDITO',       'Credito interno');

INSERT INTO tipo_comp (codigo, nombre, serie, num_actual) VALUES
('BOL', 'Boleta simulada',  'B001', 0),
('FAC', 'Factura simulada', 'F001', 0);

-- Cuentas iniciales. La clave encriptada se debe cambiar en la aplicacion.
INSERT INTO usuario (id_rol, cuenta, hash_clave, nombres, apellidos, email, estado)
SELECT id_rol, 'admin.sistema',
       '$2y$10$CAMBIAR_ESTE_HASH_POR_BCRYPT_REAL_EN_PRODUCCION____',
       'Administrador', 'TI',
       'ti@lasolucionferretera.com', 'ACTIVO'
FROM rol WHERE codigo = 'ADMIN_SISTEMA';

INSERT INTO usuario (id_rol, cuenta, hash_clave, nombres, apellidos, email, estado)
SELECT id_rol, 'admin.negocio',
       '$2y$10$CAMBIAR_ESTE_HASH_POR_BCRYPT_REAL_EN_PRODUCCION____',
       'Administrador', 'Negocio',
       'admin@lasolucionferretera.com', 'ACTIVO'
FROM rol WHERE codigo = 'ADMIN_NEGOCIO';

-- Cliente para boletas rapidas. DNI 00000000 cumple la regla de 8 numeros.
INSERT INTO cliente (tipo_doc, num_doc, nombres)
VALUES ('DNI', '00000000', 'Cliente varios');

INSERT INTO parametro (codigo, valor, descripcion) VALUES
('MONEDA',     'PEN',  'Moneda del sistema: soles peruanos'),
('SIMBOLO',    'S/',   'Simbolo que se muestra en pantallas'),
('IGV_TASA',   '0.18', 'Porcentaje de IGV'),
('NOM_MONEDA', 'Sol',  'Nombre de la moneda');
