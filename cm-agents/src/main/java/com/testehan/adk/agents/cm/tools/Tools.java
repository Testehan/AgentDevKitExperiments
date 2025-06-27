package com.testehan.adk.agents.cm.tools;

import com.google.adk.tools.Annotations;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Map;

public class Tools {

    public static Map<String, String> getTextFromUrl(
            @Annotations.Schema(description = "The url for which the retrieval must be done")
            String url) {
        System.out.println("Tool called: Fetching content from " + url);
        try {
            // Use JSoup to connect to the URL, get the HTML, and return its text
            Document doc = Jsoup.connect(url).get();
            return Map.of(
                    "status", "success",
                    "report", doc.text());
        } catch (IOException e) {
            e.printStackTrace();
            return Map.of(
                    "status", "error",
                    "report", "Error: Could not fetch content from the URL.");
        }
    }
}
