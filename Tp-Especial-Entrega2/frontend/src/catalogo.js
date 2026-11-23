// Datos que casi no cambian (evento, lugar, nombres de sectores y asientos), guardados en memoria
// mientras la pagina esta abierta: "Mis entradas" y la compra los piden muchas veces.
import * as srv from './servicios'

const eventos = new Map()
const asientos = new Map()

/** Evento + lugar + nombre de cada sector. Si el lugar no responde, igual devuelve el evento. */
export function datosDeEvento(eventoId) {
  if (!eventos.has(eventoId)) {
    const pedido = (async () => {
      const ev = await srv.evento(eventoId)
      let lugar = null
      const sectores = new Map()
      if (ev.lugarId != null) {
        const [l, secs] = await Promise.all([
          srv.lugar(ev.lugarId).catch(() => null),
          srv.sectoresDeLugar(ev.lugarId).catch(() => []),
        ])
        lugar = l
        for (const s of secs) sectores.set(s.id, s)
      }
      return { evento: ev, lugar, sectores }
    })()
    pedido.catch(() => eventos.delete(eventoId)) // si fallo, que se pueda reintentar
    eventos.set(eventoId, pedido)
  }
  return eventos.get(eventoId)
}

/** asientoId -> { fila, numero } de un sector numerado. */
export function asientosDe(sectorId) {
  if (!asientos.has(sectorId)) {
    const pedido = srv.asientosDeSector(sectorId)
      .then((lista) => new Map(lista.map((a) => [a.id, a])))
    pedido.catch(() => asientos.delete(sectorId))
    asientos.set(sectorId, pedido)
  }
  return asientos.get(sectorId)
}

export function nombreSector(datos, sectorId) {
  return datos?.sectores.get(sectorId)?.nombre ?? `Sector ${sectorId}`
}

export function textoAsiento(mapaAsientos, asientoId) {
  const a = mapaAsientos?.get(asientoId)
  return a ? `Fila ${a.fila}, asiento ${a.numero}` : `Asiento ${asientoId}`
}
