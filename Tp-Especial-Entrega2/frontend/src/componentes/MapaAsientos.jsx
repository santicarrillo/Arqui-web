// Plano de un sector numerado: una fila de botones por cada fila del sector.
// Los asientos vienen de lugares (fila y numero) y cuales estan libres, de ventas (disponibilidad).

const porNumero = (a, b) => String(a).localeCompare(String(b), 'es', { numeric: true })

export default function MapaAsientos({ asientos, libres, elegidos, onElegir }) {
  const filas = new Map()
  for (const a of asientos.values()) {
    if (!filas.has(a.fila)) filas.set(a.fila, [])
    filas.get(a.fila).push(a)
  }
  const ordenadas = [...filas.entries()].sort(([a], [b]) => porNumero(a, b))

  return (
    <div className="mapa">
      <div className="mapa-escenario">Escenario</div>
      <div className="mapa-filas">
        {ordenadas.map(([fila, lista]) => (
          <div className="mapa-fila" key={fila}>
            <span className="mapa-fila-nombre" aria-hidden="true">{fila}</span>
            {lista.sort((a, b) => porNumero(a.numero, b.numero)).map((a) => {
              const elegido = elegidos.has(a.id)
              const libre = libres.has(a.id)
              return (
                <button
                  key={a.id}
                  type="button"
                  className={`asiento ${elegido ? 'asiento-elegido' : libre ? 'asiento-libre' : 'asiento-ocupado'}`}
                  disabled={!libre && !elegido}
                  aria-pressed={elegido}
                  aria-label={`Fila ${fila}, asiento ${a.numero}${libre || elegido ? '' : ', ocupado'}`}
                  onClick={() => onElegir(a.id)}
                >
                  {a.numero}
                </button>
              )
            })}
          </div>
        ))}
      </div>
      <ul className="mapa-leyenda" aria-label="Referencias">
        <li><span className="asiento asiento-libre" aria-hidden="true" /> Libre</li>
        <li><span className="asiento asiento-elegido" aria-hidden="true" /> Elegido</li>
        <li><span className="asiento asiento-ocupado" aria-hidden="true" /> Ocupado</li>
      </ul>
    </div>
  )
}
