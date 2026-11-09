package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Reemplaza los procedimientos de compra y ajuste.
 * La compra guarda costo promedio, no el último costo sobre todo el stock.
 * El ajuste de salida usa ese promedio y el de entrada lo recalcula.
 * También agrega el código de operación de la venta (Yape, Plin, etc.).
 */
public class V9__CostoPromedioYAjuste extends BaseJavaMigration {

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection con = context.getConnection();
        agregarColumna(con, "linea_ajuste", "costo_unit",
                "ALTER TABLE linea_ajuste ADD COLUMN costo_unit DECIMAL(12,2) NULL " +
                "COMMENT 'Costo de una entrada. La salida usa el costo promedio del producto' AFTER cantidad");
        agregarColumna(con, "venta", "codigo_operacion",
                "ALTER TABLE venta ADD COLUMN codigo_operacion VARCHAR(40) NULL " +
                "COMMENT 'Codigo de operacion cuando el pago no es efectivo ni credito' AFTER id_metodo");

        try (Statement st = con.createStatement()) {
            st.execute("DROP PROCEDURE IF EXISTS sp_confirmar_compra");
            st.execute(SP_CONFIRMAR_COMPRA);
            st.execute("DROP PROCEDURE IF EXISTS sp_confirmar_ajuste");
            st.execute(SP_CONFIRMAR_AJUSTE);
        }
    }

    private void agregarColumna(Connection con, String tabla, String columna, String ddl) throws Exception {
        String sql = """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tabla);
            ps.setString(2, columna);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) return;
            }
        }
        try (Statement st = con.createStatement()) {
            st.execute(ddl);
        }
    }

    private static final String SP_CONFIRMAR_COMPRA = """
            CREATE PROCEDURE sp_confirmar_compra (
              IN p_id_compra  INT UNSIGNED,
              IN p_id_usuario INT UNSIGNED
            )
            COMMENT 'Confirma una compra: entra mercaderia, actualiza el costo promedio y anota el kardex'
            BEGIN
              DECLARE v_estado      VARCHAR(12);
              DECLARE v_id_prod     INT UNSIGNED;
              DECLARE v_cant        DECIMAL(12,3);
              DECLARE v_costo       DECIMAL(12,2);
              DECLARE v_stock       DECIMAL(12,3);
              DECLARE v_promedio    DECIMAL(12,2);
              DECLARE v_nuevo_costo DECIMAL(12,2);
              DECLARE v_done        INT DEFAULT 0;
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

                  SELECT stock_actual, IFNULL(precio_compra, 0) INTO v_stock, v_promedio
                  FROM producto
                  WHERE id_producto = v_id_prod
                  FOR UPDATE;

                  IF v_stock IS NULL THEN
                    ROLLBACK;
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Un producto de la compra no existe';
                  END IF;

                  IF v_stock = 0 THEN
                    SET v_nuevo_costo = v_costo;
                  ELSE
                    SET v_nuevo_costo = ROUND((v_stock * v_promedio + v_cant * v_costo) / (v_stock + v_cant), 2);
                  END IF;

                  UPDATE producto
                    SET stock_actual = stock_actual + v_cant,
                        precio_compra = v_nuevo_costo
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
            END
            """;

    private static final String SP_CONFIRMAR_AJUSTE = """
            CREATE PROCEDURE sp_confirmar_ajuste (
              IN p_id_ajuste  INT UNSIGNED,
              IN p_id_usuario INT UNSIGNED
            )
            COMMENT 'Confirma un ajuste: corrige el stock, el costo promedio si entra mercaderia, y el kardex'
            BEGIN
              DECLARE v_estado      VARCHAR(12);
              DECLARE v_id_prod     INT UNSIGNED;
              DECLARE v_tipo        VARCHAR(10);
              DECLARE v_cant        DECIMAL(12,3);
              DECLARE v_costo       DECIMAL(12,2);
              DECLARE v_stock       DECIMAL(12,3);
              DECLARE v_promedio    DECIMAL(12,2);
              DECLARE v_nuevo       DECIMAL(12,3);
              DECLARE v_nuevo_costo DECIMAL(12,2);
              DECLARE v_costo_kardex DECIMAL(12,2);
              DECLARE v_mov         VARCHAR(20);
              DECLARE v_done        INT DEFAULT 0;
              DECLARE cur CURSOR FOR
                SELECT id_producto, tipo, cantidad, costo_unit
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
                  FETCH cur INTO v_id_prod, v_tipo, v_cant, v_costo;
                  IF v_done = 1 THEN
                    LEAVE read_loop;
                  END IF;

                  SELECT stock_actual, IFNULL(precio_compra, 0) INTO v_stock, v_promedio
                  FROM producto
                  WHERE id_producto = v_id_prod
                  FOR UPDATE;

                  IF v_stock IS NULL THEN
                    ROLLBACK;
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Un producto del ajuste no existe';
                  END IF;

                  IF v_tipo = 'SALIDA' THEN
                    IF v_stock < v_cant THEN
                      ROLLBACK;
                      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stock insuficiente para el ajuste de salida';
                    END IF;
                    SET v_nuevo = v_stock - v_cant;
                    SET v_mov = 'AJUSTE_SALIDA';
                    SET v_costo_kardex = v_promedio;
                    UPDATE producto
                      SET stock_actual = v_nuevo
                    WHERE id_producto = v_id_prod;
                  ELSE
                    IF v_costo IS NULL OR v_costo <= 0 THEN
                      ROLLBACK;
                      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La entrada por ajuste necesita un costo mayor que cero';
                    END IF;
                    SET v_nuevo = v_stock + v_cant;
                    SET v_mov = 'AJUSTE_ENTRADA';
                    SET v_costo_kardex = v_costo;
                    IF v_stock = 0 THEN
                      SET v_nuevo_costo = v_costo;
                    ELSE
                      SET v_nuevo_costo = ROUND((v_stock * v_promedio + v_cant * v_costo) / (v_stock + v_cant), 2);
                    END IF;
                    UPDATE producto
                      SET stock_actual = v_nuevo,
                          precio_compra = v_nuevo_costo
                    WHERE id_producto = v_id_prod;
                  END IF;

                  INSERT INTO kardex (
                    id_producto, id_usuario, tipo_mov, documento, id_doc,
                    cantidad, costo_unit, stock_ant, stock_nuevo, observacion
                  ) VALUES (
                    v_id_prod, p_id_usuario, v_mov, 'AJUSTE', p_id_ajuste,
                    v_cant, v_costo_kardex, v_stock, v_nuevo, CONCAT('Ajuste ', v_tipo)
                  );
                END LOOP;
                CLOSE cur;

                UPDATE ajuste
                  SET estado = 'CONFIRMADO'
                WHERE id_ajuste = p_id_ajuste;
              COMMIT;
            END
            """;
}
