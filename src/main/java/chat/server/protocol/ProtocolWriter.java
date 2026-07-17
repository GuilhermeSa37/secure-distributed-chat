package chat.server.protocol;

import java.io.PrintWriter;

public class ProtocolWriter {
    private final PrintWriter out;

    public ProtocolWriter(PrintWriter out) {
        this.out = out;
    }

    public void ok(String text) {
        out.println("OK " + text);
    }

    public void error(String text) {
        out.println("ERR " + text);
    }
}
