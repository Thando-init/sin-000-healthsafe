package co.wethinkcode.healthsafe.mq;

/**
 * Shared by every producer/consumer service that talks to the "equipment-failure-queue"
 * ActiveMQ queue. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 */
public final class MqConfig {

    /** Broker connection URL; can be overridden with -Dhealthsafe.broker=. */
    public static final String BROKER_URL = System.getProperty(
            "healthsafe.broker", "tcp://localhost:61616"
    );

    /** Broadcast destination for staffing updates. */
    public static final String TOPIC = "staffing-events-topic";

    /** Guaranteed-delivery destination for equipment failures. */
    public static final String QUEUE = "equipment-failure-queue";

    private MqConfig() {
        // Constants class: do not instantiate.
    }

}
