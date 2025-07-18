package com.testehan.adk.agents.cm.tools;

import com.testehan.adk.agents.cm.config.ConfigLoader;
import org.jetbrains.annotations.NotNull;
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
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
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

    private final Map<String, BiFunction<ChromeOptions, String, Map>> domainHandlers = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(HumanizedBrowsing.class);

    public HumanizedBrowsing() {
        domainHandlers.put("https://www.olx.ro/",this::extractOlxData);
        domainHandlers.put("https://www.publi24.ro/",this::extractPubliData);
    }

    public Map<String, Object> browseUrl(String targetUrl) {
        LOGGER.info("fetch from {}", targetUrl);

        var encodedTargetUrl = URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);
        var fullUrl = String.format(PROXIED_URL_TEMPLATE, ConfigLoader.getScraperApiKey(), encodedTargetUrl);

        // Select a random User-Agent for this session
        String randomUserAgent = USER_AGENTS.get(new Random().nextInt(USER_AGENTS.size()));
        LOGGER.info("Using User-Agent: {}", randomUserAgent);

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless=new");
        chromeOptions.addArguments("--disable-gpu");
        chromeOptions.addArguments("--window-size=1920,1080");

        // Add arguments to evade basic bot detection
        chromeOptions.addArguments("--user-agent=" + randomUserAgent);
        chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
        chromeOptions.setExperimentalOption("excludeSwitches", List.of("enable-automation"));

        // For IP Rotation (Advanced): Uncomment to use a proxy
        // String proxy = "http://your-proxy-host:port";
        // chromeOptions.addArguments("--proxy-server=" + proxy);

        for (var entry : domainHandlers.entrySet()) {
            if (targetUrl.startsWith(entry.getKey())) {
                return entry.getValue().apply(chromeOptions, fullUrl);
            }
        }

        return handleDefault(fullUrl);
    }

    @NotNull
    private Map<String, Object> extractOlxData(ChromeOptions chromeOptions, String fullUrl) {
        WebDriver driver = null;
        try {
            driver = new ChromeDriver(chromeOptions);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15)); // Increased wait time slightly

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

            var extractedData = "Page Text: " + text + "\n\n" + "Image URLs: " + imageUrls;

            return Map.of(
                    "status", "success",
                    "extractedData", extractedData
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

    private Map<String, Object>  extractPubliData(ChromeOptions chromeOptions, String fullUrl) {
        WebDriver driver = null;
        try {
            driver = new ChromeDriver(chromeOptions);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15)); // Increased wait time slightly

            driver.get(fullUrl);

            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            LOGGER.info("Page body loaded.");

            // Simulate human scrolling : This is crucial for lazy-loading content and looks more natural.
            simulateHumanScrolling(driver);

            var mainContentSelector = "detail-left";
            var mainListingText = "";
            try {
                WebElement mainContentContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.className(mainContentSelector)));
                LOGGER.info("Found the main container.");
                mainListingText = mainContentContainer.getAttribute("innerHTML");
                LOGGER.info("publi24: " + mainListingText);
            } catch (TimeoutException e) {
                LOGGER.warn("Main content container with selector '{}' was not found on the page.", mainContentSelector);
            }

            var ownerNameSelector = "user-profile-name";
            var ownerName = "";
            try {
                WebElement mainContentContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.className(ownerNameSelector)));
                LOGGER.info("Found the owner name container.");
                ownerName = mainContentContainer.getAttribute("innerHTML");
                LOGGER.info("publi24: " + ownerName);
            } catch (TimeoutException e) {
                LOGGER.warn("Main content container with selector '{}' was not found on the page.", mainContentSelector);
            }

            LOGGER.info("Obtained page text successfully.");

            var extractedData = "Page Text: " + mainListingText + " owner name : " + ownerName;

            return Map.of(
                    "status", "success",
                    "extractedData", extractedData
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

    private Map<String, Object>  handleDefault(String url) {
        LOGGER.error(" !!!! No handler for: {}" , url);
        return Map.of("status", "error", "message", "No handler for " + url);
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
