package travelplanner.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/**
 * Coordinator and buyer. For now it only answers trip REQUESTs with an INFORM,
 * which is the minimum demo for the 1st checkpoint.
 */
public class PlannerAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println("[" + getLocalName() + "] ready");

        addBehaviour(new CyclicBehaviour(this) {
            private final MessageTemplate template =
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST);

            @Override
            public void action() {
                ACLMessage msg = myAgent.receive(template);
                if (msg == null) {
                    block(); // sleep until the next message arrives
                    return;
                }
                System.out.println("[" + getLocalName() + "] REQUEST from "
                        + msg.getSender().getLocalName() + ": " + msg.getContent());

                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent("Trip request accepted for planning");
                myAgent.send(reply);
            }
        });
    }

    @Override
    protected void takeDown() {
        System.out.println("[" + getLocalName() + "] terminating");
    }
}
