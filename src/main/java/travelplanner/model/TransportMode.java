package travelplanner.model;

import java.util.Arrays;
import java.util.Locale;

/** Means of transport the traveller prefers. ANY lets the providers choose. */
public enum TransportMode {
    ANY, TRAIN, PLANE;

    /** Case-insensitive parse with a readable error message. */
    public static TransportMode parse(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "preferred_mode must be one of " + Arrays.toString(values()) + ", got '" + text + "'");
        }
    }
}
