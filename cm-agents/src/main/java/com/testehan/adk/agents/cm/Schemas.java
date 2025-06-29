package com.testehan.adk.agents.cm;

import com.google.genai.types.Schema;

import java.util.List;
import java.util.Map;

public class Schemas {
    public static final Schema PROPERTY_INFORMATION =
            Schema.builder()
                    .type("OBJECT")
                    .description("Schema for the extracted property information.")
                    .properties(
                            Map.of(
                                    "name",
                                    Schema.builder()
                                            .type("STRING")
                                            .description("Name of the listing.")
                                            .build(),
                                    "city",
                                    Schema.builder()
                                            .type("STRING")
                                            .description("City where the apartment is located.")
                                            .build(),
                                    "area",
                                    Schema.builder()
                                            .type("STRING")
                                            .description(
                                                    "This must contain the address if available. If address is not mentioned use area where the apartment is located.")
                                            .build(),
                                    "shortDescription",
                                    Schema.builder()
                                            .type("STRING")
                                            .description("The apartment description.")
                                            .build(),
                                    "price",
                                    Schema.builder()
                                            .type("INTEGER")
                                            .description("Price of the apartment. I only want the number, not the currency.")
                                            .build(),
                                    "surface",
                                    Schema.builder()
                                            .type("INTEGER")
                                            .description("Surface area of the apartment in square meters.")
                                            .build(),
                                    "noOfRooms",
                                    Schema.builder()
                                            .type("INTEGER")
                                            .description("Number of rooms in the apartment.")
                                            .build(),
                                    "floor",
                                    Schema.builder()
                                            .type("INTEGER")
                                            .description("Floor of the apartment.")
                                            .build(),
                                    "ownerName",
                                    Schema.builder()
                                            .type("STRING")
                                            .description("Name of the owner.")
                                            .build(),
                                    "imageUrls", Schema.builder()
                                            .type("ARRAY")
                                            .description("A list of all found image URLs from the page.")
                                            .items(Schema.builder().type("STRING").build())
                                            .build()
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
