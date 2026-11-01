-- Detiene el script si NO esta conectado a Oracle Cloud (Autonomous Database).
-- Protege la base local XE: estos scripts borran o reconstruyen objetos.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
BEGIN
    IF SYS_CONTEXT('USERENV', 'CLOUD_SERVICE') IS NULL THEN
        RAISE_APPLICATION_ERROR(-20990,
            'Este script es solo para la base de Oracle Cloud. No se ejecuto nada.');
    END IF;
END;
/
