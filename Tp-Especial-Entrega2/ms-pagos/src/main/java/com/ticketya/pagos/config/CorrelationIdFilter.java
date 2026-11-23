package com.ticketya.pagos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Trazabilidad por servicio. Toma el X-Correlation-Id que inyecta el gateway (o genera uno),
 * lo pone en el MDC para que TODAS las lineas de log de este micro lo lleven, y registra una
 * linea por request (metodo, ruta, status, tiempo). Asi una misma operacion se puede seguir
 * con el mismo id a traves de todos los microservicios.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String corr = req.getHeader(HEADER);
        if (corr == null || corr.isBlank()) {
            corr = UUID.randomUUID().toString().substring(0, 8);
        }
        long inicio = System.currentTimeMillis();
        MDC.put(MDC_KEY, corr);
        res.setHeader(HEADER, corr);
        try {
            chain.doFilter(req, res);
        } finally {
            long ms = System.currentTimeMillis() - inicio;
            log.info("{} {} -> {} ({} ms)", req.getMethod(), req.getRequestURI(), res.getStatus(), ms);
            MDC.remove(MDC_KEY);
        }
    }
}
