package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST service that owns the hospital emergency level.
 *
 * <p>The level is deliberately kept in memory because persistence is outside the
 * scope of this educational prototype.</p>
 */
public final class AlertLevelServiceApp {

    static final int PORT = 7032;
    private static final int MIN_LEVEL = 0;
    private static final int MAX_LEVEL = 8;

    private AlertLevelServiceApp() {
        // Application class: do not instantiate.
    }

    /** Starts the service with a normal emergency level of zero. */
    public static void main(String[] args) {
        createApp(new AtomicInteger(MIN_LEVEL)).start(PORT);
    }

    /** Builds the HTTP application around an injectable level store. */
    static Javalin createApp(AtomicInteger level) {
        Javalin app = Javalin.create(config ->
                config.jsonMapper(new JavalinJackson()));

        app.get("/health", context -> context.result("OK"));
        app.get("/alert-level", context ->
                context.json(Map.of("level", level.get())));

        app.put("/alert-level", context -> {
            try {
                int requestedLevel = parseLevel(context.body());
                if (requestedLevel < MIN_LEVEL || requestedLevel > MAX_LEVEL) {
                    throw new IllegalArgumentException("level must be between 0 and 8");
                }
                level.set(requestedLevel);
                context.json(Map.of("level", requestedLevel));
            } catch (Exception exception) {
                context.status(400).json(Map.of(
                        "error", "level must be an integer between 0 and 8"
                ));
            }
        });

        return app;
    }

    /** Parses the small JSON payload without adding a second domain model.*/
    private static int parseLevel(String body) {
        String digitsOnly = body.replaceAll("\\D+", "");
        if (digitsOnly.isBlank()) {
            throw new IllegalArgumentException("level is missing");
        }
        return Integer.parseInt(digitsOnly);
    }
}
