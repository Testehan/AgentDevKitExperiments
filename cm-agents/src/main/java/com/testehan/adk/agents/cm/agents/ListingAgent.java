package com.testehan.adk.agents.cm.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.tools.FunctionTool;
import com.testehan.adk.agents.cm.tools.Tools;

import static com.testehan.adk.agents.cm.Schemas.PROPERTY_INFORMATION;
import static com.testehan.adk.agents.cm.config.Constants.*;

public class ListingAgent {

    // Agent 1 - The API Scout. Its only job is to call the API.
    private static BaseAgent createApiScoutAgent() {
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
    private static BaseAgent createExtractorAgent() {
        return LlmAgent.builder()
                .name(EXTRACTOR_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("This agent calls uses extractPageContentAndImages using the provided url")
                .instruction("Your only job is to call the '" + TOOL_EXTRACT + "' tool using the provided '{url_to_use}'. " +
                        "After you get the result from the tool, your job is done. Output the raw result from the tool directly.")
                .tools(FunctionTool.create(Tools.class, TOOL_EXTRACT))
                .build();
    }

    // Agent 3 - The Formatter Agent. Its only job is to format the input to the provided schema.
    private static BaseAgent createFormatterAgent() {
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


    // Agent 4 - The Master Orchestrator with Looping Logic. This is the new Root Agent.
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

}
