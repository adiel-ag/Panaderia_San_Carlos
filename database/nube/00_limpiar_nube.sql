-- Borra TODO lo que haya en el esquema actual de Oracle Cloud (tablas, paquetes,
-- secuencias...) para empezar de cero despues de una copia fallida.
-- Solo funciona conectado a Oracle Cloud: en la base local XE se niega.
SET SERVEROUTPUT ON
@@solo_nube.sql

BEGIN
    FOR o IN (SELECT object_name, object_type FROM user_objects
               WHERE object_type IN ('TABLE', 'VIEW', 'PACKAGE', 'PROCEDURE',
                                     'FUNCTION', 'TRIGGER', 'SEQUENCE', 'TYPE', 'SYNONYM')
                 AND object_name NOT LIKE 'ISEQ$$%'
                 AND object_name NOT LIKE 'BIN$%'
               ORDER BY DECODE(object_type, 'TABLE', 1, 2)) LOOP
        BEGIN
            IF o.object_type = 'TABLE' THEN
                EXECUTE IMMEDIATE 'DROP TABLE "' || o.object_name || '" CASCADE CONSTRAINTS PURGE';
            ELSE
                EXECUTE IMMEDIATE 'DROP ' || o.object_type || ' "' || o.object_name || '"';
            END IF;
            DBMS_OUTPUT.PUT_LINE('Borrado: ' || o.object_type || ' ' || o.object_name);
        EXCEPTION
            WHEN OTHERS THEN
                -- Ya se borro junto con otro objeto (por ejemplo, un cuerpo de paquete).
                IF SQLCODE NOT IN (-942, -4043, -2289) THEN RAISE; END IF;
        END;
    END LOOP;
END;
/
PURGE RECYCLEBIN;

PROMPT === Esquema de la nube vacio
