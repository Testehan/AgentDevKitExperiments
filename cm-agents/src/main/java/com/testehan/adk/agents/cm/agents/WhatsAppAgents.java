package com.testehan.adk.agents.cm.agents;

import com.google.adk.agents.BaseAgent;

import static com.testehan.adk.agents.cm.agents.CommonAgents.createApiScoutAgent;

public class WhatsAppAgents {
// Steps
    // 1. get not contacted numbers
    // 2. see if they are available on WA
    // 3. check if their is a history of conversation or if this is a new one
    // 4. determine via llm what message to send next

    // Agent 1 - The API Scout. Its only job is to call the API.
    private static BaseAgent apiScout = createApiScoutAgent();

}
