package travelplanner;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Starts the JADE main container and the agents listed in config.properties.
 *
 * Usage:
 *   java -jar target/travel-planner.jar               (uses the bundled config.properties)
 *   java -jar target/travel-planner.jar my.properties (uses a file on disk)
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) throws Exception {
        Properties config = loadConfig(args.length > 0 ? args[0] : null);

        Runtime runtime = Runtime.instance();
        runtime.setCloseVM(true); // exit the JVM when the platform shuts down

        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, config.getProperty("jade.host", "localhost"));
        profile.setParameter(Profile.MAIN_PORT, config.getProperty("jade.port", "1099"));
        profile.setParameter(Profile.GUI, config.getProperty("jade.gui", "false"));

        ContainerController main = runtime.createMainContainer(profile);

        // Agents get the whole config as their first argument, followed by any args from the spec.
        for (AgentSpec spec : parseAgents(config.getProperty("agents", ""))) {
            Object[] agentArgs = new Object[spec.args.size() + 1];
            agentArgs[0] = config;
            for (int i = 0; i < spec.args.size(); i++) {
                agentArgs[i + 1] = spec.args.get(i);
            }
            AgentController agent = main.createNewAgent(spec.name, spec.className, agentArgs);
            agent.start();
        }
    }

    private static Properties loadConfig(String path) throws IOException {
        Properties props = new Properties();
        if (path != null) {
            try (InputStream in = new FileInputStream(path)) {
                props.load(in);
            }
        } else {
            try (InputStream in = Launcher.class.getResourceAsStream("/config.properties")) {
                if (in == null) {
                    throw new IOException("config.properties not found on the classpath");
                }
                props.load(in);
            }
        }
        return props;
    }

    /** Parses "name:Class(a,b);name2:Class2" into agent specs. */
    static List<AgentSpec> parseAgents(String value) {
        List<AgentSpec> specs = new ArrayList<>();
        for (String entry : value.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("Agent entry needs name:Class -> " + entry);
            }
            String name = entry.substring(0, colon).trim();
            String rest = entry.substring(colon + 1).trim();

            List<String> args = new ArrayList<>();
            int open = rest.indexOf('(');
            String className = rest;
            if (open >= 0 && rest.endsWith(")")) {
                className = rest.substring(0, open).trim();
                String inner = rest.substring(open + 1, rest.length() - 1);
                for (String a : inner.split(",")) {
                    if (!a.isBlank()) {
                        args.add(a.trim());
                    }
                }
            }
            specs.add(new AgentSpec(name, className, args));
        }
        return specs;
    }

    record AgentSpec(String name, String className, List<String> args) {
    }
}
