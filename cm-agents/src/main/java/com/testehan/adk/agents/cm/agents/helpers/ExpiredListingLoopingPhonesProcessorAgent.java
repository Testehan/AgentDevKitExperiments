package com.testehan.adk.agents.cm.agents.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.common.base.Strings;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.testehan.adk.agents.cm.config.ConfigLoader.*;
import static com.testehan.adk.agents.cm.config.Constants.*;

public class ExpiredListingLoopingPhonesProcessorAgent extends BaseAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpiredListingLoopingPhonesProcessorAgent.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BaseAgent conversationAgent;
    private final BaseAgent nextReplyAgent;

    public ExpiredListingLoopingPhonesProcessorAgent(BaseAgent conversationAgent, BaseAgent nextReplyAgent) {
        super(
                "looping_phones_processor_agent",
                "A deterministic agent that receives a list of phones, loops through them.",
                null,
                null,
                null
        );
        this.conversationAgent = conversationAgent;
        this.nextReplyAgent = nextReplyAgent;
    }

    @Override
    protected Flowable<Event> runAsyncImpl(InvocationContext ctx) {
        // Step 1: Get the input from the previous agent
        String jsonPhonesRaw = (String) ctx.session().state().get(OUTPUT_SCOUT_AGENT);
        String jsonPhones = jsonPhonesRaw.replaceFirst("(?s)```json\\s*", "").replaceFirst("(?s)```\\s*$", "");

        return Flowable.create(emitter -> {
            try {
                List<Map<String, String>> pairs = new ArrayList<>();
                try {
                    pairs = OBJECT_MAPPER.readValue(jsonPhones, new TypeReference<>() {});
                } catch (JsonProcessingException e) {
                    LOGGER.error("ExpiredListingLoopingPhonesProcessorAgent received invalid json array of phones to process. {}", jsonPhones);
                }
                LOGGER.info("ExpiredListingLoopingPhonesProcessorAgent received {} phones to process.", pairs.size());

                HttpClient client = HttpClient.newHttpClient();

                int i = 0;
                // Step 2: Loop through the phone numbers
                for (Map<String, String> pair : pairs) {
                    i++;
                    String phone = pair.get("phoneNumber");
                    String url = pair.get("url");
                    LOGGER.info("Phone number {} : ExpiredListingLoopingPhonesProcessorAgent is now processing phone: {}", i, phone);

                    String phoneEncoded = URLEncoder.encode(phone, StandardCharsets.UTF_8);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(getApiEndpointGetPhones() + "/" + phoneEncoded))
                            .header("Authorization", getAuthenticationHeaderValue())
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

                    } else {
                        // send an initial message to this lead
                        postLeadReply(client, phone, getRandomReactivateMessage(), true);

                    }


                    if (userConsent.equalsIgnoreCase("true"))
                    {
                        reactivateListing(client, url);
                        updateLeadStatus(client, phone, "DONE");
                        postLeadReply(client, phone, "Anuntul a fost reactivat :)", false);
                    } else if (userConsent.equalsIgnoreCase("false")){
                        // leave deactivated
                        updateLeadStatus(client, phone, "DONE");
                        postLeadReply(client, phone, "Am inteles. Anuntul ramane dezactivat.", false);
                    } else if (userConsent.equalsIgnoreCase("undecided")){

                        LOGGER.info("--- 🚀 RUNNING Next Reply AGENT ---");
                        Event finalEvent = nextReplyAgent.runAsync(ctx).blockingLast();
                        String rawOutput = "";
                        if (finalEvent != null && NEXT_REPLY_AGENT.equals(finalEvent.author())) {
                            // The content() method on the Event gives us the payload.
                            // The text() method on Content concatenates all parts into a single string.
                            rawOutput = finalEvent.content().get().text();
                        }
                        LOGGER.info("\n--- ✅ Next reply AGENT FINISHED. Raw output: ---\n {}", rawOutput);

                        postLeadReply(client, phone, rawOutput, false);

                    } else {
                        LOGGER.warn("⚠️ The Conversation agent returned an unexpected value {}", userConsent);
                    }

                }

                LOGGER.info("ExpiredListingLoopingPhonesProcessorAgent has finished. Final output created. \n");
                // After the loop has finished successfully, signal completion of the stream.
                emitter.onComplete();

            } catch (Exception e) {
                LOGGER.error("An error occurred during the execution of the agent.", e);
                // If any exception occurs, propagate it through the stream.
                emitter.onError(e);
            }
        }, BackpressureStrategy.BUFFER);
    }

    private void reactivateListing(HttpClient client, String url) throws IOException, InterruptedException {
        HttpRequest request;

        request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("- Listing reactivation -> Response code: {}  Response body: {}", response.statusCode(), response.body());
    }

    private static void updateLeadStatus(HttpClient client, String phone, String status) throws IOException, InterruptedException {
        HttpRequest request;
        String phoneEncoded = URLEncoder.encode(phone, StandardCharsets.UTF_8);
        String statusEncoded = URLEncoder.encode(status, StandardCharsets.UTF_8);

        request = HttpRequest.newBuilder()
                .uri(URI.create(getApiEndpointPatchLeadStatus() + "?phoneNumber=" + phoneEncoded + "&status=" + statusEncoded))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .header("Content-Type", "application/json")
                .header("Authorization", getAuthenticationHeaderValue())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("- Lead status updated -> Response code: {}  Response body: {}", response.statusCode(), response.body());
    }

    private static void postLeadReply(HttpClient client, String phoneNumber, String reply, Boolean isFirstMessage) throws IOException, InterruptedException {
//        HttpRequest request;
//        String phoneEncoded = URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8);
//        String replyEncoded = URLEncoder.encode(reply, StandardCharsets.UTF_8);
//
//        request = HttpRequest.newBuilder()
//                .uri(URI.create(getApiEndpointPostLeadReply() + "?phoneNumber=" + phoneEncoded + "&reply=" + replyEncoded + "&isFirstMessage=" + isFirstMessage))
//                .method("POST", HttpRequest.BodyPublishers.noBody())
//                .header("Content-Type", "application/json")
//                .header("Authorization", getAuthenticationHeaderValue())
//                .build();
//
//        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//        LOGGER.info("- Lead reply sent -> Response code: {}  Response body: {}", response.statusCode(), response.body());
    }


    @Override
    protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
        return Flowable.error(new UnsupportedOperationException("runLive not implemented."));
    }
}
