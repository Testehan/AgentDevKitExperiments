package com.testehan.adk.agents.cm.tools;

import com.google.adk.tools.Annotations;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Tools {

    private static final Logger logger = LoggerFactory.getLogger(Tools.class);

    public static Map<String, String> getTextFromUrl(
            @Annotations.Schema(name = "getTextFromUrl", description = "The url for which the retrieval must be done")
            String url) {
        logger.info("Tool called: Fetching content from {}", url);
        try {
            // Use JSoup to connect to the URL, get the HTML, and return its text
            Document doc = Jsoup.connect(url).get();
            String text = doc.text();

            logger.info("Obtained the following text: {}",text);

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

        logger.info("Tool called: Fetching content and images from {} with Selenium", url);

        // Configure Chrome to run in headless mode (no UI)
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = null;
        try {
            // This will use the ChromeDriver from your system's PATH.
            // Make sure you have ChromeDriver installed.
            driver = new ChromeDriver(options);

            driver.get(url);

            // IMPORTANT: Wait for the page to load dynamically.
            // We wait up to 10 seconds for the 'body' tag to be present.
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // 1. Get the page text
            String text = driver.findElement(By.tagName("body")).getText();
            logger.info("Obtained page text successfully.");

            // 2. Get image URLs from the specific container
            List<String> imageUrls = new ArrayList<>();
            String gallerySelector = "[data-testid='image-galery-container']";
            try {
                // Wait for the gallery container to be present to ensure it has loaded
                WebElement galleryContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(gallerySelector)));
                logger.info("Found the image gallery container.");

                // Now find all 'img' tags *within* that container
                List<WebElement> imageElements = galleryContainer.findElements(By.tagName("img"));

                imageUrls = imageElements.stream()
                        .map(img -> img.getAttribute("src"))
                        .filter(src -> src != null && src.startsWith("http"))
                        .distinct()
                        .collect(Collectors.toList());

                logger.info("Found {} unique image URLs inside the gallery container.", imageUrls.size());

            } catch (org.openqa.selenium.TimeoutException e) {
                // This is a normal case if a page doesn't have the gallery
                logger.warn("Image gallery container with selector '{}' was not found on the page.", gallerySelector);
            }

            return Map.of(
                    "status", "success",
                    "pageText", text,
                    "imageUrls", imageUrls
            );
        } catch (Exception e) {
            logger.error("Error fetching content with Selenium", e);
            return Map.of(
                    "status", "error",
                    "report", "Error: Could not fetch content from the URL with Selenium."
            );
        } finally {

            if (driver != null) {
                driver.quit();
            }
        }
    }
}
