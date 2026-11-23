/** Caja de aviso. tipo: "error" | "ok" | "info". */
export default function Mensaje({ tipo = 'info', children, accion }) {
  if (!children) return null
  return (
    <div className={`mensaje mensaje-${tipo}`} role={tipo === 'error' ? 'alert' : 'status'}>
      <div>{children}</div>
      {accion}
    </div>
  )
}

export function Cargando({ texto = 'Cargando…' }) {
  return <p className="cargando" role="status">{texto}</p>
}
