// Recorre el ciclo completo contra Tomcat y Oracle reales:
// pedido -> producción -> despacho -> inventario, con un usuario por rol.
//
//   node scripts/prueba-ciclo-completo.mjs                  (escribe en Oracle)
//   node scripts/prueba-ciclo-completo.mjs --solo-verificar (solo inicia sesión y consulta)
//
// Lee usuarios y contraseñas de .env (nunca se imprimen). Todo lo que crea
// lleva "PRUEBA-AUTO" en la observación para poder identificarlo.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// fileURLToPath decodifica espacios y acentos de la ruta (p. ej. "Angel Gomez").
const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const soloVerificar = process.argv.includes('--solo-verificar')
const MARCA = 'PRUEBA-AUTO'
const ROLES = ['ADMINISTRADOR', 'GERENTE', 'SUCURSAL', 'PRODUCCION', 'BODEGA', 'DESPACHO']

// Unidades que se piden de cada producto final con receta activa (se usan los
// que haya en el catálogo real; la API no expone los ingredientes de la receta,
// así que el consumo se deduce de cuánto baja cada insumo).
const CANTIDAD_POR_PRODUCTO = 20

let fallos = 0
// Productos finales que no se pudieron probar por no tener receta: se informan al final.
const sinReceta = []
const ok = (texto) => console.log(`  ✔ ${texto}`)
const mal = (texto) => { fallos++; console.log(`  ✘ ${texto}`) }
const nota = (texto) => console.log(`  · ${texto}`)
const titulo = (texto) => console.log(`\n${texto}`)
const redondear = (n) => Math.round(n * 1000) / 1000

function leerEnv() {
  const archivo = path.join(raiz, '.env')
  if (!fs.existsSync(archivo)) throw new Error('No existe .env: copie .env.example como .env y complételo.')
  const valores = {}
  for (const linea of fs.readFileSync(archivo, 'utf8').split(/\r?\n/)) {
    const m = linea.match(/^\s*([A-Z_]+)\s*=\s*(.*)\s*$/)
    if (m) valores[m[1]] = m[2]
  }
  return valores
}

class Sesion {
  constructor(base, rol) { this.base = base; this.rol = rol; this.cookie = '' }
  async pedir(metodo, ruta, cuerpo) {
    const respuesta = await fetch(this.base + ruta, {
      method: metodo,
      headers: { Accept: 'application/json', ...(cuerpo !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(this.cookie ? { Cookie: this.cookie } : {}) },
      body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
    })
    const nueva = respuesta.headers.getSetCookie?.().find((c) => c.startsWith('JSESSIONID='))
    if (nueva) this.cookie = nueva.split(';')[0]
    const texto = await respuesta.text()
    let datos = null
    try { datos = texto ? JSON.parse(texto) : null } catch { datos = texto }
    return { status: respuesta.status, datos }
  }
  async exigir(metodo, ruta, cuerpo, esperado = [200, 201]) {
    const r = await this.pedir(metodo, ruta, cuerpo)
    if (!esperado.includes(r.status)) throw new Error(`${this.rol} ${metodo} ${ruta} -> ${r.status} ${r.datos?.message ?? ''}`)
    return r.datos
  }
  async entrar(usuario, clave) {
    const r = await this.pedir('POST', '/auth/login', { username: usuario, password: clave })
    if (r.status !== 200) throw new Error(`No se pudo iniciar sesión como ${this.rol} (${r.status}: ${r.datos?.message ?? ''})`)
    return r.datos
  }
  salir() { return this.pedir('POST', '/auth/logout') }
}

async function todasLasExistencias(sesion) {
  const lista = []
  for (let pagina = 1; ; pagina++) {
    const r = await sesion.exigir('GET', `/inventario/existencias?pagina=${pagina}&tamano=100`)
    lista.push(...r.items)
    if (lista.length >= r.total || r.items.length === 0) return new Map(lista.map((x) => [x.nombre, x]))
  }
}

async function principal() {
  const env = leerEnv()
  const base = (env.PSC_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '')
  const sesiones = {}

  titulo('1. Inicio de sesión por rol')
  for (const rol of ROLES) {
    const usuario = env[`PSC_USUARIO_${rol}`], clave = env[`PSC_CLAVE_${rol}`]
    if (!usuario || !clave) { mal(`${rol}: faltan usuario o contraseña en .env`); continue }
    const sesion = new Sesion(base, rol)
    try {
      const datos = await sesion.entrar(usuario, clave)
      if (datos.rol !== rol) mal(`${usuario} inició sesión pero su rol es ${datos.rol}, no ${rol}`)
      else ok(`${rol}: ${usuario}${datos.idSucursal ? ` (sucursal ${datos.idSucursal})` : ''}`)
      sesiones[rol] = { sesion, datos }
    } catch (error) { mal(error.message) }
  }
  if (Object.keys(sesiones).length < ROLES.length) throw new Error('Corrija .env o los usuarios antes de seguir.')

  titulo('2. Permisos por rol (solo lectura)')
  const comprobaciones = [
    ['GERENTE', 'GET', '/usuarios', 403], ['BODEGA', 'GET', '/pedidos', 403], ['SUCURSAL', 'GET', '/producciones', 403],
    ['DESPACHO', 'GET', '/usuarios', 403], ['PRODUCCION', 'GET', '/inventario/existencias', 200], ['ADMINISTRADOR', 'GET', '/usuarios', 200],
  ]
  for (const [rol, metodo, ruta, esperado] of comprobaciones) {
    const r = await sesiones[rol].sesion.pedir(metodo, ruta)
    r.status === esperado ? ok(`${rol} ${ruta} -> ${r.status}`) : mal(`${rol} ${ruta} -> ${r.status}, se esperaba ${esperado}`)
  }
  const propios = await sesiones.SUCURSAL.sesion.exigir('GET', '/pedidos?tamano=100')
  const ajenos = propios.items.filter((p) => p.idSucursal !== sesiones.SUCURSAL.datos.idSucursal)
  ajenos.length === 0 ? ok(`SUCURSAL solo ve su sucursal (${propios.total} pedidos)`) : mal(`SUCURSAL ve ${ajenos.length} pedidos de otras sucursales`)

  titulo('3. Catálogo')
  const productos = await sesiones.ADMINISTRADOR.sesion.exigir('GET', '/productos')
  const porNombre = new Map(productos.map((p) => [p.nombre, p]))
  const finales = productos.filter((p) => p.nombreTipoProducto === 'PRODUCTO_FINAL' && p.estado === 'ACTIVO')
  const conReceta = []
  for (const p of finales) {
    const r = await sesiones.ADMINISTRADOR.sesion.pedir('GET', `/recetas/productos/${p.idProducto}/activa`)
    if (r.status === 200) { conReceta.push(p); ok(`${p.nombre}: receta activa #${r.datos.idReceta}`) }
    // Solo "no tiene receta" (404) se omite; una receta ambigua (409) u otro error es un fallo.
    else if (r.status === 404) { sinReceta.push(p.nombre); nota(`${p.nombre}: sin receta activa, no se puede producir`) }
    else mal(`${p.nombre}: receta inutilizable (${r.status} ${r.datos?.message ?? ''})`)
  }
  if (!conReceta.length) mal('Ningún producto final tiene receta activa: no se puede producir nada')
  const PEDIDO = Object.fromEntries(conReceta.map((p) => [p.nombre, CANTIDAD_POR_PRODUCTO]))
  const antes = await todasLasExistencias(sesiones.BODEGA.sesion)
  const insumos = productos.filter((p) => p.nombreTipoProducto === 'INSUMO' && p.estado === 'ACTIVO')
  for (const p of insumos) {
    const x = antes.get(p.nombre)
    nota(`${p.nombre}: ${x?.existencia ?? 'sin inventario'} ${x?.unidad ?? ''}`)
  }
  if (fallos) throw new Error('El catálogo no está listo para el ciclo.')
  if (soloVerificar) return

  titulo('4. Pedido (SUCURSAL)')
  const manana = new Date(); manana.setDate(manana.getDate() + 1)
  const fechaEntrega = `${manana.getFullYear()}-${String(manana.getMonth() + 1).padStart(2, '0')}-${String(manana.getDate()).padStart(2, '0')}`
  const { idPedido } = await sesiones.SUCURSAL.sesion.exigir('POST', '/pedidos', {
    idSucursal: sesiones.SUCURSAL.datos.idSucursal, fechaEntrega, observacion: `${MARCA} ciclo completo`,
    detalles: Object.entries(PEDIDO).map(([nombre, cantidad]) => ({ idProducto: porNombre.get(nombre).idProducto, cantidad })),
  })
  let pedido = await sesiones.ADMINISTRADOR.sesion.exigir('GET', `/pedidos/${idPedido}`)
  pedido.estado === 'CONFIRMADO' ? ok(`Pedido #${idPedido} creado y CONFIRMADO`) : mal(`Pedido #${idPedido} en estado ${pedido.estado}`)
  const intentoGerente = await sesiones.GERENTE.sesion.pedir('POST', '/pedidos', { idSucursal: 1, fechaEntrega, detalles: [] })
  intentoGerente.status === 403 ? ok('GERENTE no puede crear pedidos (403)') : mal(`GERENTE creó o intentó crear un pedido: ${intentoGerente.status}`)

  titulo('5. Producción (PRODUCCION)')
  const prod = sesiones.PRODUCCION.sesion
  const { idProduccion } = await prod.exigir('POST', '/producciones', { observacion: `${MARCA} ciclo completo` })
  ok(`Producción #${idProduccion} creada`)
  await prod.exigir('POST', `/producciones/${idProduccion}/pedidos`, { idPedido })
  ok(`Pedido #${idPedido} asociado`)
  const plan = await prod.exigir('POST', `/producciones/${idProduccion}/generar-plan`)
  for (const [nombre, cantidad] of Object.entries(PEDIDO)) {
    const fila = plan.find((d) => d.idProducto === porNombre.get(nombre).idProducto)
    fila && Number(fila.cantidadPlanificada) === cantidad ? ok(`Plan: ${nombre} ${fila.cantidadPlanificada}`) : mal(`Plan de ${nombre}: ${fila?.cantidadPlanificada ?? 'ausente'}, se esperaba ${cantidad}`)
  }
  pedido = await sesiones.ADMINISTRADOR.sesion.exigir('GET', `/pedidos/${idPedido}`)
  pedido.estado === 'EN_PRODUCCION' ? ok('Pedido pasó a EN_PRODUCCION') : mal(`Pedido en ${pedido.estado} tras generar el plan`)
  const finalizarAntes = await prod.pedir('POST', `/producciones/${idProduccion}/finalizar`)
  finalizarAntes.status === 409 ? ok('No deja finalizar sin registrar lo producido (409)') : mal(`Finalizar sin producir respondió ${finalizarAntes.status}`)
  for (const fila of plan) await prod.exigir('PUT', `/producciones/${idProduccion}/productos/${fila.idProducto}/cantidad-producida`, { cantidadProducida: fila.cantidadPlanificada })
  ok('Cantidades producidas registradas')
  await prod.exigir('POST', `/producciones/${idProduccion}/finalizar`)
  const produccion = await prod.exigir('GET', `/producciones/${idProduccion}`)
  produccion.estado === 'FINALIZADA' ? ok('Producción FINALIZADA') : mal(`Producción en ${produccion.estado}`)
  pedido = await sesiones.ADMINISTRADOR.sesion.exigir('GET', `/pedidos/${idPedido}`)
  pedido.estado === 'LISTO_DESPACHO' ? ok('Pedido pasó a LISTO_DESPACHO') : mal(`Pedido en ${pedido.estado} tras finalizar`)

  titulo('6. Inventario tras la producción')
  const despuesProduccion = await todasLasExistencias(sesiones.BODEGA.sesion)
  let consumidos = 0
  for (const p of insumos) {
    const previo = antes.get(p.nombre)?.existencia, ahora = despuesProduccion.get(p.nombre)?.existencia
    if (previo == null || ahora == null) continue
    const consumo = redondear(previo - ahora)
    if (consumo < 0) mal(`${p.nombre} subió ${-consumo} durante la producción`)
    else if (consumo > 0) { consumidos++; ok(`${p.nombre}: −${consumo} ${despuesProduccion.get(p.nombre).unidad} (${previo} → ${ahora}; ${redondear(consumo / CANTIDAD_POR_PRODUCTO)} por unidad)`) }
  }
  consumidos > 0 ? ok(`Se descontaron ${consumidos} insumos según la receta`) : mal('La producción no descontó ningún insumo')
  for (const [nombre, cantidad] of Object.entries(PEDIDO)) {
    const real = redondear((despuesProduccion.get(nombre).existencia ?? 0) - (antes.get(nombre)?.existencia ?? 0))
    real === cantidad ? ok(`${nombre}: +${real} producidos`) : mal(`${nombre}: entraron ${real}, se esperaban ${cantidad}`)
  }

  titulo('7. Despacho (DESPACHO)')
  const despacho = await sesiones.DESPACHO.sesion.exigir('POST', `/pedidos/${idPedido}/despacho`, { observacion: `${MARCA} ciclo completo` })
  ok(`Despacho #${despacho.idDespacho} registrado`)
  pedido = await sesiones.ADMINISTRADOR.sesion.exigir('GET', `/pedidos/${idPedido}`)
  pedido.estado === 'DESPACHADO' ? ok('Pedido DESPACHADO') : mal(`Pedido en ${pedido.estado} tras despachar`)
  const repetido = await sesiones.DESPACHO.sesion.pedir('POST', `/pedidos/${idPedido}/despacho`, { observacion: MARCA })
  repetido.status === 409 ? ok('No permite despachar dos veces (409)') : mal(`Segundo despacho respondió ${repetido.status}`)
  const final = await todasLasExistencias(sesiones.BODEGA.sesion)
  for (const [nombre, cantidad] of Object.entries(PEDIDO)) {
    const real = redondear(despuesProduccion.get(nombre).existencia - final.get(nombre).existencia)
    real === cantidad ? ok(`${nombre}: −${real} despachados (queda ${final.get(nombre).existencia})`) : mal(`${nombre}: salieron ${real}, se esperaban ${cantidad}`)
  }
  const visto = await sesiones.SUCURSAL.sesion.pedir('GET', `/pedidos/${idPedido}/despacho`)
  visto.status === 200 ? ok('SUCURSAL ve el despacho de su pedido') : mal(`SUCURSAL no ve su despacho (${visto.status})`)

  console.log(`\nDatos creados (marcados ${MARCA}): pedido #${idPedido}, producción #${idProduccion}, despacho #${despacho.idDespacho}.`)
  for (const { sesion } of Object.values(sesiones)) await sesion.salir()
}

principal()
  .catch((error) => { fallos++; console.log(`\n✘ ${error.message}`) })
  .finally(() => {
    const aviso = sinReceta.length ? `\nAVISO: ${sinReceta.length} producto(s) final(es) sin receta activa no se probaron: ${sinReceta.join(', ')}.` : ''
    console.log(fallos ? `${aviso}\nRESULTADO: ${fallos} problema(s).`
      : sinReceta.length ? `${aviso}\nRESULTADO: correcto para los productos con receta.` : '\nRESULTADO: todo correcto.')
    process.exitCode = fallos ? 1 : 0
  })
