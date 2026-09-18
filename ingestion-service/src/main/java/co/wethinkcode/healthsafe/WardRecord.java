package co.wethinkcode.healthsafe;

/**
 * Immutable representation of one cleaned hospital ward record.
 *
 * @param wardId          canonical ward identifier, for example {@code W-05}
 * @param wing            cleaned wing name, or an empty string when unavailable
 * @param department      canonical department name
 * @param bedsAvailable   valid non-negative bed count, or {@code null} when the source was missing/invalid
 * @param dataQualityNote explanation of a source-data problem, when one was found
 */
public record WardRecord(
        String wardId,
        String wing,
        String department,
        Integer bedsAvailable,
        String dataQualityNote
) {
}
