package com.testehan.adk.agents.cm.tools;

import com.google.adk.tools.Annotations;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class Tools {

    private static final Logger LOGGER = LoggerFactory.getLogger(Tools.class);

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
}
