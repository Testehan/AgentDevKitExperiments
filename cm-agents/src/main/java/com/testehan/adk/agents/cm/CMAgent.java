package com.testehan.adk.agents.cm;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.testehan.adk.agents.cm.tools.Tools;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class CMAgent {

    private static final Logger logger = LoggerFactory.getLogger(CMAgent.class);

    private static String USER_ID = "casamia";
    private static String NAME = "multi_tool_agent";

    // The run your agent with Dev UI, the ROOT_AGENT should be a global public static variable.
    public static BaseAgent ROOT_AGENT = initAgent();

    public static BaseAgent initAgent() {
        logger.info("Agent init started");

        return LlmAgent.builder()
                .name(NAME)
                .model("gemini-2.5-pro")
                .description("Agent to extract relevant real estate information.")
                .instruction(
                        "You are a real estate data analyst. " +
                        "Your task is to extract key information from the webpage at the URL. " +
                        "First, use your tool to fetch the content of the URL. " +
                        "Then, analyze the text to find the following details and return them as a clean JSON object: " +
                        "- title (the main title of the listing) " +
                        "- price (the numerical value and currency) " +
                        "- location (the city and neighborhood) " +
                        "- usable_area (the area in square meters) " +
                        "- detailed_description (the exact description of the property)"
                )
                .tools(
                        FunctionTool.create(Tools.class, "getTextFromUrl")
                       )
                .build();
    }

    // TODO
    // 1. ADD json for how you want the result of the agent to look like
    // 2,. with the help of selenium and headless browser, you need to make another agent or to improve
    // existing one so that you can also get the image links of the property. because these are loaded
    // with js dynamically, this approach is needed, as the current solution from above gets only the html code

    public static void main(String[] args) throws Exception {
        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);

        Session session = runner.sessionService()
                            .createSession(NAME, USER_ID)
                            .blockingGet();

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("\nYou > ");
                String userInput = scanner.nextLine();

                if ("quit".equalsIgnoreCase(userInput)) {
                    break;
                }

                Content userMsg = Content.fromParts(Part.fromText(userInput));
                Flowable<Event> events = runner.runAsync(USER_ID, session.id(), userMsg);

                System.out.print("\nAgent > ");
                events.blockingForEach(event -> System.out.println(event.stringifyContent()));
            }
        }
    }
}
