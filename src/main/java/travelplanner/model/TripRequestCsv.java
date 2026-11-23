package travelplanner.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converts TripRequest to and from CSV.
 *
 * The ACL message content sent to the Planner is the header line followed by one data line,
 * so the receiver can check the column order:
 *
 *   request_id,origin,destination,...
 *   R0001,Krakow,Warsaw,243.87,199.25,5,ANY,258,7.8,4.9,no
 */
public final class TripRequestCsv {

    public static final String HEADER = "request_id,origin,destination,max_transport_eur,"
            + "max_hotel_per_night_eur,nights,preferred_mode,max_transport_duration_min,"
            + "min_hotel_rating,max_center_distance_km,negotiation_enabled";

    /** Values for ACLMessage.setLanguage / setOntology, so receivers know how to read the content. */
    public static final String LANGUAGE = "csv";
    public static final String ONTOLOGY = "trip-request";

    private static final int COLUMNS = HEADER.split(",").length;

    private TripRequestCsv() {
    }

    // ---- writing ---------------------------------------------------------------------------

    public static String toRow(TripRequest r) {
        return String.join(",",
                r.requestId(),
                r.origin(),
                r.destination(),
                number(r.maxTransportEur()),
                number(r.maxHotelPerNightEur()),
                Integer.toString(r.nights()),
                r.preferredMode().name(),
                Integer.toString(r.maxTransportDurationMin()),
                number(r.minHotelRating()),
                number(r.maxCenterDistanceKm()),
                r.negotiationEnabled() ? "yes" : "no");
    }

    /** Header + one row: the content of a trip-request ACL message. */
    public static String toMessageContent(TripRequest r) {
        return HEADER + "\n" + toRow(r);
    }

    // ---- reading ---------------------------------------------------------------------------

    public static TripRequest fromRow(String row) {
        String[] f = row.split(",", -1);
        if (f.length != COLUMNS) {
            throw new IllegalArgumentException("expected " + COLUMNS + " columns, got " + f.length + ": " + row);
        }
        return new TripRequest(
                f[0],
                f[1],
                f[2],
                parseDouble("max_transport_eur", f[3]),
                parseDouble("max_hotel_per_night_eur", f[4]),
                parseInt("nights", f[5]),
                TransportMode.parse(f[6]),
                parseInt("max_transport_duration_min", f[7]),
                parseDouble("min_hotel_rating", f[8]),
                parseDouble("max_center_distance_km", f[9]),
                parseYesNo("negotiation_enabled", f[10]));
    }

    /** Reads message content: an optional header line followed by exactly one data line. */
    public static TripRequest fromMessageContent(String content) {
        if (content == null) {
            throw new IllegalArgumentException("empty message content");
        }
        List<String> lines = content.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
        if (!lines.isEmpty() && lines.get(0).startsWith("request_id")) {
            if (!lines.get(0).equals(HEADER)) {
                throw new IllegalArgumentException("unexpected CSV header: " + lines.get(0));
            }
            lines = lines.subList(1, lines.size());
        }
        if (lines.size() != 1) {
            throw new IllegalArgumentException("expected exactly one request row, got " + lines.size());
        }
        return fromRow(lines.get(0));
    }

    /** Reads a whole CSV file (header first). Lines starting with # or // are skipped. */
    public static List<TripRequest> readAll(BufferedReader reader) throws IOException {
        List<TripRequest> requests = new ArrayList<>();
        String line;
        int lineNo = 0;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//") || line.equals(HEADER)) {
                continue;
            }
            try {
                requests.add(fromRow(line));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("line " + lineNo + ": " + e.getMessage(), e);
            }
        }
        return requests;
    }

    // ---- field parsers (also used for typed console input) ---------------------------------

    /** Accepts both "243.87" and "243,87" (Polish decimal comma) when typed by hand. */
    public static double parseDouble(String field, String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number, got '" + text + "'");
        }
    }

    public static int parseInt(String field, String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a whole number, got '" + text + "'");
        }
    }

    public static boolean parseYesNo(String field, String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "yes", "y", "true", "1" -> true;
            case "no", "n", "false", "0" -> false;
            default -> throw new IllegalArgumentException(field + " must be yes or no, got '" + text + "'");
        };
    }

    /** 120.0 -> "120", 243.87 -> "243.87", always with a dot regardless of system locale. */
    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}