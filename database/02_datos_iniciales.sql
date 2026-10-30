-- Origen: SCRIPTS_BD/FASE 1/INSERCION DE DATOS.txt
-- Verificado contra la base de datos real el 2026-10-29.
-- Corregido: se agregan los tipos de producto INSUMO y PRODUCTO_FINAL, que el
-- script original no cargaba. PKG_PEDIDO compara contra el nombre
-- 'PRODUCTO_FINAL', por lo que sin ellos no se puede pedir ningun producto.
--
-- Solo carga catalogos de referencia. Usuarios, productos, recetas e
-- inventario son datos de operacion y se registran aparte.

-- Roles
INSERT INTO ROL (nombre, descripcion)
VALUES ('ADMINISTRADOR', 'Administración general del sistema');

INSERT INTO ROL (nombre, descripcion)
VALUES ('GERENTE', 'Consulta y supervisión general');

INSERT INTO ROL (nombre, descripcion)
VALUES ('SUCURSAL', 'Gestión de pedidos de sucursal');

INSERT INTO ROL (nombre, descripcion)
VALUES ('PRODUCCION', 'Gestión de producción');

INSERT INTO ROL (nombre, descripcion)
VALUES ('BODEGA', 'Gestión de inventarios');

INSERT INTO ROL (nombre, descripcion)
VALUES ('DESPACHO', 'Gestión de despachos');

-- Sucursales
INSERT INTO SUCURSAL (nombre)
VALUES ('CENTRAL');

INSERT INTO SUCURSAL (nombre)
VALUES ('CHIVA');

INSERT INTO SUCURSAL (nombre)
VALUES ('BARCO');

INSERT INTO SUCURSAL (nombre)
VALUES ('HEYDI');

INSERT INTO SUCURSAL (nombre)
VALUES ('TERMINAL');

-- Unidades de medida
INSERT INTO UNIDAD_MEDIDA (nombre, abreviatura)
VALUES ('KILOGRAMO', 'KG');

INSERT INTO UNIDAD_MEDIDA (nombre, abreviatura)
VALUES ('LITRO', 'L');

INSERT INTO UNIDAD_MEDIDA (nombre, abreviatura)
VALUES ('UNIDAD', 'UND');

-- Tipos de producto (los paquetes dependen de estos nombres exactos)
INSERT INTO TIPO_PRODUCTO (nombre)
VALUES ('INSUMO');

INSERT INTO TIPO_PRODUCTO (nombre)
VALUES ('PRODUCTO_FINAL');

COMMIT;
