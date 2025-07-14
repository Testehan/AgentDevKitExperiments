package com.testehan.adk.agents.cm;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.testehan.adk.agents.cm.agents.LoopingProcessorAgent;
import com.testehan.adk.agents.cm.config.ConfigLoader;
import com.testehan.adk.agents.cm.tools.ListingUploader;
import com.testehan.adk.agents.cm.tools.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.testehan.adk.agents.cm.Schemas.PROPERTY_INFORMATION;
import static com.testehan.adk.agents.cm.config.Constants.*;


public class CMAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(CMAgent.class);

    // Agent 1 - The API Scout. Its only job is to call the API.
    public static BaseAgent createApiScoutAgent() {
        return LlmAgent.builder()
                .name(API_SCOUT_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("This agent calls an API and extracts a clean list of URLs from the tool's response.")
                .instruction(
                        "You are an API client that extracts data. " +
                        "You must call the '" + TOOL_GET_URLS + "' tool. " +
                        "The tool will return a map containing a 'status' and a 'urls' key. " +
                        "Your final output MUST be ONLY the value of the 'urls' key. " +
                        "Return the raw JSON array of URLs and nothing else. Do not include 'status', commentary, or any other text."
                )
                .tools(FunctionTool.create(Tools.class, TOOL_GET_URLS))
                .outputKey(OUTPUT_SCOUT_AGENT) // Key for storing output in session state
                .build();
    }

    // Agent 2 - The Extractor Agent. Its only job is to extract data and make sure it is valid.
    public static BaseAgent createExtractorAgent() {
        return LlmAgent.builder()
                .name(EXTRACTOR_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("This agent calls uses extractPageContentAndImages using the provided url")
                .instruction("Your only job is to call the '" + TOOL_EXTRACT + "' tool using the provided '{url_to_use}'. " +
                            "After you get the result from the tool, your job is done. Output the raw result from the tool directly.")
                .tools(FunctionTool.create(Tools.class, TOOL_EXTRACT))
                .build();
    }

    public static BaseAgent createFormatterAgent() {
        final String schemaDefinition = PROPERTY_INFORMATION.toJson();

        return LlmAgent.builder()
                .name(FORMATTER_AGENT)
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
                .build();
    }


    // Agent 3 - The Master Orchestrator with Looping Logic. This is the new Root Agent.
    public static BaseAgent createOrchestratorAgent() {
        // The orchestrator needs to know about its sub-agents.
        BaseAgent apiScout = createApiScoutAgent();
        BaseAgent extractor = createExtractorAgent();
        BaseAgent formatter = createFormatterAgent();

        return SequentialAgent.builder()
                .name(MASTER_ORCHESTRATOR_AGENT_NAME)
                .description("Manages a data pipeline by fetching a list of URLs and then looping through them to call an extractor agent for each.")
                .subAgents(apiScout, new LoopingProcessorAgent(extractor,formatter))
                .build();
    }

    // The run your agent with Dev UI, the ROOT_AGENT should be a global public static variable.
    public static BaseAgent ROOT_AGENT = createOrchestratorAgent();

    public static void main(String[] args) throws Exception {
        // Create a single-threaded executor that can schedule commands.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // Define the task that will be executed periodically.
        Runnable agentRunner = () -> {
            try {
                LOGGER.info("EXECUTING: Starting the orchestrator agent run...");

                runRootAgent();

                LOGGER.info("SUCCESS: Orchestrator agent run finished.");

            } catch (Exception e) {
                // It's crucial to catch exceptions, otherwise the scheduler
                // will stop executing the task if it throws an unhandled exception.
                LOGGER.error("ERROR: An exception occurred during agent execution: {}", e.getMessage());
                e.printStackTrace();
            }
        };

        LOGGER.info("Scheduler initialized. The agent will run every 3 hours.");
        scheduler.scheduleAtFixedRate(agentRunner, 0, 5, TimeUnit.HOURS);

        // This application will keep running because the scheduler thread is active.
        // In a real server application, you would manage the lifecycle and
        // implement a graceful shutdown of the scheduler when the app terminates.

    }

    private static void runRootAgent() {
        // We now initialize the runner with our single ROOT_AGENT, the orchestrator.
        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);

        Session session = runner.sessionService()
                .createSession(ROOT_AGENT.name(), USER_ID)
                .blockingGet();

        Content apiUrl = Content.fromParts(Part.fromText(ConfigLoader.getApiEndpointGetLeads()));

        runner.runAsync(USER_ID, session.id(), apiUrl)
            .filter(event -> {
                // We only want to process events that have a non-null stateDelta
                // and contain our specific result key.
                if (event.actions() == null || event.actions().stateDelta() == null) {
                    return false; // Discard events without a stateDelta.
                }
                // Keep the event only if it contains the "individual_json_result" key.
                return event.actions().stateDelta().containsKey(OUTPUT_MASTER_ORCHESTRATOR_LISTING);
            })
            .blockingForEach(event -> {
                ConcurrentMap<String, Object> stateDelta = event.actions().stateDelta();
                String individualJson = (String) stateDelta.get(OUTPUT_MASTER_ORCHESTRATOR_LISTING);
                String listingSourceUrl = (String) stateDelta.get(OUTPUT_MASTER_ORCHESTRATOR_URL);

                if (individualJson != null && !individualJson.isEmpty()) {
                    LOGGER.info("✅ Received a result: \n{}", individualJson);
                    ListingUploader uploader = new ListingUploader();
                    uploader.upload(individualJson,listingSourceUrl);
                }
            });
    }

}
