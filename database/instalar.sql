-- Crea la base de datos de Panaderia San Carlos en un esquema VACIO.
-- Ejecutar desde la carpeta database con SQL*Plus (ver README.md).
-- Se detiene en el primer error para no dejar una instalacion a medias.

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET ECHO OFF
SET DEFINE OFF

PROMPT === 1/3 Tablas
@@01_tablas/01_rol.sql
@@01_tablas/02_sucursal.sql
@@01_tablas/03_tipo_producto.sql
@@01_tablas/04_unidad_medida.sql
@@01_tablas/05_usuario.sql
@@01_tablas/06_producto.sql
@@01_tablas/07_receta.sql
@@01_tablas/08_receta_detalle.sql
@@01_tablas/09_pedido.sql
@@01_tablas/10_pedido_detalle.sql
@@01_tablas/11_produccion.sql
@@01_tablas/12_produccion_pedido.sql
@@01_tablas/13_produccion_detalle.sql
@@01_tablas/14_inventario.sql
@@01_tablas/15_movimiento_inventario.sql
@@01_tablas/16_despacho.sql

PROMPT === 2/3 Datos iniciales
@@02_datos_iniciales.sql

PROMPT === 3/3 Paquetes
-- Orden por dependencias: PRODUCCION usa RECETA e INVENTARIO; DESPACHO usa INVENTARIO.
-- Un paquete con errores de compilacion queda creado pero INVALIDO sin activar
-- WHENEVER SQLERROR; SHOW ERRORS muestra el detalle y la verificacion final lo detiene.
@@03_paquetes/01_pkg_receta.sql
SHOW ERRORS PACKAGE BODY PKG_RECETA
@@03_paquetes/02_pkg_inventario.sql
SHOW ERRORS PACKAGE BODY PKG_INVENTARIO
@@03_paquetes/03_pkg_pedido.sql
SHOW ERRORS PACKAGE BODY PKG_PEDIDO
@@03_paquetes/04_pkg_produccion.sql
SHOW ERRORS PACKAGE BODY PKG_PRODUCCION
@@03_paquetes/05_pkg_despacho.sql
SHOW ERRORS PACKAGE BODY PKG_DESPACHO

PROMPT === Verificacion
-- Falla (y el script termina con error) si falta un objeto esperado, si alguno
-- quedo invalido o si faltan los tipos de producto. Cubre tambien un archivo
-- inexistente: SQL*Plus solo avisa SP2-0310 y continua, pero el objeto faltara.
DECLARE
    v_problemas VARCHAR2(4000);
BEGIN
    FOR esperado IN (
        SELECT SUBSTR(column_value, 1, INSTR(column_value, ':') - 1) AS tipo,
               SUBSTR(column_value, INSTR(column_value, ':') + 1)    AS nombre
          FROM TABLE(sys.odcivarchar2list(
               'TABLE:ROL', 'TABLE:SUCURSAL', 'TABLE:TIPO_PRODUCTO', 'TABLE:UNIDAD_MEDIDA',
               'TABLE:USUARIO', 'TABLE:PRODUCTO', 'TABLE:RECETA', 'TABLE:RECETA_DETALLE',
               'TABLE:PEDIDO', 'TABLE:PEDIDO_DETALLE', 'TABLE:PRODUCCION',
               'TABLE:PRODUCCION_PEDIDO', 'TABLE:PRODUCCION_DETALLE', 'TABLE:INVENTARIO',
               'TABLE:MOVIMIENTO_INVENTARIO', 'TABLE:DESPACHO',
               'PACKAGE:PKG_RECETA', 'PACKAGE BODY:PKG_RECETA',
               'PACKAGE:PKG_INVENTARIO', 'PACKAGE BODY:PKG_INVENTARIO',
               'PACKAGE:PKG_PEDIDO', 'PACKAGE BODY:PKG_PEDIDO',
               'PACKAGE:PKG_PRODUCCION', 'PACKAGE BODY:PKG_PRODUCCION',
               'PACKAGE:PKG_DESPACHO', 'PACKAGE BODY:PKG_DESPACHO'))
         WHERE NOT EXISTS (
               SELECT 1 FROM user_objects o
                WHERE o.object_type = SUBSTR(column_value, 1, INSTR(column_value, ':') - 1)
                  AND o.object_name = SUBSTR(column_value, INSTR(column_value, ':') + 1)
                  AND o.status = 'VALID')
    ) LOOP
        v_problemas := v_problemas || CHR(10) || '  falta o invalido: '
                       || esperado.tipo || ' ' || esperado.nombre;
    END LOOP;

    FOR tipo IN (
        SELECT column_value AS nombre
          FROM TABLE(sys.odcivarchar2list('INSUMO', 'PRODUCTO_FINAL'))
         WHERE column_value NOT IN (SELECT nombre FROM TIPO_PRODUCTO)
    ) LOOP
        v_problemas := v_problemas || CHR(10) || '  falta el tipo de producto: ' || tipo.nombre;
    END LOOP;

    IF v_problemas IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20999, 'Instalacion incompleta:' || v_problemas);
    END IF;
END;
/

PROMPT === Instalacion terminada: 16 tablas y 5 paquetes validos
EXIT SUCCESS
