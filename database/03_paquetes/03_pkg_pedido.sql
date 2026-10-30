-- Origen: SCRIPTS_BD/PKG/PKG_PEDIDO.txt y BODY_PEDIDO.txt
-- Verificado contra el codigo instalado en la base de datos el 2026-10-29.

CREATE OR REPLACE PACKAGE PKG_PEDIDO AS

    PROCEDURE SP_CREAR_PEDIDO (
        p_id_sucursal   NUMBER,
        p_id_usuario    NUMBER,
        p_fecha_entrega DATE,
        p_observacion   VARCHAR2 DEFAULT NULL,
        p_id_pedido     OUT NUMBER
    );

    PROCEDURE SP_AGREGAR_DETALLE (
        p_id_pedido   NUMBER,
        p_id_producto NUMBER,
        p_cantidad    NUMBER
    );

    PROCEDURE SP_CONFIRMAR_PEDIDO (
        p_id_pedido NUMBER
    );

    PROCEDURE SP_CANCELAR_PEDIDO (
        p_id_pedido NUMBER
    );

END PKG_PEDIDO;
/

CREATE OR REPLACE PACKAGE BODY PKG_PEDIDO AS


    PROCEDURE SP_CREAR_PEDIDO (
        p_id_sucursal   NUMBER,
        p_id_usuario    NUMBER,
        p_fecha_entrega DATE,
        p_observacion   VARCHAR2 DEFAULT NULL,
        p_id_pedido     OUT NUMBER
    )
    IS
        v_rol               ROL.nombre%TYPE;
        v_sucursal_usuario  USUARIO.id_sucursal%TYPE;
        v_estado_sucursal   SUCURSAL.estado%TYPE;
    BEGIN

        -- Verificar usuario activo
        SELECT
            r.nombre,
            u.id_sucursal
        INTO
            v_rol,
            v_sucursal_usuario
        FROM USUARIO u
        INNER JOIN ROL r
            ON r.id_rol = u.id_rol
        WHERE u.id_usuario = p_id_usuario
          AND u.estado = 'ACTIVO';


        -- Verificar sucursal
        SELECT estado
        INTO v_estado_sucursal
        FROM SUCURSAL
        WHERE id_sucursal = p_id_sucursal;


        IF v_estado_sucursal <> 'ACTIVO' THEN
            RAISE_APPLICATION_ERROR(
                -20301,
                'La sucursal se encuentra inactiva'
            );
        END IF;


        -- Un usuario SUCURSAL solo puede pedir para su sucursal
        IF v_rol = 'SUCURSAL'
           AND NVL(v_sucursal_usuario, -1) <> p_id_sucursal THEN

            RAISE_APPLICATION_ERROR(
                -20302,
                'El usuario no pertenece a esta sucursal'
            );

        END IF;


        IF p_fecha_entrega < TRUNC(SYSDATE) THEN

            RAISE_APPLICATION_ERROR(
                -20303,
                'La fecha de entrega no puede estar en el pasado'
            );

        END IF;


        INSERT INTO PEDIDO (
            id_sucursal,
            id_usuario,
            fecha_entrega,
            estado,
            observacion
        )
        VALUES (
            p_id_sucursal,
            p_id_usuario,
            p_fecha_entrega,
            'PENDIENTE',
            p_observacion
        )
        RETURNING id_pedido INTO p_id_pedido;


    EXCEPTION

        WHEN NO_DATA_FOUND THEN

            RAISE_APPLICATION_ERROR(
                -20304,
                'Usuario o sucursal no encontrados'
            );

    END SP_CREAR_PEDIDO;



    PROCEDURE SP_AGREGAR_DETALLE (
        p_id_pedido   NUMBER,
        p_id_producto NUMBER,
        p_cantidad    NUMBER
    )
    IS
        v_estado_pedido   PEDIDO.estado%TYPE;
        v_tipo_producto   TIPO_PRODUCTO.nombre%TYPE;
        v_estado_producto PRODUCTO.estado%TYPE;
    BEGIN

        IF p_cantidad <= 0 THEN
            RAISE_APPLICATION_ERROR(
                -20305,
                'La cantidad debe ser mayor que cero'
            );
        END IF;


        SELECT estado
        INTO v_estado_pedido
        FROM PEDIDO
        WHERE id_pedido = p_id_pedido
        FOR UPDATE;


        IF v_estado_pedido <> 'PENDIENTE' THEN

            RAISE_APPLICATION_ERROR(
                -20306,
                'Solo se pueden modificar pedidos pendientes'
            );

        END IF;


        SELECT
            tp.nombre,
            p.estado
        INTO
            v_tipo_producto,
            v_estado_producto
        FROM PRODUCTO p
        INNER JOIN TIPO_PRODUCTO tp
            ON tp.id_tipo_producto = p.id_tipo_producto
        WHERE p.id_producto = p_id_producto;


        IF v_tipo_producto <> 'PRODUCTO_FINAL' THEN

            RAISE_APPLICATION_ERROR(
                -20307,
                'Solo pueden solicitarse productos finales'
            );

        END IF;


        IF v_estado_producto <> 'ACTIVO' THEN

            RAISE_APPLICATION_ERROR(
                -20308,
                'El producto se encuentra inactivo'
            );

        END IF;


        INSERT INTO PEDIDO_DETALLE (
            id_pedido,
            id_producto,
            cantidad
        )
        VALUES (
            p_id_pedido,
            p_id_producto,
            p_cantidad
        );


    EXCEPTION

        WHEN DUP_VAL_ON_INDEX THEN

            RAISE_APPLICATION_ERROR(
                -20309,
                'El producto ya fue agregado al pedido'
            );

        WHEN NO_DATA_FOUND THEN

            RAISE_APPLICATION_ERROR(
                -20310,
                'Pedido o producto no encontrado'
            );

    END SP_AGREGAR_DETALLE;



    PROCEDURE SP_CONFIRMAR_PEDIDO (
        p_id_pedido NUMBER
    )
    IS
        v_estado   PEDIDO.estado%TYPE;
        v_detalles NUMBER;
    BEGIN

        SELECT estado
        INTO v_estado
        FROM PEDIDO
        WHERE id_pedido = p_id_pedido
        FOR UPDATE;


        IF v_estado <> 'PENDIENTE' THEN

            RAISE_APPLICATION_ERROR(
                -20311,
                'Solo se pueden confirmar pedidos pendientes'
            );

        END IF;


        SELECT COUNT(*)
        INTO v_detalles
        FROM PEDIDO_DETALLE
        WHERE id_pedido = p_id_pedido;


        IF v_detalles = 0 THEN

            RAISE_APPLICATION_ERROR(
                -20312,
                'No se puede confirmar un pedido sin productos'
            );

        END IF;


        UPDATE PEDIDO
        SET estado = 'CONFIRMADO'
        WHERE id_pedido = p_id_pedido;

    END SP_CONFIRMAR_PEDIDO;



    PROCEDURE SP_CANCELAR_PEDIDO (
        p_id_pedido NUMBER
    )
    IS
        v_estado       PEDIDO.estado%TYPE;
        v_producciones NUMBER;
    BEGIN

        SELECT estado
        INTO v_estado
        FROM PEDIDO
        WHERE id_pedido = p_id_pedido
        FOR UPDATE;


        IF v_estado NOT IN ('PENDIENTE', 'CONFIRMADO') THEN

            RAISE_APPLICATION_ERROR(
                -20313,
                'El pedido ya no puede cancelarse'
            );

        END IF;


        SELECT COUNT(*)
        INTO v_producciones
        FROM PRODUCCION_PEDIDO
        WHERE id_pedido = p_id_pedido;


        IF v_producciones > 0 THEN

            RAISE_APPLICATION_ERROR(
                -20314,
                'El pedido ya está asociado a una producción'
            );

        END IF;


        UPDATE PEDIDO
        SET estado = 'CANCELADO'
        WHERE id_pedido = p_id_pedido;

    END SP_CANCELAR_PEDIDO;


END PKG_PEDIDO;
/
