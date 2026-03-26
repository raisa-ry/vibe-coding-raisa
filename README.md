# HikePack AI

AI-powered hiking gear list generator. Enter your trip details and get a personalised pack list with safety "what-if" scenarios for every condition.

## Features

- **Trip inputs** — duration (hours or days), temperature range, weather (rain/wind/snow/heat), terrain, experience level
- **AI-generated pack list** — 5 gear categories tailored to your trip, powered by Claude
- **What-if scenarios** — Cold Snap, Sudden Storm, Injury, Getting Lost — each with extra gear and action steps
- **Pro tips** — safety and comfort advice specific to your conditions
- **Shareable link** — trip inputs encoded in the URL hash; paste the link and the list auto-regenerates
- **Print view** — clean printable checklist with all scenarios expanded

## Quick Start

### Prerequisites

- Java 21+
- Maven (or use the IntelliJ bundled Maven)
- An [Anthropic API key](https://console.anthropic.com/) _(optional — app runs with a built-in stub if omitted)_

### Run

```bash
# With AI generation
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run

# Without API key (uses built-in stub — fully functional for demos)
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080).

### Build & run as JAR

```bash
mvn package -DskipTests
java -jar target/hikepack-ai-1.0.0.jar
```

## How it works

1. Fill in the form (duration, temperature, conditions, terrain, experience level)
2. Click **Generate My Pack List**
3. The backend calls `claude-sonnet-4-6` (or the stub if no key is set) and returns structured JSON
4. Results render as interactive checkboxes you can tick off as you pack
5. Use **Copy Share Link** to share the pre-filled list with a trip partner
6. Use **Print Checklist** for a paper copy

## Project Structure

```
src/main/java/com/hiking/
  HikingPackListApplication.java     Spring Boot entry point
  controller/PackListController.java POST /api/generate
  service/PackListService.java       Claude API + logic-based stub
  model/                             TripInput, PackListResponse, Category, WhatIfScenario

src/main/resources/static/
  index.html                         Single-page app
  style.css                          Responsive earth-tone theme + print styles
  app.js                             Form handling, API calls, share link, print
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | _(none)_ | Anthropic API key; falls back to stub if unset |
| `SERVER_PORT` | `8080` | HTTP port override |
