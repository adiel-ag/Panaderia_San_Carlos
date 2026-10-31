import { createRouter, createWebHashHistory } from 'vue-router'
import InicioView from '../views/InicioView.vue'
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'inicio', component: InicioView },
    { path: '/login', name: 'login', component: LoginView },
  ],
})

export default router
