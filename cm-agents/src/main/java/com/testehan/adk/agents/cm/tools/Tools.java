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

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.testehan.adk.agents.cm.config.ConfigLoader.getAuthenticationHeaderValue;
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
     * Calls a given API endpoint to fetch a JSON array of strings representing URLs or phones.
     * @param apiEndpoint The full URL of the API to call.
     * @return A map containing the status and a list of strings found.
     */
    @Annotations.Schema(
            name = TOOL_GET_STRINGS,
            description = "Navigates to an API to get the list of strings that must be processed."
    )
    public static Map<String, Object> getStringsFromApi(@Annotations.Schema(name = "apiEndpoint", description = "The endpoint for which the retrieval must be done") String apiEndpoint) {
        LOGGER.info("Tool called: Fetching data from API endpoint: {}", apiEndpoint);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .header("Accept", "application/json") // Good practice to specify expected content type
                .header("Authorization", getAuthenticationHeaderValue())
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
            List<String> strings = OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});

            LOGGER.info("Successfully fetched {} strings from the API {}.", strings.size(), apiEndpoint);
            return Map.of("status", "success", OUTPUT_SCOUT_AGENT, strings);

        } catch (Exception e) {
            LOGGER.error("An error occurred while calling the API", e);
            return Map.of("status", "error", "message", "An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Calls a given API endpoint to fetch a JSON array of Map<String, String> representing phoneNumber key to url value.
     * @param apiEndpoint The full URL of the API to call.
     * @return A map containing the status and a list of Map<String, String> found.
     */
    @Annotations.Schema(
            name = TOOL_GET_MAPS,
            description = "Navigates to an API to get the list of Map<String, String> that must be processed."
    )
    public static Map<String, Object> getMapsFromApi(@Annotations.Schema(name = "apiEndpoint", description = "The endpoint for which the retrieval must be done") String apiEndpoint) {
        LOGGER.info("Tool called: Fetching data from API endpoint: {}", apiEndpoint);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .header("Accept", "application/json") // Good practice to specify expected content type
                .header("Authorization", getAuthenticationHeaderValue())
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
            List<Map<String,String>> pairs = OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});

            LOGGER.info("Successfully fetched {} strings from the API {}", pairs.size(), apiEndpoint);
            return Map.of("status", "success", OUTPUT_SCOUT_AGENT, pairs);

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


    @Annotations.Schema(
            name = TOOL_FORMAT_LISTING_LOCAL,
            description = "Sends scraped text to a local endpoint for formatting."
    )
    public static Map<String, Object> formatListingLocal(@Annotations.Schema(name = "scrapedText", description = "The scrapedText that must be sent to the local endpoint for formatting")
                                                          String scrapedText) {

        // this is the local spring app that connects to ollama
        String endpointUrl = "http://localhost:8077/api/v1/ollama/format";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Content-Type", "text/plain")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(scrapedText))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // ADK tools should return a Map.
                return OBJECT_MAPPER.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            } else {
                String errorMessage = "Error: Local endpoint returned status code " + response.statusCode()
                        + " with body: " + response.body();
                LOGGER.error("EXECUTING TOOL_FORMAT_LISTING_LOCAL: {}",errorMessage);
                return Map.of("error", errorMessage);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMessage = "Error calling local formatting endpoint: " + e.getMessage();
            LOGGER.error("EXECUTING TOOL_FORMAT_LISTING_LOCAL: {}",errorMessage);
            return Map.of("error", errorMessage);
        }
    }


    /**
     * Executes the Gemini CLI with a given prompt and returns the output.
     *
     * @param scrapedText The text prompt to send to Gemini.
     * @return The response from the Gemini CLI.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the current thread is interrupted while waiting.
     */
    @Annotations.Schema(
            name = TOOL_FORMAT_LISTING_LOCAL_GEMINI,
            description = "Sends scraped text to a local Gemini for formatting."
    )
    public static Map<String, Object> formatListingLocalGemini(@Annotations.Schema(name = "scrapedText", description = "The scrapedText that must be sent to the local endpoint for formatting")
                                        String scrapedText) {
        try {
            Map<String, Object> standardSchemaMap = buildStandardJsonSchema();
            String standardSchemaJson = OBJECT_MAPPER.writeValueAsString(standardSchemaMap);

            String PROMPT_FORMAT_LISTING = """
            Ești un expert în formatarea JSON. Vei primi un text brut: {rawText}
            Nu încerca să extragi date de pe internet sau să navighezi. Nu genera date. Singura ta sarcină este să convertești textul furnizat într-un obiect JSON valid care respectă schema furnizată.
            Textul furnizat ca input este în limba română. Textul furnizat la final trebuie să fie tot în limba română.
            
            Foarte important:
            - Câmpul „city” trebuie să conțină un nume real de oraș din România. Dacă valoarea este „Cluj”, folosește „Cluj-Napoca”.
            - Nu inventa adrese. Dacă nu este menționată o stradă, lasă doar cartierul sau zona (ex: „Nufărul”).
            - Nu adăuga sau inventa detalii care nu sunt prezente în text.
            
            La formatarea câmpului „name”:
            - Rescrie numele astfel încât să sune profesionist, concis și atractiv.
            - NU include cuvinte precum „Proprietar”, „PF”, „închiriez” sau „de închiriat”.
            - Evidențiază numărul de camere, suprafața și zona, dacă sunt disponibile.
            - Păstrează-l sub 70 de caractere.
            - NU adăuga sau inventa detalii care nu sunt prezente în textul de intrare.
            
            La formatarea câmpului „shortDescription”:
            - Include TOATE informațiile utile din textul original, fără a omite detalii.
            - Păstrează detalii despre compartimentare, dotări, suprafață, balcon, an construcție, etaj, mobilier, echipamente, reguli (fumat, animale) și condiții de închiriere.
            - Poți reformula pentru claritate și coerență, dar NU rezuma și NU scurta textul.
            - Menține un ton natural, fluent și complet. Este preferabil ca textul să fie lung, dar informativ.
            - Nu adăuga referințe la platforme imobiliare (ex: OLX, Publi24).
            
            La formatarea câmpului „area”:
            - Extrage cea mai precisă informație de localizare disponibilă.
            - Poate fi numele unei străzi, cartier, piață sau punct de reper cunoscut.
            - Urmează ordinea: adresă exactă > cartier > punct de reper.
            - Dacă nu este menționat nimic, lasă câmpul gol.
            - NU inventa locații inexistente.
            
            Iată DEFINIȚIA SCHEMEI PE CARE ȘIRUL TĂU JSON FINAL TREBUIE SĂ O RESPECTE:
            {format}
            
            ATENȚIE: Descrierea trebuie să fie completă. Nu omite niciun detaliu prezent în textul original, chiar dacă pare minor.
            
            Răspunsul tău final TREBUIE să fie NUMAI șirul JSON brut. Nu-l încadra în markdown și nu adăuga niciun alt text.
            """;

            String prompt = PROMPT_FORMAT_LISTING.replace("{rawText}", scrapedText)
                    .replace("{format}", standardSchemaJson);

            // 1. Create the ProcessBuilder
            // This command assumes 'gemini' is in your system's PATH.
            // If not, you'd need to provide the full path to the executable.
            ProcessBuilder processBuilder = new ProcessBuilder("gemini");

            // Optional: Merge the error stream with the standard output stream.
            // This is convenient for capturing both normal output and errors in one place.
            processBuilder.redirectErrorStream(true);

            StringBuilder output = new StringBuilder();

            // 2. Start the Process
            Process process = null;

            process = processBuilder.start();


            // 3. Send the Prompt (write to the process's standard input)
            // We use a try-with-resources block to ensure the writer is closed automatically.
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                writer.write(prompt);
                // IMPORTANT: You must close the writer. This sends the EOF (End-of-File) signal
                // to the gemini process, letting it know that the input is complete.
                // Without this, the gemini process will wait for more input forever.
            }

            // 4. Read the Response (read from the process's standard output)
            // Use try-with-resources for the reader as well.
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            String formattedListing = output.toString().replaceFirst("(?s)```json\\s*", "").replaceFirst("(?s)```\\s*$", "");
            // 5. Wait for the process to complete and check the exit code
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                // If the exit code is non-zero, something went wrong.
                // The captured output may contain the error message.
                var errorMessage = "Gemini CLI exited with a non-zero code: " + exitCode + "\nOutput:\n" + output;
                LOGGER.error("EXECUTING TOOL_FORMAT_LISTING_LOCAL_GEMINI: {}",errorMessage);
                return Map.of("error", errorMessage);
            } else {
                return OBJECT_MAPPER.readValue(formattedListing, new TypeReference<Map<String, Object>>() {});
            }

        } catch (IOException | InterruptedException e) {
            String errorMessage = "Error calling local formatting endpoint: " + e.getMessage();
            LOGGER.error("EXECUTING TOOL_FORMAT_LISTING_LOCAL: {}",errorMessage);
            return Map.of("error", errorMessage);

        }
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
