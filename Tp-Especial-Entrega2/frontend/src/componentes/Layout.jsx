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
          {sesion.rol === 'ADMINISTRADOR' && <NavLink to="/devoluciones">Devoluciones</NavLink>}
        </nav>
        <div className="barra-usuario">
          <span>{sesion.nombre}</span>
          <button type="button" className="boton-texto" onClick={salir}>Salir</button>
        </div>
      </header>
      {sesion.rol === 'ADMINISTRADOR' && (
        <p className="aviso-rol">
          Entraste como administrador: en <Link to="/devoluciones">Devoluciones</Link> aprobás o rechazás los pedidos de los clientes.
        </p>
      )}
      {sesion.rol !== 'CLIENTE' && sesion.rol !== 'ADMINISTRADOR' && (
        <p className="aviso-rol">
          Entraste con el rol {sesion.rol}. Esta web es para clientes: podés ver los eventos, pero no comprar.
        </p>
      )}
      <main className="contenido">{children}</main>
      <footer className="pie">Conectado al gateway en {API_URL}</footer>
    </div>
  )
}
