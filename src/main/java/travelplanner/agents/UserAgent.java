package travelplanner.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Properties;

/**
 * Represents the traveller: sends the trip request from config.properties
 * to the Planner and prints the answer.
 */
public class UserAgent extends Agent {

    private static final String CONVERSATION_ID = "trip-request";

    @Override
    protected void setup() {
        System.out.println("[" + getLocalName() + "] ready");

        Object[] args = getArguments();
        Properties config = (args != null && args.length > 0 && args[0] instanceof Properties p)
                ? p : new Properties();
        String request = buildRequest(config);

        addBehaviour(new OneShotBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(new AID("planner", AID.ISLOCALNAME));
                msg.setConversationId(CONVERSATION_ID);
                msg.setContent(request);
                myAgent.send(msg);
                System.out.println("[" + getLocalName() + "] sent REQUEST: " + request);
            }
        });

        addBehaviour(new CyclicBehaviour(this) {
            private final MessageTemplate template = MessageTemplate.and(
                    MessageTemplate.MatchConversationId(CONVERSATION_ID),
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM));

            @Override
            public void action() {
                ACLMessage msg = myAgent.receive(template);
                if (msg == null) {
                    block();
                    return;
                }
                System.out.println("[" + getLocalName() + "] INFORM from "
                        + msg.getSender().getLocalName() + ": " + msg.getContent());
            }
        });
    }

    /** Simple key=value;... content for now; can be replaced by JSON or a JADE ontology later. */
    private static String buildRequest(Properties c) {
        return String.join(";",
                "origin=" + c.getProperty("request.origin", ""),
                "destination=" + c.getProperty("request.destination", ""),
                "nights=" + c.getProperty("request.nights", ""),
                "budgetEur=" + c.getProperty("request.budgetEur", ""),
                "maxLegHours=" + c.getProperty("request.maxLegHours", ""),
                "accommodationType=" + c.getProperty("request.accommodationType", ""),
                "minStars=" + c.getProperty("request.minStars", ""),
                "breakfast=" + c.getProperty("request.breakfast", ""),
                "priority=" + c.getProperty("request.priority", ""));
    }

    @Override
    protected void takeDown() {
        System.out.println("[" + getLocalName() + "] terminating");
    }
}
