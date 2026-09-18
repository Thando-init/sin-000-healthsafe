package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;



/**
 * Calculates a simple on-call recommendation from two REST dependencies.
 *
 * The service validates the ward through ward-service, reads the emergency
 * level from alert-level-service, and publishes the result as a topic event.</p>
 */
public class StaffingServiceApp {

    static final int PORT = 7033;

    private StaffingServiceApp() {
        // Application class: do not instantiate.
    }

    public static void main(String[] args) {
        createApp().start(PORT);
        // TODO (Provides on-call schedules for doctors based on ward and status.)
        // Add domain endpoints for staffing-service here.
    }

    /** Creates the staffing endpoint. */
    static Javalin createApp() {
        Javalin app = Javalin.create(config ->
                config.jsonMapper(new JavalinJackson()));

        app.get("/health", context -> context.result("OK"));
        app.get("/staffing/{wardId}", context -> handleStaffingRequest(context));

        return app;
    }

    /** Performs the two synchronous dependency calls and maps failures to 503. */
    private static void handleStaffingRequest(io.javalin.http.Context context) {
        try {
            String wardId = context.pathParam("wardId");
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> wardResponse = get(
                    client,
                    System.getProperty("healthsafe.ward", "http://localhost:7031")
                            + "/wards/" + wardId
            );
            if (wardResponse.statusCode() == 404) {
                context.status(404).json(Map.of("error", "Ward not found"));
                return;
            }
            if (wardResponse.statusCode() != 200) {
                throw new java.lang.IllegalStateException("Ward service unavailable");
            }

            HttpResponse<String> alertResponse = get(
                    client,
                    System.getProperty("healthsafe.alert", "http://localhost:7032")
                            + "/alert-level"
            );
            if (alertResponse.statusCode() != 200) {
                throw new java.lang.IllegalStateException("Alert service unavailable");
            }

            int alertLevel = Integer.parseInt(alertResponse.body().replaceAll("\\D+", ""));
            Map<String, Object> event = buildStaffingEvent(wardId, alertLevel);
            publish(event);
            context.json(event);
        } catch (Exception exception) {
            context.status(503).json(Map.of("error", "A dependency is unavailable"));
        }
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Applies the prototype staffing rule: one base person plus one per two alert levels. */
    static Map<String, Object> buildStaffingEvent(String wardId, int alertLevel) {
        int recommendedStaff = Math.max(1, 1 + (alertLevel / 2));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("wardId", wardId.toUpperCase(Locale.ROOT));
        event.put("alertLevel", alertLevel);
        event.put("recommendedStaff", recommendedStaff);
        return event;
    }

    /** Publishes a non-persistent broadcast event; the REST response remains authoritative. */
    static void publish(Map<String, Object> event) {
        ConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        try (Connection connection = factory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createTopic(MqConfig.TOPIC));
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            producer.send(session.createTextMessage(new ObjectMapper().writeValueAsString(event)));
        } catch (Exception exception) {
            // Messaging is best-effort for an informational topic event.
            System.err.println("Staffing event not published: " + exception.getMessage());
        }
    }

}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
