# Recetas y catálogo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mantener disponible la consulta y el historial de recetas del catálogo real; preparar la gestión de productos e insumos solo cuando se requieran altas reales. Crear las ocho fórmulas pendientes cuando se conozcan sus cantidades.

**Architecture:** Se conserva el flujo de recetas versionadas y su interfaz. La API de listado e historial ya funciona tras recargar Tomcat. El catálogo Oracle actual difiere del script de ejemplo; no se ejecuta dicho script. El CRUD de `PRODUCTO` queda como ampliación posterior si se necesitan productos o insumos nuevos.

**Tech Stack:** Java 17, Jakarta REST/Jersey, JDBC/Oracle, Vue 3, TypeScript, PrimeVue, JUnit 5, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-31-recetas-analisis-diseno.md`

## Global Constraints

- Trabajar en la carpeta actual, según la preferencia expresa del usuario.
- Conservar versiones históricas y `PRODUCCION_DETALLE.id_receta`.
- `ADMINISTRADOR` escribe productos; `PRODUCCION` y `BODEGA` consultan el catálogo.
- No cambiar tipo ni unidad tras el alta. No desactivar productos en esta entrega.
- No aplicar fórmulas a productos que ya tengan receta activa.
- No añadir dependencias ni tablas nuevas.

## Review Focus

- Nombre duplicado durante el alta o la edición → 409, sin dejar una transacción abierta.
- Tipo o unidad inexistentes en Oracle → 400, sin crear una fila incompleta.
- Stock mínimo negativo, fuera de `NUMBER(12,3)` o texto vacío → 400 en API y error de campo en UI.
- Producto sin insumos disponibles → el formulario de receta informa la causa y enlaza al catálogo.
- Dos sesiones crean productos con el mismo nombre → una gana y la otra recibe 409.

---

### Task 1: Diagnóstico de la carga de recetas

**Files:** `frontend/src/views/RecetasView.vue`, `frontend/src/api/cliente.ts`, `src/main/java/gt/edu/umg/sancarlos/resource/RecetaResource.java`, `src/main/java/gt/edu/umg/sancarlos/dao/RecetaDAO.java`, `database/01_tablas/*.sql` (lectura). `docs/superpowers/specs/2026-10-31-recetas-analisis-diseno.md` (registrar hallazgo).

**Interfaces:** `GET /api/health`, `GET /api/auth/me`, `GET /api/recetas`.

- [x] Registrar si Tomcat y Oracle están disponibles; obtener estado y cuerpo de `GET /api/recetas` con una sesión autorizada. Responde 200 tras reiniciar Tomcat; antes devolvía 404 por clases cargadas antes de la compilación.
- [x] La rama 500/503 no aplica: `GET /api/recetas` respondió 200 con sesión y devolvió los nueve productos finales reales.
- [x] Registrar causa raíz, reproducción y capa afectada. Era un despliegue desactualizado, no un defecto del endpoint: el proceso Tomcat arrancó antes de que se recompilaran las clases. El reempaquetado pasó 179 pruebas Java y 368 de Vue.
- [x] La rama de entorno apagado no aplica: Tomcat escucha en 8080, Oracle respondió a consultas y el historial de ESPECIAL devolvió una versión.

### Task 2 (condicional): Alta y corrección segura de productos e insumos en la API

El usuario pidió conservar el catálogo real. Como ya existen los nueve productos finales y diez insumos activos, esta ampliación no bloquea la consulta ni el alta de versiones. Ejecutarla cuando se solicite registrar productos o insumos nuevos.

**Files:**
- Create: `src/main/java/gt/edu/umg/sancarlos/dto/CrearProductoDTO.java`, `src/main/java/gt/edu/umg/sancarlos/dto/ActualizarProductoDTO.java`.
- Modify: `src/main/java/gt/edu/umg/sancarlos/dao/ProductoDAO.java`, `src/main/java/gt/edu/umg/sancarlos/service/ProductoService.java`, `src/main/java/gt/edu/umg/sancarlos/resource/ProductoResource.java`.
- Test: `src/test/java/gt/edu/umg/sancarlos/service/ProductoServiceTest.java`, `src/test/java/gt/edu/umg/sancarlos/resource/ProductoResourceHttpTest.java`, `src/test/java/gt/edu/umg/sancarlos/resource/MatrizRolesTest.java`.

**Interfaces:** `POST /api/productos` consume `{nombre,tipo,unidad,stockMinimo}` y devuelve 201 con `Producto`; `PUT /api/productos/{id}` consume `{nombre,stockMinimo}` y devuelve 200 con `Producto`. `GET /api/productos` y `GET /api/productos/{id}` conservan sus respuestas. El DAO resuelve los IDs de `TIPO_PRODUCTO` y `UNIDAD_MEDIDA` por nombres; solo admite `INSUMO`/`PRODUCTO_FINAL` y abreviaturas de unidad registradas. El servicio valida, abre una transacción para cada escritura y traduce `ORA-00001` a 409.

- [ ] Escribir pruebas de Resource para permisos, cuerpo/ID inválido y respuesta 201/200; ejecutarlas y confirmar fallo por endpoints ausentes.
- [ ] Escribir pruebas de Service para nombre, tipo, unidad y `stockMinimo` (0 a 999999999.999, hasta tres decimales), error por catálogo ausente y duplicado; confirmar fallo esperado.
- [ ] Implementar DTO, DAO, Service y Resource con `ConnectionFactory.aplicarTimeout` y `commit`/`rollback` explícitos; mantener el estado inicial `ACTIVO` de la tabla.
- [ ] Ejecutar `"C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd" -DskipFrontend test`; exigir 0 fallos. Revisar el diff y registrar el resultado.

### Task 3 (condicional): Pantalla de catálogo y acceso desde Recetas

Depende de la necesidad de altas reales y de completar Task 2; no es necesaria para consultar o versionar las recetas de los productos existentes.

**Files:**
- Create: `frontend/src/api/productos.ts`, `frontend/src/views/ProductosView.vue`, `frontend/src/views/ProductosView.test.ts`.
- Modify: `frontend/src/router/menu.ts`, `frontend/src/router/index.ts`, `frontend/src/views/RecetasView.vue`, `frontend/src/views/NuevaRecetaView.vue`, `frontend/src/style.css`.
- Test: `frontend/src/router/menu.test.ts`, `frontend/src/router/index.test.ts`, `frontend/src/views/RecetasView.test.ts`, `frontend/src/views/NuevaRecetaView.test.ts`.

**Interfaces:** ruta `#/productos`, opción de menú «Productos» para ADMINISTRADOR/PRODUCCION/BODEGA; alta y edición visibles solo para ADMINISTRADOR. El formulario usa `POST/PUT /api/productos`, consulta `GET /api/productos` y ofrece `INSUMO`/`PRODUCTO_FINAL` y `UND`/`KG`/`L` conforme a los catálogos iniciales. Un producto final recién creado aparece en Recetas como «Sin receta» al recargar.

- [ ] Escribir pruebas de ruta, menú, filtrado, validación, alta, edición y permisos; comprobar que fallan por pantalla/comportamiento ausente.
- [ ] Implementar cliente, vista y enlaces desde Recetas y desde el estado sin insumos; conservar los tokens visuales existentes y estados de carga, vacío, error y reintento.
- [ ] Ejecutar `..\target\node\npm.cmd test` y `..\target\node\npm.cmd run build` desde `frontend`; exigir salida 0.
- [ ] Revisar la pantalla a 1440, 768 y 390 px, en claro y oscuro; corregir desbordes, enfoque y mensajes detectados.

### Task 4: Fórmulas del catálogo real e integración

La base contiene ALEMAN, BATIDAS, CACHOS, CAMPECHANAS, CORTADAS, CUBOS, ESPECIAL, FRANCES y SHECAS. Solo ESPECIAL tiene receta. El usuario eligió conservar este catálogo; el script de ejemplo contiene otros nombres e insumos y no se aplicará a esta base. Antes de crear versiones para los otros ocho productos se requieren rendimientos e ingredientes con cantidad por tanda.

**Files:** `src/main/java/gt/edu/umg/sancarlos/resource/RecetaResource.java`, `frontend/src/views/RecetasView.vue`, `frontend/src/views/RecetaView.vue`, `frontend/src/views/NuevaRecetaView.vue`, `scripts/prueba-ciclo-completo.mjs` (verificación). El script de ejemplo queda como referencia documental.

**Interfaces:** `GET /api/recetas`, `GET /api/recetas/productos/{id}/versiones`, `GET /api/recetas/{idReceta}` y `POST /api/recetas`. `ESPECIAL` y cualquier otra receta activa quedan intactas.

- [x] Consultar Oracle y la API: 9 productos finales, 10 insumos activos, 8 productos sin receta y 1 versión de ESPECIAL.
- [x] Comparar los nombres y unidades del script de ejemplo con el esquema real; registrar la divergencia y la decisión de conservar el catálogo real.
- [ ] Obtener del negocio rendimiento e ingredientes con cantidad por tanda para ALEMAN, BATIDAS, CACHOS, CAMPECHANAS, CORTADAS, CUBOS, FRANCES y SHECAS.
- [ ] Crear las primeras versiones mediante la interfaz o la API, sin modificar ESPECIAL; verificar una vigente y sus ingredientes por producto.
- [ ] Recorrer la interfaz: listado, detalle, historial, nueva versión y conflicto. Ejecutar la prueba de ciclo completo contra Tomcat/Oracle con una fórmula real aprobada.
- [x] Ejecutar suites Java y frontend: 179 y 368 pruebas aprobadas durante `mvn package`.

## Execution notes

La tarea 1 quedó resuelta al reiniciar Tomcat con las clases actualizadas; no exigió cambios de código. Las tareas 2 y 3 son condicionales porque el catálogo real ya tiene productos e insumos disponibles. La tarea 4 requiere fórmulas reales: no extrapolar cantidades de productos distintos ni cargar el script de ejemplo en el esquema del negocio.
