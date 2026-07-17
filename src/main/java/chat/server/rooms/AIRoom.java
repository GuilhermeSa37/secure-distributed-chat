package chat.server.rooms;

import chat.server.ai.OllamaClient;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class AIRoom extends Room {
    private static final int AI_CONTEXT_MESSAGE_LIMIT = 10;

    private final String prompt;
    private final OllamaClient ollamaClient;
    private final ReentrantLock aiLock = new ReentrantLock();

    public AIRoom(String name, String prompt, OllamaClient ollamaClient) {
        super(name);
        this.prompt = prompt;
        this.ollamaClient = ollamaClient;
    }

    public void triggerAI() {
        // AI generation is triggered manually by the authenticated AI command.
        // It uses the room's original prompt configured at CREATE_AI_ROOM time.
        triggerAI(this.prompt);
    }

    public void triggerAI(String prompt) {
        // The work runs in a virtual thread so the command handler does not block on Ollama.
        Thread.startVirtualThread(() -> generateBotResponse(prompt));
    }

    private void generateBotResponse(String customPrompt) {
        // Serialize AI calls per room to avoid overlapping Bot responses with inconsistent context.
        aiLock.lock();
        try {
            List<Message> context = recentMessagesSnapshot(AI_CONTEXT_MESSAGE_LIMIT);
            String response = ollamaClient.generate(customPrompt, context);
            postBotMessage(response);
        } catch (Exception e) {
            System.err.println("[server] AI response generation failed: " + e.getMessage());
            postBotMessage("I couldn't generate a response right now. Please try again.");
        } finally {
            aiLock.unlock();
        }
    }
}
