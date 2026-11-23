import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import * as srv from '../servicios'
import { asientosDe, datosDeEvento, nombreSector, textoAsiento } from '../catalogo'
import Mensaje, { Cargando } from '../componentes/Mensaje'
import Entrada from '../componentes/Entrada'

export default function MisEntradas() {
  const [grupos, setGrupos] = useState(null)        // [{ datos, entradas }], del evento mas cercano al mas lejano
  const [asientos, setAsientos] = useState({})
  const [error, setError] = useState(null)

  useEffect(() => {
    let vigente = true
    ;(async () => {
      try {
        const entradas = await srv.misEntradas()
        const porEvento = new Map()
        for (const e of entradas) {
          if (!porEvento.has(e.eventoId)) porEvento.set(e.eventoId, [])
          porEvento.get(e.eventoId).push(e)
        }
        const ids = [...porEvento.keys()]
        const datos = await Promise.all(ids.map((id) => datosDeEvento(id).catch(() => null)))
        const lista = ids.map((id, i) => ({ eventoId: id, datos: datos[i], entradas: porEvento.get(id) }))
        lista.sort((a, b) => String(a.datos?.evento.fechaHora ?? '').localeCompare(String(b.datos?.evento.fechaHora ?? '')))
        if (!vigente) return
        setGrupos(lista)

        const sectores = [...new Set(entradas.filter((e) => e.asientoId != null).map((e) => e.sectorId))]
        const mapas = await Promise.all(sectores.map((s) => asientosDe(s).catch(() => null)))
        if (vigente) setAsientos(Object.fromEntries(sectores.map((s, i) => [s, mapas[i]])))
      } catch (err) {
        if (vigente) setError(err.message)
      }
    })()
    return () => { vigente = false }
  }, [])

  if (error) return <Mensaje tipo="error">{error}</Mensaje>
  if (!grupos) return <Cargando texto="Buscando tus entradas…" />

  return (
    <>
      <h1 className="titulo">Mis entradas</h1>
      {grupos.length === 0 && (
        <p className="vacio">Todavía no compraste entradas. <Link to="/">Buscá un evento</Link> y elegí tus lugares.</p>
      )}
      {grupos.map((g) => (
        <section key={g.eventoId} className="grupo" aria-label={g.datos?.evento.nombre ?? `Evento ${g.eventoId}`}>
          <div className="entradas">
            {g.entradas.map((e) => (
              <Entrada key={e.entradaId} evento={g.datos?.evento} lugar={g.datos?.lugar} entrada={e}
                       sector={nombreSector(g.datos, e.sectorId)}
                       asiento={e.asientoId != null ? textoAsiento(asientos[e.sectorId], e.asientoId) : null}>
                {e.estado === 'VALIDA' && <Devolucion entradaId={e.entradaId} />}
              </Entrada>
            ))}
          </div>
        </section>
      ))}
    </>
  )
}

/** Pedir la devolucion de una entrada: la aprueba un administrador (hasta 72 h antes del evento). */
function Devolucion({ entradaId }) {
  const [paso, setPaso] = useState('inicio')        // inicio | confirmar | pedida
  const [error, setError] = useState(null)

  async function pedir() {
    setError(null)
    try {
      await srv.pedirDevolucion(entradaId)
      setPaso('pedida')
    } catch (err) {
      setError(err.message)
      setPaso('inicio')
    }
  }

  if (paso === 'pedida') {
    return <p className="entrada-nota">Pediste la devolución. Cuando la apruebe un administrador, te reintegramos el dinero.</p>
  }
  return (
    <div className="entrada-acciones">
      {paso === 'inicio' ? (
        <button type="button" className="boton-texto" onClick={() => setPaso('confirmar')}>Pedir devolución</button>
      ) : (
        <>
          <span>¿Devolver esta entrada?</span>
          <button type="button" className="boton-texto boton-peligro" onClick={pedir}>Sí, pedir devolución</button>
          <button type="button" className="boton-texto" onClick={() => setPaso('inicio')}>No</button>
        </>
      )}
      {error && <p className="entrada-nota entrada-nota-error" role="alert">{error}</p>}
    </div>
  )
}
