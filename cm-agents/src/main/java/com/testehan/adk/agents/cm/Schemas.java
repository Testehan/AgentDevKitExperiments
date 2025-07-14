package com.testehan.adk.agents.cm;

import com.google.genai.types.Schema;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

public class Schemas {
    public static final Schema PROPERTY_INFORMATION =
            Schema.builder()
                    .type("OBJECT")
                    .description("Schema for the extracted property information.")
                    .properties(
                            Map.ofEntries(
                                    entry("name",
                                        Schema.builder()
                                                .type("STRING")
                                                .description("Name of the listing.")
                                                .build()),
                                    entry("city",
                                        Schema.builder()
                                                .type("STRING")
                                                .description("City where the apartment is located.")
                                                .build()),
                                    entry("area",
                                        Schema.builder()
                                                .type("STRING")
                                                .description("This must contain the address if available. If address is not mentioned use area where the apartment is located.")
                                                .build()),
                                    entry("shortDescription",
                                        Schema.builder()
                                                .type("STRING")
                                                .description("The apartment description.")
                                                .build()),
                                    entry("price",
                                        Schema.builder()
                                                .type("INTEGER")
                                                .description("Price of the apartment. I only want the number, not the currency.")
                                                .build()),
                                    entry("surface",
                                        Schema.builder()
                                                .type("INTEGER")
                                                .description("Surface area of the apartment in square meters.")
                                                .build()),
                                    entry("noOfRooms",
                                        Schema.builder()
                                                .type("INTEGER")
                                                .description("Number of rooms in the apartment.")
                                                .build()),
                                    entry("floor",
                                        Schema.builder()
                                                .type("STRING")
                                                .description("Floor of the apartment.")
                                                .build()),
                                    entry("ownerName",
                                        Schema.builder()
                                                .type("STRING")
                                                .description("Name of the owner.")
                                                .build()),
                                    entry("imageUrls",
                                        Schema.builder()
                                            .type("ARRAY")
                                            .description("A list of all found image URLs from the page.")
                                            .items(Schema.builder().type("STRING").build())
                                            .build())
                            ))
                    .required(
                            List.of(
                                    "name",
                                    "city",
                                    "area",
                                    "shortDescription",
                                    "price",
                                    "surface",
                                    "noOfRooms",
                                    "floor",
                                    "ownerName"))
                    .build();
}
