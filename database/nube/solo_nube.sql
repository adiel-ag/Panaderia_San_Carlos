-- Detiene el script si NO esta conectado a Oracle Cloud (Autonomous Database)
-- o si el usuario no es PANADERIA. Protege la base local XE, el usuario ADMIN
-- y cualquier otro esquema: estos scripts borran o reconstruyen objetos.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
BEGIN
    IF SYS_CONTEXT('USERENV', 'CLOUD_SERVICE') IS NULL THEN
        RAISE_APPLICATION_ERROR(-20990,
            'Este script es solo para la base de Oracle Cloud. No se ejecuto nada.');
    END IF;
    IF USER <> 'PANADERIA' THEN
        RAISE_APPLICATION_ERROR(-20991,
            'Este script solo se ejecuta conectado como PANADERIA (ahora: ' || USER
            || '). No se ejecuto nada.');
    END IF;
END;
/
