# Panadería San Carlos — guía del proyecto para evaluación

**Fecha de revisión:** 31 de octubre de 2026  
**Propósito:** explicar qué resuelve el sistema, cómo viaja la información y dónde se observa cada criterio de la evaluación de 20 puntos. Está escrita para una persona sin experiencia en programación.

## 1. ¿Qué problema resuelve?

La panadería necesita coordinar pedidos de sucursales, fabricación, consumo de ingredientes, existencias y despacho. El sistema reúne esos pasos para que cada área vea los datos que le corresponden y para que un pedido pueda seguirse desde su registro hasta su entrega.

Un ejemplo sencillo del recorrido es:

1. Una sucursal registra un **pedido** con productos y cantidades.
2. El personal de **producción** agrupa pedidos y genera un plan. Para cada producto, el sistema necesita una receta vigente que indique cuánto rinde una tanda y qué ingredientes consume.
3. Al registrar lo producido y finalizar, el sistema calcula el consumo proporcional de ingredientes y actualiza el **inventario**.
4. El área de **despacho** registra la entrega del pedido listo.
5. La administración consulta **usuarios**, permisos y el historial de las operaciones.

La pantalla de **Recetas** permite ver productos con y sin fórmula, revisar la receta vigente, consultar versiones anteriores y guardar una versión nueva. Las versiones antiguas se conservan para saber qué fórmula se usó en producciones previas.

```text
Persona usuaria → Pantalla Vue → API REST en Tomcat → Reglas Java → Oracle
                      ↑                                  ↓
                      └──── respuesta y mensajes ──────┘
```

## 2. Frontend — 5 puntos

El frontend es la parte que la persona ve y utiliza en el navegador. Está hecho con **Vue 3 y TypeScript**. **PrimeVue** aporta controles como tablas, botones y mensajes; **Vue Router** organiza las pantallas y **Pinia** conserva el estado de la sesión. **Vite** prepara los archivos para publicarlos.

| Criterio | Cómo se observa en este proyecto |
| --- | --- |
| Validaciones | Los formularios revisan campos obligatorios, cantidades positivas y datos repetidos antes de enviar. Por ejemplo, una receta exige rendimiento válido, al menos un ingrediente y evita insumos duplicados. El servidor vuelve a validar: una revisión en pantalla por sí sola no protege los datos. |
| Estilos y maquetado | Menú lateral, encabezado y área principal mantienen una disposición común. Las tablas, filtros, formularios y avisos separan visualmente cada tarea. El proyecto incluye temas claro y oscuro. Conviene mostrar la pantalla en escritorio y móvil durante la evaluación. |
| Funcionamiento | La interfaz consulta la API para mostrar pedidos, recetas, producción, inventario, despacho y usuarios. Muestra carga, resultados, mensajes de error y opciones de reintento cuando corresponde. |
| Solución al problema | El usuario puede seguir un pedido, consultar existencias y distinguir productos con receta de los que todavía no pueden producirse. |
| Identificación UMG | **Pendiente de completar visualmente:** no se encontró un nombre o logotipo de la Universidad Mariano Gálvez en las pantallas revisadas. La marca de la panadería sí aparece. |

**Ejemplo de validación entendible:** si alguien escribe cero como rendimiento de una receta, la pantalla avisa que debe ser mayor que cero. Si otra persona guarda una versión mientras el formulario está abierto, el servidor informa el conflicto y evita sobrescribir cambios sin advertencia.

**Dónde verlo:** `frontend/src/views/`, `frontend/src/router/`, `frontend/src/api/cliente.ts` y `frontend/src/views/recetas.ts`.

## 3. Backend — 10 puntos

El backend recibe las peticiones del navegador, verifica permisos, aplica las reglas del negocio y consulta Oracle. Se ejecuta en **Apache Tomcat 10.1** y utiliza **Java 17, Jakarta EE 10, Jersey (REST), JDBC y el controlador Oracle**. Maven construye un archivo WAR que incluye tanto Java como los archivos compilados de Vue.

### Organización y reutilización

El código está separado por responsabilidad. Así se puede cambiar una pantalla o una consulta sin mezclar toda la lógica en un solo archivo:

| Carpeta Java | Función en palabras sencillas |
| --- | --- |
| `resource` | Puertas de entrada de la API: recibe solicitudes y devuelve respuestas HTTP. |
| `service` | Reglas del negocio: comprueba datos, permisos y orden de las operaciones. |
| `dao` | Consultas y escritura en Oracle. |
| `dto` y `model` | Formas de representar los datos que entran, salen o se guardan. |
| `config` | Configuración de conexión, tiempos máximos y puesta en marcha. |
| `exception` | Errores de negocio que se convierten en respuestas comprensibles. |

La conexión a Oracle se abre mediante `ConnectionFactory`, que lee dirección, usuario y clave de variables de entorno. También define límites de tiempo para conectar, leer y ejecutar consultas. Las conexiones se cierran después de usarlas. Esta lógica común evita repetir la configuración en cada módulo.

### Rutas neumónicas y servicios REST

Una **ruta** es una dirección que describe el recurso solicitado. Aquí se usa `/api` como prefijo y nombres que indican su finalidad. `GET` consulta, `POST` crea o ejecuta una acción y `PUT` actualiza. Ejemplos:

| Solicitud | Significado |
| --- | --- |
| `GET /api/health` | Comprueba si la API responde. |
| `POST /api/auth/login` | Inicia sesión. |
| `GET /api/pedidos` | Lista pedidos. |
| `POST /api/pedidos` | Crea un pedido. |
| `GET /api/recetas` | Lista productos y su estado de receta. |
| `GET /api/recetas/productos/{idProducto}/versiones` | Muestra el historial de recetas de un producto. |
| `POST /api/recetas` | Guarda una receta nueva o una nueva versión. |
| `GET /api/inventario/existencias` | Consulta existencias. |
| `POST /api/producciones/{id}/generar-plan` | Genera el plan de una producción. |

El identificador entre llaves representa un número concreto. Por ejemplo, `/api/recetas/productos/33/versiones` pregunta por las versiones del producto 33. Las respuestas se envían como **JSON**, un formato de datos que entiende el navegador.

### Seguridad, transacciones y errores

La persona inicia sesión y el servidor usa esa sesión para comprobar identidad y rol. Cada área tiene permisos diferentes: una cuenta de sucursal registra pedidos, producción trabaja con recetas y fabricación, bodega consulta inventario y administración gestiona usuarios. La API verifica los permisos aunque alguien intente saltarse la pantalla.

Una **transacción** reúne varios cambios que deben completarse juntos. Al guardar una nueva versión de receta, Java reserva el producto, cierra la versión activa anterior, crea la nueva e inserta sus ingredientes. Si ocurre un error, ejecuta **rollback** y deshace esos cambios; si todo sale bien, ejecuta **commit** y los confirma. Se aplica el mismo principio a operaciones como pedidos, producción, inventario y despacho.

Los errores tienen respuestas distintas: datos inválidos (400), falta de sesión (401), falta de permiso (403), dato inexistente (404), conflicto entre cambios simultáneos (409) o base no disponible (503). Esto ayuda a la pantalla a mostrar un aviso adecuado.

**Identificación UMG:** `gt.edu.umg` aparece como identificador Maven y `gt.edu.umg.sancarlos` como base de los paquetes Java. Esto identifica el origen académico del código, aunque falta la identificación visible señalada en frontend.

**Dónde verlo:** `pom.xml`, `src/main/java/gt/edu/umg/sancarlos/` y `frontend/vite.config.ts`.

## 4. Base de datos — 5 puntos

La información se almacena en **Oracle**. Los scripts del proyecto describen **17 tablas**, catálogos iniciales y **7 paquetes** de reglas. Las tablas separan conceptos como producto, receta, ingrediente, pedido, producción, movimiento de inventario, despacho, usuario y sucursal. Esa separación evita repetir información y permite relacionar una producción con la versión de receta que realmente utilizó.

| Criterio | Cómo se observa |
| --- | --- |
| Modelo lógico | Cada concepto tiene su tabla y sus relaciones. `RECETA` guarda versión y rendimiento; `RECETA_DETALLE` relaciona esa versión con ingredientes y cantidades. |
| Paquetes y procedimientos almacenados | Los paquetes `PKG_RECETA`, `PKG_INVENTARIO`, `PKG_PEDIDO`, `PKG_PRODUCCION`, `PKG_DESPACHO`, `PKG_SEGURIDAD` y `PKG_USUARIO` agrupan reglas cercanas a los datos. Varios ofrecen procedimientos (`SP_...`) para acciones de negocio. |
| Commit y rollback | Java controla la confirmación o reversión de las operaciones que agrupan varios cambios. Los paquetes pueden señalar errores; quien llama decide confirmar o deshacer la transacción. |
| Excepciones | Los paquetes generan errores específicos cuando una operación incumple reglas, por ejemplo un usuario duplicado o una receta que no puede usarse. Java transforma esos problemas en respuestas de la API. |
| Integridad | Claves primarias y foráneas impiden referencias inexistentes; reglas `UNIQUE` evitan duplicados; restricciones `CHECK` impiden cantidades negativas o estados inválidos. |

**Ejemplo concreto de integridad:** una receta no puede tener dos veces el mismo ingrediente, su rendimiento debe ser mayor que cero y cada ingrediente debe referirse a un producto existente. Si algo falla, no se confirma una receta incompleta.

**Dónde verlo:** `database/01_tablas/`, `database/03_paquetes/`, `database/instalar.sql` y `database/README.md`.

## 5. Demostración recomendada del flujo

1. Iniciar sesión con un usuario de prueba y señalar el menú que corresponde a su rol.
2. Abrir **Recetas**: explicar la diferencia entre «Con receta» y «Sin receta», consultar ESPECIAL y su historial. La base revisada el 31 de octubre tenía 9 productos finales, de los que 8 aún no tenían fórmula.
3. Mostrar un **pedido** y cómo sus productos llegan a la planificación de **producción**. Para demostrar una producción completa, seleccionar un producto que tenga receta vigente y existencias suficientes.
4. Mostrar **Inventario** y explicar cómo se registran entradas, salidas y el consumo calculado al finalizar producción.
5. Mostrar **Despacho** y el seguimiento del pedido listo.
6. Explicar qué ocurre si falta una receta, se introduce una cantidad inválida o un usuario intenta una acción sin permiso.

## 6. Estado actual y puntos por completar

- La conexión Vue → Tomcat → Oracle se comprobó el 31 de octubre de 2026. El empaquetado terminó con 179 pruebas Java y 368 de frontend aprobadas; la API de recetas respondió correctamente tras reiniciar Tomcat con las clases actuales.
- **Ocho productos del catálogo real aún no tienen receta.** No se inventaron rendimientos ni cantidades: se necesitan las fórmulas reales del negocio para que producción pueda planificarlos correctamente. El catálogo SQL de ejemplo contiene nombres e insumos distintos y no se aplicó a esta base.
- La identificación visual de **Universidad Mariano Gálvez (UMG)** sigue pendiente en la interfaz si la rúbrica exige que aparezca en pantalla. Conviene agregarla de forma clara antes de la entrega.
- Las pruebas aprobadas respaldan las funciones verificadas; no equivalen a una calificación automática de 20/20. La demostración debe mostrar los flujos y los puntos pendientes con honestidad.

### Términos rápidos

**Frontend:** pantalla del navegador. **Backend:** lógica que atiende solicitudes. **API REST:** conjunto de rutas por las que se comunican pantalla y servidor. **Base de datos:** lugar donde se conservan los registros. **Paquete/SP:** reglas y procedimientos ejecutados dentro de Oracle. **Commit:** confirmar cambios. **Rollback:** deshacer una operación incompleta. **WAR:** archivo que Tomcat publica como aplicación web.
