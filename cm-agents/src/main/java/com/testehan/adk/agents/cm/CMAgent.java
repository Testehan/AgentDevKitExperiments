package com.testehan.adk.agents.cm;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.testehan.adk.agents.cm.tools.Tools;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class CMAgent {

    private static final Logger logger = LoggerFactory.getLogger(CMAgent.class);
    public static final String USED_MODEL_NAME = "gemini-2.5-pro";

    private static String USER_ID = "casamia";

    private static final String BROWSER_AGENT_NAME = "browser_agent";
    private static final String EXTRACTOR_AGENT_NAME = "extractor_agent";

    private static final Schema PROPERTY_INFORMATION =
            Schema.builder()
                    .type("OBJECT")
                    .description("Schema for the extracted property information.")
                    .properties(
                            Map.of(
                                    "name",
                                    Schema.builder()
                                            .type("STRING")
                                            .description("Name of the listing.")
                                            .build(),
                                    "city",
                                    Schema.builder()
                                            .type("STRING")
                                            .description("City where the apartment is located.")
                                            .build(),
                                    "area",
                                    Schema.builder()
                                            .type("STRING")
                                            .description(
                                                    "The address or area where the apartment is located in the mentioned city.")
                                            .build(),
                                    "shortDescription",
                                    Schema.builder()
                                            .type("STRING")
                                            .description("The apartment description.")
                                            .build(),
                                    "price",
                                    Schema.builder()
                                            .type("INTEGER")
                                            .description("Price of the apartment. I only want the number, not the currency.")
                                            .build(),
                                    "surface",
                                    Schema.builder()
                                            .type("INTEGER")
                                            .description("Surface area of the apartment in square meters.")
                                            .build(),
                                    "noOfRooms",
                                    Schema.builder()
                                            .type("INTEGER")
                                            .description("Number of rooms in the apartment.")
                                            .build(),
                                    "floor",
                                    Schema.builder()
                                            .type("INTEGER")
                                            .description("Floor of the apartment.")
                                            .build(),
                                    "ownerName",
                                    Schema.builder()
                                            .type("STRING")
                                            .description("Name of the owner.")
                                            .build(),
                                    "imageUrls", Schema.builder()
                                            .type("ARRAY")
                                            .description("A list of all found image URLs from the page.")
                                            .items(Schema.builder().type("STRING").build())
                                            .build()
                            ))
                    .required(
                            List.of(
                                    "name",
                                    "city",
                                    "area",
                                    "shortDescription",
                                    "price",
                                    "surface",
                                    "noOfRooms",
                                    "floor",
                                    "ownerName"))
                    .build();

    // Agent 1 - The Browser Agent. Its only job is to use the tool.
    public static BaseAgent createBrowserAgent() {
        return LlmAgent.builder()
                .name(BROWSER_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("This agent accepts a URL and uses a headless browser to return the full page text and a list of image URLs.")
                .instruction("You are a web browser. Given a URL, call the 'extractPageContentAndImages' tool and return the result.")
                .tools(FunctionTool.create(Tools.class, "extractPageContentAndImages"))
                .build();
    }

    // Agent 2 - The Extractor Agent. Its only job is to extract data.
    public static BaseAgent createExtractorAgent() {
        return LlmAgent.builder()
                .name(EXTRACTOR_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("Agent to extract relevant real estate information from text.")
                .instruction("You are a real estate data analyst. Analyze the provided text to find all the required " +
                        "details and return them as a clean JSON object matching the schema.")
                .outputSchema(PROPERTY_INFORMATION)
                .build();
    }

    // The run your agent with Dev UI, the ROOT_AGENT should be a global public static variable.
    public static BaseAgent ROOT_AGENT = createOrchestratorAgent();

    // NEW: Agent 3 - The Orchestrator. This is our new root agent.
    public static BaseAgent createOrchestratorAgent() {
        // First, create the sub-agents that the orchestrator will use.
        BaseAgent browser = createBrowserAgent();
        BaseAgent extractor = createExtractorAgent();

        return LlmAgent.builder()
                .name("orchestrator_agent")
                .model(USED_MODEL_NAME)
                .description("A real estate assistant that extracts property details and image URLs from a webpage.")
                .instruction(
                        "You are a master real estate analyst. Your goal is to extract structured data from a user-provided URL. "
                                + "You must do this in two steps: "
                                + "1. First, call the 'browser_agent' with the URL. This will return a JSON object " +
                                "containing 'pageText' and 'imageUrls'. "
                                + "2. Second, take the entire JSON object returned by the browser and pass it as input " +
                                "to the 'extractor_agent' to get the final structured data. Make sure to include both " +
                                "the text and the list of image URLs in the prompt for the extractor."
                )
                .subAgents(browser, extractor)
                .build();
    }

    public static void main(String[] args) throws Exception {
        // We now initialize the runner with our single ROOT_AGENT, the orchestrator.
        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);

        Session session = runner.sessionService()
                .createSession(ROOT_AGENT.name(), USER_ID)
                .blockingGet();

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("\nYou > ");
                String userInput = scanner.nextLine();

                if ("quit".equalsIgnoreCase(userInput)) {
                    break;
                }

                // The user's input is sent directly to the orchestrator agent.
                Content userMsg = Content.fromParts(Part.fromText(userInput));

                // The orchestrator's LLM will now handle the logic of calling the
                // browser and then the extractor for us.
                System.out.print("\nAgent > ");
                Flowable<Event> events = runner.runAsync(USER_ID, session.id(), userMsg);
                events.blockingForEach(event -> System.out.println(event.stringifyContent()));
            }
        }
    }
}
