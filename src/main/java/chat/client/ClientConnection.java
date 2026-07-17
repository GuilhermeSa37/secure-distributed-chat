package chat.client;

import chat.common.security.TlsConfig;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class ClientConnection implements AutoCloseable {
    private final SSLSocket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    public ClientConnection(String host, int port, SSLSocketFactory socketFactory) throws IOException {
        this.socket = (SSLSocket) socketFactory.createSocket(host, port);
        TlsConfig.configureClientSocket(socket);
        socket.startHandshake();
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void setSoTimeout(int timeoutMs) throws java.net.SocketException {
        this.socket.setSoTimeout(timeoutMs);
    }

    public String readLine() throws IOException {
        return in.readLine();
    }

    public void sendLine(String line) throws IOException {
        out.println(line);
        if (out.checkError()) {
            throw new IOException("Write failed: connection lost.");
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
