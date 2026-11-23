package travelplanner.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

/**
 * Placeholder Transport / Accommodation agent: always offers the same thing and always confirms.
 * Arguments (from config): service type, price in EUR, description. Example:
 *   transport1:travelplanner.agents.DummyProviderAgent(transport,120,Train 4h 30min)
 *
 * PROPOSE content: type;price;description
 */
public class DummyProviderAgent extends Agent {

    @Override
    protected void setup() {
        Object[] args = getArguments(); // [0] is the config, then the values from config.properties
        String type = (String) args[1];
        String offer = type + ";" + args[2] + ";" + args[3];

        // Register in the yellow pages so the Planner can find us
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(type);
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {
                    block();
                    return;
                }
                ACLMessage reply = msg.createReply();
                if (msg.getPerformative() == ACLMessage.CFP) {
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(offer);
                } else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("Booking confirmed");
                } else {
                    return; // REJECT_PROPOSAL or anything else: nothing to do
                }
                send(reply);
            }
        });
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            // platform is shutting down
        }
    }
}