package chat.tests;

import chat.client.ClientConnection;
import chat.client.Reconnector;
import chat.common.Constants;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ScenarioChecks {
    private static final String PASSWORD = "scenario-pass";

    private ScenarioChecks() {
    }

    public static void main(String[] args) throws Exception {
        String scenario = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "reconnect";
        String host = args.length > 1 ? args[1] : "localhost";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : Constants.DEFAULT_PORT;

        Reconnector reconnector = new Reconnector(host, port);
        System.out.println("[scenario] Scenario: " + scenario + " | target=" + host + ":" + port);
        System.out.println("[scenario] Start the server first with: ./scripts/run-server.sh " + port);

        switch (scenario) {
            case "reconnect" -> scenarioReconnect(reconnector);
            case "invalid-token" -> scenarioInvalidToken(reconnector);
            case "heartbeat" -> scenarioHeartbeatTimeout(reconnector);
            case "slow-client" -> scenarioSlowClient(reconnector);
            case "concurrent" -> scenarioConcurrent(reconnector);
            default -> {
                System.out.println("[scenario] Unknown scenario: " + scenario);
                printUsage();
                System.exit(2);
            }
        }
    }

    private static void printUsage() {
        System.out.println("[scenario] Available scenarios:");
        System.out.println("  reconnect      - login, join, force local socket close, reconnect with RESUME token");
        System.out.println("  invalid-token  - attempt RESUME with a fake token and show server rejection");
        System.out.println("  heartbeat      - ignore PING/PONG until the server closes the dead connection");
        System.out.println("  slow-client    - keep one client from reading while another client proves room continues");
        System.out.println("  concurrent     - spawn several virtual clients sending messages at the same time");
    }

    private static void scenarioReconnect(Reconnector reconnector) throws Exception {
        String room = scenarioRoom("Reconnect");
        try (ClientConnection first = connect(reconnector, "first")) {
            String token = login(first, "alice", "alice", "first");
            send(first, "JOIN " + room, "first");
            drain(first, "first", 1500, true);
            send(first, "MSG before reconnect", "first");
            drain(first, "first", 1500, true);

            System.out.println("[scenario] Closing the first socket locally to simulate a broken TCP/TLS connection.");
            first.close();
            Thread.sleep(2500);

            try (ClientConnection second = connect(reconnector, "second")) {
                send(second, "RESUME " + token, "second");
                drain(second, "second", 2500, true);
                send(second, "MSG after reconnect; session and room should be preserved", "second");
                drain(second, "second", 2500, true);
                send(second, "QUIT", "second");
            }
        }
        System.out.println("[scenario] Reconnect scenario complete.");
    }

    private static void scenarioInvalidToken(Reconnector reconnector) throws Exception {
        try (ClientConnection client = connect(reconnector, "invalid-token")) {
            send(client, "RESUME FAKE_EXPIRED_TOKEN_123", "invalid-token");
            drain(client, "invalid-token", 2500, true);
        }
        System.out.println("[scenario] Invalid-token scenario complete.");
    }

    private static void scenarioHeartbeatTimeout(Reconnector reconnector) throws Exception {
        System.out.println("[scenario] This scenario takes about one heartbeat timeout. The client intentionally ignores PING.");
        try (ClientConnection client = connect(reconnector, "heartbeat")) {
            login(client, "bob", "bob", "heartbeat");
            send(client, "JOIN " + scenarioRoom("Heartbeat"), "heartbeat");
            client.setSoTimeout(Constants.HEARTBEAT_TIMEOUT_MILLIS + 30_000);

            long deadline = System.currentTimeMillis() + Constants.HEARTBEAT_TIMEOUT_MILLIS + 45_000L;
            while (System.currentTimeMillis() < deadline) {
                String line = client.readLine();
                if (line == null) {
                    System.out.println("[heartbeat] server closed the connection after missed PONGs.");
                    return;
                }
                if (line.equals("PING")) {
                    System.out.println("[heartbeat] received PING and intentionally did NOT send PONG.");
                } else {
                    System.out.println("[heartbeat] < " + line);
                }
            }
        } catch (SocketTimeoutException e) {
            System.out.println("[heartbeat] local timeout reached while waiting for server close: " + e.getMessage());
        }
        System.out.println("[scenario] Heartbeat scenario complete.");
    }

    private static void scenarioSlowClient(Reconnector reconnector) throws Exception {
        String room = scenarioRoom("Slow");
        String suffix = Long.toString(System.currentTimeMillis());

        ClientConnection slow = connect(reconnector, "slow-client");
        registerAndLogin(slow, "slow_" + suffix, PASSWORD, "slow-client");
        send(slow, "JOIN " + room, "slow-client");
        drain(slow, "slow-client", 1000, true);
        System.out.println("[scenario] Slow client now stops reading for several seconds.");

        try (ClientConnection observer = connect(reconnector, "observer");
             ClientConnection sender = connect(reconnector, "sender")) {
            registerAndLogin(observer, "observer_" + suffix, PASSWORD, "observer");
            send(observer, "JOIN " + room, "observer");
            drain(observer, "observer", 1000, true);

            registerAndLogin(sender, "sender_" + suffix, PASSWORD, "sender");
            send(sender, "JOIN " + room, "sender");
            drain(sender, "sender", 1000, true);

            Thread observerReader = Thread.startVirtualThread(() -> drainQuietly(observer, "observer", 8000, true));
            for (int i = 1; i <= 25; i++) {
                send(sender, "MSG slow-client scenario payload " + i, "sender");
                Thread.sleep(50);
            }
            observerReader.join();
            System.out.println("[scenario] Observer kept receiving messages while slow client was not reading.");
        } finally {
            slow.close();
        }
        System.out.println("[scenario] Slow-client scenario complete.");
    }

    private static void scenarioConcurrent(Reconnector reconnector) throws Exception {
        String room = scenarioRoom("Concurrent");
        String suffix = Long.toString(System.currentTimeMillis());

        try (ClientConnection monitor = connect(reconnector, "monitor")) {
            registerAndLogin(monitor, "monitor_" + suffix, PASSWORD, "monitor");
            send(monitor, "JOIN " + room, "monitor");
            drain(monitor, "monitor", 1000, true);

            Thread monitorReader = Thread.startVirtualThread(() -> drainQuietly(monitor, "monitor", 10_000, true));
            List<Thread> workers = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                final int id = i;
                workers.add(Thread.startVirtualThread(() -> {
                    String label = "worker-" + id;
                    try (ClientConnection worker = connect(reconnector, label)) {
                        registerAndLogin(worker, "worker_" + suffix + "_" + id, PASSWORD, label);
                        send(worker, "JOIN " + room, label);
                        drain(worker, label, 500, true);
                        send(worker, "MSG concurrent hello from " + label, label);
                        drain(worker, label, 500, true);
                    } catch (Exception e) {
                        System.out.println("[" + label + "] failed: " + e.getMessage());
                    }
                }));
            }

            for (Thread worker : workers) {
                worker.join();
            }
            monitorReader.join();
        }
        System.out.println("[scenario] Concurrent scenario complete.");
    }

    private static ClientConnection connect(Reconnector reconnector, String label) throws IOException {
        ClientConnection client = reconnector.connect();
        client.setSoTimeout(10_000);
        String welcome = client.readLine();
        System.out.println("[" + label + "] < " + welcome);
        return client;
    }

    private static void registerAndLogin(ClientConnection client, String username, String password, String label) throws IOException {
        send(client, "REGISTER " + username + " " + password, label);
        drain(client, label, 800, true);
        login(client, username, password, label);
    }

    private static String login(ClientConnection client, String username, String password, String label) throws IOException {
        send(client, "LOGIN " + username + " " + password, label);
        long deadline = System.currentTimeMillis() + 5000;
        String token = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                String line = client.readLine();
                if (line == null) {
                    throw new IOException("connection closed during login");
                }
                if (line.equals("PING")) {
                    client.sendLine("PONG");
                    continue;
                }
                System.out.println("[" + label + "] < " + line);
                if (line.startsWith("OK TOKEN ")) {
                    token = line.substring("OK TOKEN ".length());
                    client.setSoTimeout(Constants.HEARTBEAT_TIMEOUT_MILLIS);
                    return token;
                }
            } catch (SocketTimeoutException ignored) {
                // Keep waiting until the deadline.
            }
        }
        throw new IOException("login did not return a token for " + username);
    }

    private static void send(ClientConnection client, String line, String label) throws IOException {
        System.out.println("[" + label + "] > " + line);
        client.sendLine(line);
    }

    private static void drainQuietly(ClientConnection client, String label, long millis, boolean answerPing) {
        try {
            drain(client, label, millis, answerPing);
        } catch (Exception e) {
            System.out.println("[" + label + "] drain ended: " + e.getMessage());
        }
    }

    private static void drain(ClientConnection client, String label, long millis, boolean answerPing) throws IOException {
        long deadline = System.currentTimeMillis() + millis;
        int previousTimeout = 1000;
        client.setSoTimeout(previousTimeout);
        while (System.currentTimeMillis() < deadline) {
            try {
                String line = client.readLine();
                if (line == null) {
                    System.out.println("[" + label + "] < connection closed");
                    return;
                }
                if (line.equals("PING")) {
                    System.out.println("[" + label + "] < PING" + (answerPing ? " -> PONG" : " ignored"));
                    if (answerPing) {
                        client.sendLine("PONG");
                    }
                } else {
                    System.out.println("[" + label + "] < " + line);
                }
            } catch (SocketTimeoutException ignored) {
                // No output during this small interval; keep draining until deadline.
            }
        }
    }

    private static String scenarioRoom(String prefix) {
        return "Scenario" + prefix + "_" + Instant.now().toEpochMilli();
    }
}
