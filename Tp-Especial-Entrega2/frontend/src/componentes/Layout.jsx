import { Link, NavLink } from 'react-router-dom'
import { useSesion } from '../sesion'
import { API_URL } from '../api'

export default function Layout({ children }) {
  const { sesion, salir } = useSesion()
  return (
    <div className="pagina">
      <header className="barra">
        <Link to="/" className="marca" aria-label="TicketYa, ir a eventos">TicketYa</Link>
        <nav className="barra-nav" aria-label="Principal">
          <NavLink to="/" end>Eventos</NavLink>
          <NavLink to="/entradas">Mis entradas</NavLink>
        </nav>
        <div className="barra-usuario">
          <span>{sesion.nombre}</span>
          <button type="button" className="boton-texto" onClick={salir}>Salir</button>
        </div>
      </header>
      {sesion.rol !== 'CLIENTE' && (
        <p className="aviso-rol">
          Entraste con el rol {sesion.rol}. Esta web está pensada para clientes: si comprás, la compra queda a tu nombre.
        </p>
      )}
      <main className="contenido">{children}</main>
      <footer className="pie">Conectado al gateway en {API_URL}</footer>
    </div>
  )
}
