package kz.damulab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import kz.damulab.passkeys.PasskeyCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Эмулирует authenticator с настоящей EC-парой: проверяет весь HTTP-цикл, сериализацию
 * challenge, хранение ключа, подпись, UV, origin и запрет повторного использования.
 * Заглушка PasskeyService здесь скрыла бы исходную ошибку browser JSON vs storage JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasskeyFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasskeyCredentialRepository credentials;

    @Test
    void reportsKeyThatExistsOnDeviceButWasNotSavedOnServer() throws Exception {
        Device device = registerDevice();
        // Состояние после прежней ошибки настройки: телефон сохранил ключ, а сервер — нет.
        // Удаляем только ключ тестового аккаунта, оставляя реальную подпись authenticator.
        credentials.deleteAll(credentials.findAllByCredentialId(encode(device.id())));
        MockHttpSession session = new MockHttpSession();
        JsonNode options = loginOptions(session, null);
        mvc.perform(post("/api/passkeys/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assertion(device, options, "http://localhost:8080", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("key_not_registered"))
                .andExpect(jsonPath("$.message").value(containsString("профиле")))
                .andExpect(jsonPath("$.reference").isNotEmpty());
        mvc.perform(get("/api/me").session(session)).andExpect(status().is3xxRedirection());
    }

    @Test
    void signsInWithSyncedPasskey() throws Exception {
        // Google Password Manager и iCloud используют BE/BS, которых не было в старом fixture.
        Device device = registerDevice(0x18);
        MockHttpSession session = new MockHttpSession();
        JsonNode options = loginOptions(session, null);
        mvc.perform(post("/api/passkeys/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assertion(device, options, "http://localhost:8080", true, 0x18)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").value("/student"));
    }

    @Test
    void registersAndSignsInWithDiscoverableAndExistingKeys() throws Exception {
        Device device = registerDevice();
        for (String username : new String[] {null, device.email()}) {
            MockHttpSession session = new MockHttpSession();
            JsonNode options = loginOptions(session, username);
            String oldSessionId = session.getId();
            String assertion = assertion(device, options, "http://localhost:8080", true);
            mvc.perform(post("/api/passkeys/login").session(session)
                            .contentType(MediaType.APPLICATION_JSON).content(assertion))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.redirectUrl").value("/student"));
            assertThat(session.getId()).isNotEqualTo(oldSessionId);
            mvc.perform(get("/api/me").session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(device.email()));
            mvc.perform(post("/api/passkeys/login").session(session)
                            .contentType(MediaType.APPLICATION_JSON).content(assertion))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("session_expired"));
        }
    }

    @Test
    void rejectsWrongOriginAndMissingUserVerification() throws Exception {
        Device device = registerDevice();
        for (boolean wrongOrigin : new boolean[] {true, false}) {
            MockHttpSession session = new MockHttpSession();
            JsonNode options = loginOptions(session, null);
            String assertion = assertion(device, options,
                    wrongOrigin ? "https://untrusted.example" : "http://localhost:8080", wrongOrigin);
            mvc.perform(post("/api/passkeys/login").session(session)
                            .contentType(MediaType.APPLICATION_JSON).content(assertion))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.reference").isNotEmpty());
            mvc.perform(get("/api/me").session(session)).andExpect(status().is3xxRedirection());
        }
    }

    @Test
    void entryAlsoWorksWithExistingSessionAndOffersPasswordFallback() throws Exception {
        mvc.perform(get("/app").with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("data-passkey-entry")));
        mvc.perform(get("/login?reauth=true").with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("id=" + (char) 34 + "password" + (char) 34)));
        mvc.perform(get("/passkeys/setup")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/passkeys/setup").with(user("parent@damulab.kz").roles("PARENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Использовать сохранённый ключ")));
    }

    private Device registerDevice() throws Exception {
        return registerDevice(0);
    }

    private Device registerDevice(int backupFlags) throws Exception {
        String email = "biometric-" + UUID.randomUUID() + "@example.com";
        var registration = mvc.perform(post("/register").with(csrf())
                        .param("email", email).param("password", "password123").param("fullName", "Biometric Test")
                        .param("role", "STUDENT").param("gradeNo", "4"))
                .andExpect(redirectedUrl("/passkeys/setup")).andReturn();
        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);
        var result = mvc.perform(post("/api/passkeys/register/options").session(session))
                .andExpect(status().isOk()).andReturn();
        JsonNode options = json.readTree(result.getResponse().getContentAsString()).path("publicKey");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        byte[] id = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        ECPublicKey publicKey = (ECPublicKey) pair.getPublic();
        // COSE EC2: {1:2, 3:-7, -1:1, -2:x, -3:y}; ключи CBOR должны быть числами.
        byte[] cose = ByteBuffer.allocate(77)
                .put(java.util.HexFormat.of().parseHex("a5010203262001215820"))
                .put(coordinate(publicKey.getW().getAffineX().toByteArray()))
                .put(java.util.HexFormat.of().parseHex("225820"))
                .put(coordinate(publicKey.getW().getAffineY().toByteArray())).array();
        // RP hash + UP/UV/AT + counter + AAGUID + credential ID + COSE key (attestation=none).
        byte[] authenticatorData = ByteBuffer.allocate(37 + 16 + 2 + id.length + cose.length)
                .put(hash("localhost".getBytes(StandardCharsets.UTF_8))).put((byte) (0x45 | backupFlags)).putInt(0)
                .put(new byte[16]).putShort((short) id.length).put(id).put(cose).array();
        byte[] attestation = new ObjectMapper(new CBORFactory()).writeValueAsBytes(
                Map.of("fmt", "none", "attStmt", Map.of(), "authData", authenticatorData));
        String payload = json.writeValueAsString(Map.of(
                "id", encode(id), "rawId", encode(id), "type", "public-key",
                "response", Map.of("attestationObject", encode(attestation),
                        "clientDataJSON", encode(clientData("webauthn.create", options, "http://localhost:8080"))),
                "clientExtensionResults", Map.of("credProps", Map.of("rk", true))));
        mvc.perform(post("/api/passkeys/register").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registered").value(true));
        mvc.perform(post("/api/passkeys/register").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("session_expired"));
        return new Device(email, pair, id, options.path("user").path("id").asText());
    }

    private JsonNode loginOptions(MockHttpSession session, String username) throws Exception {
        String body = json.writeValueAsString(java.util.Collections.singletonMap("username", username));
        var result = mvc.perform(post("/api/passkeys/login/options").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("publicKey");
    }

    private String assertion(Device device, JsonNode options, String origin, boolean verified) throws Exception {
        return assertion(device, options, origin, verified, 0);
    }

    private String assertion(Device device, JsonNode options, String origin, boolean verified, int backupFlags) throws Exception {
        byte[] client = clientData("webauthn.get", options, origin);
        // Счётчик 0 разрешён WebAuthn для authenticator без поддержки счётчиков.
        byte[] auth = ByteBuffer.allocate(37).put(hash("localhost".getBytes(StandardCharsets.UTF_8)))
                .put((byte) ((verified ? 5 : 1) | backupFlags)).putInt(0).array();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(device.pair().getPrivate());
        signer.update(auth);
        signer.update(hash(client));
        return json.writeValueAsString(Map.of("id", encode(device.id()), "rawId", encode(device.id()),
                "type", "public-key", "clientExtensionResults", Map.of(),
                "response", Map.of("authenticatorData", encode(auth), "clientDataJSON", encode(client),
                        "signature", encode(signer.sign()), "userHandle", device.userHandle())));
    }

    private byte[] clientData(String type, JsonNode options, String origin) throws Exception {
        return json.writeValueAsBytes(Map.of("type", type, "challenge", options.path("challenge").asText(),
                "origin", origin, "crossOrigin", false));
    }

    private byte[] coordinate(byte[] bytes) {
        byte[] result = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, result, 32 - length, length);
        return result;
    }

    private byte[] hash(byte[] bytes) throws Exception { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    private String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private record Device(String email, KeyPair pair, byte[] id, String userHandle) {}
}
