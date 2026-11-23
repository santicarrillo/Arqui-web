/**
 * Fondo decorativo de las pantallas de acceso: luces de escenario que barren desde arriba y el publico en
 * silueta abajo, con algunas manos levantadas y celulares prendidos. Es solo decoracion (aria-hidden) y se
 * dibuja con SVG + CSS, sin imagenes externas. Con "reducir movimiento" queda quieto.
 */

// Numeros pseudoaleatorios fijos: el publico sale igual en cada render.
function aleatorio(semilla) {
  let s = semilla
  return () => {
    s = (s * 9301 + 49297) % 233280
    return s / 233280
  }
}

function publico(semilla, cantidad, base, escala) {
  const r = aleatorio(semilla)
  const personas = []
  for (let i = 0; i < cantidad; i++) {
    const x = (i + 0.2 + r() * 0.6) * (600 / cantidad)
    const tam = escala * (0.85 + r() * 0.3)
    const y = base - r() * 8
    const mano = r() < 0.28 ? (r() < 0.5 ? -1 : 1) : 0
    const celular = mano !== 0 && r() < 0.6
    personas.push({ x, y, tam, mano, celular, demora: (r() * 3).toFixed(2) })
  }
  return personas
}

const atras = publico(7, 26, 132, 9)
const adelante = publico(23, 18, 150, 13)

function Fila({ personas, color, conCelulares }) {
  return (
    <g fill={color}>
      {personas.map((p, i) => {
        const brazoX = p.x + p.mano * p.tam * 0.9
        const brazoArriba = p.y - p.tam * 4.2
        return (
          <g key={i}>
            <circle cx={p.x} cy={p.y - p.tam * 1.9} r={p.tam} />
            <rect x={p.x - p.tam * 1.5} y={p.y - p.tam * 0.9} width={p.tam * 3} height={40} rx={p.tam * 1.2} />
            {p.mano !== 0 && (
              <line x1={p.x + p.mano * p.tam * 1.1} y1={p.y - p.tam * 0.4} x2={brazoX} y2={brazoArriba}
                    stroke={color} strokeWidth={p.tam * 0.7} strokeLinecap="round" />
            )}
            {conCelulares && p.celular && (
              <circle className="escenario-celular" cx={brazoX} cy={brazoArriba - p.tam * 0.6} r={p.tam * 0.35}
                      style={{ animationDelay: `${p.demora}s` }} />
            )}
          </g>
        )
      })}
    </g>
  )
}

export default function Escenario() {
  return (
    <div className="escenario" aria-hidden="true">
      <div className="escenario-bruma" />
      <div className="escenario-haz escenario-haz-1" />
      <div className="escenario-haz escenario-haz-2" />
      <div className="escenario-haz escenario-haz-3" />
      <div className="escenario-haz escenario-haz-4" />
      <div className="escenario-focos">
        {Array.from({ length: 7 }, (_, i) => <span key={i} style={{ animationDelay: `${i * 0.4}s` }} />)}
      </div>
      <svg className="escenario-publico" viewBox="0 0 600 160" preserveAspectRatio="xMidYMax slice">
        <Fila personas={atras} color="#1c1020" conCelulares />
        <Fila personas={adelante} color="#08070a" conCelulares />
      </svg>
    </div>
  )
}
