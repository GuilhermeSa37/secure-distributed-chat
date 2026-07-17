package chat.common;

public final class Constants {
    public static final int DEFAULT_PORT = 12345;
    public static final int OUTBOUND_QUEUE_CAPACITY = 1000;
    public static final long TOKEN_TTL_SECONDS = 60 * 60;

    public static final int HEARTBEAT_INTERVAL_SECONDS = 15;
    public static final int HEARTBEAT_TIMEOUT_MILLIS = 60_000;

    public static final String[] TLS_PROTOCOLS = {"TLSv1.3"};
    public static final String[] TLS_CIPHER_SUITES = {
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384"
    };

    public static final String SERVER_KEYSTORE_PATH = config(
            "chat.server.keystore",
            "CHAT_SERVER_KEYSTORE_PATH",
            "certs/server-keystore.p12"
    );

    public static final String CLIENT_TRUSTSTORE_PATH = config(
            "chat.client.truststore",
            "CHAT_CLIENT_TRUSTSTORE_PATH",
            "certs/client-truststore.p12"
    );

    private static final String DEVELOPMENT_TLS_PASSWORD = "local-development-only";

    private Constants() {
    }

    public static char[] tlsPassword() {
        return config(
                "chat.tls.password",
                "CHAT_TLS_PASSWORD",
                DEVELOPMENT_TLS_PASSWORD
        ).toCharArray();
    }

    private static String config(String systemProperty, String environmentVariable, String fallback) {
        String propertyValue = System.getProperty(systemProperty);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        String environmentValue = System.getenv(environmentVariable);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        return fallback;
    }
}
