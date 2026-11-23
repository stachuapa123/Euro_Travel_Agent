package travelplanner.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.StaleProxyException;
import travelplanner.model.TripRequest;
import travelplanner.model.TripRequestCsv;
import travelplanner.ui.ConsoleUserInterface;
import travelplanner.ui.UserInterface;

import java.util.Properties;

/**
 * Represents the traveller. It collects trip requests through a UserInterface (console for now),
 * sends each one to the Planner as a REQUEST with CSV content, and shows the Planner's replies.
 *
 * Config keys (config.properties):
 *   user.ui                       console (a gui option can be added later)
 *   user.planner                  local name of the Planner agent      (default: planner)
 *   user.replyTimeoutSec          how long the console waits for a reply (default: 30)
 *   user.shutdownPlatformOnQuit   stop the whole JADE platform on quit  (default: true)
 */
public class UserAgent extends Agent {

    private UserInterface ui;
    private AID planner;
    private boolean shutdownPlatformOnQuit;

    @Override
    protected void setup() {
        Properties config = configFromArguments();
        planner = new AID(config.getProperty("user.planner", "planner"), AID.ISLOCALNAME);
        shutdownPlatformOnQuit = Boolean.parseBoolean(config.getProperty("user.shutdownPlatformOnQuit", "true"));
        int replyTimeoutSec = Integer.parseInt(config.getProperty("user.replyTimeoutSec", "30").trim());

        ui = createUserInterface(config.getProperty("user.ui", "console").trim(), replyTimeoutSec);

        addBehaviour(new ReplyListener());

        // The UI calls these from its own thread; we only add behaviours there,
        // so all message handling still happens on the agent thread.
        ui.start(new UserInterface.Listener() {
            @Override
            public void onSubmit(TripRequest request) {
                addBehaviour(new SendRequest(request));
            }

            @Override
            public void onQuit() {
                doDelete();
            }
        });
    }

    @Override
    protected void takeDown() {
        if (ui != null) {
            ui.close();
        }
        if (shutdownPlatformOnQuit) {
            // Killing the container from inside one of its agents must happen on another thread.
            new Thread(() -> {
                try {
                    getContainerController().kill();
                } catch (StaleProxyException e) {
                    // platform already stopping
                }
            }, "platform-shutdown").start();
        }
    }

    private UserInterface createUserInterface(String type, int replyTimeoutSec) {
        return switch (type.toLowerCase()) {
            case "console" -> new ConsoleUserInterface(replyTimeoutSec);
            // case "gui" -> new SwingUserInterface();   // later
            default -> throw new IllegalArgumentException("Unknown user.ui: " + type);
        };
    }

    private Properties configFromArguments() {
        Object[] args = getArguments();
        if (args != null && args.length > 0 && args[0] instanceof Properties p) {
            return p;
        }
        return new Properties();
    }

    // ---- behaviours ------------------------------------------------------------------------

    /** Sends one trip request to the Planner. */
    private class SendRequest extends OneShotBehaviour {
        private final TripRequest request;

        SendRequest(TripRequest request) {
            super(UserAgent.this);
            this.request = request;
        }

        @Override
        public void action() {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(planner);
            msg.setLanguage(TripRequestCsv.LANGUAGE);
            msg.setOntology(TripRequestCsv.ONTOLOGY);
            msg.setConversationId(request.requestId());
            msg.setReplyWith(request.requestId() + "-" + System.currentTimeMillis());
            msg.setContent(TripRequestCsv.toMessageContent(request));
            myAgent.send(msg);
        }
    }

    /** Shows every reply from the Planner (or delivery failures from the AMS) in the UI. */
    private class ReplyListener extends CyclicBehaviour {
        ReplyListener() {
            super(UserAgent.this);
        }

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg == null) {
                block();
                return;
            }
            String id = msg.getConversationId() != null ? msg.getConversationId() : "?";
            String from = msg.getSender() != null ? msg.getSender().getLocalName() : "?";
            String content = msg.getContent() != null ? msg.getContent() : "";

            switch (msg.getPerformative()) {
                case ACLMessage.AGREE -> ui.showStatus(id, "Planner agreed: " + content);
                case ACLMessage.INFORM -> ui.showResult(id, content);
                case ACLMessage.REFUSE -> ui.showError(id, "Planner refused: " + content);
                case ACLMessage.NOT_UNDERSTOOD -> ui.showError(id, "Planner did not understand: " + content);
                case ACLMessage.FAILURE -> ui.showError(id, "ams".equalsIgnoreCase(from)
                        ? "Could not deliver the request to '" + planner.getLocalName()
                        + "'. Is the Planner agent running?"
                        : "Planner failed: " + content);
                default -> ui.showStatus(id, "Unexpected " + ACLMessage.getPerformative(msg.getPerformative())
                        + " from " + from + ": " + content);
            }
        }
    }
}