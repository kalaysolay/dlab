package kz.damulab.tools;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import nl.martijndwars.webpush.Utils;

/**
 * Генерация VAPID-ключей для Web Push без Node.js / npx.
 * Запуск: {@code ./gradlew.bat generateVapidKeys}
 * Скопируйте вывод в /etc/damulab/damulab.env на сервере.
 */
public final class GenerateVapidKeys {

    private GenerateVapidKeys() {
    }

    public static void main(String[] args) throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();

        ECPublicKey publicKey = (ECPublicKey) pair.getPublic();
        ECPrivateKey privateKey = (ECPrivateKey) pair.getPrivate();

        String publicB64 = base64Url(Utils.encode(publicKey));
        String privateB64 = base64Url(Utils.encode(privateKey));

        System.out.println();
        System.out.println("# Добавьте в /etc/damulab/damulab.env:");
        System.out.println("VAPID_PUBLIC_KEY=" + publicB64);
        System.out.println("VAPID_PRIVATE_KEY=" + privateB64);
        System.out.println("VAPID_SUBJECT=mailto:admin@damulab.kz");
        System.out.println();
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
