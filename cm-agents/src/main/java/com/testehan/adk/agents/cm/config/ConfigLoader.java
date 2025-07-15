package com.testehan.adk.agents.cm.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ConfigLoader {

    private static final String SECRETS_PROPERTIES_FILE = "secrets.properties";
    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream(SECRETS_PROPERTIES_FILE)) {
            if (input == null) {
                throw new RuntimeException("Could not find " + SECRETS_PROPERTIES_FILE);
            }
            PROPERTIES.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + SECRETS_PROPERTIES_FILE, e);
        }
    }

    public static String getScraperApiKey() {
        return PROPERTIES.getProperty("scraper.api.key");
    }

    public static String getWhatsAppApiToken() {
        return PROPERTIES.getProperty("whatsapp.api.access.token");
    }

    public static String getApiEndpointGetLeads() {
        return PROPERTIES.getProperty("api.endpoint.get.leads");
    }

    public static String getApiEndpointGetPhones() {
        return PROPERTIES.getProperty("api.endpoint.get.phones");
    }

    public static String getApiEndpointPathLeadStatus() {
        return PROPERTIES.getProperty("api.endpoint.patch.lead.status");
    }

    public static String getApiEndpointPostLeadReply() {
        return PROPERTIES.getProperty("api.endpoint.post.lead.reply");
    }

    public static String getApiEndpointPostListing() {
        return PROPERTIES.getProperty("api.endpoint.post.listing");
    }

    public static String getApiEndpointUsername() {
        return PROPERTIES.getProperty("api.endpoint.user.name");
    }

    public static String getApiEndpointPassword() {
        return PROPERTIES.getProperty("api.endpoint.user.password");
    }


    public static String getAuthenticationHeaderValue(){
        String authString = getApiEndpointUsername() + ":" + getApiEndpointPassword();
        String encodedAuthString = Base64.getEncoder().encodeToString(authString.getBytes());
        String authHeaderValue = "Basic " + encodedAuthString;
        return authHeaderValue;
    }

}
