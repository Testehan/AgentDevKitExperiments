package com.testehan.adk.agents.cm.agents.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.common.base.Strings;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.testehan.adk.agents.cm.config.ConfigLoader.*;
import static com.testehan.adk.agents.cm.config.Constants.*;

public class LoopingPhonesProcessorAgent extends BaseAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoopingPhonesProcessorAgent.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BaseAgent conversationAgent;

    public LoopingPhonesProcessorAgent(BaseAgent conversationAgent) {
        super(
                "looping_phones_processor_agent",
                "A deterministic agent that receives a list of phones, loops through them, checks if available on whatsapp.",
                null,
                null,
                null
        );
        this.conversationAgent = conversationAgent;
    }

    @Override
    protected Flowable<Event> runAsyncImpl(InvocationContext ctx) {
        // Step 1: Get the input from the previous agent
        String jsonPhonesRaw = (String) ctx.session().state().get(OUTPUT_SCOUT_AGENT);
        String jsonPhones = jsonPhonesRaw.replaceFirst("(?s)```json\\s*", "").replaceFirst("(?s)```\\s*$", "");

        return Flowable.create(emitter -> {
            try {
                List<String> phonesToProcess = new ArrayList<>();
                try {
                    phonesToProcess = OBJECT_MAPPER.readValue(jsonPhones, List.class);
                } catch (JsonProcessingException e) {
                    LOGGER.error("LoopingPhonesProcessorAgent received invalid json array of phones to process. {}", jsonPhones);
                }
                LOGGER.info("LoopingPhonesProcessorAgent received {} phones to process.", phonesToProcess.size());

                // --- BASIC AUTHENTICATION LOGIC ---
                String authString = getApiEndpointUsername() + ":" + getApiEndpointPassword();
                String encodedAuthString = Base64.getEncoder().encodeToString(authString.getBytes());
                String authHeaderValue = "Basic " + encodedAuthString;

                HttpClient client = HttpClient.newHttpClient();

                // Step 2: Loop through the URLs
                for (String phone : phonesToProcess) {
                    LOGGER.info("LoopingProcessorAgent is now processing URL: {}", phone);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(getApiEndpointGetPhones()+"/"+phone))
                            .header("Authorization", authHeaderValue)
                            .build();

                    String conversation = "";
                    try {
                        // Send the request and get the response
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() == 401) { // Unauthorized
//                            return Map.of("status", "error", "message", "API call failed. The provided credentials were an incorrect (Unauthorized).");
                        }
                        if (response.statusCode() != 200) {
//                            return Map.of("status", "error", "message", "API call failed with status code: " + response.statusCode());
                        }

                        conversation = response.body();
                        LOGGER.info("Successfully fetched  conversation {} ", conversation);
//                        return Map.of("status", "success", OUTPUT_SCOUT_AGENT, strings);

                    } catch (Exception e) {
                        LOGGER.error("An error occurred while calling the API", e);
                        // TODO think what should happen in this case...i think that no new message should be sent to users in this case.
                        // obviously in this scenario the conversation is "" but we don't want the agent to think that he should see
                        // this as an empty conversation, and start with the initial conversations...rather an empty string..
//
                    }

                    ctx.session().state().put(AGENT_VAR_CURRENT_CONVERSATION, conversation);

                    String userConsent = "";
                    if (!Strings.isNullOrEmpty(conversation)){

                        LOGGER.info("--- 🚀 RUNNING Conversation evaluation AGENT ---");
                        Event finalEvent = conversationAgent.runAsync(ctx).blockingLast();
                        String rawOutput = "";
                        // The final response from the agent is an Event. We can get the text directly from its content.
                        if (finalEvent != null && CONVERSATION_AGENT.equals(finalEvent.author())) {
                            // The content() method on the Event gives us the payload.
                            // The text() method on Content concatenates all parts into a single string.
                            rawOutput = finalEvent.content().get().text();
                        }

                        userConsent = rawOutput;
                        LOGGER.info("\n--- ✅ Conversation evaluation AGENT FINISHED. Raw output: ---\n {}", rawOutput);

                    }

                    if (userConsent.equalsIgnoreCase("yes")){
                        // set the lead on accepted.
                    } else if (userConsent.equalsIgnoreCase("no")){
                        // set the lead on NOT accepted
                    } else if (userConsent.equalsIgnoreCase("undecided")){
                        // call the next reply agent
                    } else {
                        LOGGER.warn("⚠️ The Conversation agent returned an unexpected value {}", userConsent);
                    }

                    // 2. see if they are available on WA ...TODO ..right now i can't do that with the unverified business account...

//                    // --- RUN THE FIRST AGENT ---


//                    LOGGER.info("\n--- ✅ SCRAPER FINISHED. Raw output: ---\n {}", scraperOutputString);
//
//                    // --- RUN THE SECOND AGENT ---
//                    // Pass the extractor's output as the input for the formatter agent
//                    ctx.session().state().put(AGENT_VAR_LISTING_SCRAPED_TEXT, scraperOutputString);
//
//                    LOGGER.info("\n--- 🚀 RUNNING FORMATTER AGENT ---");
//                    Event finalEvent = formatterAgent.runAsync(ctx).blockingLast();
//
//                    String rawOutput = "";
//                    // The final response from the agent is an Event. We can get the text directly from its content.
//                    if (finalEvent != null && FORMATTER_AGENT.equals(finalEvent.author())) {
//                        // The content() method on the Event gives us the payload.
//                        // The text() method on Content concatenates all parts into a single string.
//                        rawOutput = finalEvent.content().get().text();
//                    }
//
//                    // **CRITICAL STEP**: Clean the LLM output to get pure JSON.
//                    // This removes the "```json" at the start and the "```" at the end.
//                    // The (?s) flag allows '.' to match newline characters.
//                    String resultJson = rawOutput.replaceFirst("(?s)```json\\s*", "").replaceFirst("(?s)```\\s*$", "");
//
//
//                    if (Objects.nonNull(resultJson) && !resultJson.trim().isEmpty()) {
//                        LOGGER.info("Successfully extracted data for URL: {}", phone);
//                        ConcurrentMap<String, Object> stateUpdate = new ConcurrentHashMap<>();
//
//                        stateUpdate.put(OUTPUT_MASTER_ORCHESTRATOR_LISTING, resultJson);
//                        stateUpdate.put(OUTPUT_MASTER_ORCHESTRATOR_URL, phone);
//
//                        // Build the event carrying this single result.
//                        Event resultEvent = Event.builder()
//                                .author(this.name())
//                                .actions(EventActions.builder().stateDelta(stateUpdate).build())
//                                .build();
//
//                        // Emit the event to the Flowable stream.
//                        emitter.onNext(resultEvent);
//
//                    } else {
//                        LOGGER.warn("Extractor returned no valid result {} for URL: {}", resultJson, phone);
//                    }
                }

                LOGGER.info("LoopingPhonesProcessorAgent has finished. Final output created. \n");
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
