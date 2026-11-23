// Una funcion por endpoint del gateway que usa la web. Los contratos estan en
// documentacion/TicketYa-E2.postman_collection.json y en el Swagger de cada microservicio.
import { api } from './api'

// ---------- usuarios ----------
export const login = (email, password) =>
  api('/usuarios/login', { method: 'POST', body: { email, password } })

export const registrarCliente = (nombre, email, password) =>
  api('/usuarios', { method: 'POST', body: { nombre, email, password, rol: 'CLIENTE' } })

export const usuario = (id) => api(`/usuarios/${id}`)

// ---------- eventos ----------
export function buscarEventos(filtros = {}) {
  const params = new URLSearchParams()
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor !== undefined && valor !== null && String(valor).trim() !== '') params.set(clave, String(valor).trim())
  }
  const consulta = params.toString()
  return api(`/eventos${consulta ? `?${consulta}` : ''}`)
}

export const evento = (id) => api(`/eventos/${id}`)

// ---------- lugares ----------
export const lugar = (id) => api(`/lugares/${id}`)
export const sectoresDeLugar = (lugarId) => api(`/lugares/${lugarId}/sectores`)
export const asientosDeSector = (sectorId) => api(`/sectores/${sectorId}/asientos`)

// ---------- ventas ----------
export const disponibilidad = (eventoId, sectorId) =>
  api(`/disponibilidad?eventoId=${eventoId}&sectorId=${sectorId}`)

export const crearCompra = (pedido, clave) =>
  api('/compras', { method: 'POST', body: pedido, headers: { 'Idempotency-Key': clave } })

export const compra = (id) => api(`/compras/${id}`)
export const pagarCompra = (id, medioPago) => api(`/compras/${id}/pagar`, { method: 'POST', body: { medioPago } })
export const cancelarCompra = (id) => api(`/compras/${id}`, { method: 'DELETE' })

export const misEntradas = () => api('/entradas')
export const pedirDevolucion = (entradaId) => api(`/entradas/${entradaId}/devolucion`, { method: 'POST' })

// ---------- fila virtual ----------
export const estadoFila = (eventoId) => api(`/filas/${eventoId}`)
export const sacarTurno = (eventoId) => api(`/filas/${eventoId}/turno`, { method: 'POST' })
export const miTurno = (eventoId) => api(`/filas/${eventoId}/turno`)
