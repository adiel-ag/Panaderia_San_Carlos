# Recetas: plan de análisis y diseño

**Estado:** diagnóstico de integración verificado el 31 de octubre de 2026; catálogo real conservado por decisión del usuario.  
**Referencia:** código actual, `database/README.md`, captura aportada el 31 de octubre de 2026 y reportes visuales en `.taste-skill/recetas/`. No se encontró un plan escrito previo en el repositorio; los reportes son referencia de las pantallas ya diseñadas.

## 1. Propósito y alcance

Dejar utilizable el módulo de Recetas de Panadería San Carlos: consultar qué productos finales pueden producirse, revisar su historial y versiones, y crear las recetas que faltan además de la de ESPECIAL. La prioridad inmediata es diagnosticar y resolver el error de carga que muestra la captura.

El usuario confirmó que desea gestionar las recetas del resto de productos y autorizó incluir la gestión de insumos si hace falta. **Decisión de diseño propuesta:** incorporar un catálogo sencillo de productos finales e insumos. Hoy no hay API para dar de alta ninguno; sin esa capacidad, una receta nueva depende de que los productos y cada insumo ya se hayan cargado por SQL. El alcance funcional de este catálogo es alta, consulta y corrección de nombre y stock mínimo; el cambio de tipo o unidad de un producto usado y la desactivación con dependencias requieren reglas de negocio adicionales y quedan fuera de esta primera entrega.

El usuario eligió usar `database/datos_prueba/catalogo_ciclo_completo.sql` como **referencia inicial**. La consulta de Oracle reveló que sus cuatro productos y varios insumos no coinciden con el catálogo real. El usuario decidió conservar el catálogo real y esperar las fórmulas de sus productos. Por tanto, las cantidades siguientes sirven solo para entender el formato de una receta; no se cargarán como datos de negocio ni se asignarán por semejanza de nombre.

| Producto | Rinde por tanda | Ingredientes de referencia por tanda |
| --- | ---: | --- |
| Pan francés | 100 UND | Harina de trigo 5 KG; levadura 0.1 KG; sal 0.1 KG; manteca vegetal 0.25 KG |
| Pan de manteca | 60 UND | Harina de trigo 3 KG; azúcar 0.6 KG; manteca vegetal 0.8 KG; levadura 0.06 KG; huevo 6 UND; sal 0.03 KG |
| Concha | 50 UND | Harina de trigo 2.5 KG; azúcar 0.8 KG; mantequilla 0.5 KG; huevo 8 UND; leche 0.5 L; levadura 0.05 KG; esencia de vainilla 0.02 L |
| Champurrada | 40 UND | Harina de trigo 2 KG; azúcar 0.7 KG; manteca vegetal 0.6 KG; huevo 6 UND; esencia de vainilla 0.01 L |

Para cada producto pendiente se necesita: nombre del producto final, unidad de venta, rendimiento por tanda, y por cada ingrediente su nombre, unidad y cantidad consumida en esa tanda. Si el producto o ingrediente no existe, el administrador lo registrará primero en el catálogo. El stock mínimo puede definirse allí y no forma parte de la fórmula.

No se modifica el ciclo de pedidos, producción, inventario o despacho salvo por una incompatibilidad demostrada con las recetas. Tampoco se convierte el historial en edición o borrado de versiones: `PRODUCCION_DETALLE.id_receta` conserva la versión utilizada.

## 2. Estado encontrado

- Existen tres pantallas: `RecetasView.vue` (listado y filtros), `RecetaView.vue` (detalle e historial) y `NuevaRecetaView.vue` (creación de la primera receta o una nueva versión).
- El listado solo contiene productos finales que ya existen en `PRODUCTO`; por tanto, tampoco permite descubrir ni crear productos nuevos desde esa vista.
- `GET /api/recetas` lista un renglón por producto final activo. `GET /api/recetas/{idReceta}` consulta un detalle, `GET /api/recetas/productos/{idProducto}/versiones` consulta el historial y `POST /api/recetas` guarda una versión.
- `ADMINISTRADOR` y `PRODUCCION` pueden guardar; `GERENTE` consulta. El frontend restringe navegación y la API exige los roles.
- El servidor crea versiones en una transacción: bloquea el producto, comprueba `versionBase`, desactiva la vigente e inserta la nueva con sus ingredientes. La producción sigue apuntando a la versión anterior que usó.
- La captura ocultaba el código HTTP. Al verificar con sesión iniciada, Tomcat devolvía 404 para `GET /api/recetas`, aunque el código y la carpeta expandida ya contenían esa ruta. Tomcat había arrancado a las 00:47 y las clases se recompilaron después, a las 09:17; conservaba la versión antigua cargada. Tras empaquetar y arrancar Tomcat nuevamente, `GET /api/health`, el inicio de sesión y `GET /api/recetas` devolvieron 200. El listado real trae 9 productos finales: ESPECIAL tiene una versión y 8 carecen de receta. Hay 10 insumos activos.
- El primer intento de `mvn package` falló en `npm ci` con `ENOTEMPTY` al limpiar `frontend/node_modules/primeicons/raw-svg`. El reintento, sin Vite escuchando en 5173, instaló 220 paquetes, compiló Vue y generó el WAR. Pasaron 179 pruebas Java y 368 de Vue. No fue un error de conexión de Oracle.
- El catálogo real contiene ALEMAN, BATIDAS, CACHOS, CAMPECHANAS, CORTADAS, CUBOS, ESPECIAL, FRANCES y SHECAS; los insumos existentes también difieren de los del script de ejemplo. No se debe ejecutar ese script sobre el esquema real para completar estas ocho recetas.
- Las pruebas de la vista usan una respuesta simulada. `RecetaDAOTest` inspecciona el SQL generado con conexiones falsas; no ejecuta el listado en Oracle. La consulta real sigue pendiente de verificación.
- Los reportes `.taste-skill/recetas/{lista,detalle,nueva}` registran revisiones previas de las tres pantallas en tamaños de escritorio, tableta y móvil. Sirven para conservar el diseño existente, no para dar por verificado el error actual.

## 3. Plan de análisis

### A. Reproducir y ubicar el fallo

1. Con Tomcat y Oracle disponibles, abrir `#/recetas` con una cuenta autorizada y registrar el resultado de `GET /api/recetas`: estado HTTP, cuerpo JSON seguro y hora de la petición. No registrar cookies ni contraseñas.
2. Comparar con `GET /api/health` y `GET /api/auth/me` para distinguir indisponibilidad del servidor, sesión/permisos y fallo de consulta.
3. Si la API responde 500 o 503, leer la excepción del servidor y ejecutar la consulta de listado en el esquema Oracle correspondiente, sin modificar datos. Verificar objetos y columnas requeridas: `PRODUCTO`, `TIPO_PRODUCTO`, `UNIDAD_MEDIDA`, `RECETA` y `RECETA_DETALLE`.
4. Revisar configuración de conexión y despliegue del WAR solo si la evidencia sitúa allí el fallo. Comparar la versión desplegada con el código fuente actual.
5. Documentar una causa raíz única y una reproducción mínima antes de decidir el cambio. Un 401/403, un 503 de conexión y una excepción SQL requieren soluciones distintas.

### B. Verificar el contrato y el flujo

1. Confirmar que el listado entrega productos finales activos con estados `sin-receta`, `vigente` o `ambigua` según `recetasVigentes`.
2. Probar detalle, historial y formulario con producto sin receta, con versión vigente y con recetas activas duplicadas.
3. Comprobar que rendimiento y cantidades usan la unidad correcta, que cada ingrediente es un insumo activo y que no se repite.
4. Probar el conflicto de `versionBase`: si otra persona guarda, responder 409 y conservar lo escrito en el formulario.
5. Validar que una producción ya planificada conserva `id_receta` de su versión original después de crear una nueva.
6. Mantener el inventario real de productos e insumos. Las ocho recetas pendientes requieren rendimiento y cantidades por tanda del negocio; el script de ejemplo no proporciona esas fórmulas.

## 4. Diseño funcional y de interfaz

### Listado: «¿Qué productos están listos para producir?»

Mantener el filtro Todos / Sin receta / Con receta y el resumen calculado desde los renglones. Mostrar producto, estado, versión, rendimiento con unidad, ingredientes y fecha. La acción por fila será «Crear receta» o «Nueva versión» para los roles que pueden guardar. Una receta ambigua debe señalarse como problema que impide producir, con una ruta para crear una versión que restablezca una sola vigente.

El listado distingue carga, ausencia de productos finales, filtro sin resultados y error. Ante error debe ofrecer reintento y una explicación acorde con la respuesta conocida, sin mostrar mensajes internos de Oracle. No representar un fallo como lista vacía.

### Detalle: «¿Qué receta se usa y cuáles se usaron?»

Mostrar rendimiento por tanda, ingredientes y consumo aproximado por unidad de la versión vigente; debajo, historial con fechas y acceso al detalle de cada versión. Para un producto sin receta o con ambigüedad, explicar por qué no puede producirse y ofrecer la acción correspondiente a quien tenga permiso.

### Formulario: «¿Qué versión nueva voy a guardar?»

Partir de la versión vigente cuando exista. Pedir rendimiento y al menos un ingrediente, con cantidades por tanda y vista previa por unidad. Validar junto al campo, impedir duplicados y preservar cambios ante navegación accidental. Antes de guardar, explicar que se creará una versión y que la historia de producción no cambiará. Si aparece un 409, mantener los datos escritos y permitir consultar la versión reciente.

### Catálogo: «¿Están disponibles el producto y sus insumos?»

Agregar una pantalla de catálogo con dos tipos (`PRODUCTO_FINAL` e `INSUMO`), búsqueda y alta. Cada alta exige nombre único, unidad de medida existente (`UND`, `KG` o `L`) y stock mínimo no negativo. Permitir corregir nombre y stock mínimo; mostrar tipo y unidad como datos fijos después del alta para no cambiar el significado de recetas, movimientos y pedidos históricos. La opción de crear receta se ofrece para un producto final activo; el selector de ingredientes muestra insumos activos. Si no hay insumos utilizables, enlazar al catálogo en vez de dejar un formulario sin salida.

**Permisos propuestos:** `ADMINISTRADOR` da de alta y corrige productos; `PRODUCCION` y `BODEGA` pueden consultar el catálogo, y `PRODUCCION` continúa creando versiones de recetas. Incorporar «Productos» al menú principal, porque también alimenta pedidos e inventario. Estos permisos son una decisión de diseño para revisar, no una regla existente del proyecto.

### Continuidad visual

Conservar el marco, tipografía, colores y componentes PrimeVue del proyecto (`frontend/src/tema/tokens.css`); la captura es referencia del aspecto actual, no una instrucción de rediseñarlo. Revisar ancho de tabla, enfoque por teclado, mensajes y acciones a 1440, 768 y 390 px, en temas claro y oscuro. Las cifras deben alinearse y llevar unidad; los estados no dependerán solo del color.

## 5. Reglas y límites técnicos

- Mantener Vue 3/TypeScript/PrimeVue en la interfaz y Java 17/Jersey/JDBC/Oracle en la API; no se necesitan dependencias nuevas para el alcance actual.
- El servidor es la fuente de verdad para permisos, vigencia, validación y concurrencia. El cliente valida para orientar al usuario.
- El catálogo usa la tabla `PRODUCTO` existente y los catálogos `TIPO_PRODUCTO`/`UNIDAD_MEDIDA`; no requiere tablas nuevas para alta y corrección. Debe comprobar unicidad del nombre y devolver 409 si otro usuario lo crea primero.
- Cada guardado es atómico; cualquier fallo revierte la desactivación de versiones anteriores y las inserciones nuevas.
- No alterar recetas históricas ni el `id_receta` de producciones existentes.
- Usar los errores HTTP existentes: 400 para datos inválidos, 401/403 para acceso, 404 para recurso ausente, 409 para conflicto y 503 para indisponibilidad temporal de Oracle.

## 6. Criterios de aceptación

1. Con Tomcat y Oracle operativos, el listado carga productos reales sin el error de la captura; se conoce y registra la causa raíz corregida.
2. Un producto sin receta se identifica y permite crear la primera versión; uno con receta muestra la vigente y su historial.
3. Guardar una versión nueva deja exactamente una vigente, conserva las anteriores y no cambia las producciones ya registradas.
4. Los roles de consulta no ven acciones de escritura y la API rechaza sus solicitudes de guardado.
5. El formulario conserva sus datos al recibir un conflicto de versión y muestra una salida clara para revisar la versión reciente.
6. Se prueban los estados de carga, vacío, error, reintento y conflicto; se revisan teclado, pantallas estrechas y ambos temas.
7. Pasan las pruebas pertinentes de Java y frontend y se verifica al menos un recorrido real contra Tomcat y Oracle.
8. Un administrador puede registrar un producto final y los insumos necesarios desde la aplicación; después puede crear su primera receta sin ejecutar SQL manual.
9. Se conoce el inventario de productos sin receta. Se crean primeras versiones para los productos reales cuando el negocio proporcione sus rendimientos e ingredientes; no se introducen productos de ejemplo en la base real.

## 7. Orden de trabajo

1. **Diagnóstico de integración:** obtener estado HTTP, excepción y prueba de consulta; registrar causa raíz.
2. **Corrección puntual:** añadir primero una reproducción automatizada, aplicar el cambio mínimo en la capa responsable y repetir la prueba.
3. **Catálogo de productos e insumos:** añadir API y pantalla de alta/consulta/corrección con permisos y pruebas, sin cambiar datos existentes.
4. **Cierre funcional:** recorrer listado, detalle, historial y guardado; usar las cuatro fórmulas de referencia para productos coincidentes y documentar los demás sin fórmula.
5. **Revisión visual y accesible:** comprobar estados, anchos y navegación; ajustar si la evidencia muestra problemas.
6. **Verificación final:** suites, compilación, ciclo real y resumen de lo resuelto y de lo que quede fuera del alcance.

El plan de implementación con archivos y pruebas exactas se cerrará después del diagnóstico: adelantar una modificación de SQL, API o interfaz sin conocer el fallo supondría una causa no demostrada.
