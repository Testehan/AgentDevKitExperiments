package com.testehan.adk.agents.cm.tools;

import com.testehan.adk.agents.cm.config.ConfigLoader;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class HumanizedBrowsing
{
    private static final String PROXIED_URL_TEMPLATE = "https://api.scraperapi.com/?api_key=%s&url=%s&country_code=ro";

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(HumanizedBrowsing.class);


    public Map<String, Object> browseUrl(String targetUrl) {
        LOGGER.info("fetch from {}", targetUrl);

        targetUrl = URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);
        String fullUrl = String.format(PROXIED_URL_TEMPLATE, ConfigLoader.getScraperApiKey(), targetUrl);

        // Select a random User-Agent for this session
        String randomUserAgent = USER_AGENTS.get(new Random().nextInt(USER_AGENTS.size()));
        LOGGER.info("Using User-Agent: {}", randomUserAgent);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        // Add arguments to evade basic bot detection
        options.addArguments("--user-agent=" + randomUserAgent);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));

        // For IP Rotation (Advanced): Uncomment to use a proxy
        // String proxy = "http://your-proxy-host:port";
        // options.addArguments("--proxy-server=" + proxy);

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15)); // Increased wait time slightly

            // Introduce a random delay before even loading the page
//            sleepRandomly(1, 3);

            driver.get(fullUrl);

            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            LOGGER.info("Page body loaded.");

            // Simulate human scrolling : This is crucial for lazy-loading content and looks more natural.
            simulateHumanScrolling(driver);

            String mainContentSelector = "[data-testid='main']";
            String text = "";
            try {
                WebElement mainContentContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(mainContentSelector)));
                LOGGER.info("Found the main container.");
                text = mainContentContainer.getText();
            } catch (TimeoutException e) {
                LOGGER.warn("Main content container with selector '{}' was not found on the page.", mainContentSelector);
            }
            LOGGER.info("Obtained page text successfully.");

            List<String> imageUrls = new ArrayList<>();
            String gallerySelector = "[data-testid='image-galery-container']";
            try {
                WebElement galleryContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(gallerySelector)));
                LOGGER.info("Found the image gallery container.");

                List<WebElement> imageElements = galleryContainer.findElements(By.tagName("img"));

                imageUrls = imageElements.stream()
                        .map(img -> img.getAttribute("src"))
                        .filter(src -> src != null && !src.isEmpty() && src.startsWith("http"))
                        .distinct()
                        .collect(Collectors.toList());

                LOGGER.info("Found {} unique image URLs inside the gallery container.", imageUrls.size());

            } catch (TimeoutException e) {
                LOGGER.warn("Image gallery container with selector '{}' was not found on the page.", gallerySelector);
            }

            return Map.of(
                    "status", "success",
                    "pageText", text,
                    "imageUrls", imageUrls
            );

        } catch (Exception e) {
            LOGGER.error("An unexpected error occurred during scraping", e);
            return Map.of("status", "error", "message", e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
                LOGGER.info("WebDriver has been closed.");
            }
        }
    }

    /**
     * Pauses execution for a random duration to mimic human reading/thinking time.
     * @param minSeconds The minimum seconds to sleep.
     * @param maxSeconds The maximum seconds to sleep.
     */
    private void sleepRandomly(int minSeconds, int maxSeconds) {
        try {
            long sleepTime = ThreadLocalRandom.current().nextLong(minSeconds * 1000L, maxSeconds * 1000L);
            LOGGER.info("Simulating human delay: sleeping for {} ms", sleepTime);
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Thread was interrupted during sleep.", e);
        }
    }

    /**
     * Simulates a human scrolling down the page.
     * This helps trigger lazy-loaded elements and makes behavior less bot-like.
     * @param driver The WebDriver instance.
     */
    private void simulateHumanScrolling(WebDriver driver) {
        LOGGER.info("Simulating human scrolling...");
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long pageHeight = (long) js.executeScript("return document.body.scrollHeight");
        int scrollIncrement = 500; // Scroll 500 pixels at a time

        for (int y = 0; y < pageHeight; y += scrollIncrement) {
            js.executeScript("window.scrollTo(0, " + y + ");");
            // Add a small, random pause between scrolls
            sleepRandomly(0, 1); // Sleeps for 0 to 3 second
        }

        // One final scroll to the bottom to be sure
        js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
        sleepRandomly(0, 1); // Pause after scrolling is complete
    }

}
