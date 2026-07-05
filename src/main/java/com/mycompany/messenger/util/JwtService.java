package com.mycompany.messenger.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Сервис для работы с JWT (JSON Web Token).
 * Генерирует токены при входе/регистрации и проверяет их при запросах.
 * <p>
 * Токен содержит userCode (кто пользователь) и срок действия 24 часа.
 * Секретный ключ генерируется один раз при старте приложения.
 * При перезапуске сервера все старые токены становятся невалидными.
 */
public class JwtService {

    // Секретный ключ для подписи токенов (алгоритм HS256)
    //private static final SecretKey SECRET_KEY = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
    private static final SecretKey SECRET_KEY = Jwts.SIG.HS256.key().build();

    // Срок действия токена: 24 часа в миллисекундах
    private static final long EXPIRATION_MS = 86_400_000;

    /**
     * Создаёт JWT-токен для указанного пользователя.
     *
     * @param userCode код пользователя (будет храниться в subject токена)
     * @return строка JWT (подписанный токен)
     */
    public String generateToken(String userCode) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .subject(userCode)                // userCode — владелец токена
                .issuedAt(now)                    // когда выпущен
                .expiration(expiration)           // когда истекает
                .signWith(SECRET_KEY)             // подписываем секретным ключом
                .compact();
    }

    /**
     * Проверяет JWT-токен и извлекает userCode.
     *
     * @param token строка JWT
     * @return userCode, если токен валиден; null если токен недействителен
     */
    public String validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(SECRET_KEY)       // проверяем подпись
                    .build()
                    .parseSignedClaims(token)      // парсим токен
                    .getPayload();

            return claims.getSubject();           // возвращаем userCode из subject

        } catch (JwtException e) {
            // Токен недействителен (испорчен, истёк, неверная подпись)
            return null;
        }
    }
}
