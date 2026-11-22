package usuarios.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// Emite el JWT firmado con HS256. El gateway valida con la MISMA clave (jwt.secret).
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiracionMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiracion-segundos:3600}") long expiracionSeg) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracionMs = expiracionSeg * 1000L;
    }

    public String generar(long usuarioId, String rol) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expiracionMs))
                .signWith(key)
                .compact();
    }

    public long expiracionSegundos() { return expiracionMs / 1000L; }
}
