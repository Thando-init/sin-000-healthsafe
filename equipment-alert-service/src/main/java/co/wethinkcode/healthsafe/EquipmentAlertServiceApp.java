package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Consumes critical equipment-failure alerts from a persistent ActiveMQ queue.
 *
 * Client acknowledgement is used deliberately: the message is acknowledged
 * only after its body has been added to the in-memory alert list.
 */

public class EquipmentAlertServiceApp {

    static final int PORT = 7034;

    private EquipmentAlertServiceApp() {
        // Application class: do not instantiate.
    }

    /** Starts the consumer and exposes the recorded alerts over HTTP. */
    public static void main(String[] args) {
        List<String> alerts = new CopyOnWriteArrayList<>();
        startConsumer(alerts);
        createApp(alerts).start(PORT);
        // TODO (Uses a Queue to guarantee delivery of critical medical equipment failure alerts.)
        // Mechanism: ActiveMQ Queue (guaranteed delivery)
    }

    /** Creates health and inspection endpoints around the received alert list. */
    static Javalin createApp(List<String> alerts) {
        Javalin app = Javalin.create(config ->
                config.jsonMapper(new JavalinJackson()));
        app.get("/health", context -> context.result("OK"));
        app.get("/alerts", context -> context.json(alerts));
        return app;
    }

    /** Starts a daemon JMS consumer using client acknowledgement mode. */
    static void startConsumer(List<String> alerts) {
        Thread consumerThread = new Thread(() -> {
            try {
                ConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
                Connection connection = factory.createConnection();
                Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(
                        session.createQueue(MqConfig.QUEUE)
                );
                consumer.setMessageListener(message -> {
                    try {
                        if (message instanceof TextMessage textMessage) {
                            alerts.add(textMessage.getText());
                        }
                        // Acknowledge only after the message has been recorded.
                        message.acknowledge();
                    } catch (Exception exception) {
                        System.err.println(
                                "Alert acknowledgement failed: " + exception.getMessage()
                        );
                    }
                });
                connection.start();
            } catch (Exception exception) {
                System.err.println("Equipment queue unavailable: " + exception.getMessage());
            }
        }, "equipment-alert-consumer");

        consumerThread.setDaemon(true);
        consumerThread.start();
    }

}

// MQ TODO: consumes ActiveMQ queue MqConfig.QUEUE at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// Producer: ward-service publishes here when it detects an equipment failure on one of its wards.
