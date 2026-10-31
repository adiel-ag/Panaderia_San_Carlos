-- Productos y recetas de ejemplo para probar el ciclo completo:
-- pedido -> producción -> despacho -> inventario.
--
-- La aplicación no tiene pantallas para crear productos ni recetas, por eso
-- se cargan aquí. Usuarios e inventario inicial se cargan desde la aplicación
-- (pantallas Usuarios e Inventario).
--
-- Se puede ejecutar más de una vez: solo inserta lo que todavía no existe
-- (los nombres de producto son únicos). No modifica ni borra datos: si un
-- producto ya tiene una receta activa, se deja tal como está.
-- Si algo falla, no se guarda nada (todo se confirma al final).
--
--   cd database
--   set NLS_LANG=SPANISH_SPAIN.AL32UTF8
--   sqlplus usuario/contraseña@//host:puerto/servicio @datos_prueba/catalogo_ciclo_completo.sql
--
-- Cómo usa Oracle estos datos:
--   * Una receta dice cuántas unidades salen por tanda (rendimiento) y cuánto
--     insumo usa esa tanda. Al finalizar una producción se descuenta:
--       insumo de la receta x (unidades producidas / rendimiento).
--   * Cada producto final necesita UNA receta ACTIVA; sin ella no se puede
--     generar el plan de producción.

SET SERVEROUTPUT ON

DECLARE
    -- Los ingredientes solo se cargan en una receta recién creada por este
    -- script: nunca se modifica una receta que ya existía.
    v_receta_nueva BOOLEAN := FALSE;

    PROCEDURE producto (p_nombre VARCHAR2, p_tipo VARCHAR2, p_unidad VARCHAR2,
                        p_minimo NUMBER) IS
    BEGIN
        INSERT INTO PRODUCTO (id_tipo_producto, id_unidad, nombre, stock_minimo)
        SELECT tp.id_tipo_producto, um.id_unidad, p_nombre, p_minimo
          FROM TIPO_PRODUCTO tp, UNIDAD_MEDIDA um
         WHERE tp.nombre = p_tipo
           AND um.abreviatura = p_unidad
           AND NOT EXISTS (SELECT 1 FROM PRODUCTO WHERE nombre = p_nombre);
        DBMS_OUTPUT.PUT_LINE(RPAD(p_nombre, 24)
            || CASE SQL%ROWCOUNT WHEN 1 THEN 'creado' ELSE 'ya existía' END);
    END;

    -- Crea la receta versión 1 solo si el producto no tiene una receta activa.
    PROCEDURE receta (p_producto VARCHAR2, p_rendimiento NUMBER) IS
    BEGIN
        INSERT INTO RECETA (id_producto, version, rendimiento)
        SELECT p.id_producto, 1, p_rendimiento
          FROM PRODUCTO p
         WHERE p.nombre = p_producto
           AND NOT EXISTS (SELECT 1 FROM RECETA r
                            WHERE r.id_producto = p.id_producto AND r.estado = 'ACTIVA');
        v_receta_nueva := SQL%ROWCOUNT = 1;
        DBMS_OUTPUT.PUT_LINE('Receta ' || RPAD(p_producto, 17)
            || CASE SQL%ROWCOUNT WHEN 1 THEN 'creada' ELSE 'ya existía' END);
    END;

    PROCEDURE ingrediente (p_producto VARCHAR2, p_insumo VARCHAR2, p_cantidad NUMBER) IS
    BEGIN
        IF NOT v_receta_nueva THEN
            RETURN;
        END IF;
        INSERT INTO RECETA_DETALLE (id_receta, id_insumo, cantidad)
        SELECT r.id_receta, i.id_producto, p_cantidad
          FROM RECETA r
          JOIN PRODUCTO p ON p.id_producto = r.id_producto
          JOIN PRODUCTO i ON i.nombre = p_insumo
         WHERE p.nombre = p_producto
           AND r.estado = 'ACTIVA'
           AND NOT EXISTS (SELECT 1 FROM RECETA_DETALLE d
                            WHERE d.id_receta = r.id_receta AND d.id_insumo = i.id_producto);
        IF SQL%ROWCOUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20999, 'No se pudo agregar ' || p_insumo || ' a ' || p_producto);
        END IF;
    END;
BEGIN
    -- Insumos: nombre, tipo, unidad, existencia mínima.
    producto('Harina de trigo',     'INSUMO', 'KG',  50);
    producto('Azúcar',              'INSUMO', 'KG',  20);
    producto('Manteca vegetal',     'INSUMO', 'KG',  10);
    producto('Mantequilla',         'INSUMO', 'KG',   5);
    producto('Levadura',            'INSUMO', 'KG',   2);
    producto('Sal',                 'INSUMO', 'KG',   2);
    producto('Huevo',               'INSUMO', 'UND', 60);
    producto('Leche',               'INSUMO', 'L',   10);
    producto('Esencia de vainilla', 'INSUMO', 'L',  0.5);

    -- Productos finales (se piden por unidad).
    producto('Pan francés',    'PRODUCTO_FINAL', 'UND', 100);
    producto('Pan de manteca', 'PRODUCTO_FINAL', 'UND',  50);
    producto('Concha',         'PRODUCTO_FINAL', 'UND',  40);
    producto('Champurrada',    'PRODUCTO_FINAL', 'UND',  40);

    -- Pan francés: 100 unidades por tanda.
    receta('Pan francés', 100);
    ingrediente('Pan francés', 'Harina de trigo', 5);
    ingrediente('Pan francés', 'Levadura',        0.1);
    ingrediente('Pan francés', 'Sal',             0.1);
    ingrediente('Pan francés', 'Manteca vegetal', 0.25);

    -- Pan de manteca: 60 unidades por tanda.
    receta('Pan de manteca', 60);
    ingrediente('Pan de manteca', 'Harina de trigo', 3);
    ingrediente('Pan de manteca', 'Azúcar',          0.6);
    ingrediente('Pan de manteca', 'Manteca vegetal', 0.8);
    ingrediente('Pan de manteca', 'Levadura',        0.06);
    ingrediente('Pan de manteca', 'Huevo',           6);
    ingrediente('Pan de manteca', 'Sal',             0.03);

    -- Concha: 50 unidades por tanda.
    receta('Concha', 50);
    ingrediente('Concha', 'Harina de trigo',     2.5);
    ingrediente('Concha', 'Azúcar',              0.8);
    ingrediente('Concha', 'Mantequilla',         0.5);
    ingrediente('Concha', 'Huevo',               8);
    ingrediente('Concha', 'Leche',               0.5);
    ingrediente('Concha', 'Levadura',            0.05);
    ingrediente('Concha', 'Esencia de vainilla', 0.02);

    -- Champurrada: 40 unidades por tanda.
    receta('Champurrada', 40);
    ingrediente('Champurrada', 'Harina de trigo',     2);
    ingrediente('Champurrada', 'Azúcar',              0.7);
    ingrediente('Champurrada', 'Manteca vegetal',     0.6);
    ingrediente('Champurrada', 'Huevo',               6);
    ingrediente('Champurrada', 'Esencia de vainilla', 0.01);

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Listo. Ahora cargue el inventario inicial de los insumos desde la pantalla Inventario.');
END;
/
