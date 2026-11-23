import { useEffect } from 'react'
import * as srv from '../servicios'
import { hora } from '../formato'

// Fila virtual (F10): si el evento la tiene activa, solo compra quien tiene el turno habilitado.
// Mientras el cliente espera, se consulta su turno cada 3 segundos.
export default function FilaVirtual({ eventoId, turno, setTurno, setError }) {
  const esperando = turno?.estado === 'ESPERANDO'

  useEffect(() => {
    if (!esperando) return undefined
    const id = setInterval(() => {
      srv.miTurno(eventoId).then(setTurno).catch(() => {})
    }, 3000)
    return () => clearInterval(id)
  }, [esperando, eventoId, setTurno])

  async function sacar() {
    setError(null)
    try {
      setTurno(await srv.sacarTurno(eventoId))
    } catch (err) {
      setError(err.message)
    }
  }

  if (!turno || turno.estado === 'VENCIDO') {
    return (
      <div className="fila">
        <p>
          {turno ? 'Tu turno para comprar venció.' : 'Este evento tiene mucha demanda y usa fila virtual: para comprar necesitás un turno.'}
        </p>
        <button type="button" className="boton boton-secundario" onClick={sacar}>
          {turno ? 'Sacar otro turno' : 'Sacar turno'}
        </button>
      </div>
    )
  }
  if (esperando) {
    return (
      <div className="fila" aria-live="polite">
        <p className="fila-numero">Tu número: {turno.numero}</p>
        <p>
          {turno.personasAntes === 0
            ? 'Sos el próximo. En unos segundos se habilita tu turno.'
            : `Hay ${turno.personasAntes} ${turno.personasAntes === 1 ? 'persona' : 'personas'} antes que vos. No cierres esta página.`}
        </p>
      </div>
    )
  }
  return (
    <div className="fila fila-turno" aria-live="polite">
      <p className="fila-numero">Es tu turno</p>
      <p>Tenés hasta las {hora(turno.venceEn)} para comprar.</p>
    </div>
  )
}
