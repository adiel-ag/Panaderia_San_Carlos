-- Origen: SCRIPTS_BD/PKG/PKG_RECETA.txt y BODY_RECETA.txt
-- Verificado contra el codigo instalado en la base de datos el 2026-10-29.

CREATE OR REPLACE PACKAGE PKG_RECETA AS

    FUNCTION FN_RECETA_ACTIVA (
        p_id_producto NUMBER
    )
    RETURN NUMBER;

END PKG_RECETA;
/

CREATE OR REPLACE PACKAGE BODY PKG_RECETA AS

    FUNCTION FN_RECETA_ACTIVA (
        p_id_producto NUMBER
    )
    RETURN NUMBER
    IS
        v_id_receta NUMBER;
    BEGIN

        SELECT id_receta
        INTO v_id_receta
        FROM RECETA
        WHERE id_producto = p_id_producto
          AND estado = 'ACTIVA'
          AND fecha_inicio <= SYSDATE
          AND (
                fecha_fin IS NULL
                OR fecha_fin >= TRUNC(SYSDATE)
              );


        RETURN v_id_receta;


    EXCEPTION

        WHEN NO_DATA_FOUND THEN

            RAISE_APPLICATION_ERROR(
                -20201,
                'El producto no posee una receta activa'
            );


        WHEN TOO_MANY_ROWS THEN

            RAISE_APPLICATION_ERROR(
                -20202,
                'El producto posee más de una receta activa'
            );

    END FN_RECETA_ACTIVA;


END PKG_RECETA;
/
