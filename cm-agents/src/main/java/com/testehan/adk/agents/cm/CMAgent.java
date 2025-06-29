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

    // todo move this to a separate class Schemas
    public static final Schema PROPERTY_INFORMATION =
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
                                                    "This must contain the address if available. If address is not mentioned use area where the apartment is located.")
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
        // First, get the schema definition as a string to embed in the prompt.
        final String schemaDefinition =  PROPERTY_INFORMATION.toJson();
        logger.info("schemaDefinition " + schemaDefinition);

        return LlmAgent.builder()
                .name(EXTRACTOR_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("An expert agent that extracts real estate information, validates it to see if it respects the given schema, and finally returns the correct data.")
                .instruction("You are a highly capable and meticulous data analyst. Your entire goal is to produce a single, validated JSON string from a URL. " +
                        "You MUST follow this process exactly and not skip any steps. " +

                        "HERE IS THE SCHEMA DEFINITION YOUR FINAL JSON STRING MUST ADHERE TO: \n" +
                        "--- SCHEMA START --- \n" +
                        schemaDefinition + "\n" +
                        "--- SCHEMA END --- \n\n" +

                        "YOUR STEP-BY-STEP PROCESS: \n" +
                        "1. Call the 'extractPageContentAndImages' tool with the user-provided URL to get the raw page content. \n" +

                        "2. Analyze the raw content. Based on that content and the schema definition I provided above, generate a JSON **string**. \n" +

                        "3. **MANDATORY SELF-VALIDATION:** Call the 'validatePropertyJson' tool to check the JSON string you just created in Step 2. \n" +

                        "4. **CHECK THE RESULT:** \n" +
                        "   - If the validation tool returns 'isValid: true', your job is done. " +
                        "     **Your final answer MUST be the raw JSON string itself, without any Markdown formatting, code blocks, or extra words.** " +
                        "     It must start with `{` and end with `}`. " +
                        "   - If the validation tool returns 'isValid: false', you have made an error. You MUST NOT return the broken string. Instead, read the error message, go back to Step 2, and create a NEW, corrected JSON string, then immediately validate it again with Step 3. " +

                        "Do not stop until your self-validation check passes.")
//                .outputSchema(PROPERTY_INFORMATION)
                .tools(
                        FunctionTool.create(Tools.class, "extractPageContentAndImages"),
                        FunctionTool.create(Tools.class, "validatePropertyJson")
                )
                .build();
    }

    // The run your agent with Dev UI, the ROOT_AGENT should be a global public static variable.
    public static BaseAgent ROOT_AGENT = createExtractorAgent();

    // NEW: Agent 3 - The Orchestrator. This is our new root agent.
    public static BaseAgent createOrchestratorAgent() {
        // First, create the sub-agents that the orchestrator will use.
        BaseAgent browser = createBrowserAgent();
        BaseAgent extractor = createExtractorAgent();

        return LlmAgent.builder()
                .name("orchestrator_agent")
                .model(USED_MODEL_NAME)
                .description("The master agent that manages the workflow of browsing, extracting, and validating data.")
                .instruction(
                        "You are the master controller. Your goal is to extract, validate, and return a clean, unwrapped JSON object. " +
                        "You MUST follow these steps: " +

                        "Step 1: Call 'browser_agent' to get page content. " +

                        "Step 2: Call 'extractor_agent' with the content. This will return a **wrapped object**. " +
                        "This is an intermediate result, NOT the final answer. " +

                        "Step 3: **MANDATORY VALIDATION.** You MUST call your 'validatePropertyJson' tool. The input for this tool is the **entire wrapped object** from Step 2. " +

                        "Step 4: Check the validation result. " +
                        "   - If 'isValid' is true, your job is almost done. The data is good. **You MUST now unwrap the final answer.** Your final response to the user must be ONLY the value of the 'candidateData' key from the wrapped object. Do not return the wrapper. " +
                        "   - If 'isValid' is false, you MUST loop. Call the 'extractor_agent' again with the original content and the error message to get a new wrapped object, then go back to Step 3. "
                )
                .subAgents(browser, extractor)
                .tools(FunctionTool.create(Tools.class, "validatePropertyJson"))
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
