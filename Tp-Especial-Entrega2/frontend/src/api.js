// Cliente HTTP de la web: todas las llamadas pasan por el API Gateway.
// - agrega el token (Authorization: Bearer ...) si hay sesion;
// - traduce los errores del backend ({error, mensaje}) a un ApiError con codigo y mensaje;
// - si el gateway responde 401 con sesion abierta (token vencido o invalido), avisa para cerrar la sesion.

export const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  constructor(status, codigo, mensaje) {
    super(mensaje)
    this.status = status
    this.codigo = codigo
  }
}

let token = null
let alVencerSesion = () => {}

export function usarToken(nuevoToken, onVencida) {
  token = nuevoToken
  if (onVencida) alVencerSesion = onVencida
}

const MENSAJES_POR_ESTADO = {
  401: 'Tenés que iniciar sesión.',
  403: 'No tenés permiso para hacer esto.',
  404: 'No se encontró lo que buscabas.',
  503: 'El servicio no está disponible en este momento. Probá de nuevo en unos segundos.',
}

export async function api(ruta, { method = 'GET', body, headers = {} } = {}) {
  const encabezados = { ...headers }
  if (body !== undefined) encabezados['Content-Type'] = 'application/json'
  if (token) encabezados.Authorization = `Bearer ${token}`

  let respuesta
  try {
    respuesta = await fetch(API_URL + ruta, {
      method,
      headers: encabezados,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'SIN_CONEXION',
      `No se pudo conectar con el gateway en ${API_URL}. Revisá que el sistema esté levantado.`)
  }

  if (respuesta.status === 401 && token) alVencerSesion()

  const texto = await respuesta.text()
  let datos = null
  if (texto) {
    try { datos = JSON.parse(texto) } catch { datos = null }
  }

  if (!respuesta.ok) {
    const codigo = datos?.error ?? respuesta.headers.get('X-Auth-Error') ?? `HTTP_${respuesta.status}`
    const mensaje = datos?.mensaje ?? MENSAJES_POR_ESTADO[respuesta.status] ?? 'Algo salió mal. Probá de nuevo.'
    throw new ApiError(respuesta.status, codigo, mensaje)
  }
  return datos
}

/** Clave para que repetir un pedido de compra no cree dos compras (Idempotency-Key). */
export function nuevaClave() {
  if (globalThis.crypto?.randomUUID) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
