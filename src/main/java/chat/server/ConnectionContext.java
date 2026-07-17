package chat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ConnectionContext implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    private volatile boolean closed;
    private volatile long lastActivity = System.currentTimeMillis();

    public ConnectionContext(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    public String readLine() throws IOException {
        String line = in.readLine();
        if (line != null) {
            lastActivity = System.currentTimeMillis();
        }
        return line;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public void writeLine(String line) {
        out.println(line);
        if (out.checkError()) {
            close();
        }
    }

    public boolean isClosed() {
        return closed || socket.isClosed();
    }

    @Override
    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public void setSoTimeout(int timeoutMs) throws java.net.SocketException {
        this.socket.setSoTimeout(timeoutMs);
    }
}