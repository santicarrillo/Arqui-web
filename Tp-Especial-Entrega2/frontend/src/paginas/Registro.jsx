import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useSesion } from '../sesion'
import Mensaje from '../componentes/Mensaje'
import Escenario from '../componentes/Escenario'

export default function Registro() {
  const { sesion, registrar } = useSesion()
  const navegar = useNavigate()
  const [nombre, setNombre] = useState('')
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
      await registrar(nombre.trim(), email.trim(), password)
      navegar('/', { replace: true })
    } catch (err) {
      setError(err.codigo === 'EMAIL_DUPLICADO' ? 'Ya hay una cuenta con ese email. Iniciá sesión.' : err.message)
      setEnviando(false)
    }
  }

  return (
    <div className="acceso">
      <section className="acceso-portada">
        <Escenario />
        <h1 className="marca marca-grande">TicketYa</h1>
        <p>Creá tu cuenta de cliente para comprar entradas y tenerlas siempre a mano.</p>
      </section>
      <form className="acceso-form" onSubmit={enviar}>
        <h2>Crear cuenta</h2>
        <label>
          Nombre
          <input autoComplete="name" required value={nombre} onChange={(e) => setNombre(e.target.value)} />
        </label>
        <label>
          Email
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          Contraseña
          <input type="password" autoComplete="new-password" required minLength={4} value={password}
                 onChange={(e) => setPassword(e.target.value)} />
        </label>
        <Mensaje tipo="error">{error}</Mensaje>
        <button type="submit" className="boton boton-principal" disabled={enviando}>
          {enviando ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
        <p className="acceso-alternativa">¿Ya tenés cuenta? <Link to="/login">Iniciá sesión</Link></p>
      </form>
    </div>
  )
}
