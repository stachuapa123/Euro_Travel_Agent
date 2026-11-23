package travelplanner.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

/**
 * Placeholder recommender: picks the cheapest transport and the cheapest accommodation.
 * REQUEST content: one proposal per line, "agentName;type;price;description"
 * INFORM content:  "transportAgentName;accommodationAgentName"
 */
public class RecommendationAgent extends Agent {

    @Override
    protected void setup() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("recommendation");
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
                if (msg.getPerformative() != ACLMessage.REQUEST) {
                    return;
                }
                String bestTransport = "", bestHotel = "";
                double transportPrice = Double.MAX_VALUE, hotelPrice = Double.MAX_VALUE;
                for (String line : msg.getContent().split("\n")) {
                    String[] f = line.split(";"); // name;type;price;description
                    double price = Double.parseDouble(f[2]);
                    if (f[1].equals("transport") && price < transportPrice) {
                        transportPrice = price;
                        bestTransport = f[0];
                    } else if (f[1].equals("accommodation") && price < hotelPrice) {
                        hotelPrice = price;
                        bestHotel = f[0];
                    }
                }
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent(bestTransport + ";" + bestHotel);
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