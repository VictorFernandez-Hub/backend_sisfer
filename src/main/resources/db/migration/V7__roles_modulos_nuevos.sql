-- =============================================================================
-- V7: Roles al día con los módulos nuevos (Ventas, Compras, Inventario y Kardex)
--
-- 1. Permisos que faltaban en el catálogo: clientes y anulaciones.
-- 2. Accesos de cada rol a esos permisos. El administrador del sistema
--    tiene acceso total desde el commit "Dar acceso total al administrador del sistema".
-- 3. Descripciones de los roles según lo que hoy hacen en la aplicación.
-- 4. Módulos del menú (rol_modulo) para los roles base, para que la pantalla
--    "Roles y accesos" muestre lo que ve cada uno.
--
-- Todo es idempotente (INSERT IGNORE / UPDATE): se puede correr sobre una base
-- que ya tenga parte de estos datos.
-- =============================================================================

-- 1) Permisos nuevos -----------------------------------------------------------
INSERT IGNORE INTO permiso (codigo, modulo, descripcion) VALUES
('CLI_CRUD',   'CLIENTE', 'Registrar y cambiar clientes'),
('CLI_VER',    'CLIENTE', 'Consultar clientes'),
('VTA_ANULAR', 'VENTA',   'Anular ventas confirmadas (devuelve el stock)'),
('COM_ANULAR', 'COMPRA',  'Anular compras en borrador');

-- 2) Accesos -------------------------------------------------------------------
-- Administrador del sistema: todos los permisos
INSERT IGNORE INTO acceso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol AS r CROSS JOIN permiso AS p
WHERE r.codigo = 'ADMIN_SISTEMA';

-- Administrador del negocio: los permisos nuevos (ya tenía el resto menos SEG_ROL)
INSERT IGNORE INTO acceso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol AS r CROSS JOIN permiso AS p
WHERE r.codigo = 'ADMIN_NEGOCIO'
  AND p.codigo IN ('CLI_CRUD','CLI_VER','VTA_ANULAR','COM_ANULAR');

-- Almacenero: anula los borradores de compra que registró
INSERT IGNORE INTO acceso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol AS r CROSS JOIN permiso AS p
WHERE r.codigo = 'ALMACENERO'
  AND p.codigo IN ('COM_ANULAR');

-- Vendedor: registra a los clientes a los que les vende
INSERT IGNORE INTO acceso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol AS r CROSS JOIN permiso AS p
WHERE r.codigo = 'VENDEDOR'
  AND p.codigo IN ('CLI_CRUD','CLI_VER');

-- Gerente: solo consulta
INSERT IGNORE INTO acceso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM rol AS r CROSS JOIN permiso AS p
WHERE r.codigo = 'GERENTE'
  AND p.codigo IN ('CLI_VER');

-- 3) Descripciones -------------------------------------------------------------
UPDATE rol SET descripcion = 'Usuarios, roles y bitácora. Tiene acceso total a todos los módulos.'
WHERE codigo = 'ADMIN_SISTEMA';
UPDATE rol SET descripcion = 'Opera la ferretería: productos, precios, proveedores, clientes, compras, ventas y reportes.'
WHERE codigo = 'ADMIN_NEGOCIO';
UPDATE rol SET descripcion = 'Registra y confirma compras, ve inventario y kardex, y gestiona proveedores.'
WHERE codigo = 'ALMACENERO';
UPDATE rol SET descripcion = 'Registra ventas, cobra, emite comprobantes y gestiona clientes.'
WHERE codigo = 'VENDEDOR';
UPDATE rol SET descripcion = 'Consulta ventas, compras, inventario, kardex y reportes. No registra.'
WHERE codigo = 'GERENTE';

-- 4) Módulos del menú ----------------------------------------------------------
-- Un módulo en rol_modulo da acceso COMPLETO a ese módulo (ver UserDetailsServiceImpl).
-- Por eso al gerente solo se le asignan módulos de consulta.
-- USUARIOS, ROLES y BITACORA también se asignan: un rol administrador creado desde la
-- pantalla puede gestionar usuarios y asignar módulos a los demás roles.

INSERT IGNORE INTO rol_modulo (id_rol, modulo)
SELECT r.id_rol, m.modulo
FROM rol AS r
JOIN (
  SELECT 'ADMIN_SISTEMA' AS rol, 'CLIENTES' AS modulo UNION ALL
  SELECT 'ADMIN_SISTEMA', 'PRODUCTOS'   UNION ALL
  SELECT 'ADMIN_SISTEMA', 'PROVEEDORES' UNION ALL
  SELECT 'ADMIN_SISTEMA', 'VENTAS'      UNION ALL
  SELECT 'ADMIN_SISTEMA', 'COMPRAS'     UNION ALL
  SELECT 'ADMIN_SISTEMA', 'INVENTARIO'  UNION ALL
  SELECT 'ADMIN_SISTEMA', 'KARDEX'      UNION ALL
  SELECT 'ADMIN_SISTEMA', 'USUARIOS'    UNION ALL
  SELECT 'ADMIN_SISTEMA', 'ROLES'       UNION ALL
  SELECT 'ADMIN_SISTEMA', 'BITACORA'    UNION ALL

  SELECT 'ADMIN_NEGOCIO', 'CLIENTES'    UNION ALL
  SELECT 'ADMIN_NEGOCIO', 'PRODUCTOS'   UNION ALL
  SELECT 'ADMIN_NEGOCIO', 'PROVEEDORES' UNION ALL
  SELECT 'ADMIN_NEGOCIO', 'VENTAS'      UNION ALL
  SELECT 'ADMIN_NEGOCIO', 'COMPRAS'     UNION ALL
  SELECT 'ADMIN_NEGOCIO', 'INVENTARIO'  UNION ALL
  SELECT 'ADMIN_NEGOCIO', 'KARDEX'      UNION ALL

  SELECT 'ALMACENERO',    'PRODUCTOS'   UNION ALL
  SELECT 'ALMACENERO',    'PROVEEDORES' UNION ALL
  SELECT 'ALMACENERO',    'COMPRAS'     UNION ALL
  SELECT 'ALMACENERO',    'INVENTARIO'  UNION ALL
  SELECT 'ALMACENERO',    'KARDEX'      UNION ALL

  -- El vendedor ve Productos por su rol, pero solo para consultar: no se le asigna el módulo
  SELECT 'VENDEDOR',      'CLIENTES'    UNION ALL
  SELECT 'VENDEDOR',      'VENTAS'      UNION ALL

  SELECT 'GERENTE',       'INVENTARIO'  UNION ALL
  SELECT 'GERENTE',       'KARDEX'
) AS m ON m.rol = r.codigo;
