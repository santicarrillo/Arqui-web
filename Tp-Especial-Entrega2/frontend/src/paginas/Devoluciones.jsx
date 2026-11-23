import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import * as srv from '../servicios'
import { datosDeEvento } from '../catalogo'
import { useSesion } from '../sesion'
import { fechaLarga, pesos } from '../formato'
import Mensaje, { Cargando } from '../componentes/Mensaje'

/**
 * Devoluciones pendientes, para el administrador: las pidieron los clientes desde "Mis entradas".
 * Aprobar deja la entrada DEVUELTA, libera el lugar y pide el reintegro a pagos; rechazar la deja valida.
 * Los permisos los controla el backend (GET /devoluciones y PUT /entradas/{id}/devolucion piden ADMINISTRADOR).
 */
export default function Devoluciones() {
  const { sesion } = useSesion()
  const [lista, setLista] = useState(null)
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)

  useEffect(() => {
    if (sesion.rol !== 'ADMINISTRADOR') return
    let vigente = true
    ;(async () => {
      try {
        const pendientes = await srv.devoluciones('PENDIENTE')
        const conDatos = await Promise.all(pendientes.map(async (d) => {
          const [datos, cliente] = await Promise.all([
            d.eventoId != null ? datosDeEvento(d.eventoId).catch(() => null) : null,
            d.clienteId != null ? srv.usuario(d.clienteId).catch(() => null) : null,
          ])
          return { ...d, evento: datos?.evento ?? null, cliente }
        }))
        if (vigente) setLista(conDatos)
      } catch (err) {
        if (vigente) setError(err.message)
      }
    })()
    return () => { vigente = false }
  }, [sesion.rol])

  if (sesion.rol !== 'ADMINISTRADOR') return <Navigate to="/" replace />
  if (error) return <Mensaje tipo="error">{error}</Mensaje>
  if (!lista) return <Cargando texto="Buscando devoluciones pendientes…" />

  function resuelta(d, aprobada) {
    setLista((actual) => actual.filter((x) => x.devolucionId !== d.devolucionId))
    setAviso(`${aprobada ? 'Aprobaste' : 'Rechazaste'} la devolución de la entrada #${d.entradaId}`
      + (aprobada ? ': el lugar quedó libre y se pidió el reintegro.' : ': la entrada sigue válida.'))
  }

  return (
    <>
      <h1 className="titulo">Devoluciones</h1>
      <p className="nota">Pedidos de los clientes esperando tu aprobación. Se pueden pedir hasta 72 horas antes del evento.</p>
      <Mensaje tipo="ok">{aviso}</Mensaje>
      {lista.length === 0 ? (
        <p className="vacio">No hay devoluciones pendientes.</p>
      ) : (
        <ul className="devoluciones">
          {lista.map((d) => <Pedido key={d.devolucionId} d={d} alResolver={resuelta} />)}
        </ul>
      )}
    </>
  )
}

function Pedido({ d, alResolver }) {
  const [confirmar, setConfirmar] = useState(null)   // null | 'aprobar' | 'rechazar'
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)

  async function resolver() {
    const aprobar = confirmar === 'aprobar'
    setEnviando(true)
    setError(null)
    try {
      await srv.resolverDevolucion(d.entradaId, aprobar)
      alResolver(d, aprobar)
    } catch (err) {
      setError(err.message)
      setEnviando(false)
      setConfirmar(null)
    }
  }

  return (
    <li className="devolucion">
      <div className="devolucion-datos">
        <h2>{d.evento?.nombre ?? `Evento ${d.eventoId ?? '—'}`}</h2>
        {d.evento?.fechaHora && <p className="nota">{fechaLarga(d.evento.fechaHora)}</p>}
        <dl className="entrada-datos">
          <div><dt>Entrada</dt><dd>#{d.entradaId}</dd></div>
          <div><dt>Cliente</dt><dd>{d.cliente?.nombre ?? `Usuario ${d.clienteId ?? '—'}`}</dd></div>
          {d.cliente?.email && <div><dt>Email</dt><dd>{d.cliente.email}</dd></div>}
          <div><dt>A reintegrar</dt><dd>{d.monto != null ? pesos(d.monto) : '—'}</dd></div>
          <div><dt>Pedida</dt><dd>{fechaLarga(d.fechaSolicitud)}</dd></div>
        </dl>
      </div>
      <div className="devolucion-acciones">
        {confirmar === null ? (
          <>
            <button type="button" className="boton boton-principal" onClick={() => setConfirmar('aprobar')}>Aprobar</button>
            <button type="button" className="boton boton-secundario" onClick={() => setConfirmar('rechazar')}>Rechazar</button>
          </>
        ) : (
          <>
            <span>{confirmar === 'aprobar' ? '¿Aprobar y reintegrar el dinero?' : '¿Rechazar el pedido?'}</span>
            <button type="button" className={`boton ${confirmar === 'aprobar' ? 'boton-principal' : 'boton-secundario'}`}
                    onClick={resolver} disabled={enviando}>
              {enviando ? 'Enviando…' : 'Sí, confirmar'}
            </button>
            <button type="button" className="boton-texto" onClick={() => setConfirmar(null)} disabled={enviando}>Cancelar</button>
          </>
        )}
        {error && <p className="entrada-nota entrada-nota-error" role="alert">{error}</p>}
      </div>
    </li>
  )
}
