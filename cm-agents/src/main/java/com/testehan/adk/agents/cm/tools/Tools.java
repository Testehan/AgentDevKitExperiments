package com.testehan.adk.agents.cm.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.tools.Annotations;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.testehan.adk.agents.cm.config.ConfigLoader.getApiEndpointPassword;
import static com.testehan.adk.agents.cm.config.ConfigLoader.getApiEndpointUsername;
import static com.testehan.adk.agents.cm.config.Constants.*;

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

    /**
     * Calls a given API endpoint to fetch a JSON array of URLs.
     * @param apiEndpoint The full URL of the API to call.
     * @return A map containing the status and a list of URLs found.
     */
    @Annotations.Schema(
            name = TOOL_GET_URLS,
            description = "Navigates to an API to get the list of URLs that must be processed."
    )
    public static Map<String, Object> getUrlsFromApi(@Annotations.Schema(name = "apiEndpoint", description = "The endpoint for which the retrieval must be done") String apiEndpoint) {
        LOGGER.info("Tool called: Fetching URLs from API endpoint: {}", apiEndpoint);

        // --- BASIC AUTHENTICATION LOGIC ---
        String authString = getApiEndpointUsername() + ":" + getApiEndpointPassword();
        String encodedAuthString = Base64.getEncoder().encodeToString(authString.getBytes());
        String authHeaderValue = "Basic " + encodedAuthString;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .header("Accept", "application/json") // Good practice to specify expected content type
                .header("Authorization", authHeaderValue)
                .build();
        try {
            // Send the request and get the response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) { // Unauthorized
                return Map.of("status", "error", "message", "API call failed. The provided credentials were an incorrect (Unauthorized).");
            }
            if (response.statusCode() != 200) {
                return Map.of("status", "error", "message", "API call failed with status code: " + response.statusCode());
            }

            String responseBody = response.body();
            // Parse the JSON array of strings into a Java List<String>
            List<String> urls = OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});

            LOGGER.info("Successfully fetched {} URLs from the API.", urls.size());
            return Map.of("status", "success", OUTPUT_SCOUT_AGENT, urls);

        } catch (Exception e) {
            LOGGER.error("An error occurred while calling the API", e);
            return Map.of("status", "error", "message", "An unexpected error occurred: " + e.getMessage());
        }
    }

    @Annotations.Schema(
            name = TOOL_EXTRACT,
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
