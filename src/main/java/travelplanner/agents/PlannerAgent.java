package travelplanner.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import travelplanner.model.TripRequest;
import travelplanner.model.TripRequestCsv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple Planner. For each trip request (conversation-id = request_id):
 *   User --REQUEST--> Planner --AGREE--> User
 *   Planner --CFP--> all transport + accommodation providers found in the DF
 *   providers --PROPOSE/REFUSE--> Planner
 *   Planner --REQUEST(proposals)--> Recommender --INFORM(chosen)--> Planner
 *   Planner --ACCEPT_PROPOSAL--> chosen, --REJECT_PROPOSAL--> others
 *   chosen --INFORM--> Planner --INFORM(final plan)--> User
 * No negotiation or timeouts yet.
 */
public class PlannerAgent extends Agent {

    /** Progress of one trip request. */
    private static class Job {
        ACLMessage userRequest;
        int waitingFor;                                  // answers still expected
        List<ACLMessage> proposals = new ArrayList<>();
        List<ACLMessage> accepted = new ArrayList<>();
    }

    private final Map<String, Job> jobs = new HashMap<>();

    @Override
    protected void setup() {
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {
                    block();
                    return;
                }
                switch (msg.getPerformative()) {
                    case ACLMessage.REQUEST -> startJob(msg);
                    case ACLMessage.PROPOSE, ACLMessage.REFUSE -> onProviderAnswer(msg);
                    case ACLMessage.INFORM -> {
                        if (msg.getSender().getLocalName().equals(recommender().getLocalName())) {
                            onRecommendation(msg);
                        } else {
                            onConfirmation(msg);
                        }
                    }
                    default -> { }
                }
            }
        });
    }

    // 1. User request -> CFP to every provider
    private void startJob(ACLMessage msg) {
        ACLMessage userReply = msg.createReply();
        TripRequest trip;
        try {
            trip = TripRequestCsv.fromMessageContent(msg.getContent());
        } catch (IllegalArgumentException e) {
            userReply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
            userReply.setContent(e.getMessage());
            send(userReply);
            return;
        }
        List<AID> providers = new ArrayList<>(find("transport"));
        providers.addAll(find("accommodation"));
        if (providers.isEmpty()) {
            userReply.setPerformative(ACLMessage.FAILURE);
            userReply.setContent("No providers registered in the DF");
            send(userReply);
            return;
        }

        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        providers.forEach(cfp::addReceiver);
        cfp.setConversationId(trip.requestId());
        cfp.setContent(msg.getContent());
        send(cfp);

        Job job = new Job();
        job.userRequest = msg;
        job.waitingFor = providers.size();
        jobs.put(trip.requestId(), job);

        userReply.setPerformative(ACLMessage.AGREE);
        userReply.setContent("sent CFP to " + providers.size() + " providers");
        send(userReply);
    }

    // 2. Collect proposals; when all have answered, ask the recommender
    private void onProviderAnswer(ACLMessage msg) {
        Job job = jobs.get(msg.getConversationId());
        if (job == null) {
            return;
        }
        if (msg.getPerformative() == ACLMessage.PROPOSE) {
            job.proposals.add(msg);
        }
        if (--job.waitingFor > 0) {
            return;
        }
        if (job.proposals.isEmpty()) {
            finish(msg.getConversationId(), ACLMessage.FAILURE, "No provider made an offer");
            return;
        }
        StringBuilder content = new StringBuilder();
        for (ACLMessage p : job.proposals) {
            content.append(p.getSender().getLocalName()).append(';').append(p.getContent()).append('\n');
        }
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.addReceiver(recommender());
        request.setConversationId(msg.getConversationId());
        request.setContent(content.toString().trim());
        send(request);
    }

    // 3. Accept the recommended offers, reject the rest
    private void onRecommendation(ACLMessage msg) {
        Job job = jobs.get(msg.getConversationId());
        if (job == null) {
            return;
        }
        List<String> chosen = List.of(msg.getContent().split(";"));
        for (ACLMessage p : job.proposals) {
            ACLMessage reply = p.createReply();
            if (chosen.contains(p.getSender().getLocalName())) {
                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                job.accepted.add(p);
            } else {
                reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
            }
            send(reply);
        }
        job.waitingFor = job.accepted.size();
    }

    // 4. When every accepted provider confirmed, send the plan to the user
    private void onConfirmation(ACLMessage msg) {
        Job job = jobs.get(msg.getConversationId());
        if (job == null || --job.waitingFor > 0) {
            return;
        }
        job.accepted.sort(Comparator.comparing(p -> p.getContent().startsWith("transport") ? 0 : 1));
        StringBuilder plan = new StringBuilder("Selected plan:\n");
        double total = 0;
        for (ACLMessage p : job.accepted) {
            String[] f = p.getContent().split(";"); // type;price;description
            plan.append(String.format("  %-13s %-30s %8s EUR  (%s)%n", f[0], f[2], f[1], p.getSender().getLocalName()));
            total += Double.parseDouble(f[1]);
        }
        plan.append(String.format("  Total: %.2f EUR", total));
        finish(msg.getConversationId(), ACLMessage.INFORM, plan.toString());
    }

    private void finish(String requestId, int performative, String content) {
        Job job = jobs.remove(requestId);
        ACLMessage reply = job.userRequest.createReply();
        reply.setPerformative(performative);
        reply.setContent(content);
        send(reply);
    }

    private AID recommender() {
        List<AID> found = find("recommendation");
        return found.isEmpty() ? new AID("recommender", AID.ISLOCALNAME) : found.get(0);
    }

    /** Yellow-pages search: all agents offering a service of the given type. */
    private List<AID> find(String type) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(type);
        template.addServices(sd);
        List<AID> result = new ArrayList<>();
        try {
            for (DFAgentDescription d : DFService.search(this, template)) {
                result.add(d.getName());
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return result;
    }
}