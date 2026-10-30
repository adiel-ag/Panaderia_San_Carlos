-- Origen: SCRIPTS_BD/FASE 3/PRODUCCIÓN PEDIDO.txt
-- Verificado contra la base de datos real el 2026-10-29.

CREATE TABLE PRODUCCION_PEDIDO (
    id_produccion NUMBER NOT NULL,
    id_pedido NUMBER NOT NULL,

    CONSTRAINT pk_produccion_pedido
        PRIMARY KEY (id_produccion, id_pedido),

    CONSTRAINT fk_prod_pedido_prod
        FOREIGN KEY (id_produccion)
        REFERENCES PRODUCCION(id_produccion),

    CONSTRAINT fk_prod_pedido_pedido
        FOREIGN KEY (id_pedido)
        REFERENCES PEDIDO(id_pedido)
);
