import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import * as srv from '../servicios'
import { nuevaClave } from '../api'
import { asientosDe, datosDeEvento, nombreSector, textoAsiento } from '../catalogo'
import { capital, fechaLarga, pesos } from '../formato'
import Mensaje, { Cargando } from '../componentes/Mensaje'
import MapaAsientos from '../componentes/MapaAsientos'
import FilaVirtual from '../componentes/FilaVirtual'

export default function EventoDetalle() {
  const eventoId = Number(useParams().id)
  const navegar = useNavigate()

  const [datos, setDatos] = useState(null)          // { evento, lugar, sectores }
  const [disp, setDisp] = useState({})              // sectorId -> disponibilidad de ventas
  const [fila, setFila] = useState(null)
  const [turno, setTurno] = useState(null)
  const [abierto, setAbierto] = useState(null)      // sector numerado con el plano abierto
  const [mapas, setMapas] = useState({})            // sectorId -> asientos del sector
  const [elegidos, setElegidos] = useState({})      // sectorId -> [asientoId] (numerados)
  const [cantidades, setCantidades] = useState({})  // sectorId -> cantidad (zonas)
  const [error, setError] = useState(null)
  const [comprando, setComprando] = useState(false)
  const clave = useRef(nuevaClave())

  async function cargarDisponibilidad(ev) {
    const lista = await Promise.all(ev.sectores.map((s) => srv.disponibilidad(eventoId, s.sectorId).catch(() => null)))
    const nueva = Object.fromEntries(ev.sectores.map((s, i) => [s.sectorId, lista[i]]))
    setDisp(nueva)
    return nueva
  }

  useEffect(() => {
    let vigente = true
    ;(async () => {
      try {
        const d = await datosDeEvento(eventoId)
        if (!vigente) return
        setDatos(d)
        await cargarDisponibilidad(d.evento)
        const f = await srv.estadoFila(eventoId).catch(() => null)
        if (!vigente) return
        setFila(f)
        if (f?.activa) setTurno(await srv.miTurno(eventoId).catch(() => null))
      } catch (err) {
        if (vigente) setError(err.message)
      }
    })()
    return () => { vigente = false }
  }, [eventoId]) // eslint-disable-line react-hooks/exhaustive-deps

  // cada pedido distinto lleva su propia Idempotency-Key; reintentar el mismo pedido usa la misma
  useEffect(() => { clave.current = nuevaClave() }, [elegidos, cantidades])

  if (error && !datos) return <Mensaje tipo="error">{error}</Mensaje>
  if (!datos) return <Cargando texto="Cargando el evento…" />

  const { evento, lugar } = datos
  const limite = evento.limitePorCliente ?? 6
  const totalEntradas = Object.values(elegidos).reduce((n, l) => n + l.length, 0)
    + Object.values(cantidades).reduce((n, c) => n + c, 0)
  const precioDe = (sectorId) => evento.sectores.find((s) => s.sectorId === sectorId)?.precio ?? 0
  const total = Object.entries(elegidos).reduce((t, [s, l]) => t + l.length * precioDe(Number(s)), 0)
    + Object.entries(cantidades).reduce((t, [s, c]) => t + c * precioDe(Number(s)), 0)
  const filaActiva = fila?.activa === true
  const puedeComprar = totalEntradas > 0 && (!filaActiva || turno?.puedeComprar)

  async function abrirPlano(sectorId) {
    setAbierto(abierto === sectorId ? null : sectorId)
    if (!mapas[sectorId]) {
      try {
        const m = await asientosDe(sectorId)
        setMapas((prev) => ({ ...prev, [sectorId]: m }))
      } catch (err) {
        setError(err.message)
      }
    }
  }

  function elegirAsiento(sectorId, asientoId) {
    const actuales = elegidos[sectorId] ?? []
    if (actuales.includes(asientoId)) {
      setElegidos({ ...elegidos, [sectorId]: actuales.filter((a) => a !== asientoId) })
    } else if (totalEntradas >= limite) {
      setError(`Podés comprar hasta ${limite} entradas para este evento.`)
    } else {
      setError(null)
      setElegidos({ ...elegidos, [sectorId]: [...actuales, asientoId] })
    }
  }

  function cambiarCantidad(sectorId, delta, libres) {
    const actual = cantidades[sectorId] ?? 0
    const nueva = actual + delta
    if (nueva < 0 || nueva > libres) return
    if (delta > 0 && totalEntradas >= limite) {
      setError(`Podés comprar hasta ${limite} entradas para este evento.`)
      return
    }
    setError(null)
    setCantidades({ ...cantidades, [sectorId]: nueva })
  }

  async function comprar() {
    setError(null)
    setComprando(true)
    const lugares = [
      ...Object.entries(elegidos).flatMap(([s, ids]) => ids.map((a) => ({ sectorId: Number(s), asientoId: a }))),
      ...Object.entries(cantidades).filter(([, c]) => c > 0).map(([s, c]) => ({ sectorId: Number(s), cantidad: c })),
    ]
    try {
      const c = await srv.crearCompra({ eventoId, lugares }, clave.current)
      navegar(`/compras/${c.compraId}`)
    } catch (err) {
      setError(err.message)
      setComprando(false)
      if (err.codigo?.startsWith('TURNO_')) {
        setFila(await srv.estadoFila(eventoId).catch(() => fila))
        setTurno(await srv.miTurno(eventoId).catch(() => null))
      } else if (err.status === 409) {
        // alguien se adelanto: se actualiza la disponibilidad y se sacan los asientos que ya no estan libres
        const nueva = await cargarDisponibilidad(evento)
        setElegidos((prev) => Object.fromEntries(Object.entries(prev).map(([s, ids]) => {
          const libres = new Set(nueva[s]?.asientosLibres ?? [])
          return [s, ids.filter((a) => libres.has(a))]
        })))
      }
    }
  }

  const lineas = [
    ...Object.entries(elegidos).filter(([, l]) => l.length > 0).map(([s, ids]) => ({
      clave: `n${s}`,
      texto: `${nombreSector(datos, Number(s))}: ${ids.map((a) => textoAsiento(mapas[s], a)).join('; ')}`,
      importe: ids.length * precioDe(Number(s)),
    })),
    ...Object.entries(cantidades).filter(([, c]) => c > 0).map(([s, c]) => ({
      clave: `z${s}`,
      texto: `${nombreSector(datos, Number(s))}: ${c} ${c === 1 ? 'entrada' : 'entradas'}`,
      importe: c * precioDe(Number(s)),
    })),
  ]

  return (
    <div className="detalle">
      <section className="detalle-info">
        <Link to="/" className="volver">Volver a eventos</Link>
        <h1 className="titulo">{evento.nombre}</h1>
        <p className="detalle-cuando">{fechaLarga(evento.fechaHora)} h</p>
        <p>
          {capital(evento.tipo)}
          {lugar ? ` en ${lugar.nombre}, ${lugar.direccion}, ${lugar.ciudad}` : evento.ciudad ? ` en ${evento.ciudad}` : ''}
        </p>
        {evento.participantes?.length > 0 && (
          <p>Con {evento.participantes.map((p) => p.nombre).join(', ')}</p>
        )}
        {evento.descripcion && <p className="detalle-descripcion">{evento.descripcion}</p>}

        {evento.estado !== 'PUBLICADO' ? (
          <Mensaje tipo="info">Este evento no está a la venta ({evento.estado.toLowerCase()}).</Mensaje>
        ) : (
          <>
            <h2 className="subtitulo">Elegí tus lugares</h2>
            <p className="nota">Hasta {limite} entradas por persona.</p>
            <ul className="sectores">
              {evento.sectores.map((s) => {
                const d = disp[s.sectorId]
                const libres = d?.libres ?? 0
                const elegidosAca = elegidos[s.sectorId] ?? []
                return (
                  <li key={s.sectorId} className="sector">
                    <div className="sector-cabecera">
                      <div>
                        <h3>{nombreSector(datos, s.sectorId)}</h3>
                        <p className="nota">
                          {s.numerado ? 'Asientos numerados' : 'Sin numerar'}.{' '}
                          {d == null ? 'No se pudo consultar la disponibilidad.' : libres === 0 ? 'Agotado.' : `Quedan ${libres}.`}
                        </p>
                      </div>
                      <p className="sector-precio">{pesos(s.precio)}</p>
                      {s.numerado ? (
                        <button type="button" className="boton boton-secundario" disabled={libres === 0 && elegidosAca.length === 0}
                                aria-expanded={abierto === s.sectorId} onClick={() => abrirPlano(s.sectorId)}>
                          {abierto === s.sectorId ? 'Cerrar plano' : 'Elegir asientos'}
                        </button>
                      ) : (
                        <div className="cantidad" aria-label={`Cantidad en ${nombreSector(datos, s.sectorId)}`}>
                          <button type="button" onClick={() => cambiarCantidad(s.sectorId, -1, libres)}
                                  disabled={!cantidades[s.sectorId]} aria-label="Una menos">−</button>
                          <output>{cantidades[s.sectorId] ?? 0}</output>
                          <button type="button" onClick={() => cambiarCantidad(s.sectorId, 1, libres)}
                                  disabled={(cantidades[s.sectorId] ?? 0) >= libres} aria-label="Una más">+</button>
                        </div>
                      )}
                    </div>
                    {s.numerado && abierto === s.sectorId && (
                      mapas[s.sectorId]
                        ? <MapaAsientos asientos={mapas[s.sectorId]} libres={new Set(d?.asientosLibres ?? [])}
                                        elegidos={new Set(elegidosAca)} onElegir={(a) => elegirAsiento(s.sectorId, a)} />
                        : <Cargando texto="Cargando el plano…" />
                    )}
                  </li>
                )
              })}
            </ul>
          </>
        )}
      </section>

      {evento.estado === 'PUBLICADO' && (
        <aside className="pedido" aria-label="Tu pedido">
          <h2 className="subtitulo">Tu pedido</h2>
          {filaActiva && <FilaVirtual eventoId={eventoId} turno={turno} setTurno={setTurno} setError={setError} />}
          {lineas.length === 0 ? (
            <p className="nota">Todavía no elegiste lugares.</p>
          ) : (
            <ul className="pedido-lineas">
              {lineas.map((l) => (
                <li key={l.clave}><span>{l.texto}</span><span>{pesos(l.importe)}</span></li>
              ))}
            </ul>
          )}
          <p className="pedido-total"><span>Total</span><span>{pesos(total)}</span></p>
          <Mensaje tipo="error">{error}</Mensaje>
          <button type="button" className="boton boton-principal boton-ancho" disabled={!puedeComprar || comprando} onClick={comprar}>
            {comprando ? 'Reservando…' : totalEntradas > 0 ? `Comprar ${totalEntradas} ${totalEntradas === 1 ? 'entrada' : 'entradas'}` : 'Comprar'}
          </button>
          <p className="nota">Al comprar, tus lugares quedan reservados 10 minutos mientras pagás.</p>
        </aside>
      )}
    </div>
  )
}
