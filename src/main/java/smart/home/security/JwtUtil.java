package smart.home.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.keys.directory}")
    private String keysDirectory;

    @Value("${jwt.keys.private}")
    private String privateKeyFile;

    @Value("${jwt.keys.public}")
    private String publicKeyFile;

    @Value("${jwt.access-token.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.issuer}")
    private String issuer;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            KeyPair keyPair = loadOrGenerateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();
            log.info("JWT RSA keys initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize JWT utilities", e);
            throw new RuntimeException("Failed to initialize JWT utilities", e);
        }
    }

    private KeyPair loadOrGenerateKeyPair() throws Exception {
        File privKeyFile = new File(privateKeyFile);
        File pubKeyFile = new File(publicKeyFile);

        if (privKeyFile.exists() && pubKeyFile.exists()) {
            log.info("Loading existing RSA key pair from {}", keysDirectory);
            return loadKeysFromFiles(privKeyFile, pubKeyFile);
        } else {
            log.info("Generating new RSA key pair and saving to {}", keysDirectory);
            KeyPair keyPair = generateKeyPair();
            saveKeysToFiles(keyPair, privKeyFile, pubKeyFile);
            return keyPair;
        }
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    private void saveKeysToFiles(KeyPair keyPair, File privateKeyFile, File publicKeyFile) throws IOException {
        Path keysDir = Paths.get(keysDirectory);
        if (!Files.exists(keysDir)) {
            Files.createDirectories(keysDir);
        }

        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(privateKeyFile))) {
            pemWriter.writeObject(keyPair.getPrivate());
        }

        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(publicKeyFile))) {
            pemWriter.writeObject(keyPair.getPublic());
        }

        log.info("RSA key pair saved successfully to {}", keysDirectory);
    }

    private KeyPair loadKeysFromFiles(File privateKeyFile, File publicKeyFile) throws Exception {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

        PrivateKey privateKey;
        try (PEMParser pemParser = new PEMParser(new FileReader(privateKeyFile))) {
            Object object = pemParser.readObject();
            if (object instanceof PEMKeyPair) {
                privateKey = converter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
            } else if (object instanceof PrivateKeyInfo) {
                privateKey = converter.getPrivateKey((PrivateKeyInfo) object);
            } else {
                throw new IllegalArgumentException("Unsupported private key format");
            }
        }

        PublicKey publicKey;
        try (PEMParser pemParser = new PEMParser(new FileReader(publicKeyFile))) {
            Object object = pemParser.readObject();
            if (object instanceof SubjectPublicKeyInfo) {
                publicKey = converter.getPublicKey((SubjectPublicKeyInfo) object);
            } else if (object instanceof PEMKeyPair) {
                publicKey = converter.getPublicKey(((PEMKeyPair) object).getPublicKeyInfo());
            } else {
                throw new IllegalArgumentException("Unsupported public key format");
            }
        }

        return new KeyPair(publicKey, privateKey);
    }

    public String generateAccessToken(String username, Long userId) {
        Instant now = Instant.now();
        log.debug("Generating access token for user: {}", username);
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim("userId", userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiration, ChronoUnit.SECONDS)))
                .signWith(privateKey)
                .compact();
    }

    public String extractUsername(String token) {
        return validateToken(token).getSubject();
    }

    public boolean validateToken(String token, String username) {
        try {
            Claims claims = validateToken(token);
            return claims.getSubject().equals(username) && !isTokenExpired(claims);
        } catch (Exception e) {
            log.warn("Token validation failed for user {}: {}", username, e.getMessage());
            return false;
        }
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}