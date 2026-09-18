package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class IngestionServiceApp {

    static final int PORT = 7030;

    private IngestionServiceApp() {
        // Application class: do not instantiate.
    }

    public static void main(String[] args) throws Exception{
        createApp(loadBundledRecords()).start(PORT);
        // TODO: read and clean src/main/resources/wards-outdated.csv (wards, wings, specialist departments data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume. => Done in WardCleaner
    }

    /** Loads the CSV packaged in src/main/resources. */
    static List<WardRecord> loadBundledRecords() throws Exception {
        try (InputStream input = IngestionServiceApp.class
                .getResourceAsStream("/wards-outdated.csv")) {
            if (input == null) {
                throw new IllegalStateException("wards-outdated.csv is missing");
            }
            return WardCleaner.load(input);
        }
    }

    /**
     * Builds the HTTP application separately from main so it can be tested without
     * coupling tests to the bundled file or a fixed port.
     */
    static Javalin createApp(List<WardRecord> wards) {
        Javalin app = Javalin.create(config ->
                config.jsonMapper(new JavalinJackson()));

        app.get("/health", context -> context.result("OK"));
        app.get("/wards", context -> context.json(wards));
        app.get("/wards/{id}", context -> wards.stream()
                .filter(ward -> ward.wardId().equalsIgnoreCase(context.pathParam("id")))
                .findFirst()
                .ifPresentOrElse(
                        context::json,
                        () -> context.status(404).json(Map.of("error", "Ward not found"))
                ));

        return app;
    }


}
