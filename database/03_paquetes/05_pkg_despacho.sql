-- Origen: SCRIPTS_BD/PKG/PKG_DESPACHO.txt y BODY_DESPACHO.txt
-- Verificado contra el codigo instalado en la base de datos el 2026-10-29.

CREATE OR REPLACE PACKAGE PKG_DESPACHO AS

    PROCEDURE SP_DESPACHAR_PEDIDO (
        p_id_pedido   NUMBER,
        p_id_usuario  NUMBER,
        p_observacion VARCHAR2 DEFAULT NULL
    );

END PKG_DESPACHO;
/

CREATE OR REPLACE PACKAGE BODY PKG_DESPACHO AS


    PROCEDURE SP_DESPACHAR_PEDIDO (
        p_id_pedido   NUMBER,
        p_id_usuario  NUMBER,
        p_observacion VARCHAR2 DEFAULT NULL
    )
    IS

        v_estado      PEDIDO.estado%TYPE;
        v_id_despacho NUMBER;

    BEGIN

        SAVEPOINT sv_despacho;


        SELECT estado
        INTO v_estado
        FROM PEDIDO
        WHERE id_pedido = p_id_pedido
        FOR UPDATE;


        IF v_estado <> 'LISTO_DESPACHO' THEN

            RAISE_APPLICATION_ERROR(
                -20501,
                'El pedido aún no está listo para despacho'
            );

        END IF;



        INSERT INTO DESPACHO (
            id_pedido,
            id_usuario,
            estado,
            observacion
        )
        VALUES (
            p_id_pedido,
            p_id_usuario,
            'PENDIENTE',
            p_observacion
        )
        RETURNING id_despacho
        INTO v_id_despacho;



        FOR item IN (

            SELECT
                id_producto,
                cantidad

            FROM PEDIDO_DETALLE

            WHERE id_pedido = p_id_pedido

        )
        LOOP


            PKG_INVENTARIO.SP_SALIDA(

                p_id_producto =>
                    item.id_producto,

                p_cantidad =>
                    item.cantidad,

                p_id_usuario =>
                    p_id_usuario,

                p_origen =>
                    'DESPACHO',

                p_id_referencia =>
                    v_id_despacho,

                p_observacion =>
                    'Despacho hacia sucursal'

            );


        END LOOP;



        UPDATE DESPACHO
        SET
            estado = 'DESPACHADO',
            fecha_despacho = CURRENT_TIMESTAMP
        WHERE id_despacho = v_id_despacho;


        UPDATE PEDIDO
        SET estado = 'DESPACHADO'
        WHERE id_pedido = p_id_pedido;


    EXCEPTION

        WHEN DUP_VAL_ON_INDEX THEN

            ROLLBACK TO sv_despacho;

            RAISE_APPLICATION_ERROR(
                -20502,
                'El pedido ya posee un despacho'
            );


        WHEN OTHERS THEN

            ROLLBACK TO sv_despacho;

            RAISE;

    END SP_DESPACHAR_PEDIDO;


END PKG_DESPACHO;
/
