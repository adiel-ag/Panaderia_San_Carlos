-- Reglas de acceso (paso 2: inicio de sesion).
-- Java calcula la huella PBKDF2 de la clave escrita; este paquete la compara
-- con la guardada y decide el acceso. La huella guardada nunca sale de Oracle.
-- Errores 206xx: seguridad.
--
-- Flujo de un inicio de sesion:
--   1. SP_OBTENER_PARAMETROS_CLAVE: algoritmo, iteraciones y salt del usuario.
--   2. Java calcula la huella de la clave escrita con esos parametros.
--   3. SP_VALIDAR_ACCESO: compara y registra el resultado en UNA operacion,
--      con el usuario, su rol y su credencial reservados (FOR UPDATE). Asi no
--      se puede aceptar una verificacion obsoleta: si entre 1 y 3, o mientras
--      se valida, cambian la clave, el estado del usuario o del rol, o el
--      bloqueo, la decision se toma con los datos vigentes.
--   Quien llama debe hacer COMMIT tambien cuando el resultado es
--   c_clave_incorrecta o c_cuenta_bloqueada, o el intento fallido no cuenta.

CREATE OR REPLACE PACKAGE PKG_SEGURIDAD AS

    c_max_intentos     CONSTANT PLS_INTEGER := 5;
    c_minutos_bloqueo  CONSTANT PLS_INTEGER := 15;

    -- Valores de p_resultado de SP_VALIDAR_ACCESO.
    c_acceso_correcto  CONSTANT PLS_INTEGER := 0;
    c_clave_incorrecta CONSTANT PLS_INTEGER := 1;
    c_cuenta_bloqueada CONSTANT PLS_INTEGER := 2;  -- incorrecta y alcanzo el limite


    PROCEDURE SP_ESTABLECER_CLAVE (
        p_id_usuario  NUMBER,
        p_algoritmo   VARCHAR2,
        p_iteraciones NUMBER,
        p_salt        VARCHAR2,
        p_clave_hash  VARCHAR2
    );


    PROCEDURE SP_OBTENER_PARAMETROS_CLAVE (
        p_username    VARCHAR2,
        p_algoritmo   OUT VARCHAR2,
        p_iteraciones OUT NUMBER,
        p_salt        OUT VARCHAR2
    );


    PROCEDURE SP_VALIDAR_ACCESO (
        p_username    VARCHAR2,
        p_algoritmo   VARCHAR2,
        p_iteraciones NUMBER,
        p_salt        VARCHAR2,
        p_clave_hash  VARCHAR2,
        p_id_usuario  OUT NUMBER,
        p_resultado   OUT NUMBER
    );


END PKG_SEGURIDAD;
/

CREATE OR REPLACE PACKAGE BODY PKG_SEGURIDAD AS

    -- Las marcas de tiempo del bloqueo usan SYSTIMESTAMP (hora del servidor)
    -- para que la comparacion no dependa de la zona horaria de cada sesion.

    PROCEDURE SP_ESTABLECER_CLAVE (
        p_id_usuario  NUMBER,
        p_algoritmo   VARCHAR2,
        p_iteraciones NUMBER,
        p_salt        VARCHAR2,
        p_clave_hash  VARCHAR2
    )
    IS
        v_existe NUMBER;
    BEGIN

        IF p_algoritmo IS NULL
           OR p_algoritmo <> 'PBKDF2WithHmacSHA256'
           OR p_iteraciones IS NULL
           OR p_iteraciones <= 0
           OR p_salt IS NULL
           OR p_clave_hash IS NULL THEN

            RAISE_APPLICATION_ERROR(
                -20604,
                'Datos de clave invalidos'
            );

        END IF;


        SELECT COUNT(*)
        INTO v_existe
        FROM USUARIO
        WHERE id_usuario = p_id_usuario;


        IF v_existe = 0 THEN

            RAISE_APPLICATION_ERROR(
                -20601,
                'Usuario inexistente'
            );

        END IF;


        MERGE INTO USUARIO_CREDENCIAL c
        USING (
            SELECT p_id_usuario AS id_usuario
            FROM DUAL
        ) x
        ON (
            c.id_usuario = x.id_usuario
        )

        WHEN MATCHED THEN
            UPDATE SET
                c.algoritmo         = p_algoritmo,
                c.iteraciones       = p_iteraciones,
                c.salt              = p_salt,
                c.clave_hash        = p_clave_hash,
                c.intentos_fallidos = 0,
                c.bloqueado_hasta   = NULL,
                c.fecha_cambio      = SYSTIMESTAMP

        WHEN NOT MATCHED THEN
            INSERT (
                id_usuario,
                algoritmo,
                iteraciones,
                salt,
                clave_hash,
                intentos_fallidos,
                fecha_cambio
            )
            VALUES (
                p_id_usuario,
                p_algoritmo,
                p_iteraciones,
                p_salt,
                p_clave_hash,
                0,
                SYSTIMESTAMP
            );

    END SP_ESTABLECER_CLAVE;


    PROCEDURE SP_OBTENER_PARAMETROS_CLAVE (
        p_username    VARCHAR2,
        p_algoritmo   OUT VARCHAR2,
        p_iteraciones OUT NUMBER,
        p_salt        OUT VARCHAR2
    )
    IS
    BEGIN

        -- Un mismo error para usuario inexistente, inactivo, con rol inactivo
        -- o sin clave: quien intenta entrar no debe poder distinguirlos.
        SELECT
            c.algoritmo,
            c.iteraciones,
            c.salt
        INTO
            p_algoritmo,
            p_iteraciones,
            p_salt
        FROM USUARIO u
        INNER JOIN ROL r
            ON r.id_rol = u.id_rol
        INNER JOIN USUARIO_CREDENCIAL c
            ON c.id_usuario = u.id_usuario
        WHERE u.username = p_username
          AND u.estado = 'ACTIVO'
          AND r.estado = 'ACTIVO';

    EXCEPTION

        WHEN NO_DATA_FOUND THEN

            RAISE_APPLICATION_ERROR(
                -20602,
                'Credenciales no disponibles'
            );

    END SP_OBTENER_PARAMETROS_CLAVE;


    PROCEDURE SP_VALIDAR_ACCESO (
        p_username    VARCHAR2,
        p_algoritmo   VARCHAR2,
        p_iteraciones NUMBER,
        p_salt        VARCHAR2,
        p_clave_hash  VARCHAR2,
        p_id_usuario  OUT NUMBER,
        p_resultado   OUT NUMBER
    )
    IS
        v_id_usuario      USUARIO.id_usuario%TYPE;
        v_algoritmo       USUARIO_CREDENCIAL.algoritmo%TYPE;
        v_iteraciones     USUARIO_CREDENCIAL.iteraciones%TYPE;
        v_salt            USUARIO_CREDENCIAL.salt%TYPE;
        v_clave_hash      USUARIO_CREDENCIAL.clave_hash%TYPE;
        v_intentos        USUARIO_CREDENCIAL.intentos_fallidos%TYPE;
        v_bloqueado_hasta USUARIO_CREDENCIAL.bloqueado_hasta%TYPE;
    BEGIN

        p_id_usuario := NULL;


        IF p_algoritmo IS NULL
           OR p_iteraciones IS NULL
           OR p_salt IS NULL
           OR p_clave_hash IS NULL THEN

            RAISE_APPLICATION_ERROR(
                -20604,
                'Datos de clave invalidos'
            );

        END IF;


        -- FOR UPDATE reserva hasta el COMMIT las tres filas que deciden el
        -- acceso: el usuario, su rol y su credencial. Un cambio de clave, un
        -- intento simultaneo o una desactivacion del usuario o del rol esperan
        -- su turno; si una de esas transacciones estaba en curso, Oracle
        -- repite esta consulta al liberarse la fila y ve el estado vigente
        -- (un usuario o rol recien desactivado ya no cumple el WHERE: -20602).
        -- Costo: los accesos de usuarios con el mismo rol se ordenan en fila
        -- durante los milisegundos que dura esta validacion.
        SELECT
            u.id_usuario,
            c.algoritmo,
            c.iteraciones,
            c.salt,
            c.clave_hash,
            c.intentos_fallidos,
            c.bloqueado_hasta
        INTO
            v_id_usuario,
            v_algoritmo,
            v_iteraciones,
            v_salt,
            v_clave_hash,
            v_intentos,
            v_bloqueado_hasta
        FROM USUARIO u
        INNER JOIN ROL r
            ON r.id_rol = u.id_rol
        INNER JOIN USUARIO_CREDENCIAL c
            ON c.id_usuario = u.id_usuario
        WHERE u.username = p_username
          AND u.estado = 'ACTIVO'
          AND r.estado = 'ACTIVO'
        FOR UPDATE OF u.estado, r.estado, c.intentos_fallidos;


        IF v_bloqueado_hasta > SYSTIMESTAMP THEN

            RAISE_APPLICATION_ERROR(
                -20603,
                'Cuenta bloqueada temporalmente'
            );

        END IF;


        -- La huella se calculo con parametros que ya no estan vigentes (la
        -- clave cambio entre los pasos 1 y 3). No se cuenta como intento
        -- fallido: el usuario solo debe volver a intentarlo.
        IF v_algoritmo <> p_algoritmo
           OR v_iteraciones <> p_iteraciones
           OR v_salt <> p_salt THEN

            RAISE_APPLICATION_ERROR(
                -20605,
                'La credencial cambio durante el acceso'
            );

        END IF;


        IF v_clave_hash = p_clave_hash THEN

            UPDATE USUARIO_CREDENCIAL
            SET
                intentos_fallidos = 0,
                bloqueado_hasta   = NULL,
                ultimo_acceso     = SYSTIMESTAMP
            WHERE id_usuario = v_id_usuario;

            p_id_usuario := v_id_usuario;
            p_resultado  := c_acceso_correcto;

        ELSE

            v_intentos := v_intentos + 1;

            IF v_intentos >= c_max_intentos THEN

                -- Al bloquear se reinicia el contador: al vencer el bloqueo
                -- el usuario vuelve a tener c_max_intentos oportunidades.
                UPDATE USUARIO_CREDENCIAL
                SET
                    intentos_fallidos = 0,
                    bloqueado_hasta   = SYSTIMESTAMP
                                        + NUMTODSINTERVAL(c_minutos_bloqueo, 'MINUTE')
                WHERE id_usuario = v_id_usuario;

                p_resultado := c_cuenta_bloqueada;

            ELSE

                UPDATE USUARIO_CREDENCIAL
                SET intentos_fallidos = v_intentos
                WHERE id_usuario = v_id_usuario;

                p_resultado := c_clave_incorrecta;

            END IF;

        END IF;


    EXCEPTION

        WHEN NO_DATA_FOUND THEN

            RAISE_APPLICATION_ERROR(
                -20602,
                'Credenciales no disponibles'
            );

    END SP_VALIDAR_ACCESO;


END PKG_SEGURIDAD;
/
