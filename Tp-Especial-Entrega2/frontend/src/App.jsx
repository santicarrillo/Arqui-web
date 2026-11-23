import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useSesion } from './sesion'
import Layout from './componentes/Layout'
import Login from './paginas/Login'
import Registro from './paginas/Registro'
import Eventos from './paginas/Eventos'
import EventoDetalle from './paginas/EventoDetalle'
import Compra from './paginas/Compra'
import MisEntradas from './paginas/MisEntradas'
import Devoluciones from './paginas/Devoluciones'

/** Todas las pantallas menos login y registro necesitan sesion: el gateway pide token en cada ruta. */
function ConSesion({ children }) {
  const { sesion } = useSesion()
  const ubicacion = useLocation()
  if (!sesion) return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />
  return <Layout>{children}</Layout>
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/registro" element={<Registro />} />
      <Route path="/" element={<ConSesion><Eventos /></ConSesion>} />
      <Route path="/eventos/:id" element={<ConSesion><EventoDetalle /></ConSesion>} />
      <Route path="/compras/:id" element={<ConSesion><Compra /></ConSesion>} />
      <Route path="/entradas" element={<ConSesion><MisEntradas /></ConSesion>} />
      <Route path="/devoluciones" element={<ConSesion><Devoluciones /></ConSesion>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
