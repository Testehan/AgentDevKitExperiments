package com.testehan.adk.agents.cm;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.testehan.adk.agents.cm.tools.Tools;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static com.testehan.adk.agents.cm.Schemas.PROPERTY_INFORMATION;

public class CMAgent {

    private static final String USED_MODEL_NAME = "gemini-2.5-pro";
    private static String USER_ID = "casamia";
    private static final String EXTRACTOR_AGENT_NAME = "extractor_agent";

    private static final Logger LOGGER = LoggerFactory.getLogger(CMAgent.class);


    // Agent 1 - The Extractor Agent. Its only job is to extract data and make sure it is valid.
    public static BaseAgent createExtractorAgent() {
        // First, get the schema definition as a string to embed in the prompt.
        final String schemaDefinition =  PROPERTY_INFORMATION.toJson();
        LOGGER.info("schemaDefinition " + schemaDefinition);

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
