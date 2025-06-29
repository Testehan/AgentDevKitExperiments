package com.testehan.adk.agents.cm;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.testehan.adk.agents.cm.agents.LoopingProcessorAgent;
import com.testehan.adk.agents.cm.tools.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static com.testehan.adk.agents.cm.Schemas.PROPERTY_INFORMATION;

public class CMAgent {

    private static final String USED_MODEL_NAME = "gemini-2.5-pro";
    private static String USER_ID = "casamia";
    public static final String MASTER_ORCHESTRATOR_AGENT_NAME = "master_orchestrator_agent";
    private static final String API_SCOUT_AGENT_NAME = "api_scout_agent";
    private static final String EXTRACTOR_AGENT_NAME = "extractor_agent";

    private static final Logger LOGGER = LoggerFactory.getLogger(CMAgent.class);

    // Agent 1 - The API Scout. Its only job is to call the API.
    public static BaseAgent createApiScoutAgent() {
        return LlmAgent.builder()
                .name(API_SCOUT_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("This agent calls an API and extracts a clean list of URLs from the tool's response.")
                .instruction(
                        "You are an API client that extracts data. " +
                        "You must call the 'getUrlsFromApi' tool. " +
                        "The tool will return a map containing a 'status' and a 'urls' key. " +
                        "Your final output MUST be ONLY the value of the 'urls' key. " +
                        "Return the raw JSON array of URLs and nothing else. Do not include 'status', commentary, or any other text."
                )
                .tools(FunctionTool.create(Tools.class, "getUrlsFromApi"))
                .outputKey("urls") // Key for storing output in session state
                .build();
    }

    // Agent 2 - The Extractor Agent. Its only job is to extract data and make sure it is valid.
    public static BaseAgent createExtractorAgent() {
        // First, get the schema definition as a string to embed in the prompt.
        final String schemaDefinition =  PROPERTY_INFORMATION.toJson();
        LOGGER.info("schemaDefinition {}", schemaDefinition);

        return LlmAgent.builder()
                .name(EXTRACTOR_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("An expert agent that extracts real estate information, validates it to see if it respects the given schema, and finally returns the correct data.")
                .instruction("You are a highly capable and meticulous data analyst. Your goal is to produce a single, final JSON string. " +
                            "You will be given a URL to process via the '{url_to_use}' variable." +

                            "HERE IS THE SCHEMA DEFINITION YOUR FINAL JSON STRING MUST ADHERE TO: \n" +
                            "--- SCHEMA START --- \n" +
                            schemaDefinition + "\n" +
                            "--- SCHEMA END --- \n\n" +

                            "**YOUR PROCESS IS STRICT AND MUST BE FOLLOWED EXACTLY:** \n" +
                            "1. **Call the `extractPageContentAndImages` tool EXACTLY ONCE.** This is your only opportunity to get data from the URL. \n" +

                            "2. **DO NOT call `extractPageContentAndImages` again.** After the first and only tool call is complete, you MUST work only with the content provided in the tool's response. \n" +

                            "3. Analyze the tool's response content. Based *only* on that content and the schema definition, generate the final JSON string. \n" +

                            "4. Your final answer MUST be ONLY the raw JSON string. Do not wrap it in markdown or add any other text. Your entire output must start with `{` and end with `}`."
                        )

                .tools(
                        FunctionTool.create(Tools.class, "extractPageContentAndImages")//,
                   //     FunctionTool.create(Tools.class, "validatePropertyJson")
                )
                .outputKey("listing_json")
                .build();
    }

    /*
     +

                        "3. **MANDATORY SELF-VALIDATION:** Call the 'validatePropertyJson' tool to check the JSON string you just created in Step 2. \n" +

                        "4. **CHECK THE RESULT:** \n" +
                        "   - If the validation tool returns 'isValid: true', your job is done. " +
                        "     **Your final answer MUST be the raw JSON string itself, without any Markdown formatting, code blocks, or extra words.** " +
                        "     It must start with `{` and end with `}`. " +
                        "   - If the validation tool returns 'isValid: false', you have made an error. You MUST NOT return the broken string. Instead, read the error message, go back to Step 2, and create a NEW, corrected JSON string, then immediately validate it again with Step 3. " +
                        "   - **If you have tried more than 3 times and are still failing, STOP and output an error message saying 'ERROR: Failed to generate valid JSON after 3 attempts.'**"
     */

    public static BaseAgent createScraperAgent() {
        return LlmAgent.builder()
                .name("scraper_agent")
                // A simple, cheap model is fine for this.
                .model(USED_MODEL_NAME)
                .description("This agent calls uses extractPageContentAndImages using the provided url")
                .instruction("Your only job is to call the 'extractPageContentAndImages' tool using the provided '{url_to_use}'. " +
                        "After you get the result from the tool, your job is done. Output the raw result from the tool directly.")
                .tools(FunctionTool.create(Tools.class, "extractPageContentAndImages"))
                // We'll capture the output under a new key.
                .outputKey("scraper_output")
                .build();
    }

    public static BaseAgent createFormatterAgent() {
        final String schemaDefinition = PROPERTY_INFORMATION.toJson();

        return LlmAgent.builder()
                .name("formatter_agent")
                .model(USED_MODEL_NAME)
                .description("This agent takes raw input and formats it")
                // The input is now a variable named 'scraped_text'.
                .instruction("You are a JSON formatting expert. You will receive raw text under the variable '{scraped_text}'. " +
                        "Do not try to scrape or browse anything. Your only task is to convert the provided text into a valid JSON object that adheres to the provided schema.\n" +

                        "HERE IS THE SCHEMA DEFINITION YOUR FINAL JSON STRING MUST ADHERE TO: \n" +
                        "--- SCHEMA START --- \n" +
                        schemaDefinition + "\n" +
                        "--- SCHEMA END --- \n\n" +

                        "Based *only* on the provided content and the schema definition, generate the final JSON string." +
                        "Your final answer MUST be ONLY the raw JSON string. Do not wrap it in markdown or add any other text. Your entire output must start with `{` and end with `}`.")
                .outputKey("listing_json") // The final output key.
                .build();
    }


    // Agent 3 - The Master Orchestrator with Looping Logic. This is the new Root Agent.
    public static BaseAgent createOrchestratorAgent() {
        // The orchestrator needs to know about its sub-agents.
        BaseAgent apiScout = createApiScoutAgent();
        BaseAgent extractor = createScraperAgent();
        BaseAgent formatter = createFormatterAgent();

        return SequentialAgent.builder()
                .name(MASTER_ORCHESTRATOR_AGENT_NAME)
                //.model(USED_MODEL_NAME)
                .description("Manages a data pipeline by fetching a list of URLs and then looping through them to call an extractor agent for each.")
                .subAgents(apiScout, new LoopingProcessorAgent(extractor,formatter))
//                .instruction(
//                        "You are a master workflow controller for a data pipeline. Your ONLY goal is to produce a final JSON array of structured property data. " +
//                        "You MUST perform this as a multi-step process and MUST NOT stop until the final step is complete. " +
//
//                        "Step 1: You will be given an API endpoint URL. You must call the 'api_scout_agent'. " +
//                        "This will return a clean JSON array of URLs to be processed. " +
//
//                        "Step 2: **You must not report this list of URLs to the user.** This is intermediate data for your use only. " +
//                        "You will now iterate through the list of URLs. For **each URL in the list, one by one**, you will perform the next step. " +
//
//                        "Step 3: Take the current property URL from the list and pass it as input to the 'extractor_agent'. " +
//
//                        "Step 4: After you have processed **ALL** the URLs from the list, your final task is to assemble the results. " +
//                        "Your final answer MUST be a single, well-formatted **JSON array that contains all the individual property JSON objects** you collected. " +
//                        "Do not say anything else. Your entire output should start with `[` and end with `]`."
//                )
                .build();
    }

    // The run your agent with Dev UI, the ROOT_AGENT should be a global public static variable.
    public static BaseAgent ROOT_AGENT = createOrchestratorAgent();

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
//                runner.runAsync(USER_ID, session.id(), userMsg).blockingSubscribe();
                Event finalEvent = runner.runAsync(USER_ID, session.id(), userMsg).blockingLast();

                final String OUTPUT_KEY = "final_property_list";
               String finalPropertyList = (String) finalEvent.actions().stateDelta().get(OUTPUT_KEY);

                if (finalPropertyList != null) {
                    System.out.println("✅✅✅ SUCCESS! Retrieved : \n" + finalPropertyList);
                } else {
                    System.out.println("❌ FAILED: The final property list was not found in the session state.");
                }
            }
        }
    }

}
