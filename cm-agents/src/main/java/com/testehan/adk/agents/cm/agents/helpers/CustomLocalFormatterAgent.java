package com.testehan.adk.agents.cm.agents.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.testehan.adk.agents.cm.tools.Tools;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.testehan.adk.agents.cm.config.Constants.AGENT_VAR_LISTING_SCRAPED_TEXT;


public class CustomLocalFormatterAgent extends BaseAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomLocalFormatterAgent.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public CustomLocalFormatterAgent(String name, String description) {
        super(name, description, null,null,null);
    }

    @Override
    public Flowable<Event> runAsyncImpl(InvocationContext ctx) {
        LOGGER.info("--- 🤖 EXECUTING CUSTOM AGENT: {} ---", this.name());

        // 1. Get the required input from the agent's state.
        String rawText = ctx.session().state().get(AGENT_VAR_LISTING_SCRAPED_TEXT).toString();

        // 2. Validate the input. Fail clearly if the required data is missing.
        if (rawText == null || rawText.isBlank()) {
            return Flowable.error(new RuntimeException(
                    "Input variable '" + AGENT_VAR_LISTING_SCRAPED_TEXT + "' was not found in the agent's state."
            ));
        }

        Map<String, Object> formattedResult = Tools.formatListingLocalGemini(rawText);
        String formattedJsonString = "";
        try {
             formattedJsonString = OBJECT_MAPPER.writeValueAsString(formattedResult);
        } catch (JsonProcessingException e) {
            LOGGER.error("CRITICAL: Failed to serialize the result Map to a JSON string.");
            throw new RuntimeException(e);
        }

        // 5. Create a final event containing the result from the tool.
        Event finalEvent = Event.builder()
                .author(this.name())
                .content(Content.fromParts(Part.fromText(formattedJsonString)))
                .build();

        // 6. Return a Flowable that emits the single final event and then completes.
        //    This signals that the agent's work is finished, preventing any hangs or loops.
        return Flowable.just(finalEvent);
    }

    @Override
    protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
        return Flowable.error(new UnsupportedOperationException("runLive not implemented."));
    }

}
