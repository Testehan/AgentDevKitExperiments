package com.testehan.adk.agents.cm.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.SequentialAgent;
import com.testehan.adk.agents.cm.agents.helpers.LoopingPhonesProcessorAgent;

import static com.testehan.adk.agents.cm.agents.helpers.CommonAgents.createApiScoutAgent;
import static com.testehan.adk.agents.cm.config.Constants.MASTER_ORCHESTRATOR_PHONES_AGENT_NAME;

public class WhatsAppAgents {
// Steps
    // 1. get not contacted numbers
    // 2. see if they are available on WA
    // 3. check if their is a history of conversation or if this is a new one
    // 4. determine via llm what message to send next

    // Agent 1 - The API Scout. Its only job is to call the API.
    private static BaseAgent apiScout = createApiScoutAgent();

    // Agent 4 - The Master Orchestrator with Looping Logic. This is the new Root Agent.
    public static BaseAgent createOrchestratorAgent() {
        return SequentialAgent.builder()
                .name(MASTER_ORCHESTRATOR_PHONES_AGENT_NAME)
                .description("Manages a data pipeline by fetching a list of phone numbers and then looping through them")
                .subAgents(apiScout, new LoopingPhonesProcessorAgent())
                .build();
    }
}
