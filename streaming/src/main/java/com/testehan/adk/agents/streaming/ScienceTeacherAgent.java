package com.testehan.adk.agents.streaming;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;

public class ScienceTeacherAgent {

    public static final String NAME = "science-app";

    /*
        For the tool to automatically recognize the agent, its Java class has to comply with the following two rules:
            - The agent should be stored in a global public static variable named ROOT_AGENT of type BaseAgent and
            initialized at declaration time.
            - The agent definition has to be a static method so it can be loaded during the class initialization by
            the dynamic compiling classloader.
     */
    public static BaseAgent ROOT_AGENT = initAgent();

    public static BaseAgent initAgent() {

        /*
            As we saw, you do not have to write any specific code in the agent itself for the
            text streaming capability. It is provided as an ADK Agent feature by default.
         */

        return LlmAgent.builder()
                .name(NAME)
                .description("Science teacher agent")
                .model("gemini-2.0-flash-live-001")     // this live version of the model is needed in order to have audio chats
                .instruction("""
                                You are a helpful science teacher that explains
                                science concepts to kids and teenagers.
                            """)
                .build();
    }
}
