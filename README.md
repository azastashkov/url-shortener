# URL Shortener

A scalable URL shortener built with Java 21, Spring Boot, PostgreSQL, Redis, and Nginx load balancing. Includes Prometheus/Grafana monitoring and a Python load test client.

## Architecture

- **3 Spring Boot instances** behind an Nginx round-robin load balancer
- **PostgreSQL** for persistent storage with partitioned sequences (no ID collisions)
- **Redis** for caching short-to-long URL mappings
- **Prometheus + Grafana** for monitoring

## Quick Start

```bash
# Start all services
docker compose up -d

# Shorten a URL
curl -X POST "http://localhost/api/v1/data/shorten?longUrl=https://example.com"

# Redirect (use the short URL from above)
curl -v "http://localhost/api/v1/<shortUrl>"

# Run load test
docker compose --profile test up load-client

# View Grafana dashboard
open http://localhost:3000  # admin/admin
```

## Services

| Service    | Port  | Description                    |
|------------|-------|--------------------------------|
| nginx      | 80    | Load balancer                  |
| app-1      | 8081  | URL shortener instance 1       |
| app-2      | 8082  | URL shortener instance 2       |
| app-3      | 8083  | URL shortener instance 3       |
| postgres   | 5432  | PostgreSQL database            |
| redis      | 6379  | Redis cache                    |
| prometheus | 9090  | Metrics collection             |
| grafana    | 3000  | Monitoring dashboards          |

## API

### POST `/api/v1/data/shorten?longUrl=<url>`
Returns a base62-encoded short URL string.

### GET `/api/v1/{shortUrl}`
301 redirects to the original long URL.

## ID Partitioning

Each instance uses a PostgreSQL sequence with a different offset to avoid ID collisions:
- Instance 1: 1, 4, 7, 10, ...
- Instance 2: 2, 5, 8, 11, ...
- Instance 3: 3, 6, 9, 12, ...
