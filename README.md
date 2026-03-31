# HikePack AI

AI-powered hiking gear list generator. Describe your hike in plain English or fill in the form manually, and get a personalised pack list with safety "what-if" scenarios for every condition.

## Features

- **Plain-English description** — type a description like _"3-day hike up Mt. Whitney in July, expecting thunderstorms"_ and let AI auto-fill the entire form
- **Location fetch** — geocode a place name or coordinates and pull real elevation and weather data
- **Trip inputs** — temperature range (°C), weather conditions (rain/wind/snow/heat), terrain, duration, experience level
- **AI-generated pack list** — 5 gear categories tailored to your trip, powered by Claude
- **What-if scenarios** — Cold Snap, Sudden Storm, Injury, Getting Lost — each with extra gear and action steps
- **Pro tips** — safety and comfort advice specific to your conditions
- **Shareable link** — trip inputs encoded in the URL hash; paste the link and the list auto-regenerates
- **Print view** — clean printable checklist with all scenarios expanded

## Quick Start

### Prerequisites

- Java 21+
- Node.js 18+ (for the MCP description-parsing server)
- Maven (or use the IntelliJ bundled Maven)
- An [Anthropic API key](https://console.anthropic.com/) _(optional — both servers run with built-in stubs if omitted)_

### Run

**Terminal 1 — MCP server** (description parsing, port 3001):

```bash
cd mcp-server
npm install

# With AI parsing
ANTHROPIC_API_KEY=sk-ant-... node index.js

# Without API key (keyword-based stub)
node index.js
```

**Terminal 2 — Spring Boot app** (port 8080):

```bash
# With AI pack-list generation
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run

# Without API key (logic-based stub — fully functional for demos)
mvn spring-boot:run
```

> **Note:** System `mvn` may not be on PATH. Use IntelliJ's bundled Maven:
> `"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" spring-boot:run`

Open [http://localhost:8080](http://localhost:8080).

The MCP server is optional — if it is unreachable, the "Parse & Auto-Fill" button shows an inline error and the rest of the app continues to work normally.

### Build & run as JAR

```bash
mvn package -DskipTests
java -jar target/hikepack-ai-1.0.0.jar
```

## How it works

### Option A — describe your hike

1. Type a description in the **Describe Your Hike** box (e.g. _"Kazbegi in May, 2-day hike"_)
2. Click **✨ Parse & Auto-Fill** — the MCP server calls Claude (or the stub) and returns structured conditions
3. Form fields fill automatically with a highlight animation
4. Click **Generate My Pack List**

### Option B — fill the form manually

1. Enter a location and click **Fetch Conditions** to pull real weather and elevation
2. Adjust temperature, weather checkboxes, terrain, duration and experience level as needed
3. Click **Generate My Pack List**

### Under the hood

```
Browser → POST /api/parse-description
  → HikeParserService (MCP JSON-RPC client)
    → mcp-server :3001 → Claude API (or stub)
    → ParsedConditions { locationName, tripDate, tempMin/Max °C, weather flags, terrain, … }
  → form auto-fills with blue highlight animation

Browser → POST /api/location → LocationService → elevation + weather forecast
Browser → POST /api/generate → PackListService → Claude API (or stub) → gear list JSON
```

Temperatures are stored and sent as **°F** inside the Java backend; the UI converts to/from **°C** at the boundary.

## Project Structure

```
mcp-server/
  package.json          Node.js deps (@modelcontextprotocol/sdk, express)
  index.js              MCP HTTP server — parse_hike_conditions tool + keyword stub

src/main/java/com/hiking/
  controller/
    PackListController   POST /api/generate, /api/parse-description, /api/location
  service/
    PackListService      Claude API call + logic-based stub
    HikeParserService    MCP JSON-RPC client (initialize → tools/call, SSE-aware)
    LocationService      Geocoding + weather fetch
  model/
    TripInput            Request body for /api/generate
    ParsedConditions     Response body for /api/parse-description
    LocationData         Response body for /api/location
    PackListResponse, Category, WhatIfScenario

src/main/resources/static/
  index.html            Single-page app
  style.css             Responsive earth-tone theme + highlight animation + print styles
  app.js                Form handling, description parse, API calls, share link, print
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | _(none)_ | Used by both Spring Boot and the MCP server; each falls back to its own stub if unset |
| `SERVER_PORT` | `8080` | Spring Boot HTTP port override |
