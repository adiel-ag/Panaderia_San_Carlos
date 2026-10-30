-- Administracion de usuarios (paso 3). Las escrituras pasan por este paquete;
-- quien llama controla la transaccion y hace COMMIT o ROLLBACK.
-- Errores 207xx: datos, rol, sucursal, usuario y ultimo administrador.

CREATE OR REPLACE PACKAGE PKG_USUARIO AS

    PROCEDURE SP_CREAR_USUARIO (
        p_nombre      VARCHAR2,
        p_username    VARCHAR2,
        p_id_rol      NUMBER,
        p_id_sucursal NUMBER,
        p_id_usuario  OUT NUMBER
    );

    PROCEDURE SP_ACTUALIZAR_USUARIO (
        p_id_usuario  NUMBER,
        p_nombre      VARCHAR2,
        p_id_rol      NUMBER,
        p_id_sucursal NUMBER
    );

    PROCEDURE SP_CAMBIAR_ESTADO (
        p_id_usuario NUMBER,
        p_estado     VARCHAR2
    );

    PROCEDURE SP_DESBLOQUEAR (
        p_id_usuario NUMBER
    );

END PKG_USUARIO;
/

CREATE OR REPLACE PACKAGE BODY PKG_USUARIO AS

    PROCEDURE VALIDAR_NOMBRE (p_nombre VARCHAR2) IS
    BEGIN
        IF TRIM(p_nombre) IS NULL OR LENGTH(TRIM(p_nombre)) > 100 THEN
            RAISE_APPLICATION_ERROR(-20701, 'Datos de usuario invalidos');
        END IF;
    END VALIDAR_NOMBRE;

    PROCEDURE VALIDAR_ROL_SUCURSAL (
        p_id_rol      NUMBER,
        p_id_sucursal NUMBER,
        p_nombre_rol  OUT VARCHAR2
    ) IS
        v_existe NUMBER;
    BEGIN
        SELECT nombre INTO p_nombre_rol
          FROM ROL
         WHERE id_rol = p_id_rol AND estado = 'ACTIVO';

        IF p_nombre_rol = 'SUCURSAL' THEN
            IF p_id_sucursal IS NULL THEN
                RAISE_APPLICATION_ERROR(-20704, 'La sucursal no corresponde al rol');
            END IF;
            SELECT COUNT(*) INTO v_existe
              FROM SUCURSAL
             WHERE id_sucursal = p_id_sucursal AND estado = 'ACTIVO';
            IF v_existe = 0 THEN
                RAISE_APPLICATION_ERROR(-20704, 'La sucursal no corresponde al rol');
            END IF;
        ELSIF p_id_sucursal IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(-20704, 'La sucursal no corresponde al rol');
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20703, 'Rol inexistente o inactivo');
    END VALIDAR_ROL_SUCURSAL;

    PROCEDURE VALIDAR_ULTIMO_ADMIN (p_id_usuario NUMBER) IS
        v_otros NUMBER := 0;
    BEGIN
        -- Primero se reserva el usuario objetivo; despues todo el conjunto de
        -- administradores activos. El bloqueo del conjunto serializa las
        -- bajas simultaneas de dos administradores que intentan quitarse
        -- entre si; si ya reservaron filas opuestas, Oracle detecta el
        -- interbloqueo y quien llama revierte y responde conflicto.
        FOR admin IN (
            SELECT u.id_usuario
              FROM USUARIO u
              JOIN ROL r ON r.id_rol = u.id_rol
             WHERE r.nombre = 'ADMINISTRADOR'
               AND u.estado = 'ACTIVO'
               AND r.estado = 'ACTIVO'
             FOR UPDATE OF u.estado
        ) LOOP
            IF admin.id_usuario <> p_id_usuario THEN
                v_otros := v_otros + 1;
            END IF;
        END LOOP;
        IF v_otros = 0 THEN
            RAISE_APPLICATION_ERROR(-20706, 'No se puede quitar el ultimo administrador activo');
        END IF;
    END VALIDAR_ULTIMO_ADMIN;

    PROCEDURE SP_CREAR_USUARIO (
        p_nombre      VARCHAR2,
        p_username    VARCHAR2,
        p_id_rol      NUMBER,
        p_id_sucursal NUMBER,
        p_id_usuario  OUT NUMBER
    ) IS
        v_existe NUMBER;
        v_rol ROL.nombre%TYPE;
    BEGIN
        VALIDAR_NOMBRE(p_nombre);
        -- Caracteres listados uno a uno y 'c' (distingue mayusculas): un rango
        -- como a-z depende de NLS_SORT y con orden linguistico admitiria
        -- letras acentuadas o mayusculas.
        IF p_username IS NULL
           OR NOT REGEXP_LIKE(p_username,
                  '^[abcdefghijklmnopqrstuvwxyz0123456789.-]{3,50}$', 'c') THEN
            RAISE_APPLICATION_ERROR(-20701, 'Datos de usuario invalidos');
        END IF;

        SELECT COUNT(*) INTO v_existe FROM USUARIO WHERE username = p_username;
        IF v_existe > 0 THEN
            RAISE_APPLICATION_ERROR(-20702, 'El usuario ya existe');
        END IF;
        VALIDAR_ROL_SUCURSAL(p_id_rol, p_id_sucursal, v_rol);

        INSERT INTO USUARIO (id_rol, id_sucursal, nombre, username)
        VALUES (p_id_rol, p_id_sucursal, TRIM(p_nombre), p_username)
        RETURNING id_usuario INTO p_id_usuario;
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN
            RAISE_APPLICATION_ERROR(-20702, 'El usuario ya existe');
    END SP_CREAR_USUARIO;

    PROCEDURE SP_ACTUALIZAR_USUARIO (
        p_id_usuario  NUMBER,
        p_nombre      VARCHAR2,
        p_id_rol      NUMBER,
        p_id_sucursal NUMBER
    ) IS
        v_rol_actual ROL.nombre%TYPE;
        v_estado USUARIO.estado%TYPE;
        v_rol_nuevo ROL.nombre%TYPE;
    BEGIN
        VALIDAR_NOMBRE(p_nombre);
        -- Orden de bloqueo: usuario objetivo, luego administradores activos.
        SELECT r.nombre, u.estado INTO v_rol_actual, v_estado
          FROM USUARIO u JOIN ROL r ON r.id_rol = u.id_rol
         WHERE u.id_usuario = p_id_usuario
         FOR UPDATE OF u.estado;
        VALIDAR_ROL_SUCURSAL(p_id_rol, p_id_sucursal, v_rol_nuevo);
        IF v_estado = 'ACTIVO' AND v_rol_actual = 'ADMINISTRADOR'
           AND v_rol_nuevo <> 'ADMINISTRADOR' THEN
            VALIDAR_ULTIMO_ADMIN(p_id_usuario);
        END IF;
        UPDATE USUARIO
           SET nombre = TRIM(p_nombre), id_rol = p_id_rol,
               id_sucursal = p_id_sucursal
         WHERE id_usuario = p_id_usuario;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20705, 'Usuario inexistente');
    END SP_ACTUALIZAR_USUARIO;

    PROCEDURE SP_CAMBIAR_ESTADO (
        p_id_usuario NUMBER,
        p_estado     VARCHAR2
    ) IS
        v_rol ROL.nombre%TYPE;
        v_estado USUARIO.estado%TYPE;
    BEGIN
        IF p_estado IS NULL OR p_estado NOT IN ('ACTIVO', 'INACTIVO') THEN
            RAISE_APPLICATION_ERROR(-20701, 'Datos de usuario invalidos');
        END IF;
        -- Orden de bloqueo: usuario objetivo, luego administradores activos.
        SELECT r.nombre, u.estado INTO v_rol, v_estado
          FROM USUARIO u JOIN ROL r ON r.id_rol = u.id_rol
         WHERE u.id_usuario = p_id_usuario
         FOR UPDATE OF u.estado;
        IF v_estado = 'ACTIVO' AND v_rol = 'ADMINISTRADOR'
           AND p_estado = 'INACTIVO' THEN
            VALIDAR_ULTIMO_ADMIN(p_id_usuario);
        END IF;
        UPDATE USUARIO SET estado = p_estado WHERE id_usuario = p_id_usuario;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20705, 'Usuario inexistente');
    END SP_CAMBIAR_ESTADO;

    PROCEDURE SP_DESBLOQUEAR (p_id_usuario NUMBER) IS
        v_existe NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_existe FROM USUARIO WHERE id_usuario = p_id_usuario;
        IF v_existe = 0 THEN
            RAISE_APPLICATION_ERROR(-20705, 'Usuario inexistente');
        END IF;
        UPDATE USUARIO_CREDENCIAL
           SET intentos_fallidos = 0, bloqueado_hasta = NULL
         WHERE id_usuario = p_id_usuario;
        IF SQL%ROWCOUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20707, 'El usuario no tiene contrasena asignada');
        END IF;
    END SP_DESBLOQUEAR;

END PKG_USUARIO;
/
