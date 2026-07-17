package chat.common.security;

import chat.common.Constants;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

public final class TlsConfig {
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String TLS_CONTEXT = "TLSv1.3";

    private TlsConfig() {
    }

    public static SSLServerSocketFactory serverSocketFactory(Path keyStorePath,
                                                            char[] keyStorePassword,
                                                            char[] keyPassword) {
        try {
            KeyStore keyStore = loadKeyStore(keyStorePath, keyStorePassword);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyPassword);

            SSLContext sslContext = SSLContext.getInstance(TLS_CONTEXT);
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            return sslContext.getServerSocketFactory();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Could not initialize server TLS context", e);
        }
    }

    public static SSLSocketFactory clientSocketFactory(Path trustStorePath, char[] trustStorePassword) {
        try {
            KeyStore trustStore = loadKeyStore(trustStorePath, trustStorePassword);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance(TLS_CONTEXT);
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext.getSocketFactory();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Could not initialize client TLS context", e);
        }
    }

    public static void configureServerSocket(SSLServerSocket serverSocket) {
        serverSocket.setEnabledProtocols(Constants.TLS_PROTOCOLS);
        serverSocket.setEnabledCipherSuites(Constants.TLS_CIPHER_SUITES);
        serverSocket.setNeedClientAuth(false);
    }

    public static void configureClientSocket(SSLSocket socket) {
        socket.setEnabledProtocols(Constants.TLS_PROTOCOLS);
        socket.setEnabledCipherSuites(Constants.TLS_CIPHER_SUITES);

        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
    }

    public static String securitySummary() {
        return "protocols=" + Arrays.toString(Constants.TLS_PROTOCOLS)
                + ", cipherSuites=" + Arrays.toString(Constants.TLS_CIPHER_SUITES);
    }

    private static KeyStore loadKeyStore(Path path, char[] password) throws GeneralSecurityException, IOException {
        if (!Files.exists(path)) {
            throw new IOException("Keystore not found: " + path.toAbsolutePath());
        }

        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        try (InputStream input = Files.newInputStream(path)) {
            keyStore.load(input, password);
        }
        return keyStore;
    }
}
