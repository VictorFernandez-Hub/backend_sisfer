-- =============================================================================
-- V4: Activación de cuenta por correo (ACTIVACION_CUENTA_CORREO.sql, 01/11/2026)
-- Hasta aquí es el esquema que Aiven ya tenía aplicado a mano antes de Flyway.
-- Los cambios nuevos van desde V5 y se aplican solos en local y en Render.
-- =============================================================================

ALTER TABLE usuario
  ADD COLUMN token_activacion VARCHAR(64) NULL COMMENT 'Token del enlace de activación (24 h)',
  ADD COLUMN fec_token        DATETIME    NULL COMMENT 'Cuándo se generó el token';
