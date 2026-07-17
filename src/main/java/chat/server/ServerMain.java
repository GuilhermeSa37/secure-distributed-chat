package chat.server;

import chat.common.Constants;
import chat.common.security.TlsConfig;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;

public class ServerMain {
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Constants.DEFAULT_PORT;

        ChatServer server = new ChatServer();
        ClientHandler clientHandler = new ClientHandler(server);

        SSLServerSocketFactory socketFactory = TlsConfig.serverSocketFactory(
                Path.of(Constants.SERVER_KEYSTORE_PATH),
                Constants.tlsPassword(),
                Constants.tlsPassword()
        );

        try (SSLServerSocket serverSocket = (SSLServerSocket) socketFactory.createServerSocket(port)) {
            TlsConfig.configureServerSocket(serverSocket);
            System.out.println("Secure chat server listening on port " + port);
            System.out.println("TLS " + TlsConfig.securitySummary());

            while (true) {
                Socket socket = serverSocket.accept();
                Thread.startVirtualThread(() -> clientHandler.handle(socket));
            }
        }
    }
}
