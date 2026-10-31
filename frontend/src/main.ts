import { createApp } from 'vue'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import { presetCorteza } from './tema/preset'
import { iniciarTema } from './tema/useTema'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import 'primeicons/primeicons.css'
import '@fontsource-variable/fraunces/wght.css'
import '@fontsource-variable/nunito-sans/wght.css'
import './tema/tokens.css'
import './style.css'

iniciarTema()
const app = createApp(App)

app.use(PrimeVue, {
  theme: { preset: presetCorteza, options: { darkModeSelector: '.tema-oscuro' } },
  locale: {
    startsWith: 'Empieza con',
    contains: 'Contiene',
    notContains: 'No contiene',
    endsWith: 'Termina con',
    equals: 'Igual a',
    notEquals: 'Diferente de',
    noFilter: 'Sin filtro',
    lt: 'Menor que',
    lte: 'Menor o igual que',
    gt: 'Mayor que',
    gte: 'Mayor o igual que',
    dateIs: 'Fecha igual a',
    dateIsNot: 'Fecha diferente de',
    dateBefore: 'Fecha anterior a',
    dateAfter: 'Fecha posterior a',
    clear: 'Limpiar',
    apply: 'Aplicar',
    matchAll: 'Coincidir con todas',
    matchAny: 'Coincidir con alguna',
    addRule: 'Agregar regla',
    removeRule: 'Eliminar regla',
    accept: 'Aceptar',
    reject: 'Rechazar',
    choose: 'Elegir',
    upload: 'Subir',
    cancel: 'Cancelar',
    pending: 'Pendiente',
    dayNames: ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'],
    dayNamesShort: ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'],
    dayNamesMin: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
    monthNames: ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'],
    monthNamesShort: ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'],
    today: 'Hoy',
    weekHeader: 'Sem',
    firstDayOfWeek: 1,
    dateFormat: 'dd/mm/yy',
    weak: 'Débil',
    medium: 'Media',
    strong: 'Fuerte',
    passwordPrompt: 'Escribe una contraseña',
    emptyFilterMessage: 'No se encontraron resultados',
    emptyMessage: 'No hay datos disponibles',
  },
})
app.use(ToastService)
app.use(ConfirmationService)
app.use(createPinia())
app.use(router)
app.mount('#app')
