// Sesion del usuario: el token JWT que devuelve /usuarios/login, guardado en localStorage para que
// recargar la pagina no te saque. El gateway valida el token en cada llamada; si vence, responde 401
// y aca se cierra la sesion.
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { usarToken } from './api'
import * as srv from './servicios'

const CLAVE = 'ticketya.sesion'
const AVISO = 'ticketya.aviso'
const SesionContext = createContext(null)

function leerGuardada() {
  try {
    const s = JSON.parse(localStorage.getItem(CLAVE))
    if (s && s.expiraEn > Date.now()) return s
  } catch { /* nada guardado o dato roto */ }
  localStorage.removeItem(CLAVE)
  return null
}

export function SesionProvider({ children }) {
  const [sesion, setSesion] = useState(leerGuardada)

  const cerrar = useCallback((aviso) => {
    localStorage.removeItem(CLAVE)
    if (aviso) sessionStorage.setItem(AVISO, aviso)
    setSesion(null)
  }, [])

  // El token se configura en el cliente HTTP antes de que cualquier pagina haga su primera llamada.
  usarToken(sesion?.token ?? null, () => cerrar('Tu sesión venció. Iniciá sesión de nuevo.'))

  useEffect(() => {
    if (sesion) localStorage.setItem(CLAVE, JSON.stringify(sesion))
  }, [sesion])

  const iniciar = useCallback(async (email, password) => {
    const t = await srv.login(email, password)
    usarToken(t.token)
    // el nombre no viene en el token: se pide a usuarios (si falla, no impide entrar)
    const datos = await srv.usuario(t.usuarioId).catch(() => null)
    setSesion({
      token: t.token,
      usuarioId: t.usuarioId,
      rol: t.rol,
      nombre: datos?.nombre ?? email,
      expiraEn: Date.now() + t.expiraEnSegundos * 1000,
    })
  }, [])

  const registrar = useCallback(async (nombre, email, password) => {
    await srv.registrarCliente(nombre, email, password)
    await iniciar(email, password)
  }, [iniciar])

  const valor = useMemo(
    () => ({ sesion, iniciar, registrar, salir: () => cerrar() }),
    [sesion, iniciar, registrar, cerrar],
  )
  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>
}

export const useSesion = () => useContext(SesionContext)

/** Mensaje pendiente para la pantalla de login (por ejemplo, "tu sesion vencio"). Se lee una sola vez. */
export function tomarAviso() {
  const aviso = sessionStorage.getItem(AVISO)
  sessionStorage.removeItem(AVISO)
  return aviso
}
