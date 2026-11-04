-- =============================================================================
-- V6: Anulación de ventas confirmadas (módulo de Ventas)
-- sp_anular_venta devuelve el stock, anota ANULACION_VENTA en el kardex y deja el
-- comprobante como ANULADO. Con sp_confirmar_venta son los únicos que mueven el
-- stock en ventas.
-- Los comprobantes se muestran como "Boleta de venta" y "Factura". Siguen sin
-- enviarse a SUNAT: el PDF indica que no tienen valor tributario.
-- =============================================================================

UPDATE tipo_comp SET nombre = 'Boleta de venta' WHERE codigo = 'BOL';
UPDATE tipo_comp SET nombre = 'Factura'         WHERE codigo = 'FAC';

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_anular_venta $$
CREATE PROCEDURE sp_anular_venta (
  IN p_id_venta   INT UNSIGNED,
  IN p_id_usuario INT UNSIGNED,
  IN p_motivo     VARCHAR(150)
)
COMMENT 'Anula una venta confirmada: devuelve el stock, anota el kardex y anula el comprobante'
BEGIN
  DECLARE v_estado     VARCHAR(12);
  DECLARE v_id_prod    INT UNSIGNED;
  DECLARE v_cant       DECIMAL(12,3);
  DECLARE v_precio     DECIMAL(12,2);
  DECLARE v_stock      DECIMAL(12,3);
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
    IF v_estado <> 'CONFIRMADA' THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Solo se anula con devolucion una venta CONFIRMADA';
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

      -- La mercaderia vuelve al almacen
      UPDATE producto
        SET stock_actual = stock_actual + v_cant
      WHERE id_producto = v_id_prod;

      INSERT INTO kardex (
        id_producto, id_usuario, tipo_mov, documento, id_doc,
        cantidad, costo_unit, stock_ant, stock_nuevo, observacion
      ) VALUES (
        v_id_prod, p_id_usuario, 'ANULACION_VENTA', 'VENTA', p_id_venta,
        v_cant, v_precio, v_stock, v_stock + v_cant,
        LEFT(CONCAT('Anulacion de venta: ', IFNULL(p_motivo, '')), 300)
      );
    END LOOP;
    CLOSE cur;

    UPDATE comprobante
      SET estado = 'ANULADO'
    WHERE id_venta = p_id_venta;

    UPDATE venta
      SET estado = 'ANULADA'
    WHERE id_venta = p_id_venta;
  COMMIT;
END $$

DELIMITER ;
