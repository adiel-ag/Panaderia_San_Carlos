-- Migracion 003: vuelve opcional la columna heredada USUARIO.PASSWORD_HASH.
-- Algunas bases instaladas con los scripts originales tienen esa columna como
-- NOT NULL con un mismo texto de relleno en todos los usuarios. Nada la usa:
-- las contrasenas viven en USUARIO_CREDENCIAL (migracion 001). Como
-- PKG_USUARIO.SP_CREAR_USUARIO no la llena, el INSERT falla con ORA-01400.
-- Esta migracion solo quita el NOT NULL: conserva los valores existentes y
-- los usuarios nuevos la dejan vacia. Si la columna no existe (base creada
-- con instalar.sql) o ya es opcional, no cambia nada.
-- Ejecutar DESDE LA CARPETA database. Para deshacerla:
-- 003_password_hash_opcional_revertir.sql.

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET ECHO OFF
SET SERVEROUTPUT ON

PROMPT === Columna USUARIO.PASSWORD_HASH
DECLARE
    v_nullable user_tab_columns.nullable%TYPE;
BEGIN
    SELECT nullable
      INTO v_nullable
      FROM user_tab_columns
     WHERE table_name = 'USUARIO'
       AND column_name = 'PASSWORD_HASH';

    IF v_nullable = 'N' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE USUARIO MODIFY (password_hash NULL)';
        DBMS_OUTPUT.PUT_LINE('PASSWORD_HASH ahora es opcional.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('PASSWORD_HASH ya era opcional: sin cambios.');
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('La columna PASSWORD_HASH no existe: sin cambios.');
END;
/

PROMPT === Verificacion
DECLARE
    v_obligatoria NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_obligatoria
      FROM user_tab_columns
     WHERE table_name = 'USUARIO'
       AND column_name = 'PASSWORD_HASH'
       AND nullable = 'N';

    IF v_obligatoria > 0 THEN
        RAISE_APPLICATION_ERROR(-20999, 'Migracion incompleta: PASSWORD_HASH sigue siendo NOT NULL');
    END IF;
END;
/

PROMPT === Migracion 003 aplicada: PASSWORD_HASH opcional
EXIT SUCCESS
