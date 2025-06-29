package com.testehan.adk.agents.cm.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.tools.Annotations;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.testehan.adk.agents.cm.CMAgent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Tools {

    private static final Logger LOGGER = LoggerFactory.getLogger(Tools.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Helper method to build a standard-compliant JSON schema map.
    private static Map<String, Object> buildStandardJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Name of the listing."),
                        "city", Map.of("type", "string", "description", "City where the apartment is located."),
                        "area", Map.of("type", "string", "description", "This must contain the address if available. If address is not mentioned use area where the apartment is located."),
                        "shortDescription", Map.of("type", "string", "description", "The apartment description."),
                        "price", Map.of("type", "integer", "description", "Price of the apartment. I only want the number, not the currency."),
                        "surface", Map.of("type", "integer", "description", "Surface area of the apartment in square meters."),
                        "noOfRooms", Map.of("type", "integer", "description", "Number of rooms in the apartment."),
                        "floor", Map.of("type", "integer", "description", "Floor of the apartment."),
                        "ownerName", Map.of("type", "string", "description", "Name of the owner."),
                        "imageUrls", Map.of(
                                "type", "array",
                                "description", "A list of all found image URLs from the page.",
                                "items", Map.of("type", "string")
                        )
                ),
                "required", List.of(
                        "name", "city", "area", "shortDescription", "price", "surface", "noOfRooms", "floor", "ownerName"
                )
        );
    }

    public static Map<String, String> getTextFromUrl(
            @Annotations.Schema(name = "getTextFromUrl", description = "The url for which the retrieval must be done")
            String url) {
        LOGGER.info("Tool called: Fetching content from {}", url);
        try {
            // Use JSoup to connect to the URL, get the HTML, and return its text
            Document doc = Jsoup.connect(url).get();
            String text = doc.text();

            LOGGER.info("Obtained the following text: {}",text);

            return Map.of(
                    "status", "success",
                    "report", text);
        } catch (IOException e) {
            e.printStackTrace();
            return Map.of(
                    "status", "error",
                    "report", "Error: Could not fetch content from the URL.");
        }
    }

    @Annotations.Schema(
            name = "extractPageContentAndImages",
            description = "Navigates to a URL using a headless browser to get the full page text and all visible image URLs."
    )
    public static Map<String, Object> extractPageContentAndImages(
            @Annotations.Schema(name = "url", description = "The url for which the retrieval must be done")
            String url) {

        HumanizedBrowsing humanizedBrowsing = new HumanizedBrowsing();
        return humanizedBrowsing.browseUrl(url);
    }



    /**
     * Validates if the given JSON string conforms to the provided agent Schema.
     *
     * @param agentJsonString the json String that must be validated
     * @return A map indicating success or failure with a detailed error message.
     */
    @Annotations.Schema(
            name = "validatePropertyJson",
            description = "Validates if the given JSON string conforms to the provided agent Schema."
    )
    public static Map<String, Object> validatePropertyJson(
            @Annotations.Schema(name = "agentJsonString", description = "the json String that must be validated")
            String agentJsonString) {
        try {
            LOGGER.info("Tool called: validatePropertyJson {}", agentJsonString);

            Map<String, Object> standardSchemaMap = buildStandardJsonSchema();
            String standardSchemaJson = OBJECT_MAPPER.writeValueAsString(standardSchemaMap);
            LOGGER.info("LOG: Using this standard-compliant schema for validation: {}",  standardSchemaJson);


            // Step 2: Set up the validator factory
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            JsonSchema schema = factory.getSchema(standardSchemaJson);

            // Step 3: Parse the agent's output string into a JSON tree
            JsonNode node = OBJECT_MAPPER.readTree(agentJsonString);

            // Step 4: Perform the validation!
            Set<ValidationMessage> errors = schema.validate(node);

            if (errors.isEmpty()) {
                LOGGER.info("Tool validatePropertyJson: The JSON is valid!");
                return Map.of(
                        "isValid", true,
                        "message", "JSON is valid and conforms to the schema."
                );
            } else {
                // The JSON is invalid. Format the errors for the LLM.
                String errorDetails = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining(", "));

                LOGGER.info("Tool validatePropertyJson: The JSON is invalid! {}", errorDetails);

                return Map.of(
                        "isValid", false,
                        "message", "JSON is invalid. Errors: " + errorDetails
                );
            }
        } catch (Exception e) {
            // Catches errors in parsing the schema or the input JSON itself
            LOGGER.info("Tool validatePropertyJson: Exception! {}", e.getMessage());
            return Map.of(
                    "isValid", false,
                    "message", "A fatal error occurred during validation: " + e.getMessage()
            );
        }
    }
}
