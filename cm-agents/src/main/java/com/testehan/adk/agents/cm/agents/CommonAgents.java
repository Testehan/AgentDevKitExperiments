package com.testehan.adk.agents.cm.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import com.testehan.adk.agents.cm.tools.Tools;

import static com.testehan.adk.agents.cm.config.Constants.*;
import static com.testehan.adk.agents.cm.config.Constants.OUTPUT_SCOUT_AGENT;

public class CommonAgents {

    public static BaseAgent createApiScoutAgent() {
        return LlmAgent.builder()
                .name(API_SCOUT_AGENT_NAME)
                .model(USED_MODEL_NAME)
                .description("This agent calls an API and extracts a clean list of URLs from the tool's response.")
                .instruction(
                        "You are an API client that extracts data. " +
                                "You must call the '" + TOOL_GET_STRINGS + "' tool. " +
                                "The tool will return a map containing a 'status' and a '" + OUTPUT_SCOUT_AGENT +"' key. " +
                                "Your final output MUST be ONLY the value of the '" + OUTPUT_SCOUT_AGENT + "' key. " +
                                "Return the raw JSON array of URLs and nothing else. Do not include 'status', commentary, or any other text."
                )
                .tools(FunctionTool.create(Tools.class, TOOL_GET_STRINGS))
                .outputKey(OUTPUT_SCOUT_AGENT) // Key for storing output in session state
                .build();
    }

}
