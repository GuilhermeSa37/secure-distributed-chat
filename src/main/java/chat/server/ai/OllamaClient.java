package chat.server.ai;

import chat.server.rooms.Message;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class OllamaClient {
    private static final String DEFAULT_MODEL = "llama3";

    private final String endpoint;
    private final String model;
    private final HttpClient httpClient;

    public OllamaClient(String endpoint) {
        this(endpoint, System.getProperty("chat.ollama.model", DEFAULT_MODEL));
    }

    public OllamaClient(String endpoint, String model) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String generate(String prompt, List<Message> context) {
        String transcript = context.stream()
                .map(Message::asContextLine)
                .collect(Collectors.joining("\n"));

        String fullPrompt = """
                Room instruction:
                %s

                Conversation so far:
                %s

                Answer as Bot. Keep the response useful and concise.
                """.formatted(prompt, transcript);

        String requestBody = """
                {
                    "model": "%s",
                    "system": "You are Bot, an assistant participating in a chat room.",
                    "prompt": "%s",
                    "stream": false
                }
                """.formatted(escapeJson(model), escapeJson(fullPrompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/generate"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama API error: HTTP " + response.statusCode());
            }
            return extractResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama request interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not connect to Ollama at " + endpoint, e);
        }
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String extractResponse(String json) {
        String marker = "\"response\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("Ollama response did not contain a response field");
        }

        start += marker.length();
        StringBuilder value = new StringBuilder();
        boolean escaping = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                switch (c) {
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    default -> value.append(c);
                }
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                return value.toString().trim();
            } else {
                value.append(c);
            }
        }

        throw new IllegalStateException("Could not parse Ollama response field");
    }
}
