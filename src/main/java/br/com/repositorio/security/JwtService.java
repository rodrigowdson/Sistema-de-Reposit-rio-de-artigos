package br.com.repositorio.security;

import br.com.repositorio.model.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String SEGREDO_PADRAO_DEV = "troque-esta-chave-jwt-em-producao-por-uma-string-longa-e-aleatoria";

    private final SecretKey chave;
    private final long expiracaoHoras;

    public JwtService(SecurityProperties properties) {
        String segredo = properties.getJwt().getSecret();
        if (!StringUtils.hasText(segredo)) {
            throw new IllegalStateException("Configure JWT_SECRET (app.security.jwt.secret).");
        }
        if (SEGREDO_PADRAO_DEV.equals(segredo)
                || "troque-por-uma-chave-secreta-aleatoria-com-mais-de-32-caracteres".equals(segredo)) {
            log.warn("ATENÇÃO: Usando chave JWT padrão de desenvolvimento. Em produção na EC2, defina JWT_SECRET com um valor aleatório forte.");
        }
        this.chave = Keys.hmacShaKeyFor(derivarChave256Bits(segredo));
        this.expiracaoHoras = Math.max(1L, properties.getJwt().getExpiracaoHoras());
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(expiracaoHoras, ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("uid", usuario.getId())
                .claim("perfil", usuario.getPerfil().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm))
                .signWith(chave)
                .compact();
    }

    public Optional<String> extrairEmailValido(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpiracaoSegundos() {
        return expiracaoHoras * 3600L;
    }

    private byte[] derivarChave256Bits(String segredo) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(segredo.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível.", e);
        }
    }
}
