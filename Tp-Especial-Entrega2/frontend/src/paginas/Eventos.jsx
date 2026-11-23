import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import * as srv from '../servicios'
import { capital, dia, hora, hoyISO, mes } from '../formato'
import Mensaje, { Cargando } from '../componentes/Mensaje'

const SIN_FILTROS = { ciudad: '', tipo: '', participante: '', desde: hoyISO() }

export default function Eventos() {
  const [catalogo, setCatalogo] = useState([])          // todo lo publicado desde hoy: arma los desplegables
  const [eventos, setEventos] = useState(null)
  const [filtros, setFiltros] = useState(SIN_FILTROS)
  const [error, setError] = useState(null)

  async function buscar(f) {
    setError(null)
    setEventos(null)
    try {
      setEventos(await srv.buscarEventos(f))
    } catch (err) {
      setError(err.message)
      setEventos([])
    }
  }

  useEffect(() => {
    srv.buscarEventos({ desde: hoyISO() })
      .then((lista) => { setCatalogo(lista); setEventos(lista) })
      .catch((err) => { setError(err.message); setEventos([]) })
  }, [])

  // eventos filtra ciudad y tipo por valor exacto: las opciones salen de lo que hay publicado
  const ciudades = useMemo(() => [...new Set(catalogo.map((e) => e.ciudad).filter(Boolean))].sort(), [catalogo])
  const tipos = useMemo(() => [...new Set(catalogo.map((e) => e.tipo).filter(Boolean))].sort(), [catalogo])

  const cambiar = (campo) => (e) => setFiltros({ ...filtros, [campo]: e.target.value })

  function limpiar() {
    setFiltros(SIN_FILTROS)
    buscar(SIN_FILTROS)
  }

  return (
    <>
      <h1 className="titulo">Eventos</h1>
      <form className="filtros" onSubmit={(e) => { e.preventDefault(); buscar(filtros) }}>
        <label>
          Ciudad
          <select value={filtros.ciudad} onChange={cambiar('ciudad')}>
            <option value="">Todas</option>
            {ciudades.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
        <label>
          Tipo
          <select value={filtros.tipo} onChange={cambiar('tipo')}>
            <option value="">Todos</option>
            {tipos.map((t) => <option key={t} value={t}>{capital(t)}</option>)}
          </select>
        </label>
        <label>
          Artista
          <input placeholder="Nombre o parte del nombre" value={filtros.participante} onChange={cambiar('participante')} />
        </label>
        <label>
          Desde
          <input type="date" value={filtros.desde} onChange={cambiar('desde')} />
        </label>
        <div className="filtros-acciones">
          <button type="submit" className="boton boton-principal">Buscar</button>
          <button type="button" className="boton-texto" onClick={limpiar}>Limpiar</button>
        </div>
      </form>

      <Mensaje tipo="error">{error}</Mensaje>
      {eventos === null && <Cargando texto="Buscando eventos…" />}
      {eventos?.length === 0 && !error && (
        <p className="vacio">No hay eventos publicados con esos filtros. Probá con otra ciudad o sacá algún filtro.</p>
      )}
      {eventos?.length > 0 && (
        <ul className="cartelera">
          {eventos.map((e) => (
            <li key={e.id} className="cartelera-fila">
              <time className="cartelera-fecha" dateTime={e.fechaHora}>
                <span className="cartelera-dia">{dia(e.fechaHora)}</span>
                <span className="cartelera-mes">{mes(e.fechaHora)}</span>
              </time>
              <div className="cartelera-datos">
                <h2><Link to={`/eventos/${e.id}`}>{e.nombre}</Link></h2>
                <p>{capital(e.tipo)} en {e.ciudad}, {hora(e.fechaHora)} h</p>
              </div>
              <Link to={`/eventos/${e.id}`} className="boton boton-secundario">Ver entradas</Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
