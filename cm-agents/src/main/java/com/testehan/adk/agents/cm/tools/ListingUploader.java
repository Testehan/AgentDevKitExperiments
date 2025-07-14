package com.testehan.adk.agents.cm.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import static com.testehan.adk.agents.cm.config.ConfigLoader.*;

public class ListingUploader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListingUploader.class);

    public void upload(String listingJson, String listingSourceUrl) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        try {

            Listing listing = objectMapper.readValue(listingJson, Listing.class);

            try {
                // Prepare the MultipartFile[] for images (if any)
                MultiValueMap<String, Object> body = getPostListingRequestBody(listing, listingSourceUrl);

                // Create a RestTemplate instance
                RestTemplate restTemplate = new RestTemplate();
                String endpoint = getApiEndpointPostListing();

                // Prepare the request
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                String authString = getApiEndpointUsername() + ":" + getApiEndpointPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));
                headers.set("Authorization", "Basic " + encodedAuth);
                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                // Make the POST request
                ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.POST, requestEntity, String.class);

                // Handle the response
                if (response.getStatusCode() == HttpStatus.OK) {
                    LOGGER.info("Listing successfully saved!");
                } else {
                    LOGGER.info("Failed to save listing. Status code: {}  body: {}", response.getStatusCode(), response.getBody());
                }

                Thread.sleep(40000);
            } catch (HttpClientErrorException e) {
                LOGGER.error("Exception {} ", e.getMessage());
            }

        } catch (JsonProcessingException | InterruptedException e) {
            LOGGER.error("Exception {} ", e.getMessage());
        }
    }

    @NotNull
    private static MultiValueMap<String, Object> getPostListingRequestBody(Listing listing, String listingSourceUrl) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        body.add("name", listing.name);
        body.add("city", listing.city);
        body.add("area", listing.area);
        body.add("shortDescription", listing.shortDescription);
        body.add("price", Integer.valueOf(listing.price));
        body.add("propertyType", "rent");
        body.add("surface", Integer.valueOf(listing.surface));
        body.add("noOfRooms", Integer.valueOf(listing.noOfRooms));
        body.add("floor", listing.floor);
        body.add("ownerName", listing.ownerName);
        body.add("active", false);
        body.add("availableFrom", getAvailableFromToday());

        if (listing.imageUrls != null) {
            int i = 1;
            for (String imageUrl : listing.imageUrls) {
                try {
                    if(!imageUrl.contains("full-screen.5555ba1b6.svg")) {
                        // Download image from the URL
                        URL url = new URL(imageUrl);
                        InputStream inputStream = url.openStream();
                        byte[] imageBytes = toByteArray(inputStream); // Convert InputStream to byte array

                        final int j = i;
                        ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
                            @Override
                            public String getFilename() {
                                return "image" + j + ".jpg"; // or whatever file name and extension you want
                            }
                        };
                        i++;

                        // Add to the body
                        body.add("apartmentImages", fileResource);
                    }
                } catch (Exception e) {
                    System.out.println("Error processing image " + imageUrl);
                    System.out.println(e.getMessage());
                }
            }
        }

        body.add("listingSourceUrl", listingSourceUrl);
        return body;
    }

    // Helper method to convert InputStream to byte array
    private static byte[] toByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int read;
        byte[] bytes = new byte[1024];
        while ((read = inputStream.read(bytes)) != -1) {
            byteArrayOutputStream.write(bytes, 0, read);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static String getAvailableFromToday(){
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return today.format(formatter);
    }

}

class Listing {
    public String ownerName;
    public Integer price;
    public String city;
    public String area;
    public String shortDescription;
    public Integer noOfRooms;
    public String floor;
    public Integer surface;
    public String name;
    public List<String> imageUrls;
}
