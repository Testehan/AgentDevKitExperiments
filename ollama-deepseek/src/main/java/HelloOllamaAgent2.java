import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.reactivex.rxjava3.core.Flowable;

public class HelloOllamaAgent2 {

    public static BaseAgent ROOT_AGENT = initAgent();

    public static BaseAgent initAgent() {
        ChatModel chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("qwen3:1.7b") // Ensure this matches the model you pulled with Ollama
                .build();
        LangChain4j langChain4j = new LangChain4j(chatModel);
        return LlmAgent.builder()
                .name("HelloOllamaAgent")
                .model(langChain4j)
                .instruction("You are a helpful AI assistant.")
                .build();
    }

    public static void main(String[] args) {
        System.out.println("Invoking HelloOllamaAgent...\n");
        String prompt = "You are a friendly AI assistant named HelloOllamaAgent. Your primary function is " +
                "to greet users warmly and briefly introduce yourself as an AI powered by Ollama.";
        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);
        Session session = runner
                .sessionService()
                .createSession("HelloOllamaAgent", "user123")
                .blockingGet();
        System.out.println("Simulating Ollama LLM call with prompt: " + prompt);
        Content userMsg = Content.fromParts(Part.fromText(prompt));
        Flowable<Event> events = runner.runAsync("user123", session.id(), userMsg);
        System.out.print("\nAgent Response -> ");
        events.blockingForEach(event -> System.out.println(event.stringifyContent()));
    }
}