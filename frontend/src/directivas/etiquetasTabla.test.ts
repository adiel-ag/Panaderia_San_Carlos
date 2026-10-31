import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, ref } from 'vue'
import PrimeVue from 'primevue/config'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import { vEtiquetasTabla } from './etiquetasTabla'

const Tabla = defineComponent({
  components: { DataTable, Column },
  directives: { EtiquetasTabla: vEtiquetasTabla },
  setup() {
    const filas = ref([{ id: 1, nombre: 'Pan francés', total: 12 }])
    const conTotal = ref(true)
    return { filas, conTotal }
  },
  template: `
    <div v-etiquetas-tabla class="tabla-adaptable">
      <DataTable :value="filas" data-key="id">
        <Column field="nombre" header="Producto" />
        <Column v-if="conTotal" field="total" header="Total" />
        <template #empty><p>Sin datos</p></template>
      </DataTable>
    </div>`,
})

function etiquetas(wrapper: ReturnType<typeof mount>): (string | undefined)[][] {
  return wrapper.findAll('tbody > tr').map((fila) => fila.findAll('td').map((celda) => celda.attributes('data-label')))
}
async function esperarObservador(): Promise<void> {
  await nextTick()
  await new Promise((resolver) => setTimeout(resolver, 0))
}

describe('vEtiquetasTabla', () => {
  it('copia el encabezado de cada columna a sus celdas', () => {
    const wrapper = mount(Tabla, { global: { plugins: [PrimeVue] } })
    expect(etiquetas(wrapper)).toEqual([['Producto', 'Total']])
  })

  it('etiqueta las filas nuevas y sigue a las columnas que cambian', async () => {
    const wrapper = mount(Tabla, { global: { plugins: [PrimeVue] } })
    const vm = wrapper.vm as unknown as { filas: { id: number; nombre: string; total: number }[]; conTotal: boolean }
    vm.filas = [...vm.filas, { id: 2, nombre: 'Concha', total: 5 }]
    await esperarObservador()
    expect(etiquetas(wrapper)).toEqual([['Producto', 'Total'], ['Producto', 'Total']])
    vm.conTotal = false
    await esperarObservador()
    expect(etiquetas(wrapper)).toEqual([['Producto'], ['Producto']])
  })

  it('no etiqueta la fila de tabla vacía', async () => {
    const wrapper = mount(Tabla, { global: { plugins: [PrimeVue] } })
    ;(wrapper.vm as unknown as { filas: unknown[] }).filas = []
    await esperarObservador()
    const celda = wrapper.find('tbody > tr > td')
    expect(celda.text()).toContain('Sin datos')
    expect(celda.attributes('data-label')).toBeUndefined()
  })
})
