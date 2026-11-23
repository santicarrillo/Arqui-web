import { QRCodeSVG } from 'qrcode.react'
import { fechaLarga, pesos } from '../formato'

const SELLO = { USADA: 'Usada', DEVUELTA: 'Devuelta' }

/**
 * Una entrada con forma de entrada de papel: los datos a la izquierda y, del otro lado del troquel,
 * el QR que se muestra en la puerta. Si ya se uso o se devolvio, lleva un sello y el QR se apaga.
 */
export default function Entrada({ evento, lugar, sector, asiento, entrada, children }) {
  const sello = SELLO[entrada.estado]
  return (
    <article className={`entrada ${sello ? 'entrada-anulada' : ''}`} aria-label={`Entrada para ${evento?.nombre ?? 'el evento'}`}>
      <div className="entrada-cuerpo">
        <p className="entrada-evento">{evento?.nombre ?? `Evento ${entrada.eventoId}`}</p>
        {evento && <p className="entrada-fecha">{fechaLarga(evento.fechaHora)} h</p>}
        {lugar && <p className="entrada-lugar">{lugar.nombre}, {lugar.ciudad}</p>}
        <dl className="entrada-datos">
          <div><dt>Sector</dt><dd>{sector}</dd></div>
          {asiento && <div><dt>Ubicación</dt><dd>{asiento}</dd></div>}
          <div><dt>Precio</dt><dd>{pesos(entrada.precio)}</dd></div>
        </dl>
        {children}
      </div>
      <div className="entrada-talon">
        {entrada.codigoQr && <QRCodeSVG value={entrada.codigoQr} size={132} marginSize={0} bgColor="transparent" fgColor="currentColor" />}
        <p className="entrada-codigo">{entrada.codigoQr}</p>
        {sello && <span className="entrada-sello">{sello}</span>}
      </div>
    </article>
  )
}
