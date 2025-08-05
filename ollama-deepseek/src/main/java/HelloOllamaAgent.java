//import com.google.adk.agents.LlmAgent;
//import com.google.adk.models.langchain4j.LangChain4j;
//import com.google.adk.runner.InMemoryRunner;
//import com.google.adk.sessions.Session;
//import com.google.genai.types.Content;
//import com.google.genai.types.Part;
//import dev.langchain4j.model.chat.ChatModel;
//import dev.langchain4j.model.openai.OpenAiChatModel;
//
//import java.util.Scanner;
//import java.util.concurrent.ConcurrentMap;
//import java.util.concurrent.ExecutionException;

//import com.google.adk.agents.LlmAgent;
//import com.google.adk.models.langchain4j.LangChain4j;
//import com.google.adk.runner.InMemoryRunner;
//import com.google.adk.sessions.Session;
//import com.google.genai.types.Content;
//import com.google.genai.types.Part;
//import dev.langchain4j.model.chat.ChatModel;
//
//import java.util.concurrent.ConcurrentMap;
//import java.util.concurrent.ExecutionException;

public class HelloOllamaAgent {

//    public static void main(String[] args) throws Exception {
//        // Create a single-threaded executor that can schedule commands.
//
//        Runnable agentRunner = () -> {
//            try {
//
//                runLocalOllama();
//
//            } catch (Exception e) {
//                // It's crucial to catch exceptions, otherwise the scheduler
//                // will stop executing the task if it throws an unhandled exception.
//                e.printStackTrace();
//            }
//        };
//
//        agentRunner.run();
//    }
//
//    private static void runLocalOllama() throws ExecutionException, InterruptedException {
//        // 1. Configure the LangChain4j model to connect to your local Ollama instance.
//        ChatModel ollamaModel = OpenAiChatModel.builder()
//                .baseUrl("http://localhost:11434") // Default Ollama URL
//                .modelName("gpt-oss:20b") // The model you pulled with Ollama
//                .build();
//
//        // 2. Build the ADK Agent, passing the Ollama model via the LangChain4j wrapper.
//        LlmAgent ollamaAgent = LlmAgent.builder()
//                .name("ollama-assistant")
//                .description("An agent powered by a local Ollama model.")
//                .model(new LangChain4j(ollamaModel))
//                .globalInstruction("You are a helpful assistant running on a local machine. Be concise and clear in your responses.")
//                .build();
//
//        System.out.println("Your local Ollama agent is ready. Type 'exit' to quit.");
//
//        // 3. Run the agent in a loop using the InMemoryRunner.
//        InMemoryRunner runner = new InMemoryRunner(ollamaAgent);
//
//        Session session = runner.sessionService()
//                .createSession(ollamaAgent.name(), ollamaAgent.name())
//                .blockingGet();
//        var scanner = new Scanner(System.in);
//
//        while (true) {
//            System.out.print("You: ");
//            String userInput = scanner.nextLine();
//            if ("exit".equalsIgnoreCase(userInput)) {
//                break;
//            }
//            Content contentFromUser = Content.fromParts(Part.fromText(userInput));
//            runner.runAsync(ollamaAgent.name(), session.id(), contentFromUser)
//                    .filter(event -> {
//                        // We only want to process events that have a non-null stateDelta
//                        // and contain our specific result key.
//                        if (event.actions() == null || event.actions().stateDelta() == null) {
//                            return false; // Discard events without a stateDelta.
//                        }
//                        // Keep the event only if it contains the "individual_json_result" key.
//                        return true; //event.actions().stateDelta().containsKey(OUTPUT_MASTER_ORCHESTRATOR_LISTING);
//                    })
//                    .blockingForEach(event -> {
//                        ConcurrentMap<String, Object> stateDelta = event.actions().stateDelta();
//                        System.out.println("Agent: " + stateDelta.toString());
//                    });
//
//        }
//
//        scanner.close();
//        System.out.println("Session ended.");
//    }
}