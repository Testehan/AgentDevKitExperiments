package com.testehan.adk.agents.cm.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.testehan.adk.agents.cm.tools.HumanizedBrowsing;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.testehan.adk.agents.cm.config.Constants.*;

public class LoopingUrlsProcessorAgent extends BaseAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoopingUrlsProcessorAgent.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final BaseAgent extractorAgent;
    private final BaseAgent formatterAgent;

    public LoopingUrlsProcessorAgent(BaseAgent extractorAgent, BaseAgent formatterAgent) {
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
        String jsonUrls = (String) ctx.session().state().get(OUTPUT_SCOUT_AGENT);

        return Flowable.create(emitter -> {
            try {
                List<String> urlsToProcess = new ArrayList<>();
                try {
                    urlsToProcess = OBJECT_MAPPER.readValue(jsonUrls, List.class);
                } catch (JsonProcessingException e) {
                    LOGGER.error("LoopingProcessorAgent received invalid json array of URLs to process. {}", jsonUrls);
                }
                LOGGER.info("LoopingProcessorAgent received {} URLs to process.", urlsToProcess.size());

                // Step 2: Loop through the URLs
                for (String url : urlsToProcess) {
                    LOGGER.info("LoopingProcessorAgent is now processing URL: {}", url);

                    ctx.session().state().put(AGENT_VAR_LISTING_URL_INITIAL_SOURCE,url);

                    // --- RUN THE FIRST AGENT ---
                    LOGGER.info("--- 🚀 RUNNING SCRAPER AGENT ---");
                    // i abandoned investigathing why the line from below gets stuck in a loop...so i just call the tool directly
                    // to get the content of the page.
        //            extractorAgent.runAsync(ctx).blockingForEach(event -> System.out.println("SCRAPER EVENT: " + event.toJson()));
                    HumanizedBrowsing humanizedBrowsing = new HumanizedBrowsing();
                    Map<String, Object> scraperOutput =  humanizedBrowsing.browseUrl(url);

                    // The result of the first agent is now in the context under "scraper_output"
                    String scraperOutputString = "Page Text: " + scraperOutput.get("pageText") + "\n\n" +
                                                  "Image URLs: " + scraperOutput.get("imageUrls");
                    LOGGER.info("\n--- ✅ SCRAPER FINISHED. Raw output: ---\n {}", scraperOutputString);

                    // --- RUN THE SECOND AGENT ---
                    // Pass the extractor's output as the input for the formatter agent
                    ctx.session().state().put(AGENT_VAR_LISTING_SCRAPED_TEXT, scraperOutputString);

                    LOGGER.info("\n--- 🚀 RUNNING FORMATTER AGENT ---");
                    Event finalEvent = formatterAgent.runAsync(ctx).blockingLast();

                    LOGGER.info("FORMATTER FINAL EVENT: \n {}", finalEvent.toJson());

                    String rawOutput = "";
                    // The final response from the agent is an Event. We can get the text directly from its content.
                    if (finalEvent != null && FORMATTER_AGENT.equals(finalEvent.author())) {
                        // The content() method on the Event gives us the payload.
                        // The text() method on Content concatenates all parts into a single string.
                        rawOutput = finalEvent.content().get().text();
                    }

                    // **CRITICAL STEP**: Clean the LLM output to get pure JSON.
                    // This removes the "```json" at the start and the "```" at the end.
                    // The (?s) flag allows '.' to match newline characters.
                    String resultJson = rawOutput.replaceFirst("(?s)```json\\s*", "").replaceFirst("(?s)```\\s*$", "");


                    if (Objects.nonNull(resultJson) && !resultJson.trim().isEmpty()) {
                        LOGGER.info("Successfully extracted data for URL: {}", url);
                        ConcurrentMap<String, Object> stateUpdate = new ConcurrentHashMap<>();

                        stateUpdate.put(OUTPUT_MASTER_ORCHESTRATOR_LISTING, resultJson);
                        stateUpdate.put(OUTPUT_MASTER_ORCHESTRATOR_URL, url);

                        // Build the event carrying this single result.
                        Event resultEvent = Event.builder()
                                .author(this.name())
                                .actions(EventActions.builder().stateDelta(stateUpdate).build())
                                .build();

                        // Emit the event to the Flowable stream.
                        emitter.onNext(resultEvent);

                    } else {
                        LOGGER.warn("Extractor returned no valid result {} for URL: {}", resultJson, url);
                    }
                }

                LOGGER.info("LoopingProcessorAgent has finished. FinaL output created. \n");
                // After the loop has finished successfully, signal completion of the stream.
                emitter.onComplete();

        } catch (Exception e) {
            LOGGER.error("An error occurred during the execution of the agent.", e);
            // If any exception occurs, propagate it through the stream.
            emitter.onError(e);
        }
        }, BackpressureStrategy.BUFFER);
    }

    @Override
    protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
        return Flowable.error(new UnsupportedOperationException("runLive not implemented."));
    }
}
