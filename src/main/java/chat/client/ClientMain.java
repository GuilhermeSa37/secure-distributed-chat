package chat.client;

import chat.common.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ClientMain {
    private static volatile String token;
    private static volatile ClientConnection currentConnection;

    private static volatile boolean ignorePings = false;
    private static volatile boolean slowMode = false;

    private static volatile boolean quitRequested = false;
    private static volatile boolean inputThreadStarted = false;

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Constants.DEFAULT_PORT;

        Reconnector reconnector = new Reconnector(host, port);
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try {
                ClientConnection connection = reconnector.connect();
                currentConnection = connection;

                System.out.println(connection.readLine()); // OK WELCOME

                if (!inputThreadStarted) {
                    inputThreadStarted = true;
                    Thread.startVirtualThread(() -> inputLoop(keyboard));
                }

                if (token == null) {
                    printHelpBeforeLogin();
                } else {
                    connection.setSoTimeout(Constants.HEARTBEAT_TIMEOUT_MILLIS);
                    connection.sendLine("RESUME " + token);
                }

                receiveLoop(connection);

            } catch (Exception e) {
                currentConnection = null;
                if (quitRequested) {
                    System.exit(0);
                }
                System.out.println("[client] Server unavailable. Retrying...");
                sleepQuietly(2000);
            }
        }
    }

    private static void inputLoop(BufferedReader keyboard) {
        try {
            String line;
            while ((line = keyboard.readLine()) != null) {

                // TEST_* commands are client-side validation helpers retained for quick manual checks.
                // They are intercepted locally by the client and are not part of the normal server protocol.
                if (line.equalsIgnoreCase("TESTS")) {
                    printTestMenu();
                    continue;
                }
                if (line.toUpperCase().startsWith("TEST_")) {
                    handleTestCommand(line, currentConnection);
                    continue;
                }

                ClientConnection conn = currentConnection;

                if (conn != null) {
                    try {
                        conn.sendLine(line);
                    } catch (IOException e) {
                        System.out.println("[client] Error: Could not send message. Connection lost.");
                    }
                } else {
                    System.out.println("[client] Cannot send message while disconnected.");
                }

                if (line.equalsIgnoreCase("QUIT")) {
                    quitRequested = true;
                }
            }
        } catch (IOException e) {
            System.out.println("Keyboard read error: " + e.getMessage());
        }
    }

    private static void receiveLoop(ClientConnection connection) throws IOException {
        // The receive loop owns server-to-client traffic: it prints messages, stores tokens,
        // replies to heartbeat PINGs, and lets the outer loop reconnect when reads fail.
        try {
            String line;
            while ((line = connection.readLine()) != null) {

                if (slowMode) {
                    sleepQuietly(3000);
                }

                if (line.equals("PING")) {
                    if(!ignorePings) {
                        connection.sendLine("PONG");
                    }
                    else{
                        System.out.println("[test] Ignorando PING recebido do servidor...");
                    }
                    continue;
                }

                if (line.startsWith("OK RESUMED")) {
                    System.out.println("[client] Reconnected. Session resumed.");
                    continue;
                }

                if (line.startsWith("OK CURRENT_ROOM ")) {
                    System.out.println("[client] Reconnected. You are still in room " + line.substring("OK CURRENT_ROOM ".length()) + ".");
                    continue;
                }

                if (line.startsWith("OK LOGOUT")) {
                    token = null;
                    System.out.println("[client] Logged out.");
                    printHelpBeforeLogin();
                    continue;
                }

                if (line.startsWith("ERR SESSION_EXPIRED")) {
                    System.out.println("[client] Session expired. Please log in again.");
                    token = null;
                    printHelpBeforeLogin();
                    continue;
                }

                if (line.startsWith("OK TOKEN ")) {
                    token = line.substring("OK TOKEN ".length());
                    connection.setSoTimeout(Constants.HEARTBEAT_TIMEOUT_MILLIS);
                }

                System.out.println(line);
            }

            if (quitRequested) {
                System.exit(0);
            }

            System.out.println("[client] Connection lost. Reconnecting...");
            throw new IOException("Connection closed by remote host");
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("[client] Heartbeat timeout: Server is dead (no PING received). Reconnecting...");
            throw new IOException("Heartbeat timeout expired", e);
        }
    }

    private static void handleTestCommand(String command, ClientConnection connection) {
        // Validation helper dispatch. These commands are intentionally kept on the client side
        // so they can simulate local failures without becoming server chat commands.
        String[] parts = command.split(" ");
        String testName = parts[0].toUpperCase();

        System.out.println("[test] Starting " + testName + "...");

        switch (testName) {
            case "TEST_FAULT_TOLERANCE" -> {
                System.out.println("[test] Simulating network instability with random disconnects...");
                Thread.startVirtualThread(() -> {
                    for (int i = 1; i <= 3; i++) {
                        sleepQuietly(2000 + (long) (Math.random() * 3000));
                        System.out.println("[test] Triggering fault " + i + "/3...");
                        if (currentConnection != null) {
                            try {
                                currentConnection.close();
                            } catch (Exception ignored) {}
                        }
                    }
                    System.out.println("[test] Fault tolerance simulation completed.");
                });
            }
            case "TEST_RECONNECT" -> {
                System.out.println("[test] Simulating reconnection... disconnecting from the socket locally.");
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (Exception ignored) {}
                }
            }
            case "TEST_HEARTBEAT" -> {
                System.out.println("[test] Ignoring PONG responses for 75 seconds to force server timeout...");
                ignorePings = true;
                Thread.startVirtualThread(() -> {
                    sleepQuietly(75000);
                    ignorePings = false;
                    System.out.println("[test] Resuming normal PONG responses.");
                });
            }
            case "TEST_SLOW_CLIENT" -> {
                slowMode = !slowMode;
                System.out.println("[test] Slow client mode is now " + (slowMode ? "ENABLED (3s delay)" : "DISABLED"));
            }
            case "TEST_CONCURRENT" -> {
                System.out.println("[test] Spawning 20 virtual threads to send simultaneous messages...");
                for (int i = 1; i <= 20; i++) {
                    final int msgId = i;
                    Thread.startVirtualThread(() -> {
                        if (currentConnection != null) {
                            try {
                                currentConnection.sendLine("MSG [Concurrent Test] Payload ID: " + msgId);
                            } catch (Exception ignored) {}
                        }
                    });
                }
            }
            case "TEST_INVALID_TOKEN" -> {
                System.out.println("[test] Sending fake token to test rejection...");
                if (connection != null) {
                    try {
                        connection.sendLine("RESUME FAKE_EXPIRED_TOKEN_123");
                    } catch (IOException ignored) {}
                }
            }
            default -> System.out.println("[test] Unknown test command.");
        }
    }


    private static void printHelpBeforeLogin() {
        System.out.println("Commands:");
        System.out.println("  LOGIN alice alice");
        System.out.println("  LOGIN bob bob");
        System.out.println("  REGISTER username password");
        System.out.println("  LIST_ROOMS");
        System.out.println("  JOIN Library");
        System.out.println("  MSG hello everyone");
        System.out.println("  CREATE_AI_ROOM AI doodle | Summarize availability and suggest a meeting time");
        System.out.println("  AI");
        System.out.println("  HELP");
        System.out.println("  LOGOUT");
        System.out.println("  QUIT");
    }

    private static void printTestMenu() {
        System.out.println("\n=== DEVELOPER TEST MENU ===");
        System.out.println("Available test commands:");
        System.out.println("  TEST_FAULT_TOLERANCE   - Simulate general network instability");
        System.out.println("  TEST_RECONNECT         - Force drop connection and verify auto-reconnect");
        System.out.println("  TEST_HEARTBEAT         - Stop answering PINGs to trigger server timeout");
        System.out.println("  TEST_SLOW_CLIENT       - Simulate a client that reads/writes very slowly");
        System.out.println("  TEST_CONCURRENT        - Blast the server with simultaneous messages");
        System.out.println("  TEST_INVALID_TOKEN     - Try to RESUME with a fake/expired token");
        System.out.println("===========================\n");
    }


    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
