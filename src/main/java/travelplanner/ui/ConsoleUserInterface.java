package travelplanner.ui;

import travelplanner.model.TransportMode;
import travelplanner.model.TripRequest;
import travelplanner.model.TripRequestCsv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Command-line UI. Reading the keyboard blocks, so it runs on its own thread:
 * the agent thread must stay free to receive messages.
 */
public class ConsoleUserInterface implements UserInterface {

    private static final String SAMPLES_RESOURCE = "/data/requests.csv";

    private final int replyTimeoutSec;
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    private final PrintStream out = System.out;

    /** Released when a final reply (result or error) arrives, so the menu waits for it. */
    private final Semaphore finalReply = new Semaphore(0);

    private volatile boolean running;
    private Listener listener;
    private List<TripRequest> samples;
    private int autoIdCounter;

    public ConsoleUserInterface(int replyTimeoutSec) {
        this.replyTimeoutSec = replyTimeoutSec;
    }

    // ---- UserInterface ---------------------------------------------------------------------

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        this.running = true;
        Thread thread = new Thread(this::run, "console-ui");
        thread.setDaemon(true); // never keeps the JVM alive on its own
        thread.start();
    }

    @Override
    public void showStatus(String requestId, String message) {
        print("  [" + requestId + "] " + message);
    }

    @Override
    public void showResult(String requestId, String result) {
        print(box("Result for " + requestId, result));
        finalReply.release();
    }

    @Override
    public void showError(String requestId, String error) {
        print(box("Request " + requestId + " failed", error));
        finalReply.release();
    }

    @Override
    public void close() {
        running = false;
    }

    // ---- main loop -------------------------------------------------------------------------

    private void run() {
        print("");
        print("=== Euro Travel Planner ===");
        try {
            while (running) {
                print("");
                print("1) Enter a new request");
                print("2) Paste a CSV line");
                print("3) Send a sample request (" + SAMPLES_RESOURCE.substring(1) + ")");
                print("q) Quit");
                String choice = prompt("> ");
                switch (choice.trim().toLowerCase()) {
                    case "1" -> enterRequest();
                    case "2" -> pasteCsvLine();
                    case "3" -> chooseSample();
                    case "q", "quit", "exit" -> running = false;
                    case "" -> { }
                    default -> print("Unknown option: " + choice);
                }
            }
        } catch (EndOfInput e) {
            // stdin closed (Ctrl+D / Ctrl+Z): treat as quit
        }
        print("Bye.");
        listener.onQuit();
    }

    // ---- menu actions ----------------------------------------------------------------------

    private void enterRequest() {
        print("Enter the trip details (decimals may use . or ,).");

        String id = ask("request_id", "Enter = automatic",
                s -> s.isBlank() ? nextAutoId() : TripRequest.checkText("request_id", s));
        String origin = ask("origin", "e.g. Krakow", s -> TripRequest.checkText("origin", s));
        String destination = ask("destination", "e.g. Warsaw", s -> {
            String d = TripRequest.checkText("destination", s);
            if (d.equalsIgnoreCase(origin)) {
                throw new IllegalArgumentException("destination must differ from origin");
            }
            return d;
        });
        double maxTransport = ask("max_transport_eur", "EUR, e.g. 200", s -> positive("max_transport_eur", s));
        double maxHotel = ask("max_hotel_per_night_eur", "EUR, e.g. 150",
                s -> positive("max_hotel_per_night_eur", s));
        int nights = ask("nights", "e.g. 5", s -> positiveInt("nights", s));
        TransportMode mode = ask("preferred_mode", "ANY/TRAIN/BUS/PLANE, Enter = ANY",
                s -> s.isBlank() ? TransportMode.ANY : TransportMode.parse(s));
        // Typed in hours for convenience; the request itself stores minutes.
        int maxDurationMin = ask("max transport time per leg", "hours, e.g. 4.5",
                s -> hoursToMinutes(positive("max transport time", s)));
        double minRating = ask("min_hotel_rating", "0-10, e.g. 7.5", s -> {
            double v = TripRequestCsv.parseDouble("min_hotel_rating", s);
            TripRequest.checkRating("min_hotel_rating", v);
            return v;
        });
        double maxDistance = ask("max_center_distance_km", "km, e.g. 5",
                s -> positive("max_center_distance_km", s));
        boolean negotiation = ask("negotiation_enabled", "yes/no, Enter = yes",
                s -> s.isBlank() || TripRequestCsv.parseYesNo("negotiation_enabled", s));

        TripRequest request = new TripRequest(id, origin, destination, maxTransport, maxHotel, nights,
                mode, maxDurationMin, minRating, maxDistance, negotiation);

        print("");
        print(request.describe());
        String confirm = prompt("Send this request? [Y/n] ");
        if (confirm.isBlank() || confirm.trim().toLowerCase().startsWith("y")) {
            submit(request);
        } else {
            print("Cancelled.");
        }
    }

    private void pasteCsvLine() {
        print("Paste one line in this format:");
        print(TripRequestCsv.HEADER);
        String line = prompt("> ");
        if (line.isBlank()) {
            return;
        }
        try {
            TripRequest request = TripRequestCsv.fromRow(line.trim());
            print(request.describe());
            submit(request);
        } catch (IllegalArgumentException e) {
            print("  ! Invalid line: " + e.getMessage());
        }
    }

    private void chooseSample() {
        List<TripRequest> list = loadSamples();
        if (list.isEmpty()) {
            print("No sample requests found.");
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            TripRequest r = list.get(i);
            print(String.format("%2d) %s  %s -> %s, %d night(s), %s", i + 1, r.requestId(),
                    r.origin(), r.destination(), r.nights(), r.preferredMode()));
        }
        print(" a) Send all");
        String choice = prompt("Choose: ").trim();
        if (choice.equalsIgnoreCase("a")) {
            list.forEach(this::submit);
            return;
        }
        try {
            int index = Integer.parseInt(choice) - 1;
            submit(list.get(index));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            print("Unknown choice: " + choice);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Hands the request to the agent, then waits (up to the timeout) for the final reply. */
    private void submit(TripRequest request) {
        finalReply.drainPermits();
        listener.onSubmit(request);
        print("Sent " + request.requestId() + " to the planner, waiting for a reply...");
        try {
            if (!finalReply.tryAcquire(replyTimeoutSec, TimeUnit.SECONDS)) {
                print("No reply after " + replyTimeoutSec + " s. It will be shown here when it arrives.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<TripRequest> loadSamples() {
        if (samples == null) {
            try (InputStream stream = getClass().getResourceAsStream(SAMPLES_RESOURCE)) {
                if (stream == null) {
                    samples = List.of();
                } else {
                    samples = TripRequestCsv.readAll(
                            new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
                }
            } catch (IOException | IllegalArgumentException e) {
                print("  ! Could not read " + SAMPLES_RESOURCE + ": " + e.getMessage());
                samples = List.of();
            }
        }
        return samples;
    }

    /** Asks until the parser accepts the input; the parser's error message is shown otherwise. */
    private <T> T ask(String field, String hint, Function<String, T> parser) {
        while (true) {
            String line = prompt("  " + field + " (" + hint + "): ");
            try {
                return parser.apply(line);
            } catch (IllegalArgumentException e) {
                print("    ! " + e.getMessage());
            }
        }
    }

    private static double positive(String field, String text) {
        double v = TripRequestCsv.parseDouble(field, text);
        TripRequest.checkPositive(field, v);
        return v;
    }

    private static int positiveInt(String field, String text) {
        int v = TripRequestCsv.parseInt(field, text);
        TripRequest.checkPositive(field, v);
        return v;
    }

    /** 4.5 h -> 270 min, rounded to the nearest minute. */
    private static int hoursToMinutes(double hours) {
        int minutes = (int) Math.round(hours * 60);
        if (minutes < 1) {
            throw new IllegalArgumentException("max transport time must be at least 1 minute");
        }
        return minutes;
    }

    private String nextAutoId() {
        return String.format("U%04d", ++autoIdCounter);
    }

    private String prompt(String text) {
        synchronized (out) {
            out.print(text);
            out.flush();
        }
        try {
            String line = in.readLine();
            if (line == null) {
                throw new EndOfInput();
            }
            return line;
        } catch (IOException e) {
            throw new EndOfInput();
        }
    }

    private void print(String text) {
        synchronized (out) {
            out.println(text);
        }
    }

    private static String box(String title, String body) {
        String line = "-".repeat(Math.max(40, title.length() + 8));
        return line + System.lineSeparator() + "  " + title + System.lineSeparator() + line
                + System.lineSeparator() + body + System.lineSeparator() + line;
    }

    /** Thrown when the input stream is closed, to leave the menu loop. */
    private static final class EndOfInput extends RuntimeException {
    }
}