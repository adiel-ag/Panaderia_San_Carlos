import { createRouter, createWebHashHistory } from 'vue-router'
import InicioView from '../views/InicioView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [{ path: '/', name: 'inicio', component: InicioView }],
})

export default router
