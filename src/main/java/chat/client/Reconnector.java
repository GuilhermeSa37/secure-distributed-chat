package chat.client;

import chat.common.Constants;
import chat.common.security.TlsConfig;

import javax.net.ssl.SSLSocketFactory;
import java.nio.file.Path;

public class Reconnector {
    private final String host;
    private final int port;
    private final SSLSocketFactory socketFactory;

    public Reconnector(String host, int port) {
        this.host = host;
        this.port = port;
        this.socketFactory = TlsConfig.clientSocketFactory(
                Path.of(Constants.CLIENT_TRUSTSTORE_PATH),
                Constants.tlsPassword()
        );
    }

    public ClientConnection connect() throws java.io.IOException {
        return new ClientConnection(host, port, socketFactory);
    }
}
