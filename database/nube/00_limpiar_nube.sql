-- Borra SOLO los objetos de Panaderia San Carlos (los 7 paquetes y las 17
-- tablas, por su nombre exacto) del esquema PANADERIA en Oracle Cloud, para
-- empezar de cero despues de una copia fallida.
--   * Se niega en la base local XE (solo_nube.sql).
--   * Se niega si el usuario conectado no es PANADERIA: nunca borra en ADMIN
--     ni en el esquema de otra aplicacion aunque tenga tablas con estos nombres.
--   * Borra las tablas de hijas a padres y SIN "CASCADE CONSTRAINTS": si algo
--     fuera de la panaderia depende de una tabla, Oracle detiene el borrado con
--     ORA-02449 en vez de quitarle sus llaves en silencio.
SET SERVEROUTPUT ON
@@solo_nube.sql

DECLARE
    TYPE t_nombres IS TABLE OF VARCHAR2(30);
    -- Orden de hijas a padres: cada tabla se borra antes que las que referencia.
    v_tablas t_nombres := t_nombres(
        'DESPACHO', 'MOVIMIENTO_INVENTARIO', 'INVENTARIO', 'PRODUCCION_DETALLE',
        'PRODUCCION_PEDIDO', 'PRODUCCION', 'PEDIDO_DETALLE', 'PEDIDO',
        'RECETA_DETALLE', 'RECETA', 'PRODUCTO', 'USUARIO_CREDENCIAL', 'USUARIO',
        'UNIDAD_MEDIDA', 'TIPO_PRODUCTO', 'SUCURSAL', 'ROL');
    v_paquetes t_nombres := t_nombres(
        'PKG_USUARIO', 'PKG_SEGURIDAD', 'PKG_DESPACHO', 'PKG_PRODUCCION',
        'PKG_PEDIDO', 'PKG_INVENTARIO', 'PKG_RECETA');
    v_existe NUMBER;
BEGIN
    IF USER <> 'PANADERIA' THEN
        RAISE_APPLICATION_ERROR(-20991,
            'Este script solo se ejecuta conectado como PANADERIA (ahora: ' || USER
            || '). No se borro nada.');
    END IF;

    FOR i IN 1 .. v_paquetes.COUNT LOOP
        SELECT COUNT(*) INTO v_existe FROM user_objects
         WHERE object_type = 'PACKAGE' AND object_name = v_paquetes(i);
        IF v_existe > 0 THEN
            EXECUTE IMMEDIATE 'DROP PACKAGE "' || v_paquetes(i) || '"';
            DBMS_OUTPUT.PUT_LINE('Borrado: PACKAGE ' || v_paquetes(i));
        END IF;
    END LOOP;

    FOR i IN 1 .. v_tablas.COUNT LOOP
        SELECT COUNT(*) INTO v_existe FROM user_tables WHERE table_name = v_tablas(i);
        IF v_existe > 0 THEN
            EXECUTE IMMEDIATE 'DROP TABLE "' || v_tablas(i) || '" PURGE';
            DBMS_OUTPUT.PUT_LINE('Borrado: TABLE ' || v_tablas(i));
        END IF;
    END LOOP;
END;
/

PROMPT === Objetos de la panaderia borrados del esquema PANADERIA
