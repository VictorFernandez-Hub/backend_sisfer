-- =============================================================================
-- V14: Quién entregó el fondo inicial de la caja
--
-- El sencillo con el que abre el vendedor sale de alguien (el administrador, el
-- gerente o el vendedor del turno anterior). Queda registrado para saber a quién
-- pedirle cuentas si el fondo no cuadra.
-- Las cajas anteriores a este cambio y las que abren con S/ 0.00 quedan en NULL.
-- =============================================================================

ALTER TABLE caja
  ADD COLUMN id_usuario_entrega INT UNSIGNED NULL COMMENT 'Quien entrego el fondo inicial. NULL si abrio con S/ 0.00'
    AFTER monto_apertura,
  ADD KEY idx_caja_usuario_entrega (id_usuario_entrega),
  ADD CONSTRAINT fk_caja_usuario_entrega FOREIGN KEY (id_usuario_entrega) REFERENCES usuario (id_usuario);
