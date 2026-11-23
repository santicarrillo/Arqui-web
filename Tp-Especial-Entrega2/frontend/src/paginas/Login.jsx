import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { tomarAviso, useSesion } from '../sesion'
import Mensaje from '../componentes/Mensaje'
import Escenario from '../componentes/Escenario'

export default function Login() {
  const { sesion, iniciar } = useSesion()
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const [aviso] = useState(tomarAviso)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  if (sesion) return <Navigate to="/" replace />

  async function enviar(e) {
    e.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      await iniciar(email, password)
      navegar(ubicacion.state?.desde ?? '/', { replace: true })
    } catch (err) {
      setError(err.message)
      setEnviando(false)
    }
  }

  return (
    <div className="acceso">
      <section className="acceso-portada">
        <Escenario />
        <h1 className="marca marca-grande">TicketYa</h1>
        <p>Entradas para recitales, teatro y festivales. Elegís tu lugar, pagás y el QR te queda en el celular.</p>
      </section>
      <form className="acceso-form" onSubmit={enviar}>
        <h2>Iniciar sesión</h2>
        <Mensaje tipo="info">{aviso}</Mensaje>
        <label>
          Email
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          Contraseña
          <input type="password" autoComplete="current-password" required value={password}
                 onChange={(e) => setPassword(e.target.value)} />
        </label>
        <Mensaje tipo="error">{error}</Mensaje>
        <button type="submit" className="boton boton-principal" disabled={enviando}>
          {enviando ? 'Entrando…' : 'Entrar'}
        </button>
        <p className="acceso-alternativa">¿No tenés cuenta? <Link to="/registro">Creá una</Link></p>
      </form>
    </div>
  )
}
