import type { Component } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import { ROLES } from '../api/tipos'
import { useSesionStore } from '../stores/sesion'
import AppLayout from '../layouts/AppLayout.vue'
import InicioView from '../views/InicioView.vue'
import LoginView from '../views/LoginView.vue'
import CambiarClaveView from '../views/CambiarClaveView.vue'
import PendienteView from '../views/PendienteView.vue'
import PedidosView from '../views/PedidosView.vue'
import PedidoDetalleView from '../views/PedidoDetalleView.vue'
import NuevoPedidoView from '../views/NuevoPedidoView.vue'
import ProduccionesView from '../views/ProduccionesView.vue'
import ProduccionDetalleView from '../views/ProduccionDetalleView.vue'
import DespachoView from '../views/DespachoView.vue'
import InventarioView from '../views/InventarioView.vue'
import UsuariosView from '../views/UsuariosView.vue'
import SinPermisoView from '../views/SinPermisoView.vue'
import { decidirNavegacion } from './guardia'
import { menu, type OpcionMenu } from './menu'
import './meta'

// Los módulos sin pantalla propia muestran la página pendiente.
const vistas: Partial<Record<OpcionMenu['nombre'], Component>> = { pedidos: PedidosView, produccion: ProduccionesView, inventario: InventarioView, despacho: DespachoView, usuarios: UsuariosView }

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { publica: true, titulo: 'Iniciar sesión' } },
    {
      path: '/', component: AppLayout, children: [
        { path: '', name: 'inicio', component: InicioView, meta: { titulo: 'Inicio', roles: menu[0]!.roles } },
        ...menu.slice(1).map((opcion) => ({
          path: opcion.nombre, name: opcion.nombre, component: vistas[opcion.nombre] ?? PendienteView,
          meta: { titulo: opcion.etiqueta, roles: opcion.roles },
        })),
        { path: 'pedidos/nuevo', name: 'pedido-nuevo', component: NuevoPedidoView,
          meta: { titulo: 'Nuevo pedido', roles: ['ADMINISTRADOR', 'SUCURSAL'] } },
        { path: 'pedidos/:id(\\d+)', name: 'pedido', component: PedidoDetalleView,
          meta: { titulo: 'Pedido', roles: menu.find((opcion) => opcion.nombre === 'pedidos')!.roles } },
        { path: 'produccion/:id(\\d+)', name: 'produccion-detalle', component: ProduccionDetalleView,
          meta: { titulo: 'Producción', roles: menu.find((opcion) => opcion.nombre === 'produccion')!.roles } },
        { path: 'mi-clave', name: 'mi-clave', component: CambiarClaveView, meta: { titulo: 'Cambiar contraseña', roles: ROLES } },
        { path: 'sin-permiso', name: 'sin-permiso', component: SinPermisoView, meta: { titulo: 'Sin permiso', roles: ROLES } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'inicio' } },
  ],
})

router.beforeEach((to) => decidirNavegacion(to, useSesionStore()))

export default router
