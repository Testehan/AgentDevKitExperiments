package com.testehan.adk.agents.cm.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.testehan.adk.agents.cm.agents.helpers.LoopingPhonesProcessorAgent;

import static com.testehan.adk.agents.cm.agents.helpers.CommonAgents.createApiScoutAgent;
import static com.testehan.adk.agents.cm.config.Constants.*;

public class WhatsAppAgents {
// Steps
    // 1. get not contacted numbers
    // 2. see if they are available on WA - TODO I can't do this right now ..only after obtaining higher WA priviledges with a real, not trial, account
    // 3. check if their is a history of conversation or if this is a new one
    // 4. determine via llm what message to send next

    // Agent 1 - The API Scout. Its only job is to call the API.
    private static BaseAgent apiScoutAgent = createApiScoutAgent();

    // Agent 2 - The Conversation agent. Its only job is to determine if the user accepted to have its listing added
    private static BaseAgent conversationAgent = createConversationAgent();

    private static BaseAgent createConversationAgent() {
        return LlmAgent.builder()
                .name(CONVERSATION_AGENT)
                .model(USED_MODEL_NAME)
                .description("This agent determines based on a conversation if the user agrees to have its listing posted on CasaMia.ai webapp.")
                .instruction("Your mission is to determine if the user gives its consent for their listing to be published " +
                                "on the casamia.ai website. The history of the conversation until now is : " +
                                "'{"+ AGENT_VAR_CURRENT_CONVERSATION +"}'." +
                                "You must return one, and only one of the following possible values : " +
                        "1. \"yes\" - in case the user gave their consent" +
                        "2. \"no\" - in case the user rejected the offer " +
                        "3. \"undecided\" - if the conversation is ongoing and a user decision was not yet made")
                .outputKey(OUTPUT_CONVERSATION_AGENT) // Key for storing output in session state
                .build();
    }

    private static BaseAgent createNextReplyAgent() {
        return LlmAgent.builder()
                .name(NEXT_REPLY_AGENT)
                .model(USED_MODEL_NAME)
                .description("This agent determines based on a conversation the next reply to send.")
                .instruction("                        misiunea ta este sa afli daca userul doreste ca anuntul sau sa fie publicat si pe siteul casamia.ai. Uite istoricul conversatiei de pana acum \"Agent: Salut din CasaMia\n" +
                                "User: Buna\n" +
                                "User: :)\" returneaza \"userul e de acord\" daca si-a dat consimtamantul. returneaza \"userul nu e de acord\" daca nu doreste. altfel returneaza un mesaj care trimis userului ar putea duce conversatia spre scopul dorit de a publica anuntul. returneaza mesajul pe care doresti sa i'l trimiti in celelalte cazuri" +
                        "'{"+ AGENT_VAR_LISTING_URL_INITIAL_SOURCE +"}'. " +
                                "After you get the result from the tool, your job is done. Output the raw result from the tool directly.")
                .outputKey(OUTPUT_NEXT_REPLY_AGENT) // Key for storing output in session state
                .build();
    }

    // Agent 4 - The Master Orchestrator with Looping Logic. This is the new Root Agent.
    public static BaseAgent createOrchestratorAgent() {
        return SequentialAgent.builder()
                .name(MASTER_ORCHESTRATOR_PHONES_AGENT_NAME)
                .description("Manages a data pipeline by fetching a list of phone numbers and then looping through them")
                .subAgents(apiScoutAgent, new LoopingPhonesProcessorAgent(conversationAgent))
                .build();
    }
}
