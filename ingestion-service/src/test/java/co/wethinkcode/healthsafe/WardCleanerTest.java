package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the data-cleaning rules used by the ingestion service. */
class WardCleanerTest {

    @Test
    void cleansWhitespaceCanonicalisesDepartmentsAndMergesDuplicates() throws Exception {
        String csv = "ward_id, Wing ,department,beds_available\n"
                + "W-05, East  Wing ,Paediatrics,5\n"
                + "w-05,east wing,PAEDIATRICS,five\n"
                + "W-08,,Oncology,4\n"
                + "W-04,North Wing,Oncology,-1\n";

        List<WardRecord> result = WardCleaner.load(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(3, result.size());
        assertEquals("W-05", result.get(0).wardId());
        assertEquals("East Wing", result.get(0).wing());
        assertEquals("Paediatrics", result.get(0).department());
        assertEquals(5, result.get(0).bedsAvailable());
        assertNull(result.get(2).bedsAvailable());
        assertTrue(result.get(2).dataQualityNote().contains("invalid"));
    }

    @Test
    void cleansTheBundledLegacyFileWithoutCrashing() throws Exception {
        List<WardRecord> result = WardCleaner.load(
                IngestionServiceApp.class.getResourceAsStream("/wards-outdated.csv")
        );

        // Nineteen source rows become seventeen records after the W-05 duplicate is merged.
        assertEquals(17, result.size());
    }
}
