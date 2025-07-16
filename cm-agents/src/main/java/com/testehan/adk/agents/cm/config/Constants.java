package com.testehan.adk.agents.cm.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Constants {
    public static final String USED_MODEL_NAME = "gemini-2.5-pro";

    public static String USER_ID = "casamia";


    public static final String MASTER_ORCHESTRATOR_LISTINGS_AGENT_NAME = "listings_master_orchestrator_agent";
    public static final String MASTER_ORCHESTRATOR_PHONES_AGENT_NAME = "phones_master_orchestrator_agent";
    public static final String API_SCOUT_AGENT_NAME = "api_scout_agent";
    public static final String EXTRACTOR_AGENT_NAME = "extractor_agent";
    public static final String FORMATTER_AGENT = "formatter_agent";
    public static final String CONVERSATION_AGENT = "conversation_agent";
    public static final String NEXT_REPLY_AGENT = "next_reply_agent";

    public static final String AGENT_VAR_LISTING_URL_INITIAL_SOURCE = "listing_url_initial_source";
    public static final String AGENT_VAR_LISTING_SCRAPED_TEXT = "scraped_text";
    public static final String AGENT_VAR_CURRENT_CONVERSATION = "conversation_text";

    public static final String OUTPUT_SCOUT_AGENT = "scout_output";
    public static final String OUTPUT_MASTER_ORCHESTRATOR_LISTING = "individual_json_result";
    public static final String OUTPUT_MASTER_ORCHESTRATOR_URL = "listing_source_url";
    public static final String OUTPUT_CONVERSATION_AGENT = "conversation_output";
    public static final String OUTPUT_NEXT_REPLY_AGENT = "next_reply_output";

    public static final String TOOL_GET_STRINGS = "getStringsFromApi";
    public static final String TOOL_EXTRACT = "extractPageContentAndImages";


    private static final List<String> INITIAL_LEAD_MESSAGES = new ArrayList<>(List.of(
            "Anunțul tău este încă disponibil?",
            "Am văzut anunțul tău. Mai este disponibil?",
            "Îți scriem legat de anunțul tău. Mai este disponibil? ",
            "Am văzut anunțul tău. Îl putem publica gratuit pe www.casamia.ai pentru mai multă vizibilitate. Te interesează?",
            "Am văzut anunțul tău și ne-a plăcut. Mai este valabil?",
            "Îți scriem legat de apartament. Mai este disponibil?"
    ));

    public static String getRandomInitialMessage(){
        return INITIAL_LEAD_MESSAGES.get(new Random().nextInt(INITIAL_LEAD_MESSAGES.size()));
    }

}
