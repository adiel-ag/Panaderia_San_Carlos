-- Crea en Oracle Cloud las 17 tablas y los 7 paquetes, SIN datos, listos para
-- recibir la copia de la base local XE. Ejecutar conectado como PANADERIA.
-- Diferencias con instalar.sql:
--   * No carga 02_datos_iniciales.sql: los catalogos llegan con la copia.
--   * Agrega USUARIO.PASSWORD_HASH (columna heredada que la base local tiene;
--     sin ella la copia de USUARIO falla).
--   * Deja desactivadas las llaves foraneas para que la copia pueda cargar las
--     tablas en cualquier orden. 02_finalizar_nube.sql las vuelve a activar.
SET DEFINE OFF
SET SQLBLANKLINES ON
@@solo_nube.sql

PROMPT === 1/3 Tablas
@@../01_tablas/01_rol.sql
@@../01_tablas/02_sucursal.sql
@@../01_tablas/03_tipo_producto.sql
@@../01_tablas/04_unidad_medida.sql
@@../01_tablas/05_usuario.sql
@@../01_tablas/06_producto.sql
@@../01_tablas/07_receta.sql
@@../01_tablas/08_receta_detalle.sql
@@../01_tablas/09_pedido.sql
@@../01_tablas/10_pedido_detalle.sql
@@../01_tablas/11_produccion.sql
@@../01_tablas/12_produccion_pedido.sql
@@../01_tablas/13_produccion_detalle.sql
@@../01_tablas/14_inventario.sql
@@../01_tablas/15_movimiento_inventario.sql
@@../01_tablas/16_despacho.sql
@@../01_tablas/17_usuario_credencial.sql
ALTER TABLE USUARIO ADD (password_hash VARCHAR2(255));

PROMPT === 2/3 Paquetes
@@../03_paquetes/01_pkg_receta.sql
@@../03_paquetes/02_pkg_inventario.sql
@@../03_paquetes/03_pkg_pedido.sql
@@../03_paquetes/04_pkg_produccion.sql
@@../03_paquetes/05_pkg_despacho.sql
@@../03_paquetes/06_pkg_seguridad.sql
@@../03_paquetes/07_pkg_usuario.sql

PROMPT === 3/3 Llaves foraneas desactivadas para la copia
BEGIN
    FOR c IN (SELECT table_name, constraint_name FROM user_constraints
               WHERE constraint_type = 'R'
                 AND table_name IN ('ROL', 'SUCURSAL', 'TIPO_PRODUCTO', 'UNIDAD_MEDIDA',
                     'USUARIO', 'PRODUCTO', 'RECETA', 'RECETA_DETALLE', 'PEDIDO',
                     'PEDIDO_DETALLE', 'PRODUCCION', 'PRODUCCION_PEDIDO', 'PRODUCCION_DETALLE',
                     'INVENTARIO', 'MOVIMIENTO_INVENTARIO', 'DESPACHO', 'USUARIO_CREDENCIAL')) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE "' || c.table_name || '" DISABLE CONSTRAINT "'
                          || c.constraint_name || '"';
    END LOOP;
END;
/

DECLARE
    v_tablas NUMBER;
    v_invalidos NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_tablas FROM user_tables
     WHERE table_name IN ('ROL', 'SUCURSAL', 'TIPO_PRODUCTO', 'UNIDAD_MEDIDA',
           'USUARIO', 'PRODUCTO', 'RECETA', 'RECETA_DETALLE', 'PEDIDO',
           'PEDIDO_DETALLE', 'PRODUCCION', 'PRODUCCION_PEDIDO', 'PRODUCCION_DETALLE',
           'INVENTARIO', 'MOVIMIENTO_INVENTARIO', 'DESPACHO', 'USUARIO_CREDENCIAL');
    SELECT COUNT(*) INTO v_invalidos FROM user_objects
     WHERE object_type LIKE 'PACKAGE%' AND object_name LIKE 'PKG\_%' ESCAPE '\'
       AND status <> 'VALID';
    IF v_tablas <> 17 OR v_invalidos > 0 THEN
        RAISE_APPLICATION_ERROR(-20999, 'Esquema incompleto: ' || v_tablas
            || ' tablas y ' || v_invalidos || ' paquetes invalidos.');
    END IF;
END;
/
PROMPT === Listo: 17 tablas vacias y 7 paquetes validos. Ahora copie los datos.
