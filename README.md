# SentimentEngine

An event-driven sentiment analysis platform that ingests events from external sources, automatically generates scoring rules using generative AI, evaluates each event's sentiment impact, and provides multi-resolution time-series analytics.

## What It Does

- Ingests events from external data sources through an abstraction layer (`EventDataSource` interface). Any data source can be plugged in, currently supports GitHub Archive events.
- Automatically generates sentiment rules using Generative AI (OpenAI or Google Gemini) when a new event type is first registered.
- Evaluates each event's sentiment by scoring it in the range [-1.0, 1.0] based on the generated rule.
- Detects absence of events and generates absence-type events when no activity is detected globally or per event type (configurable).
- Provides time-series analytics with aggregation at MINUTE, HOUR, DAY, WEEK, and MONTH resolutions, with a detailed view of all events within any aggregated point.
- Reevaluates pending events when rules become available after initial processing.

## End-to-End Flow

1. Events are consumed from an external source and sent to a JMS queue (Apache Artemis) for buffered, parallel processing.
2. The `EventProcessor` picks up each event and:
   - Checks for absence gaps (global and per event type)
   - Registers the event type if new
   - Finds or generates a sentiment rule via AI
   - Evaluates the event's sentiment score
   - Computes time buckets and persists the processed event
3. Scheduled jobs handle rule generation for missed types, absence detection, and reevaluation of pending events.
4. The analytics API serves aggregated time-series data at any resolution, with the ability to view individual events within each bucket.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 4.1 |
| Language | Java 25 |
| Database | MySQL |
| Migrations | Flyway |
| Message Broker | Apache ActiveMQ Artemis (JMS) |
| AI Providers | OpenAI API, Google Gemini API |
| Monitoring | Prometheus + Grafana + Micrometer |
| API Docs | OpenAPI / Swagger UI |
| Build | Maven |

## Setup

### 1. Database

```sql
CREATE DATABASE sentiment_engine;
```

Flyway will run migrations automatically on startup.

### 2. Configuration

Edit `src/main/resources/application.properties` or create `application-local.properties`:

```properties
# Database
spring.datasource.username=root
spring.datasource.password=yourpassword

# Artemis
spring.artemis.user=admin
spring.artemis.password=admin

# AI Provider (choose one: openai or gemini)
genai.provider=openai
genai.openai.api-key=sk-...

# Data source paths (for GitHub Archive file loading)
events.datasource.gh-archive.file.path=/path/to/file.json.gz
events.datasource.gh-archive.directory.path=/path/to/directory/
```

### 3. Run with local profile

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 4. Monitoring (optional)

```bash
docker-compose up -d
```

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

## UI & API Docs

Swagger UI is available at: http://localhost:8080/api/swagger-ui/index.html


## Author

Kristina Mijajlevska

Faculty of Computer Science and Engineering (FINKI), Ss. Cyril and Methodius University, Skopje