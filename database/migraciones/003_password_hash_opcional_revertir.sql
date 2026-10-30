-- Deshace la migracion 003: vuelve a exigir USUARIO.PASSWORD_HASH (NOT NULL).
-- Solo es posible si ningun usuario la tiene vacia. Los usuarios creados desde
-- la API la dejan vacia, y aqui no se inventa un valor para ellos: si hay
-- alguno, el script se detiene sin cambiar nada y lo informa.

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET ECHO OFF
SET SERVEROUTPUT ON

PROMPT === Columna USUARIO.PASSWORD_HASH
DECLARE
    v_nullable user_tab_columns.nullable%TYPE;
    v_vacios   NUMBER;
BEGIN
    SELECT nullable
      INTO v_nullable
      FROM user_tab_columns
     WHERE table_name = 'USUARIO'
       AND column_name = 'PASSWORD_HASH';

    IF v_nullable = 'N' THEN
        DBMS_OUTPUT.PUT_LINE('PASSWORD_HASH ya es NOT NULL: sin cambios.');
        RETURN;
    END IF;

    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM USUARIO WHERE password_hash IS NULL'
        INTO v_vacios;

    IF v_vacios > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20999,
            'No se puede revertir: ' || v_vacios
            || ' usuario(s) tienen PASSWORD_HASH vacio'
        );
    END IF;

    EXECUTE IMMEDIATE 'ALTER TABLE USUARIO MODIFY (password_hash NOT NULL)';
    DBMS_OUTPUT.PUT_LINE('PASSWORD_HASH vuelve a ser NOT NULL.');
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('La columna PASSWORD_HASH no existe: sin cambios.');
END;
/

PROMPT === Migracion 003 revertida
EXIT SUCCESS
