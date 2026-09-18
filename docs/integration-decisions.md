# REST first, then messaging where it adds value

## Decisions

I use HTTP/JSON for request-response operations. Staffing needs an immediate answer after validating a ward and reading the current emergency level, so a synchronous call is easier to understand and gives the caller a direct status code when a dependency is unavailable.

I use the `staffing-events-topic` for staffing updates. A topic supports broadcast and does not require the staffing service to know every future consumer. The event is informational, so non-persistent delivery is acceptable for this prototype.

I use the persistent `equipment-failure-queue` for equipment failures. These alerts are commands that must be processed by one consumer, not broadcast to every subscriber. The consumer uses client acknowledgement and acknowledges only after recording the alert.

I keep each service as an independent Maven project because that is how the supplied exercise scaffold is structured. This duplicates the small MQ configuration class, but makes each service independently buildable and runnable.

For duplicate ward rows, the normalised ID is the identity key. The first reliable non-empty value wins, and later data-quality notes are retained. This is deterministic and transparent for a small legacy export, although a production system would require a source-of-truth and reconciliation workflow.

## Consequences

The system is easy to run one service at a time and demonstrates both REST and JMS. It does not provide production-grade persistence, authentication, retries, tracing, or a clinical workflow. Those omissions are deliberate scope boundaries for the attempted educational project.
