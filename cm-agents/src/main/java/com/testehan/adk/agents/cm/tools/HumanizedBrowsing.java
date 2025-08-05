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
import java.util.function.Function;
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

    private final Map<String, Function<String, Map>> domainHandlers = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(HumanizedBrowsing.class);

    private final ChromeOptions chromeOptions;

    public HumanizedBrowsing() {
        domainHandlers.put("https://www.olx.ro/",this::extractOlxData);
        domainHandlers.put("https://www.publi24.ro/",this::extractPubliData);

        chromeOptions = initializeChromeOptions();
    }

    @NotNull
    private ChromeOptions initializeChromeOptions() {
        final ChromeOptions chromeOptions;
        // Select a random User-Agent for this session
        String randomUserAgent = USER_AGENTS.get(new Random().nextInt(USER_AGENTS.size()));
        LOGGER.info("Using User-Agent: {}", randomUserAgent);

        chromeOptions = new ChromeOptions();
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
        return chromeOptions;
    }

    public Map<String, Object> browseUrl(String targetUrl) {
        LOGGER.info("fetch from {}", targetUrl);

        var encodedTargetUrl = URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);
        var fullUrl = String.format(PROXIED_URL_TEMPLATE, ConfigLoader.getScraperApiKey(), encodedTargetUrl);

        for (var entry : domainHandlers.entrySet()) {
            if (targetUrl.startsWith(entry.getKey())) {
                return entry.getValue().apply(fullUrl);
            }
        }

        return handleDefault(fullUrl);
    }

    @NotNull
    private Map<String, Object> extractOlxData(String fullUrl) {
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

            // Find the <img alt="Location"> element
            WebElement locationIcon = driver.findElement(By.xpath("//img[@alt='Location']"));

            // Find the following <div>
            WebElement cityDiv = locationIcon.findElement(By.xpath("following-sibling::div"));

            // Find the first <p> element inside that div
            WebElement firstParagraph = cityDiv.findElement(By.xpath(".//p[1]"));

            var extractedData = "Page Text: " + text + "\n\n" +
                                "City name: " + firstParagraph.getText() + "\n\n" +
                                "Image URLs: " + imageUrls;

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

    private Map<String, Object>  extractPubliData(String fullUrl) {
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
            StringBuilder mainListingText = new StringBuilder();
            mainListingText.append("Text from where to extract image URLS: ");
            try {
                WebElement mainContentContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.className(mainContentSelector)));
                LOGGER.info("Found the main container.");
                WebElement scriptElement = mainContentContainer.findElement(By.tagName("script"));
                // 3. Get the content of the script element
                var scriptContent = scriptElement.getAttribute("innerHTML");
                mainListingText.append(scriptContent);

                LOGGER.info("publi24: " + mainListingText);
            } catch (TimeoutException e) {
                LOGGER.warn("Main content container with selector '{}' was not found on the page.", mainContentSelector);
            }

            var ownerNameSelector = "user-profile-name";
            mainListingText.append(" \n Owner name : ");
            var ownerName = "";
            try {
                WebElement mainContentContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.className(ownerNameSelector)));
                ownerName = mainContentContainer.getAttribute("innerHTML");
                mainListingText.append(ownerName);
                LOGGER.info("publi24: " + ownerName);
            } catch (TimeoutException e) {
                LOGGER.warn("Main content container with selector '{}' was not found on the page.", mainContentSelector);
            }

            mainListingText.append(" \n Listing name : ");
            var listingName = "";
            try {
                listingName = driver.findElement(By.cssSelector("h1[itemprop='name']")).getText();
                mainListingText.append(listingName);
                LOGGER.info("publi24: " + listingName);
            } catch (TimeoutException e) {
                LOGGER.warn("Main content container with selector '{}' was not found on the page.", mainContentSelector);
            }

            mainListingText.append(" \n Price : ");
            var price = "";
            try {
                price = driver.findElement(By.cssSelector("[itemprop='price']")).getText();
                mainListingText.append(price);
                LOGGER.info("publi24: " + price);
            } catch (TimeoutException e) {
                LOGGER.warn("Main content container with selector '{}' was not found on the page.", mainContentSelector);
            }

            mainListingText.append(" \n City : ");
            var city = "";
            try {
                WebElement placeContainer = driver.findElement(By.cssSelector("div[itemtype='https://schema.org/Place']"));
                List<WebElement> locationLinks = placeContainer.findElements(By.cssSelector("a[itemprop='url']"));
                if (locationLinks.size() > 1) {
                    // The city is the second link in the list.
                    city = locationLinks.get(1).getText();
                    LOGGER.info("The extracted city name is: " + city);
                } else {
                    LOGGER.info("Could not find a city. Found " + locationLinks.size() + " location links.");
                }
                mainListingText.append(city);

                LOGGER.info("publi24: " + city);
            } catch (TimeoutException e) {
                LOGGER.warn("Main content container with selector '{}' was not found on the page.", mainContentSelector);
            }

            var descriptionClass = "article-attributes";
            mainListingText.append(" \n Description : ");
            var description = "";
            try {
                WebElement mainContentContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.className(descriptionClass)));
                description = mainContentContainer.getAttribute("innerHTML");
                mainListingText.append(description);
                LOGGER.info("publi24: " + description);
            } catch (TimeoutException e) {
                LOGGER.warn("Main content container with selector '{}' was not found on the page.", mainContentSelector);
            }

            descriptionClass = "article-description";
            try {
                WebElement mainContentContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.className(descriptionClass)));
                description = mainContentContainer.getAttribute("innerHTML");
                mainListingText.append(description);
                LOGGER.info("publi24: " + description);
            } catch (TimeoutException e) {
                LOGGER.warn("Main content container with selector '{}' was not found on the page.", mainContentSelector);
            }

            LOGGER.info("Obtained page text successfully.");

            var extractedData = mainListingText.toString();

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

        for (int y = 0; y < 5; y++) {
            js.executeScript("window.scrollTo(0, " + scrollIncrement + ");");
            // Add a small, random pause between scrolls
            sleepRandomly(0, 1);
            scrollIncrement = scrollIncrement + 500;
        }

        // One final scroll to the bottom to be sure
        js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
//        sleepRandomly(0, 1); // Pause after scrolling is complete
    }

}
