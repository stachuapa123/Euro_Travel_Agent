package travelplanner.model;

/**
 * One trip request from the traveller. Field names match the CSV columns:
 *
 * request_id, origin, destination, max_transport_eur, max_hotel_per_night_eur, nights,
 * preferred_mode, max_transport_duration_min, min_hotel_rating, max_center_distance_km,
 * negotiation_enabled
 *
 * Every value is checked when the object is created, so an invalid request cannot exist.
 */
public record TripRequest(
        String requestId,
        String origin,
        String destination,
        double maxTransportEur,
        double maxHotelPerNightEur,
        int nights,
        TransportMode preferredMode,
        int maxTransportDurationMin,
        double minHotelRating,
        double maxCenterDistanceKm,
        boolean negotiationEnabled) {

    public static final double MAX_RATING = 10.0;

    public TripRequest {
        requestId = checkText("request_id", requestId);
        origin = checkText("origin", origin);
        destination = checkText("destination", destination);
        if (origin.equalsIgnoreCase(destination)) {
            throw new IllegalArgumentException("origin and destination must be different");
        }
        checkPositive("max_transport_eur", maxTransportEur);
        checkPositive("max_hotel_per_night_eur", maxHotelPerNightEur);
        checkPositive("nights", nights);
        if (preferredMode == null) {
            throw new IllegalArgumentException("preferred_mode is required");
        }
        checkPositive("max_transport_duration_min", maxTransportDurationMin);
        checkRating("min_hotel_rating", minHotelRating);
        checkPositive("max_center_distance_km", maxCenterDistanceKm);
    }

    /** Readable multi-line description for the console or a GUI. */
    public String describe() {
        return String.join(System.lineSeparator(),
                "Request " + requestId + ": " + origin + " -> " + destination + ", " + nights + " night(s)",
                "  Transport:     max " + num(maxTransportEur) + " EUR, max " + maxTransportDurationMin
                        + " min, mode " + preferredMode,
                "  Accommodation: max " + num(maxHotelPerNightEur) + " EUR/night, rating >= " + num(minHotelRating)
                        + ", <= " + num(maxCenterDistanceKm) + " km from centre",
                "  Negotiation:   " + (negotiationEnabled ? "yes" : "no"));
    }

    private static String num(double v) {
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    // ---- checks shared with the input parsers --------------------------------------------

    public static String checkText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.contains(",") || trimmed.contains("\n")) {
            throw new IllegalArgumentException(field + " must not contain commas or line breaks");
        }
        return trimmed;
    }

    public static void checkPositive(String field, double value) {
        if (!(value > 0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be greater than 0, got " + value);
        }
    }

    public static void checkRating(String field, double value) {
        if (!(value >= 0 && value <= MAX_RATING)) {
            throw new IllegalArgumentException(field + " must be between 0 and " + MAX_RATING + ", got " + value);
        }
    }
}
