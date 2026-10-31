import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import BotonTema from './BotonTema.vue'
import { useTema } from '../tema/useTema'

describe('BotonTema', () => {
  it('expone el estado y la acción accesible al alternar', async () => {
    const tema = useTema()
    tema.oscuro.value = false
    const wrapper = mount(BotonTema)
    const boton = wrapper.get('button')
    // Nombre fijo; el estado lo comunica aria-pressed.
    expect(boton.attributes('aria-label')).toBe('Modo oscuro')
    expect(boton.attributes('aria-pressed')).toBe('false')
    await boton.trigger('click')
    await nextTick()
    expect(boton.attributes('aria-label')).toBe('Modo oscuro')
    expect(boton.attributes('aria-pressed')).toBe('true')
    expect(tema.oscuro.value).toBe(true)
  })
})
