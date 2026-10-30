-- Migracion 002: administracion de usuarios.
-- Ejecutar DESDE LA CARPETA database; solo crea PKG_USUARIO.

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET ECHO OFF
SET DEFINE OFF
SET SQLBLANKLINES ON

PROMPT === Paquete PKG_USUARIO
@03_paquetes/07_pkg_usuario.sql
SHOW ERRORS PACKAGE BODY PKG_USUARIO

PROMPT === Verificacion
DECLARE
    v_problemas VARCHAR2(4000);
BEGIN
    FOR esperado IN (
        SELECT SUBSTR(column_value, 1, INSTR(column_value, ':') - 1) AS tipo,
               SUBSTR(column_value, INSTR(column_value, ':') + 1)    AS nombre
          FROM TABLE(sys.odcivarchar2list(
               'PACKAGE:PKG_USUARIO', 'PACKAGE BODY:PKG_USUARIO'))
         WHERE NOT EXISTS (
               SELECT 1 FROM user_objects o
                WHERE o.object_type = SUBSTR(column_value, 1, INSTR(column_value, ':') - 1)
                  AND o.object_name = SUBSTR(column_value, INSTR(column_value, ':') + 1)
                  AND o.status = 'VALID')
    ) LOOP
        v_problemas := v_problemas || CHR(10) || '  falta o invalido: '
                       || esperado.tipo || ' ' || esperado.nombre;
    END LOOP;

    IF v_problemas IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20999, 'Migracion incompleta:' || v_problemas);
    END IF;
END;
/

PROMPT === Migracion 002 aplicada: PKG_USUARIO valido
EXIT SUCCESS
