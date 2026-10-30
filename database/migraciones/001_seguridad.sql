-- Migracion 001: inicio de sesion (paso 2).
-- Aplica a una base YA instalada solo lo nuevo: la tabla USUARIO_CREDENCIAL y
-- el paquete PKG_SEGURIDAD. No modifica tablas, datos ni paquetes existentes.
-- Ejecutar DESDE LA CARPETA database (ver README.md): las rutas de abajo son
-- relativas a ella. Se usa @ y no @@ porque @@ con '../' no se resuelve de
-- forma fiable desde la carpeta del script. Para deshacerla:
-- 001_seguridad_revertir.sql.

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET ECHO OFF
SET DEFINE OFF
-- Los scripts de tablas tienen lineas en blanco entre restricciones; sin esto
-- SQL*Plus corta la sentencia en la primera linea en blanco y no crea la tabla.
SET SQLBLANKLINES ON

PROMPT === Tabla USUARIO_CREDENCIAL
-- Si la tabla ya existe, CREATE TABLE falla (ORA-00955) y la migracion se
-- detiene antes de tocar el paquete.
@01_tablas/17_usuario_credencial.sql

PROMPT === Paquete PKG_SEGURIDAD
@03_paquetes/06_pkg_seguridad.sql
SHOW ERRORS PACKAGE BODY PKG_SEGURIDAD

PROMPT === Verificacion
DECLARE
    v_problemas VARCHAR2(4000);
BEGIN
    FOR esperado IN (
        SELECT SUBSTR(column_value, 1, INSTR(column_value, ':') - 1) AS tipo,
               SUBSTR(column_value, INSTR(column_value, ':') + 1)    AS nombre
          FROM TABLE(sys.odcivarchar2list(
               'TABLE:USUARIO_CREDENCIAL',
               'PACKAGE:PKG_SEGURIDAD', 'PACKAGE BODY:PKG_SEGURIDAD'))
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

PROMPT === Migracion 001 aplicada: USUARIO_CREDENCIAL y PKG_SEGURIDAD validos
EXIT SUCCESS
