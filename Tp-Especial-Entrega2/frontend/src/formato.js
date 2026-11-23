const PESOS = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 })
// hourCycle h23: "21:00", no "09:00 p. m." (algunos navegadores usan 12 horas para es-AR)
const FECHA_LARGA = new Intl.DateTimeFormat('es-AR', {
  weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})
const DIA = new Intl.DateTimeFormat('es-AR', { day: 'numeric' })
const MES = new Intl.DateTimeFormat('es-AR', { month: 'short' })
const HORA = new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })

// El backend manda fechas sin zona ("2026-12-20T21:00:00"): JavaScript las toma como hora local.
const fecha = (valor) => (valor instanceof Date ? valor : new Date(valor))

export const pesos = (monto) => PESOS.format(Number(monto ?? 0))
export const fechaLarga = (valor) => {
  const texto = FECHA_LARGA.format(fecha(valor))
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}
export const dia = (valor) => DIA.format(fecha(valor))
export const mes = (valor) => MES.format(fecha(valor)).replace('.', '')
export const hora = (valor) => HORA.format(fecha(valor))

/** "recital" -> "Recital" */
export const capital = (texto) => (texto ? texto.charAt(0).toUpperCase() + texto.slice(1).toLowerCase() : '')

export const hoyISO = () => {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}
