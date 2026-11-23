import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import * as srv from '../servicios'
import { asientosDe, datosDeEvento, nombreSector, textoAsiento } from '../catalogo'
import { fechaLarga, pesos } from '../formato'
import Mensaje, { Cargando } from '../componentes/Mensaje'
import Contador from '../componentes/Contador'
import Entrada from '../componentes/Entrada'

const MEDIOS = [
  { valor: 'TARJETA', texto: 'Tarjeta de crédito' },
  { valor: 'DEBITO', texto: 'Tarjeta de débito' },
  { valor: 'MERCADO_PAGO', texto: 'Mercado Pago' },
]

export default function Compra() {
  const compraId = Number(useParams().id)
  const navegar = useNavigate()
  const [compra, setCompra] = useState(null)
  const [datos, setDatos] = useState(null)
  const [asientos, setAsientos] = useState({})
  const [medio, setMedio] = useState(MEDIOS[0].valor)
  const [error, setError] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const [vencida, setVencida] = useState(false)

  useEffect(() => {
    let vigente = true
    ;(async () => {
      try {
        const c = await srv.compra(compraId)
        if (!vigente) return
        setCompra(c)
        const d = await datosDeEvento(c.eventoId).catch(() => null)
        if (!vigente) return
        setDatos(d)
        // nombres de los asientos de los sectores numerados de la compra
        const sectores = [...new Set(c.detalle.filter((x) => x.asientoId != null).map((x) => x.sectorId))]
        const mapas = await Promise.all(sectores.map((s) => asientosDe(s).catch(() => null)))
        if (vigente) setAsientos(Object.fromEntries(sectores.map((s, i) => [s, mapas[i]])))
      } catch (err) {
        if (vigente) setError(err.message)
      }
    })()
    return () => { vigente = false }
  }, [compraId])

  const alVencer = useCallback(() => setVencida(true), [])

  if (error && !compra) return <Mensaje tipo="error">{error}</Mensaje>
  if (!compra) return <Cargando texto="Cargando tu compra…" />

  const evento = datos?.evento

  async function pagar() {
    setError(null)
    setOcupado(true)
    try {
      setCompra(await srv.pagarCompra(compraId, medio))
    } catch (err) {
      if (err.status === 402) setCompra({ ...compra, estado: 'RECHAZADA' })
      if (err.codigo === 'COMPRA_VENCIDA') setCompra({ ...compra, estado: 'VENCIDA' })
      setError(err.message)
    } finally {
      setOcupado(false)
    }
  }

  async function cancelar() {
    setError(null)
    setOcupado(true)
    try {
      await srv.cancelarCompra(compraId)
      navegar(`/eventos/${compra.eventoId}`)
    } catch (err) {
      setError(err.message)
      setOcupado(false)
    }
  }

  const detalleTexto = (d) => {
    const sector = nombreSector(datos, d.sectorId)
    return d.asientoId != null ? `${sector}, ${textoAsiento(asientos[d.sectorId], d.asientoId)}` : sector
  }

  return (
    <div className="compra">
      <Link to={`/eventos/${compra.eventoId}`} className="volver">Volver al evento</Link>
      <h1 className="titulo">{evento?.nombre ?? `Compra ${compra.compraId}`}</h1>
      {evento && <p className="detalle-cuando">{fechaLarga(evento.fechaHora)} h</p>}

      {compra.estado === 'PENDIENTE' && !vencida && (
        <section className="compra-pago">
          <Contador venceEn={compra.venceEn} alVencer={alVencer} />
          <ul className="pedido-lineas">
            {compra.detalle.map((d) => (
              <li key={d.entradaId}><span>{detalleTexto(d)}</span><span>{pesos(d.precio)}</span></li>
            ))}
          </ul>
          <p className="pedido-total"><span>Total</span><span>{pesos(compra.total)}</span></p>
          <fieldset className="medios">
            <legend>Medio de pago</legend>
            {MEDIOS.map((m) => (
              <label key={m.valor} className="medio">
                <input type="radio" name="medio" value={m.valor} checked={medio === m.valor} onChange={() => setMedio(m.valor)} />
                {m.texto}
              </label>
            ))}
          </fieldset>
          <Mensaje tipo="error">{error}</Mensaje>
          <div className="compra-acciones">
            <button type="button" className="boton boton-principal" disabled={ocupado} onClick={pagar}>
              {ocupado ? 'Procesando…' : `Pagar ${pesos(compra.total)}`}
            </button>
            <button type="button" className="boton-texto" disabled={ocupado} onClick={cancelar}>Cancelar compra</button>
          </div>
        </section>
      )}

      {(compra.estado === 'VENCIDA' || (compra.estado === 'PENDIENTE' && vencida)) && (
        <Mensaje tipo="error" accion={<Link className="boton boton-secundario" to={`/eventos/${compra.eventoId}`}>Elegir de nuevo</Link>}>
          Se terminó el tiempo para pagar y tus lugares se liberaron.
        </Mensaje>
      )}

      {compra.estado === 'RECHAZADA' && (
        <Mensaje tipo="error" accion={<Link className="boton boton-secundario" to={`/eventos/${compra.eventoId}`}>Elegir de nuevo</Link>}>
          El pago fue rechazado y tus lugares se liberaron. Podés intentar con otra compra.
        </Mensaje>
      )}

      {compra.estado === 'PAGADA' && (
        <section>
          <Mensaje tipo="ok" accion={<Link className="boton boton-secundario" to="/entradas">Ver mis entradas</Link>}>
            Listo, pagaste {pesos(compra.total)}. Mostrá el QR de cada entrada en la puerta.
          </Mensaje>
          <div className="entradas">
            {compra.detalle.map((d) => (
              <Entrada key={d.entradaId} evento={evento} lugar={datos?.lugar} entrada={{ ...d, eventoId: compra.eventoId }}
                       sector={nombreSector(datos, d.sectorId)}
                       asiento={d.asientoId != null ? textoAsiento(asientos[d.sectorId], d.asientoId) : null} />
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
