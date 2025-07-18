package com.testehan.adk.agents.cm.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.testehan.adk.agents.cm.agents.helpers.InitialContactLoopingPhonesProcessorAgent;

import static com.testehan.adk.agents.cm.agents.helpers.CommonAgents.createApiScoutPhoneUrlsAgent;
import static com.testehan.adk.agents.cm.config.Constants.*;

public class WhatsAppInitialContactAgents {
// Steps Flow Initial Contact
    // 1. get not contacted numbers
    // 2. see if they are available on WA - TODO I can't do this right now ..only after obtaining higher WA priviledges with a real, not trial, account
    // 3. check if their is a history of conversation or if this is a new one
    // 4. determine via llm what message to send next

    // Agent 1 - The API Scout. Its only job is to call the API.
    private static BaseAgent apiScoutAgent = createApiScoutPhoneUrlsAgent();

    // Agent 2 - The Conversation agent. Its only job is to determine if the user accepted to have its listing added
    private static BaseAgent conversationEvaluationAgent = createConversationEvaluationAgent();

    // Agent 3 - The Next reply agent. Its only job is to determine the next reply to send
    private static BaseAgent nextReplyAgent = createNextReplyAgent();

    private static BaseAgent createConversationEvaluationAgent() {
        return LlmAgent.builder()
                .name(CONVERSATION_AGENT)
                .model(USED_MODEL_NAME)
                .description("This agent determines based on a conversation if the user agrees to have its listing posted on CasaMia.ai webapp.")
                .instruction("Your mission is to determine if the user gives its consent for their listing to be published " +
                                "on the casamia.ai website. The history of the conversation until now is : " +
                                "'{"+ AGENT_VAR_CURRENT_CONVERSATION +"}'." +
                                "You must return one, and only one of the following possible values : " +
                        "1. \"ACCEPTED\" - in case the user gave their consent and it is not a real estate agent" +
                        "2. \"DECLINED\" - in case the user rejected the offer and the user is not is a real estate agency" +
                        "3. \"ALREADY_RENTED\" - if the property was already rented and the user is not a real estate agency" +
                        "4. \"REAL_ESTATE_COMPANY\" - if the user is a real estate agency" +
                        "5. \"undecided\" - if the conversation is ongoing and you can't determine if any of the previous answer possibilities is true")
                .outputKey(OUTPUT_CONVERSATION_AGENT) // Key for storing output in session state
                .build();
    }

    private static BaseAgent createNextReplyAgent() {
        return LlmAgent.builder()
                .name(NEXT_REPLY_AGENT)
                .model(USED_MODEL_NAME)
                .description("This agent determines based on an existing conversation the next reply to send.")
                .instruction(
                        "You are helping write the next WhatsApp message in an ongoing conversation. " +
                        "The goal of the conversation is to first see if the user is a real estate agency and if not " +
                        "ask the user's permission to add their real estate listing to the new CasaMia.ai platform.\n\n" +

                        "CasaMia.ai Info:\n" +
                        "- Adding listings is free.\n" +
                        "- We're a new platform trying something different and offering this benefit to early users.\n\n" +

                        "Guidelines:\n" +
                        "- Be short, friendly, and sound like a real human.\n" +
                        "- All replies must be in Romanian. Never reply in any other language.\n" +
                        "- If the user asks whether they're speaking with an AI, answer \"no\".\n" +
                        "- If the user asks a question you don't know the answer to, politely say so.\n" +
                        "- If the last message was sent by you more than 2 days ago and the user hasn’t replied, provide a followup message." +
                        "- The message should feel appropriate to send on WhatsApp — casual, natural, and human.\n\n" +

                        "The history of the conversation until now is : '{"+ AGENT_VAR_CURRENT_CONVERSATION +"}'. ")
                .outputKey(OUTPUT_NEXT_REPLY_AGENT) // Key for storing output in session state
                .build();
    }

    // Agent 4 - The Master Orchestrator with Looping Logic. This is the new Root Agent.
    public static BaseAgent createOrchestratorAgentIntialContact() {
        return SequentialAgent.builder()
                .name(MASTER_ORCHESTRATOR_PHONES_AGENT_NAME)
                .description("Manages a data pipeline by fetching a list of phone numbers and then looping through them")
                .subAgents(apiScoutAgent, new InitialContactLoopingPhonesProcessorAgent(conversationEvaluationAgent, nextReplyAgent))
                .build();
    }
}
