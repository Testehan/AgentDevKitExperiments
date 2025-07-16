package com.testehan.adk.agents.cm.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.tools.FunctionTool;
import com.testehan.adk.agents.cm.agents.helpers.LoopingUrlsProcessorAgent;
import com.testehan.adk.agents.cm.tools.Tools;

import static com.testehan.adk.agents.cm.Schemas.PROPERTY_INFORMATION;
import static com.testehan.adk.agents.cm.agents.helpers.CommonAgents.createApiScoutAgent;
import static com.testehan.adk.agents.cm.config.Constants.*;

public class ListingAgents {

    // Agent 1 - The API Scout. Its only job is to call the API.
    private static BaseAgent apiScout = createApiScoutAgent();

    // Agent 2 - The Extractor Agent. Its only job is to extract data and make sure it is valid.
    private static BaseAgent extractor = createExtractorAgent();

    // Agent 3 - The Formatter Agent. Its only job is to format the input to the provided schema.
    private static BaseAgent formatter = createFormatterAgent();

    private static BaseAgent createExtractorAgent() {
        return LlmAgent.builder()
                .name(EXTRACTOR_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("This agent calls uses extractPageContentAndImages using the provided url")
                .instruction("Your only job is to call the '" + TOOL_EXTRACT + "' tool using the provided " +
                        "'{"+ AGENT_VAR_LISTING_URL_INITIAL_SOURCE +"}'. " +
                        "After you get the result from the tool, your job is done. Output the raw result from the tool directly.")
                .tools(FunctionTool.create(Tools.class, TOOL_EXTRACT))
                .build();
    }

    private static BaseAgent createFormatterAgent() {
        final String schemaDefinition = PROPERTY_INFORMATION.toJson();

        return LlmAgent.builder()
                .name(FORMATTER_AGENT)
                .model(USED_MODEL_NAME)
                .description("This agent takes raw input and formats it")
                .instruction("You are a JSON formatting expert. You will receive raw text under the variable '{" + AGENT_VAR_LISTING_SCRAPED_TEXT +"}'. " +
                        "Do not try to scrape or browse anything. Your only task is to convert the provided text into a valid JSON object that adheres to the provided schema.\n" +

                        "HERE IS THE SCHEMA DEFINITION YOUR FINAL JSON STRING MUST ADHERE TO: \n" +
                        "--- SCHEMA START --- \n" +
                        schemaDefinition + "\n" +
                        "--- SCHEMA END --- \n\n" +

                        "Based *only* on the provided content and the schema definition, generate the final JSON string." +
                        "Very important : the city field must contain the name of a valid city from Romania. If the value is Cluj then that is not" +
                        "a valid city name. Replace it with Cluj-Napoca." +
                        "Your final answer MUST be ONLY the raw JSON string. Do not wrap it in markdown or add any other text. Your entire output must start with `{` and end with `}`.")
                .build();
    }

    // Agent 4 - The Master Orchestrator with Looping Logic. This is the new Root Agent.
    public static BaseAgent createOrchestratorAgent() {
        return SequentialAgent.builder()
                .name(MASTER_ORCHESTRATOR_LISTINGS_AGENT_NAME)
                .description("Manages a data pipeline by fetching a list of URLs and then looping through them to call an extractor agent for each.")
                .subAgents(apiScout, new LoopingUrlsProcessorAgent(extractor,formatter))
                .build();
    }

}
