-- Origen: SCRIPTS_BD/PKG/PKG_PRODUCCION.txt y BODY_PRODUCCION.txt
-- Verificado contra el codigo instalado en la base de datos el 2026-10-29.

CREATE OR REPLACE PACKAGE PKG_PRODUCCION AS

    PROCEDURE SP_CREAR (
        p_id_usuario    NUMBER,
        p_observacion   VARCHAR2 DEFAULT NULL,
        p_id_produccion OUT NUMBER
    );


    PROCEDURE SP_ASOCIAR_PEDIDO (
        p_id_produccion NUMBER,
        p_id_pedido     NUMBER
    );


    PROCEDURE SP_GENERAR_PLAN (
        p_id_produccion NUMBER
    );


    PROCEDURE SP_REGISTRAR_PRODUCIDO (
        p_id_produccion NUMBER,
        p_id_producto   NUMBER,
        p_cantidad      NUMBER
    );


    PROCEDURE SP_FINALIZAR (
        p_id_produccion NUMBER,
        p_id_usuario    NUMBER
    );


END PKG_PRODUCCION;
/

CREATE OR REPLACE PACKAGE BODY PKG_PRODUCCION AS


    PROCEDURE SP_CREAR (
        p_id_usuario    NUMBER,
        p_observacion   VARCHAR2 DEFAULT NULL,
        p_id_produccion OUT NUMBER
    )
    IS
        v_existe NUMBER;
    BEGIN

        SELECT COUNT(*)
        INTO v_existe
        FROM USUARIO
        WHERE id_usuario = p_id_usuario
          AND estado = 'ACTIVO';


        IF v_existe = 0 THEN

            RAISE_APPLICATION_ERROR(
                -20401,
                'Usuario inexistente o inactivo'
            );

        END IF;


        INSERT INTO PRODUCCION (
            id_usuario,
            estado,
            observacion
        )
        VALUES (
            p_id_usuario,
            'PLANIFICADA',
            p_observacion
        )
        RETURNING id_produccion
        INTO p_id_produccion;

    END SP_CREAR;

    PROCEDURE SP_ASOCIAR_PEDIDO (
        p_id_produccion NUMBER,
        p_id_pedido     NUMBER
    )
    IS
        v_estado_prod   PRODUCCION.estado%TYPE;
        v_estado_pedido PEDIDO.estado%TYPE;
    BEGIN

        SELECT estado
        INTO v_estado_prod
        FROM PRODUCCION
        WHERE id_produccion = p_id_produccion
        FOR UPDATE;


        IF v_estado_prod <> 'PLANIFICADA' THEN

            RAISE_APPLICATION_ERROR(
                -20402,
                'Solo pueden agregarse pedidos a una producción planificada'
            );

        END IF;


        SELECT estado
        INTO v_estado_pedido
        FROM PEDIDO
        WHERE id_pedido = p_id_pedido
        FOR UPDATE;


        IF v_estado_pedido <> 'CONFIRMADO' THEN

            RAISE_APPLICATION_ERROR(
                -20403,
                'Solo pueden producirse pedidos confirmados'
            );

        END IF;


        INSERT INTO PRODUCCION_PEDIDO (
            id_produccion,
            id_pedido
        )
        VALUES (
            p_id_produccion,
            p_id_pedido
        );


    EXCEPTION

        WHEN DUP_VAL_ON_INDEX THEN

            RAISE_APPLICATION_ERROR(
                -20404,
                'El pedido ya está asociado a esta producción'
            );

    END SP_ASOCIAR_PEDIDO;

    PROCEDURE SP_GENERAR_PLAN (
        p_id_produccion NUMBER
    )
    IS
        v_estado       PRODUCCION.estado%TYPE;
        v_cantidad     NUMBER;
        v_invalidos    NUMBER;
        v_id_receta    NUMBER;
    BEGIN

        SAVEPOINT sv_generar_plan;


        SELECT estado
        INTO v_estado
        FROM PRODUCCION
        WHERE id_produccion = p_id_produccion
        FOR UPDATE;


        IF v_estado <> 'PLANIFICADA' THEN

            RAISE_APPLICATION_ERROR(
                -20405,
                'La producción ya fue procesada'
            );

        END IF;


        SELECT COUNT(*)
        INTO v_cantidad
        FROM PRODUCCION_PEDIDO
        WHERE id_produccion = p_id_produccion;


        IF v_cantidad = 0 THEN

            RAISE_APPLICATION_ERROR(
                -20406,
                'La producción no posee pedidos'
            );

        END IF;


        SELECT COUNT(*)
        INTO v_cantidad
        FROM PRODUCCION_DETALLE
        WHERE id_produccion = p_id_produccion;


        IF v_cantidad > 0 THEN

            RAISE_APPLICATION_ERROR(
                -20407,
                'El plan de producción ya fue generado'
            );

        END IF;


        SELECT COUNT(*)
        INTO v_invalidos
        FROM PRODUCCION_PEDIDO pp
        INNER JOIN PEDIDO p
            ON p.id_pedido = pp.id_pedido
        WHERE pp.id_produccion = p_id_produccion
          AND p.estado <> 'CONFIRMADO';


        IF v_invalidos > 0 THEN

            RAISE_APPLICATION_ERROR(
                -20408,
                'Existen pedidos que ya no están confirmados'
            );

        END IF;



        FOR item IN (

            SELECT
                pd.id_producto,
                SUM(pd.cantidad) AS cantidad_total

            FROM PRODUCCION_PEDIDO pp

            INNER JOIN PEDIDO_DETALLE pd
                ON pd.id_pedido = pp.id_pedido

            WHERE pp.id_produccion =
                  p_id_produccion

            GROUP BY pd.id_producto

        )
        LOOP


            v_id_receta :=
                PKG_RECETA.FN_RECETA_ACTIVA(
                    item.id_producto
                );


            INSERT INTO PRODUCCION_DETALLE (
                id_produccion,
                id_producto,
                id_receta,
                cantidad_planificada,
                cantidad_producida
            )
            VALUES (
                p_id_produccion,
                item.id_producto,
                v_id_receta,
                item.cantidad_total,
                0
            );


        END LOOP;



        UPDATE PEDIDO
        SET estado = 'EN_PRODUCCION'
        WHERE id_pedido IN (

            SELECT id_pedido
            FROM PRODUCCION_PEDIDO
            WHERE id_produccion = p_id_produccion

        );


        UPDATE PRODUCCION
        SET estado = 'EN_PROCESO'
        WHERE id_produccion = p_id_produccion;


    EXCEPTION

        WHEN OTHERS THEN

            ROLLBACK TO sv_generar_plan;

            RAISE;

    END SP_GENERAR_PLAN;

    PROCEDURE SP_REGISTRAR_PRODUCIDO (
        p_id_produccion NUMBER,
        p_id_producto   NUMBER,
        p_cantidad      NUMBER
    )
    IS
        v_estado PRODUCCION.estado%TYPE;
    BEGIN

        IF p_cantidad <= 0 THEN

            RAISE_APPLICATION_ERROR(
                -20409,
                'La cantidad producida debe ser mayor que cero'
            );

        END IF;


        SELECT estado
        INTO v_estado
        FROM PRODUCCION
        WHERE id_produccion = p_id_produccion;


        IF v_estado <> 'EN_PROCESO' THEN

            RAISE_APPLICATION_ERROR(
                -20410,
                'La producción no se encuentra en proceso'
            );

        END IF;


        UPDATE PRODUCCION_DETALLE
        SET cantidad_producida = p_cantidad
        WHERE id_produccion = p_id_produccion
          AND id_producto = p_id_producto;


        IF SQL%ROWCOUNT = 0 THEN

            RAISE_APPLICATION_ERROR(
                -20411,
                'El producto no pertenece a esta producción'
            );

        END IF;

    END SP_REGISTRAR_PRODUCIDO;

    PROCEDURE SP_FINALIZAR (
        p_id_produccion NUMBER,
        p_id_usuario    NUMBER
    )
    IS

        v_estado       PRODUCCION.estado%TYPE;
        v_rendimiento  RECETA.rendimiento%TYPE;
        v_factor       NUMBER;

    BEGIN

        SAVEPOINT sv_finalizar;


        SELECT estado
        INTO v_estado
        FROM PRODUCCION
        WHERE id_produccion = p_id_produccion
        FOR UPDATE;


        IF v_estado <> 'EN_PROCESO' THEN

            RAISE_APPLICATION_ERROR(
                -20412,
                'La producción no se encuentra en proceso'
            );

        END IF;



        FOR prod IN (

            SELECT
                id_producto,
                id_receta,
                cantidad_planificada,
                cantidad_producida

            FROM PRODUCCION_DETALLE

            WHERE id_produccion = p_id_produccion

        )
        LOOP


            IF prod.cantidad_producida <
               prod.cantidad_planificada THEN

                RAISE_APPLICATION_ERROR(
                    -20413,
                    'La producción realizada es menor que la planificada'
                );

            END IF;



            SELECT rendimiento
            INTO v_rendimiento
            FROM RECETA
            WHERE id_receta = prod.id_receta
              AND id_producto = prod.id_producto;


            v_factor :=
                prod.cantidad_producida
                / v_rendimiento;



            FOR ingrediente IN (

                SELECT
                    id_insumo,
                    cantidad

                FROM RECETA_DETALLE

                WHERE id_receta =
                      prod.id_receta

            )
            LOOP


                PKG_INVENTARIO.SP_SALIDA(

                    p_id_producto =>
                        ingrediente.id_insumo,

                    p_cantidad =>
                        ingrediente.cantidad
                        * v_factor,

                    p_id_usuario =>
                        p_id_usuario,

                    p_origen =>
                        'PRODUCCION',

                    p_id_referencia =>
                        p_id_produccion,

                    p_observacion =>
                        'Consumo de materia prima'

                );


            END LOOP;



            PKG_INVENTARIO.SP_ENTRADA(

                p_id_producto =>
                    prod.id_producto,

                p_cantidad =>
                    prod.cantidad_producida,

                p_id_usuario =>
                    p_id_usuario,

                p_origen =>
                    'PRODUCCION',

                p_id_referencia =>
                    p_id_produccion,

                p_observacion =>
                    'Producto terminado'

            );


        END LOOP;



        UPDATE PRODUCCION
        SET estado = 'FINALIZADA'
        WHERE id_produccion = p_id_produccion;


        UPDATE PEDIDO
        SET estado = 'LISTO_DESPACHO'
        WHERE id_pedido IN (

            SELECT id_pedido
            FROM PRODUCCION_PEDIDO
            WHERE id_produccion = p_id_produccion

        );


    EXCEPTION

        WHEN OTHERS THEN

            ROLLBACK TO sv_finalizar;

            RAISE;

    END SP_FINALIZAR;


END PKG_PRODUCCION;
/
