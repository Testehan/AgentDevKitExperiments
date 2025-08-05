package com.testehan.adk.agents.cm;

import com.google.adk.agents.BaseAgent;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.testehan.adk.agents.cm.agents.ListingAgents;
import com.testehan.adk.agents.cm.agents.WhatsAppExpiredListingAgents;
import com.testehan.adk.agents.cm.agents.WhatsAppInitialContactAgents;
import com.testehan.adk.agents.cm.config.ConfigLoader;
import com.testehan.adk.agents.cm.tools.ListingUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.testehan.adk.agents.cm.config.Constants.*;


public class CMAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(CMAgent.class);

    // The run your agent with Dev UI, the ROOT_AGENT should be a global public static variable.
    public static BaseAgent ROOT_AGENT_ADD_LISTINGS = ListingAgents.createOrchestratorAgent();
    public static BaseAgent ROOT_AGENT_WHATSAPP_INITIAL_CONTACT = WhatsAppInitialContactAgents.createOrchestratorAgentIntialContact();
    public static BaseAgent ROOT_AGENT_WHATSAPP_EXPIRED_LISTING = WhatsAppExpiredListingAgents.createOrchestratorAgentExpiredListing();

    public static void main(String[] args) throws Exception {
        // Create a single-threaded executor that can schedule commands.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService scheduler2 = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService scheduler3 = Executors.newSingleThreadScheduledExecutor();

        // Define the task that will be executed periodically.
        Runnable agentRunner = () -> {
            try {
                LOGGER.info("EXECUTING Listing flow: Starting the orchestrator agent run...");

                runRootAgentAddListings();

                LOGGER.info("SUCCESS Listing flow: Orchestrator agent run finished.");

            } catch (Exception e) {
                // It's crucial to catch exceptions, otherwise the scheduler
                // will stop executing the task if it throws an unhandled exception.
                LOGGER.error("ERROR: An exception occurred during agent execution: {}", e.getMessage());
                e.printStackTrace();
            }
        };

        Runnable agent2Runner = () -> {
            try {
                LOGGER.info("EXECUTING Leads flow: Starting the orchestrator agent run...");
                runRootAgentWhatsAppInitialContact();

                LOGGER.info("SUCCESS Leads flow: Orchestrator agent run finished.");

            } catch (Exception e) {
                LOGGER.error("ERROR: An exception occurred during agent execution: {}", e.getMessage());
                e.printStackTrace();
            }
        };

        Runnable agent3Runner = () -> {
            try {
                LOGGER.info("EXECUTING Expired listings flow: Starting the orchestrator agent run...");
                runRootAgentWhatsAppExpiredListings();

                LOGGER.info("SUCCESS Expired listings flow: Orchestrator agent run finished.");

            } catch (Exception e) {
                LOGGER.error("ERROR: An exception occurred during agent execution: {}", e.getMessage());
                e.printStackTrace();
            }
        };

        LOGGER.info("Scheduler initialized. The Add Listing agent will run every 5 hours.");
        scheduler.scheduleAtFixedRate(agentRunner, 0, 5, TimeUnit.HOURS);

        LOGGER.info("Scheduler initialized. The Leads agent will run every 10 minutes.");
//        scheduler2.scheduleAtFixedRate(agent2Runner, 0, 10, TimeUnit.MINUTES);

        LOGGER.info("Scheduler initialized. The Expired Listings agent will run every 10 minutes.");
//        scheduler3.scheduleAtFixedRate(agent3Runner, 0, 10, TimeUnit.MINUTES);

        // This application will keep running because the scheduler thread is active.
        // In a real server application, you would manage the lifecycle and
        // implement a graceful shutdown of the scheduler when the app terminates.

    }

    private static void runRootAgentAddListings() {
        // We now initialize the runner with our single ROOT_AGENT, the orchestrator.
        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT_ADD_LISTINGS);

        Session session = runner.sessionService()
                .createSession(ROOT_AGENT_ADD_LISTINGS.name(), USER_ID)
                .blockingGet();

        Content apiUrl = Content.fromParts(Part.fromText(ConfigLoader.getApiEndpointGetLeads()));

        runner.runAsync(USER_ID, session.id(), apiUrl)
            .filter(event -> {
                // We only want to process events that have a non-null stateDelta
                // and contain our specific result key.
                if (event.actions() == null || event.actions().stateDelta() == null) {
                    return false; // Discard events without a stateDelta.
                }
                // Keep the event only if it contains the "individual_json_result" key.
                return event.actions().stateDelta().containsKey(OUTPUT_MASTER_ORCHESTRATOR_LISTING);
            })
            .blockingForEach(event -> {
                ConcurrentMap<String, Object> stateDelta = event.actions().stateDelta();
                String individualJson = (String) stateDelta.get(OUTPUT_MASTER_ORCHESTRATOR_LISTING);
                String listingSourceUrl = (String) stateDelta.get(OUTPUT_MASTER_ORCHESTRATOR_URL);

                if (individualJson != null && !individualJson.isEmpty()) {
                    LOGGER.info("✅ Received a result: \n{}", individualJson);
                    ListingUploader uploader = new ListingUploader();
                    uploader.upload(individualJson,listingSourceUrl);
                }
            });
    }

    private static void runRootAgentWhatsAppInitialContact() {
        // We now initialize the runner with our single ROOT_AGENT, the orchestrator.
        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT_WHATSAPP_INITIAL_CONTACT);

        Session session = runner.sessionService()
                .createSession(ROOT_AGENT_WHATSAPP_INITIAL_CONTACT.name(), USER_ID)
                .blockingGet();

        Content apiUrl = Content.fromParts(Part.fromText(ConfigLoader.getApiEndpointGetPhones()));

        runner.runAsync(USER_ID, session.id(), apiUrl)
                .filter(event -> {
                    // We only want to process events that have a non-null stateDelta
                    // and contain our specific result key.
                    return( event.actions() == null || event.actions().stateDelta() == null);
                })
                .blockingForEach(event -> {
                    LOGGER.info("State Delta: {}", event.actions().stateDelta());
                });
    }

    private static void runRootAgentWhatsAppExpiredListings() {
        // We now initialize the runner with our single ROOT_AGENT, the orchestrator.
        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT_WHATSAPP_EXPIRED_LISTING);

        Session session = runner.sessionService()
                .createSession(ROOT_AGENT_WHATSAPP_EXPIRED_LISTING.name(), USER_ID)
                .blockingGet();

        var apiEndpointGetLeadsWithStatus = ConfigLoader.getApiEndpointGetLeadsWithStatus();
        var apiEndpointWithParam = String.format(apiEndpointGetLeadsWithStatus, "EXPIRED");

        Content apiUrl = Content.fromParts(Part.fromText(apiEndpointWithParam));

        runner.runAsync(USER_ID, session.id(), apiUrl)
                .filter(event -> {
                    // We only want to process events that have a non-null stateDelta
                    // and contain our specific result key.
                    return( event.actions() == null || event.actions().stateDelta() == null);
                })
                .blockingForEach(event -> {
                    LOGGER.info("State Delta: {}", event.actions().stateDelta());
                });
    }

}
