-- Deshace la migracion 001: elimina PKG_SEGURIDAD y USUARIO_CREDENCIAL.
-- ATENCION: borra todas las contrasenas guardadas. Ninguna otra tabla ni
-- paquete depende de estos objetos, por lo que el resto de la base no cambia.

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET ECHO OFF

PROMPT === Eliminando PKG_SEGURIDAD
DROP PACKAGE PKG_SEGURIDAD;

PROMPT === Eliminando USUARIO_CREDENCIAL
DROP TABLE USUARIO_CREDENCIAL;

PROMPT === Migracion 001 revertida
EXIT SUCCESS
