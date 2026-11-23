package travelplanner.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import travelplanner.model.TripRequest;
import travelplanner.model.TripRequestCsv;

/**
 * Coordinator and buyer. For now it parses the CSV trip request and confirms it with an INFORM;
 * search, recommendation and negotiation come later.
 */
public class PlannerAgent extends Agent {

    @Override
    protected void setup() {
        addBehaviour(new CyclicBehaviour(this) {
            private final MessageTemplate template =
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST);

            @Override
            public void action() {
                ACLMessage msg = myAgent.receive(template);
                if (msg == null) {
                    block();
                    return;
                }
                ACLMessage reply = msg.createReply();
                try {
                    if (!TripRequestCsv.ONTOLOGY.equals(msg.getOntology())) {
                        throw new IllegalArgumentException("unsupported ontology: " + msg.getOntology());
                    }
                    TripRequest request = TripRequestCsv.fromMessageContent(msg.getContent());
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("Planner received:" + System.lineSeparator()
                            + request.describe() + System.lineSeparator()
                            + "(planning not implemented yet)");
                } catch (IllegalArgumentException e) {
                    reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                    reply.setContent(e.getMessage());
                }
                myAgent.send(reply);
            }
        });
    }
}