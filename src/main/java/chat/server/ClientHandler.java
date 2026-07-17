package chat.server;

import chat.common.Constants;
import chat.server.auth.AuthResult;
import chat.server.commands.AuthenticatedCommandRouter;
import chat.server.protocol.Command;
import chat.server.protocol.CommandType;
import chat.server.protocol.ProtocolParser;
import chat.server.sessions.ClientSession;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ClientHandler {
    private final ChatServer server;
    private final ProtocolParser parser = new ProtocolParser();
    private final AuthenticatedCommandRouter authenticatedCommandRouter;

    public ClientHandler(ChatServer server) {
        this.server = server;
        this.authenticatedCommandRouter = new AuthenticatedCommandRouter(server);
    }

    public void handle(Socket socket) {
        // This method runs in one virtual thread per accepted client connection.
        // It acts as the reader/control side: authenticate, start the writer, then dispatch commands.
        ClientSession session = null;
        try (ConnectionContext connection = new ConnectionContext(socket)) {
            connection.writeLine("OK WELCOME");

            session = authenticateOrResume(connection);
            if (session == null) {
                return;
            }

            startWriterThread(session, connection);
            readCommands(session, connection);

            System.out.println("[server] Connection ended for user: " + session.username());
        } catch (SocketTimeoutException e) {
            String username = (session != null) ? session.username() : "unknown";
            System.out.println("[server] Heartbeat timeout: Client " + username + " did not respond. Disconnecting...");
        } catch (IOException e) {
            String username = (session != null) ? session.username() : "unknown";
            System.out.println("[server] Connection lost with client (" + username + "): " + e.getMessage());
        } finally {
            if (session != null && server.sessionManager().containsToken(session.token())) {
                // Unexpected TCP/TLS loss does not destroy the session.
                // The token remains recoverable until it expires or LOGOUT invalidates it.
                session.markDisconnected();
                System.out.println("[server] Session of " + session.username() + " stored for recovery.");
            }
        }
    }

    private ClientSession authenticateOrResume(ConnectionContext connection) throws IOException {
        while (!connection.isClosed()) {
            String line = connection.readLine();
            if (line == null) {
                return null;
            }

            Command command = parser.parse(line);

            switch (command.type()) {
                case PONG -> {
                    continue;
                }

                case LOGIN -> {
                    if (command.args().size() < 2) {
                        connection.writeLine("ERR BAD_ARGUMENTS Usage: LOGIN username password");
                        continue;
                    }

                    String username = command.args().get(0);
                    String password = command.args().get(1);

                    AuthResult result = server.authService().login(username, password);
                    if (!result.success()) {
                        connection.writeLine("ERR AUTH_FAILED " + result.message());
                        continue;
                    }

                    ClientSession session = server.sessionManager().createSession(username);
                    connection.writeLine("OK TOKEN " + session.token());
                    return session;
                }

                case REGISTER -> {
                    if (command.args().size() < 2) {
                        connection.writeLine("ERR BAD_ARGUMENTS Usage: REGISTER username password");
                        continue;
                    }

                    AuthResult result = server.authService().register(command.args().get(0), command.args().get(1));
                    connection.writeLine(result.success() ? "OK REGISTERED" : "ERR USER_EXISTS " + result.message());
                }

                case RESUME -> {
                    if (command.args().isEmpty()) {
                        connection.writeLine("ERR BAD_ARGUMENTS Usage: RESUME token");
                        continue;
                    }

                    String token = command.args().get(0);
                    Optional<ClientSession> resumed = server.sessionManager().resume(token);

                    if (resumed.isPresent()) {
                        ClientSession session = resumed.get();
                        connection.writeLine("OK RESUMED " + session.username());
                        session.currentRoom().ifPresent(room -> connection.writeLine("OK CURRENT_ROOM " + room.name()));
                        return session;
                    }

                    connection.writeLine("ERR SESSION_EXPIRED Token is invalid or expired");
                }

                case HELP -> {
                    connection.writeLine("OK HELP");
                    connection.writeLine("LOGIN user pass - Log in with username and password");
                    connection.writeLine("REGISTER user pass - Register a new user");
                    connection.writeLine("RESUME token - Resume an existing session using a server token");
                }

                default -> connection.writeLine("ERR NOT_AUTHENTICATED Please LOGIN, REGISTER, or RESUME first");
            }
        }

        return null;
    }

    private void startWriterThread(ClientSession session, ConnectionContext connection) {
        // The writer side is separate from the reader side so room messages can be delivered
        // even when the client is idle and not sending commands.
        Thread writer = Thread.startVirtualThread(() -> {
            try {
                while (!connection.isClosed() && !Thread.currentThread().isInterrupted()) {
                    String message = session.takeOutgoingMessageWithTimeout(Constants.HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

                    if (Thread.currentThread().isInterrupted()) {
                        if (message != null) {
                            session.prependMessage(message);
                        }
                        break;
                    }

                    if (message == null) {
                        // No outbound messages arrived during the heartbeat interval, so send PING.
                        // A healthy client answers with PONG, which the reader thread consumes.
                        System.out.println("[server] PING to " + session.username());
                        connection.writeLine("PING");
                    } else {
                        connection.writeLine(message);
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        session.bindConnection(connection, writer);
    }

    private void readCommands(ClientSession session, ConnectionContext connection) throws IOException {
        // After authentication, this reader loop processes the user protocol commands.
        // Socket timeout is used together with PING/PONG to detect dead connections.
        try {
            connection.setSoTimeout(Constants.HEARTBEAT_TIMEOUT_MILLIS);

            String line;
            while (!connection.isClosed() && (line = connection.readLine()) != null) {
                Command command = parser.parse(line);
                if (command.type() == CommandType.PONG) {
                    continue;
                }

                boolean keepRunning = authenticatedCommandRouter.dispatch(session, connection, command);
                if (!keepRunning) {
                    return;
                }
            }
        } catch (SocketTimeoutException e) {
            connection.close();
            throw e;
        }
    }
}
