-- Borra SOLO los objetos de Panaderia San Carlos (las 17 tablas y los 7
-- paquetes, por su nombre exacto) del esquema actual de Oracle Cloud, para
-- empezar de cero despues de una copia fallida. Cualquier otro objeto del
-- esquema se deja intacto.
-- Se niega en la base local XE y si el usuario conectado es ADMIN u otro
-- usuario del sistema: debe ejecutarse conectado como el usuario de la app.
SET SERVEROUTPUT ON
@@solo_nube.sql

BEGIN
    IF USER IN ('ADMIN', 'SYS', 'SYSTEM') THEN
        RAISE_APPLICATION_ERROR(-20991,
            'Conectese como el usuario de la aplicacion (PANADERIA), no como ' || USER
            || '. No se borro nada.');
    END IF;

    FOR o IN (SELECT u.object_name, u.object_type
                FROM user_objects u
                JOIN TABLE(sys.odcivarchar2list(
                     'TABLE:DESPACHO', 'TABLE:MOVIMIENTO_INVENTARIO', 'TABLE:INVENTARIO',
                     'TABLE:PRODUCCION_DETALLE', 'TABLE:PRODUCCION_PEDIDO', 'TABLE:PRODUCCION',
                     'TABLE:PEDIDO_DETALLE', 'TABLE:PEDIDO', 'TABLE:RECETA_DETALLE',
                     'TABLE:RECETA', 'TABLE:PRODUCTO', 'TABLE:USUARIO_CREDENCIAL',
                     'TABLE:USUARIO', 'TABLE:UNIDAD_MEDIDA', 'TABLE:TIPO_PRODUCTO',
                     'TABLE:SUCURSAL', 'TABLE:ROL',
                     'PACKAGE:PKG_RECETA', 'PACKAGE:PKG_INVENTARIO', 'PACKAGE:PKG_PEDIDO',
                     'PACKAGE:PKG_PRODUCCION', 'PACKAGE:PKG_DESPACHO', 'PACKAGE:PKG_SEGURIDAD',
                     'PACKAGE:PKG_USUARIO')) lista
                  ON u.object_type = SUBSTR(lista.column_value, 1, INSTR(lista.column_value, ':') - 1)
                 AND u.object_name = SUBSTR(lista.column_value, INSTR(lista.column_value, ':') + 1)
               ORDER BY DECODE(u.object_type, 'PACKAGE', 1, 2)) LOOP
        IF o.object_type = 'TABLE' THEN
            -- CASCADE CONSTRAINTS solo quita las llaves que apuntan a esta tabla.
            EXECUTE IMMEDIATE 'DROP TABLE "' || o.object_name || '" CASCADE CONSTRAINTS PURGE';
        ELSE
            EXECUTE IMMEDIATE 'DROP PACKAGE "' || o.object_name || '"';
        END IF;
        DBMS_OUTPUT.PUT_LINE('Borrado: ' || o.object_type || ' ' || o.object_name);
    END LOOP;
END;
/

PROMPT === Objetos de la panaderia borrados del esquema de la nube
