package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.json.JavalinJackson;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Serves ward lookups and owns the equipment-failure producer.
 *
 * <p>At startup, the service obtains its catalogue from the ingestion service.
 * It also subscribes to staffing events so this service demonstrates topic
 * consumption without making staffing part of its REST request path.</p>
 */
public class WardServiceApp {

    static final int PORT = 7031;

    private WardServiceApp() {
        // Application class: do not instantiate.
    }


    /** Loads wards, starts the topic listener, and binds the REST server. */
    public static void main(String[] args) {
        List<Map<String, Object>> wards = loadWards();
        startTopicListener();
        createApp(wards).start(PORT);
        // TODO (Provides lists of wards and departments.)
        // Add domain endpoints for ward-service here.
    }

    /** Fetches the cleaned catalogue from ingestion, returning an empty list on failure. */
    static List<Map<String, Object>> loadWards() {
        String ingestionUrl = System.getProperty(
                "healthsafe.ingestion", "http://localhost:7030"
        );
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(ingestionUrl + "/wards")
        ).GET().build();

        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    request, HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() == 200) {
                return new ArrayList<>(new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(response.body(), List.class));
            }
        } catch (Exception exception) {
            System.err.println("Unable to load ward catalogue: " + exception.getMessage());
        }
        return new CopyOnWriteArrayList<>();
    }

    /** Creates the ward REST endpoints for independent smoke testing. */
    static Javalin createApp(List<Map<String, Object>> wards) {
        Javalin app = Javalin.create(config ->
                config.jsonMapper(new JavalinJackson()));

        app.get("/health", context -> context.result("OK"));
        app.get("/wards", context -> context.json(wards));
        app.get("/wards/{id}", context -> wards.stream()
                .filter(ward -> String.valueOf(ward.get("wardId"))
                        .equalsIgnoreCase(context.pathParam("id")))
                .findFirst()
                .ifPresentOrElse(
                        context::json,
                        () -> context.status(404).json(Map.of("error", "Ward not found"))
                ));
        app.post("/equipment-failures", context -> {
            publishFailure(context.body());
            context.status(202).json(Map.of("queued", true));
        });

        return app;
    }

    /** Publishes a persistent queue message so failures survive consumer downtime. */
    static void publishFailure(String body) {
        ConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        try (Connection connection = factory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createQueue(MqConfig.QUEUE));
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(session.createTextMessage(body));
        } catch (JMSException exception) {
            throw new java.lang.IllegalStateException(
                    "Unable to publish equipment failure", exception
            );
        }
    }

    /** Starts a daemon listener for broadcast staffing events. */
    static void startTopicListener() {
        Thread listener = new Thread(() -> {
            try {
                ConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
                Connection connection = factory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(
                        session.createTopic(MqConfig.TOPIC)
                );
                consumer.setMessageListener(message -> {
                    try {
                        if (message instanceof TextMessage textMessage) {
                            System.out.println("Staffing event received: " + textMessage.getText());
                        }
                    } catch (JMSException exception) {
                        System.err.println("Unable to read staffing event: " + exception.getMessage());
                    }
                });
                connection.start();
            } catch (Exception exception) {
                System.err.println("Staffing topic unavailable: " + exception.getMessage());
            }
        }, "staffing-topic-listener");

        listener.setDaemon(true);
        listener.start();
    }


}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// MQ TODO: publishes to ActiveMQ queue MqConfig.QUEUE when it detects an equipment failure on one of its wards.
