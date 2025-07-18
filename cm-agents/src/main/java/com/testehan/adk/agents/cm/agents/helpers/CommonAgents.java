package com.testehan.adk.agents.cm.agents.helpers;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import com.testehan.adk.agents.cm.tools.Tools;

import static com.testehan.adk.agents.cm.config.Constants.*;
import static com.testehan.adk.agents.cm.config.Constants.OUTPUT_SCOUT_AGENT;

public class CommonAgents {

    public static BaseAgent createApiScoutURLsAgent() {
        return LlmAgent.builder()
                .name(API_SCOUT_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("This agent calls an API and extracts a clean list of URLs from the tool's response.")
                .instruction(
                        "You are an API client that extracts data. " +
                                "You must call the '" + TOOL_GET_STRINGS + "' tool. " +
                                "The tool will return a map containing a 'status' and a '" + OUTPUT_SCOUT_AGENT +"' key. " +
                                "Your final output MUST be ONLY the value of the '" + OUTPUT_SCOUT_AGENT + "' key. " +
                                "Return the raw JSON array of URLs and nothing else. Do not include 'status', commentary, or any other text." +
                                "If no URLs are found, return an empty array."
                )
                .tools(FunctionTool.create(Tools.class, TOOL_GET_STRINGS))
                .outputKey(OUTPUT_SCOUT_AGENT) // Key for storing output in session state
                .build();
    }

    public static BaseAgent createApiScoutPhoneUrlsAgent() {
        return LlmAgent.builder()
                .name(API_SCOUT_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("This agent calls an API and extracts a clean list of Map<String,String> from the tool's response.")
                .instruction(
                        "You are an API client that extracts data. " +
                                "You must call the '" + TOOL_GET_MAPS + "' tool. " +
                                "The tool will return a map containing a 'status' and a '" + OUTPUT_SCOUT_AGENT +"' key. " +
                                "Your final output MUST be ONLY the value of the '" + OUTPUT_SCOUT_AGENT + "' key. " +
                                "The '" + OUTPUT_SCOUT_AGENT + "' key contains a JSON array of objects, each with 'phoneNumber' and 'url' fields. " +
                                "Return the raw JSON array of Map<String,String> and nothing else. Do not include 'status', commentary, or any other text." +
                                "If no Map<String,String> are found, return an empty array."
                )
                .tools(FunctionTool.create(Tools.class, TOOL_GET_MAPS))
                .outputKey(OUTPUT_SCOUT_AGENT) // Key for storing output in session state
                .build();
    }

    public static BaseAgent createApiScoutPhonesAgent() {
        return LlmAgent.builder()
                .name(API_SCOUT_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("This agent calls an API and extracts a clean list of phone numbers from the tool's response.")
                .instruction(
                        "You are an API client that extracts data. " +
                                "You must call the '" + TOOL_GET_MAPS + "' tool. " +
                                "The tool will return a map containing a 'status' and a '" + OUTPUT_SCOUT_AGENT +"' key. " +
                                "Your final output MUST be ONLY the value of the '" + OUTPUT_SCOUT_AGENT + "' key. " +
                                "The '" + OUTPUT_SCOUT_AGENT + "' key contains a JSON array of objects, each with 'phoneNumber' and 'url' fields. " +
                                "Return the raw JSON array of Map<String,String> and nothing else. Do not include 'status', commentary, or any other text." +
                                "If no Map<String,String> are found, return an empty array."
                )
                .tools(FunctionTool.create(Tools.class, TOOL_GET_MAPS))
                .outputKey(OUTPUT_SCOUT_AGENT) // Key for storing output in session state
                .build();
    }

}
