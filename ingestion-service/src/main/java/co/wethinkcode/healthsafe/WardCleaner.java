package co.wethinkcode.healthsafe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts the deliberately inconsistent legacy CSV export into safe domain records.
 *
 * <p>The cleaner is intentionally forgiving: a malformed row is represented with a
 * data-quality note instead of stopping the entire import. Duplicate ward IDs are
 * merged in insertion order, with the first reliable value retained.</p>
 */
public final class WardCleaner {

    private static final Set<String> MISSING_VALUES = Set.of(
            "", "n/a", "tbd", "unknown", "-", "nan", "null"
    );
    private static final Pattern REPEATED_WHITESPACE = Pattern.compile("\\s+");

    private WardCleaner() {
        // Utility class: do not create instances.
    }

    /**
     * Reads a header row followed by ward records from a CSV input stream.
     *
     * @param input CSV data encoded as UTF-8
     * @return cleaned records in their first-seen order
     * @throws IOException when the input cannot be read
     */
    public static List<WardRecord> load(InputStream input) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            // The supplied export has a header. It is consumed rather than treated as data.
            if (reader.readLine() == null) {
                return List.of();
            }

            Map<String, WardRecord> recordsById = new LinkedHashMap<>();
            String line;
            int rowNumber = 1;

            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }

                String[] columns = line.split(",", -1);
                if (columns.length < 4) {
                    // A short row cannot be interpreted safely, so it is skipped.
                    continue;
                }

                String wardId = cleanId(columns[0]);
                if (wardId.isBlank()) {
                    continue;
                }

                String wing = cleanText(columns[1]);
                String department = canonicalDepartment(columns[2]);
                ParsedBeds beds = parseBeds(columns[3]);
                WardRecord candidate = new WardRecord(
                        wardId,
                        wing,
                        department,
                        beds.value(),
                        beds.note(rowNumber)
                );

                recordsById.merge(wardId, candidate, WardCleaner::mergeDuplicate);
            }

            return List.copyOf(recordsById.values());
        }
    }

    /**
     * Merges a later row into the first row for the same canonical ward ID.
     * Reliable existing values win; later quality notes are preserved.
     */
    private static WardRecord mergeDuplicate(WardRecord original, WardRecord duplicate) {
        Integer beds = original.bedsAvailable() != null
                ? original.bedsAvailable()
                : duplicate.bedsAvailable();

        return new WardRecord(
                original.wardId(),
                prefer(original.wing(), duplicate.wing()),
                prefer(original.department(), duplicate.department()),
                beds,
                joinNotes(original.dataQualityNote(), duplicate.dataQualityNote())
        );
    }

    private static String prefer(String original, String fallback) {
        return original == null || original.isBlank() ? fallback : original;
    }

    private static String joinNotes(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank() || first.equals(second)) {
            return first;
        }
        return first + "; " + second;
    }

    /** Normalises identifiers for case-insensitive duplicate detection. */
    static String cleanId(String raw) {
        return cleanText(raw).toUpperCase(Locale.ROOT).replace(" ", "");
    }

    /** Trims a field, collapses internal whitespace, and maps placeholders to empty text. */
    static String cleanText(String raw) {
        String value = raw == null ? "" : raw.trim();
        value = REPEATED_WHITESPACE.matcher(value).replaceAll(" ");
        return isMissing(value) ? "" : value;
    }

    /** Canonicalises department casing and the Pediatrics/Paediatrics spelling variant. */
    static String canonicalDepartment(String raw) {
        String value = cleanText(raw).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "paediatrics", "pediatrics" -> "Paediatrics";
            case "cardiology" -> "Cardiology";
            case "oncology" -> "Oncology";
            case "radiology" -> "Radiology";
            case "maternity" -> "Maternity";
            case "icu" -> "ICU";
            default -> value.isBlank()
                    ? "Unknown"
                    : Character.toUpperCase(value.charAt(0)) + value.substring(1);
        };
    }

    private static boolean isMissing(String value) {
        return MISSING_VALUES.contains(value.toLowerCase(Locale.ROOT));
    }

    /** Parses a bed count while retaining the original text for an audit note. */
    private static ParsedBeds parseBeds(String raw) {
        String value = cleanText(raw);
        if (isMissing(value)) {
            return new ParsedBeds(null, value);
        }

        try {
            int beds = Integer.parseInt(value);
            // A negative or implausibly large count is treated as invalid source data.
            return new ParsedBeds(beds >= 0 && beds <= 100 ? beds : null, value);
        } catch (NumberFormatException ignored) {
            return new ParsedBeds(null, value);
        }
    }

    /** Intermediate value used to create a row-specific data-quality note. */
    private record ParsedBeds(Integer value, String rawValue) {
        String note(int rowNumber) {
            if (value != null) {
                return "";
            }
            if (rawValue == null || rawValue.isBlank()) {
                return "row " + rowNumber + ": beds_available was missing";
            }
            return "row " + rowNumber
                    + ": beds_available was non-numeric or invalid ('"
                    + rawValue.trim() + "')";
        }
    }
}
