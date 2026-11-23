import { useEffect, useState } from 'react'

/** Cuenta regresiva hasta venceEn (la reserva dura 10 minutos). Avisa una vez cuando llega a cero. */
export default function Contador({ venceEn, alVencer }) {
  const fin = new Date(venceEn).getTime()
  const [restante, setRestante] = useState(() => Math.max(0, fin - Date.now()))

  useEffect(() => {
    const id = setInterval(() => {
      const r = Math.max(0, fin - Date.now())
      setRestante(r)
      if (r === 0) {
        clearInterval(id)
        alVencer?.()
      }
    }, 1000)
    return () => clearInterval(id)
  }, [fin, alVencer])

  const min = Math.floor(restante / 60000)
  const seg = Math.floor((restante % 60000) / 1000)
  return (
    <div className={`contador ${restante < 60000 ? 'contador-poco' : ''}`} role="timer" aria-live="off">
      <span className="contador-cifra">{min}:{String(seg).padStart(2, '0')}</span>
      <span className="contador-texto">para pagar antes de que se liberen tus lugares</span>
    </div>
  )
}
