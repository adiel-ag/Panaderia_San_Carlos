# Plan: funcionalidades que faltan para un sistema completo

**Fecha:** 31 de octubre de 2026
**Ejecución:** Claude organiza y revisa; Codex implementa cada bloque en su rama. Cada bloque se integra solo si pasan
`mvn package -Pproduccion` (Java + Vue), las consultas nuevas se verifican contra Oracle en una transacción
deshecha y la pantalla se revisa en el navegador.

**Reglas que no cambian:** no se modifica el esquema ni los paquetes PL/SQL de Oracle; la lógica nueva usa las tablas
existentes desde Java con transacciones y bloqueos (`FOR UPDATE`) donde haya concurrencia. Permisos por rol con
`@RolesAllowed` y la matriz de roles actualizada. Estilo "Horno de leña" y reglas de pantallas de trabajo.

## Bloque 1 — Catálogos (prioridad alta)
| Punto | Qué se construye | Roles |
|---|---|---|
| Productos e insumos | Pantalla **Productos**: listado con filtro por tipo y estado, **Nuevo producto** (nombre, tipo, unidad, stock mínimo), editar nombre y stock mínimo, activar/desactivar. Tipo y unidad no se cambian si el producto ya tiene movimientos, recetas o pedidos. | Ver: ADMINISTRADOR, GERENTE, PRODUCCION, BODEGA. Editar: ADMINISTRADOR, BODEGA. |
| Sucursales | Pantalla **Sucursales**: alta, editar nombre/dirección/teléfono, activar/desactivar (no se desactiva con pedidos pendientes o usuarios activos). | ADMINISTRADOR |

## Bloque 2 — Inventario y producción (prioridad alta)
| Punto | Qué se construye |
|---|---|
| Insumos necesarios antes de producir | En el detalle de una producción planificada o en proceso: tabla **Insumos necesarios** (receta vigente × cantidades) contra la existencia, con faltantes en rojo. Evita descubrir la falta al finalizar. |
| Historial de inventario | Botón **Movimientos** por producto: fecha, tipo, cantidad, origen (producción, despacho, ajuste), referencia enlazada y usuario; paginado. |

## Bloque 3 — Flujo diario (prioridad media)
| Punto | Qué se construye |
|---|---|
| Planificar en un clic | En Producción: **Planificar pedidos de una fecha**: lista los pedidos confirmados libres de esa fecha, permite desmarcar y crea la producción con el plan generado (usa `crearYGenerarPlan`). |
| Editar pedido | En el detalle: **Editar** fecha, observación y líneas mientras el pedido esté confirmado y fuera de producción. |
| Recepción en tienda | En el detalle de un pedido despachado: **Confirmar recepción** (estado del despacho `ENTREGADO`, con observación de faltantes). Usuario de la sucursal o administrador. |
| Buscar por número | Buscador **#Pedido** en Pedidos y Despacho que abre el detalle. |

## Bloque 4 — Documentos, reportes y alertas (prioridad media/baja)
| Punto | Qué se construye |
|---|---|
| Imprimir | **Hoja de despacho** (pedido, sucursal, productos, firmas) y **hoja de producción** (plan e insumos) con estilos de impresión. |
| Reportes | Pantalla **Reportes** (ADMINISTRADOR, GERENTE): pedidos por sucursal, producción por día y consumo de insumos en un rango de fechas, con exportación CSV. |
| Alertas en Inicio | Pedidos atrasados (entrega pasada sin despachar) y productos sin receta, con enlace. |

## Fuera de este plan
- **Producción con merma:** exige cambiar la regla de `PKG_PRODUCCION.SP_FINALIZAR` (cantidad producida menor a la
  planificada), es decir, modificar un paquete Oracle. Queda documentado como migración futura que debe aprobar
  quien administra la base.
- **Servicios de Windows y respaldo de Oracle:** se dejan instrucciones; instalarlos requiere permisos de
  administrador del equipo y acceso al servidor de base de datos.
