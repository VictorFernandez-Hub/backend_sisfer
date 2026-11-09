-- Cobros queda asignado a quienes administran el negocio.
-- Los demás roles se marcan desde la pantalla de Roles.

INSERT INTO rol_modulo (id_rol, modulo)
SELECT r.id_rol, 'COBROS'
FROM rol r
WHERE r.codigo IN ('ADMIN_SISTEMA', 'ADMIN_NEGOCIO')
  AND NOT EXISTS (
    SELECT 1 FROM rol_modulo rm
    WHERE rm.id_rol = r.id_rol AND rm.modulo = 'COBROS'
  );
