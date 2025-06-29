package com.testehan.adk.agents.cm.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.testehan.adk.agents.cm.tools.HumanizedBrowsing;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LoopingProcessorAgent extends BaseAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoopingProcessorAgent.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final BaseAgent extractorAgent;
    private final BaseAgent formatterAgent;

    public LoopingProcessorAgent(BaseAgent extractorAgent, BaseAgent formatterAgent) {
        super(
                "looping_processor_agent",
                "A deterministic agent that receives a list of URLs, loops through them, and calls an extractor and formatter for each.",
                null,
                null,
                null
        );
        this.extractorAgent = extractorAgent;
        this.formatterAgent = formatterAgent;
    }

    /**
     * This is the heart of the custom agent, built according to the official ADK documentation.
     * @param ctx The InvocationContext provides access to the session, runner, and input from the previous agent.
     * @return A Flowable stream of events, culminating in a single Content event with the final aggregated result.
     */
    @Override
    protected Flowable<Event> runAsyncImpl(InvocationContext ctx) {
        // Step 1: Get the input from the previous agent
        String jsonUrls = (String) ctx.session().state().get("urls");
        List<String> urlsToProcess = new ArrayList<>();
        try {
            urlsToProcess = OBJECT_MAPPER.readValue(jsonUrls, List.class);
            ctx.session().state().put("urls","");       // remove the urls from context so that the LLM is not confused
        } catch (JsonProcessingException e) {
            LOGGER.error("LoopingProcessorAgent received invalid json array of URLs to process. {}", jsonUrls);
        }
        LOGGER.info("LoopingProcessorAgent received {} URLs to process.", urlsToProcess.size());
        List<String> collectedResults = new ArrayList<>();

        // Step 2: Loop through the URLs deterministically in pure Java code.
        for (String url : urlsToProcess) {
            LOGGER.info("LoopingProcessorAgent is now processing URL: {}", url);

            ctx.session().state().put("url_to_use",url);

            // --- RUN THE FIRST AGENT ---
//            System.out.println("--- 🚀 RUNNING SCRAPER AGENT ---");
//            extractorAgent.runAsync(ctx).blockingForEach(event -> System.out.println("SCRAPER EVENT: " + event.toJson()));
            HumanizedBrowsing humanizedBrowsing = new HumanizedBrowsing();
            Map<String, Object> scraperOutput =  humanizedBrowsing.browseUrl(url);


            // The result of the first agent is now in the context under "scraper_output"
            String scraperOutputString = "Page Text: " + scraperOutput.get("pageText") +
                    " \n\nImage URLs: " + scraperOutput.get("imageUrls");
            System.out.println("\n--- ✅ SCRAPER FINISHED. Raw output: ---\n" + scraperOutputString);

            // --- RUN THE SECOND AGENT ---
            // Pass the scraper's output as the input for the formatter agent
            ctx.session().state().put("scraped_text", scraperOutputString);

            System.out.println("\n--- 🚀 RUNNING FORMATTER AGENT ---");
            Event finalEvent = formatterAgent.runAsync(ctx).blockingLast();

            System.out.println("FORMATTER FINAL EVENT: " + finalEvent.toJson());

            String rawOutput = "";
            // The final response from the agent is an Event. We can get the text directly
            // from its content.
            if (finalEvent != null && "formatter_agent".equals(finalEvent.author())) {
                // The content() method on the Event gives us the payload.
                // The text() method on Content concatenates all parts into a single string.
                rawOutput = finalEvent.content().get().text();
            }

            // **CRITICAL STEP**: Clean the LLM output to get pure JSON.
            // This removes the "```json" at the start and the "```" at the end.
            // The (?s) flag allows '.' to match newline characters.
            String resultJson = rawOutput.replaceFirst("(?s)```json\\s*", "").replaceFirst("(?s)```\\s*$", "");

            // The final, clean JSON is now in the context
//            String resultJson = (String) ctx.session().state().get("listing_json");

            if (Objects.nonNull(resultJson) && !resultJson.trim().isEmpty()) {
                LOGGER.info("Successfully extracted data for URL: {}", url);
                collectedResults.add(resultJson);
            } else {
                LOGGER.warn("Extractor returned no valid result {} for URL: {}", resultJson, url);
            }
        }

//        // Step 4: Aggregate all the individual JSON results into a final JSON array string.
        String finalOutput = "[" + String.join(",", collectedResults) + "]";
        LOGGER.info("LoopingProcessorAgent has finished. Final aggregated output created. \n {}" , finalOutput);


        try {
            // This is the key the user of your agent will use to get the result.
            // You should define this as a constant in your agent class.
            final String OUTPUT_KEY = "final_property_list";

            // 1. Create a new ConcurrentHashMap with the EXACT required types.
            ConcurrentMap<String, Object> stateUpdate = new ConcurrentHashMap<>();

            // 2. Put your final result into this map.
            //    The value (a String) is a valid Object, so this works.
            stateUpdate.put(OUTPUT_KEY, finalOutput);

            // Create the final event that will carry the result.
            Event finalResultEvent = Event.builder()
                    .author(this.name()) // Set the author to this agent's name
                    //.content(Content.fromJson(finalOutput)) // The actual content for logging
                    .actions(
                                EventActions.builder().stateDelta(stateUpdate).build()
                            )
                    .build();

            // Return the final event as a stream that emits this one item and then completes.
            return Flowable.just(finalResultEvent);

        } catch (Exception e) {
            // If anything goes wrong, return a stream that emits an error.
            return Flowable.error(e);
        }


        // Step 5: Return the final, aggregated result wrapped in a Content event.
        // This is the correct way for a custom agent to produce its final output.
      //  return Flowable.just(Event.fromJson(finalOutput));
    }

    @Override
    protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
        return Flowable.error(new UnsupportedOperationException("runLive not implemented."));
    }
}
