# Flujo diario Implementation Plan

**Goal:** Completar la planificación por fecha, edición de pedidos, recepción y búsqueda por número.
**Architecture:** Reutilizar servicios, proyecciones y rutas actuales. Las escrituras nuevas usan transacciones JDBC y bloqueos de fila; las pantallas reutilizan formularios y patrones visuales existentes.
**Tech Stack:** Java 17, Jersey, JDBC/Oracle, Vue 3, TypeScript, PrimeVue.
**Spec:** `docs/plan-funcionalidades-faltantes.md`, Bloque 3, y requisitos de esta sesión.

## Global Constraints

- Sin cambios bajo `database/`, `pom.xml` o `vite.config.ts`; sin conexión a Oracle.
- Conservar cambios existentes y la rama actual; no confirmar ni publicar.
- Pruebas con dobles de DAO/conexión y verificaciones de frontend.

## Review Focus

- Una sucursal ajena recibe 404 antes de cualquier edición o recepción.
- Edición concurrente con producción se rechaza tras bloquear el pedido.
- Un fallo al reemplazar detalles revierte toda la edición.
- La observación de recepción se recorta a 500 caracteres en SQL.
- El formulario editado no pierde cambios al navegar fuera.

## Tasks

### 1. Planificar por fecha

- [ ] Probar validación de lista, permisos y consulta de pedidos disponibles.
- [ ] Agregar DTO, consulta DAO y endpoints que reutilizan `crearYGenerarPlan`.
- [ ] Agregar diálogo, API y pruebas de interfaz.

### 2. Editar pedido

- [ ] Probar validaciones, alcance y transacción con dobles.
- [ ] Agregar DTO, DAO, servicio y endpoint.
- [ ] Reutilizar formulario para edición, ruta, botón y pruebas.

### 3. Recepción y búsqueda

- [ ] Probar estados, alcance y actualización de observación con dobles.
- [ ] Agregar recepción DAO, servicio y endpoint.
- [ ] Agregar diálogo de recepción y búsqueda en dos vistas, con pruebas.

### 4. Verificación

- [ ] Ejecutar type-check, Vitest, build y Maven offline si existe.
- [ ] Revisar cambios y reglas de alcance.
