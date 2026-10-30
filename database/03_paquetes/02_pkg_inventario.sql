-- Origen: SCRIPTS_BD/PKG/PKG_INVENTARIO.txt y BODY_INVENTARIO.txt
-- Verificado contra el codigo instalado en la base de datos el 2026-10-29.

CREATE OR REPLACE PACKAGE PKG_INVENTARIO AS

    FUNCTION FN_EXISTENCIA (
        p_id_producto NUMBER
    ) RETURN NUMBER;


    PROCEDURE SP_ENTRADA (
        p_id_producto   NUMBER,
        p_cantidad      NUMBER,
        p_id_usuario    NUMBER,
        p_origen        VARCHAR2,
        p_id_referencia NUMBER DEFAULT NULL,
        p_observacion   VARCHAR2 DEFAULT NULL
    );


    PROCEDURE SP_SALIDA (
        p_id_producto   NUMBER,
        p_cantidad      NUMBER,
        p_id_usuario    NUMBER,
        p_origen        VARCHAR2,
        p_id_referencia NUMBER DEFAULT NULL,
        p_observacion   VARCHAR2 DEFAULT NULL
    );

END PKG_INVENTARIO;
/

CREATE OR REPLACE PACKAGE BODY PKG_INVENTARIO AS


    FUNCTION FN_EXISTENCIA (
        p_id_producto NUMBER
    ) RETURN NUMBER
    IS
        v_existencia NUMBER;
    BEGIN

        SELECT cantidad_actual
        INTO v_existencia
        FROM INVENTARIO
        WHERE id_producto = p_id_producto;

        RETURN v_existencia;

    EXCEPTION

        WHEN NO_DATA_FOUND THEN
            RETURN 0;

    END FN_EXISTENCIA;


    PROCEDURE SP_ENTRADA (
        p_id_producto   NUMBER,
        p_cantidad      NUMBER,
        p_id_usuario    NUMBER,
        p_origen        VARCHAR2,
        p_id_referencia NUMBER DEFAULT NULL,
        p_observacion   VARCHAR2 DEFAULT NULL
    )
    IS
    BEGIN

        IF p_cantidad <= 0 THEN
            RAISE_APPLICATION_ERROR(
                -20001,
                'La cantidad de entrada debe ser mayor que cero'
            );
        END IF;


        MERGE INTO INVENTARIO i
        USING (
            SELECT p_id_producto AS id_producto
            FROM DUAL
        ) x
        ON (
            i.id_producto = x.id_producto
        )

        WHEN MATCHED THEN
            UPDATE SET
                i.cantidad_actual =
                    i.cantidad_actual + p_cantidad,
                i.fecha_actualizacion =
                    CURRENT_TIMESTAMP

        WHEN NOT MATCHED THEN
            INSERT (
                id_producto,
                cantidad_actual,
                fecha_actualizacion
            )
            VALUES (
                p_id_producto,
                p_cantidad,
                CURRENT_TIMESTAMP
            );


        INSERT INTO MOVIMIENTO_INVENTARIO (
            id_producto,
            id_usuario,
            tipo_movimiento,
            cantidad,
            origen,
            id_referencia,
            observacion
        )
        VALUES (
            p_id_producto,
            p_id_usuario,
            'ENTRADA',
            p_cantidad,
            p_origen,
            p_id_referencia,
            p_observacion
        );

    END SP_ENTRADA;


    PROCEDURE SP_SALIDA (
        p_id_producto   NUMBER,
        p_cantidad      NUMBER,
        p_id_usuario    NUMBER,
        p_origen        VARCHAR2,
        p_id_referencia NUMBER DEFAULT NULL,
        p_observacion   VARCHAR2 DEFAULT NULL
    )
    IS
        v_existencia NUMBER;
    BEGIN

        IF p_cantidad <= 0 THEN
            RAISE_APPLICATION_ERROR(
                -20002,
                'La cantidad de salida debe ser mayor que cero'
            );
        END IF;


        SELECT cantidad_actual
        INTO v_existencia
        FROM INVENTARIO
        WHERE id_producto = p_id_producto
        FOR UPDATE;


        IF v_existencia < p_cantidad THEN

            RAISE_APPLICATION_ERROR(
                -20003,
                'Inventario insuficiente'
            );

        END IF;


        UPDATE INVENTARIO
        SET
            cantidad_actual =
                cantidad_actual - p_cantidad,
            fecha_actualizacion =
                CURRENT_TIMESTAMP
        WHERE id_producto = p_id_producto;


        INSERT INTO MOVIMIENTO_INVENTARIO (
            id_producto,
            id_usuario,
            tipo_movimiento,
            cantidad,
            origen,
            id_referencia,
            observacion
        )
        VALUES (
            p_id_producto,
            p_id_usuario,
            'SALIDA',
            p_cantidad,
            p_origen,
            p_id_referencia,
            p_observacion
        );


    EXCEPTION

        WHEN NO_DATA_FOUND THEN

            RAISE_APPLICATION_ERROR(
                -20004,
                'El producto no posee inventario registrado'
            );

    END SP_SALIDA;


END PKG_INVENTARIO;
/
