package com.hiking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiking.model.Category;
import com.hiking.model.PackListResponse;
import com.hiking.model.TripInput;
import com.hiking.model.WhatIfScenario;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PackListService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public PackListResponse generatePackList(TripInput input) throws Exception {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return buildStubResponse(input);
        }

        String prompt = buildPrompt(input);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "claude-sonnet-4-6");
        body.put("max_tokens", 2048);
        body.put("messages", List.of(message));

        String requestBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error (" + response.statusCode() + "): " + response.body());
        }

        JsonNode responseNode = objectMapper.readTree(response.body());
        String text = responseNode.get("content").get(0).get("text").asText();
        String json = extractJson(text);

        return objectMapper.readValue(json, PackListResponse.class);
    }

    private String buildPrompt(TripInput input) {
        List<String> conditions = new ArrayList<>();
        if (input.isHasRain()) conditions.add("rain expected");
        if (input.isHasWind()) conditions.add("high winds");
        if (input.isHasSnow()) conditions.add("snow/ice possible");
        if (input.isHasHeat()) conditions.add("extreme heat");
        String condStr = conditions.isEmpty() ? "clear/mild" : String.join(", ", conditions);

        return """
                You are an expert wilderness guide and gear specialist.
                Generate a comprehensive, practical hiking pack list tailored to these trip details.

                TRIP DETAILS:
                - Duration: %d %s
                - Temperature range: %d°F to %d°F
                - Weather conditions: %s
                - Terrain: %s
                - Hiker experience: %s

                Respond with ONLY valid JSON — no markdown, no explanation, no code blocks.
                Your response must start with { and end with }.

                Use exactly this JSON structure:
                {
                  "categories": [
                    {
                      "name": "string",
                      "icon": "single emoji",
                      "items": ["item with quantity or detail as needed"]
                    }
                  ],
                  "whatIfScenarios": [
                    {
                      "scenario": "string",
                      "icon": "single emoji",
                      "description": "one-sentence what-to-do",
                      "items": ["extra item to carry or action to take"]
                    }
                  ],
                  "tips": ["concise practical safety or comfort tip"]
                }

                Required categories (in order): Essential Gear, Clothing & Layers, Food & Water, Navigation & Safety, First Aid.
                Required what-if scenarios: Cold Snap (sudden 20°F+ temperature drop), Sudden Storm, Injury or Medical Emergency, Getting Lost.
                Include 4–6 tips specific to this trip's conditions and terrain.
                Scale quantities and item specificity to the trip duration and conditions.
                """.formatted(
                input.getDurationValue(), input.getDurationType(),
                input.getTempMin(), input.getTempMax(),
                condStr,
                input.getTerrain(),
                input.getFitnessLevel()
        );
    }

    private String extractJson(String text) {
        // Strip markdown code fences if present
        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").strip();
        // Find outermost JSON object
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    // ── Stub response (no API key required) ───────────────────────────────────

    private PackListResponse buildStubResponse(TripInput input) {
        boolean multiDay  = "days".equals(input.getDurationType());
        boolean cold      = input.getTempMin() < 40;
        boolean hot       = input.getTempMax() > 85;
        boolean mountain  = input.getTerrain().toLowerCase().contains("mountain");
        boolean backcountry = input.getTerrain().toLowerCase().contains("backcountry");
        boolean beginner  = input.getFitnessLevel().toLowerCase().contains("beginner");

        PackListResponse r = new PackListResponse();
        r.setCategories(List.of(
                essentialGear(input, multiDay, mountain, backcountry),
                clothing(input, cold, hot),
                foodAndWater(input, multiDay, hot),
                navigationSafety(mountain, backcountry, beginner),
                firstAid(multiDay, mountain)
        ));
        r.setWhatIfScenarios(List.of(
                coldSnap(cold),
                suddenStorm(input),
                injury(multiDay),
                gettingLost(backcountry, mountain)
        ));
        r.setTips(buildTips(input, cold, hot, multiDay, mountain));
        return r;
    }

    private Category essentialGear(TripInput input, boolean multiDay, boolean mountain, boolean backcountry) {
        List<String> items = new ArrayList<>(List.of(
                "Daypack (20–30 L) or backpack (40–60 L for multi-day)",
                "Trekking poles",
                "Headlamp + spare batteries",
                "Sun hat or wide-brim hat",
                "Sunglasses (UV400)",
                "Sunscreen SPF 50+",
                "Insect repellent"
        ));
        if (multiDay)     items.add("Tent or bivy shelter");
        if (multiDay)     items.add("Sleeping bag (rated to " + (input.getTempMin() - 10) + "°F)");
        if (multiDay)     items.add("Sleeping pad (insulated)");
        if (mountain)     items.add("Trekking pole tips and baskets");
        if (backcountry)  items.add("Bear canister or hang bag");
        items.add("Whistle");
        items.add("Multi-tool or knife");
        items.add("Duct tape (small roll)");
        return category("Essential Gear", "🎒", items);
    }

    private Category clothing(TripInput input, boolean cold, boolean hot) {
        List<String> items = new ArrayList<>();
        if (hot) {
            items.add("Moisture-wicking shirt (light colour)");
            items.add("Convertible hiking pants or shorts");
            items.add("Cooling neck gaiter");
        } else {
            items.add("Moisture-wicking base layer (top + bottom)");
            items.add("Insulating mid-layer (fleece or down jacket)");
            items.add("Hiking pants (zip-off for versatility)");
        }
        if (cold)             items.add("Heavyweight insulated jacket");
        if (cold)             items.add("Thermal gloves");
        if (cold)             items.add("Warm beanie / balaclava");
        if (input.isHasRain()) items.add("Waterproof rain jacket (taped seams)");
        if (input.isHasRain()) items.add("Rain pants or pack cover");
        if (input.isHasWind()) items.add("Windproof softshell layer");
        if (input.isHasSnow()) items.add("Gaiters (low or high depending on snow depth)");
        if (input.isHasSnow()) items.add("Waterproof over-mitts");
        items.add("Moisture-wicking hiking socks (×" + (input.getDurationValue() + 1) + " pairs)");
        items.add("Waterproof hiking boots with ankle support");
        items.add("Camp sandals or lightweight shoes (multi-day)");
        return category("Clothing & Layers", "🧥", items);
    }

    private Category foodAndWater(TripInput input, boolean multiDay, boolean hot) {
        List<String> items = new ArrayList<>();
        double litersPerHour = hot ? 0.7 : 0.5;
        int hours = multiDay ? input.getDurationValue() * 8 : input.getDurationValue();
        int waterLiters = (int) Math.ceil(litersPerHour * hours);
        items.add("Water: " + waterLiters + " L minimum (plan for resupply if available)");
        items.add("Water filter or purification tablets");
        if (!multiDay) {
            items.add("Trail mix or nuts (" + input.getDurationValue() * 150 + " kcal)");
            items.add("Energy bars × " + Math.max(1, input.getDurationValue() / 2));
            items.add("Lunch: sandwich, wrap, or tortillas");
            items.add("Electrolyte tablets or powder");
        } else {
            items.add("Breakfast: instant oats or freeze-dried meal × " + input.getDurationValue());
            items.add("Lunch: crackers, hard cheese, salami, nut butter × " + input.getDurationValue());
            items.add("Dinner: freeze-dried or dehydrated meal × " + input.getDurationValue());
            items.add("Snacks: trail mix, energy gels, bars × " + (input.getDurationValue() * 3));
            items.add("Camp stove + fuel canister");
            items.add("Pot or cook set");
            items.add("Spork or utensil set");
            items.add("Bear-safe food storage");
        }
        items.add("Collapsible water bottle or hydration bladder (2 L)");
        return category("Food & Water", "🍫", items);
    }

    private Category navigationSafety(boolean mountain, boolean backcountry, boolean beginner) {
        List<String> items = new ArrayList<>(List.of(
                "Downloaded offline maps (AllTrails / Gaia GPS)",
                "Compass (even if you have a phone)",
                "Trail map (paper backup)",
                "Fully charged phone + portable charger"
        ));
        if (mountain || backcountry) {
            items.add("Altimeter watch");
            items.add("PLB or satellite communicator (e.g., Garmin inReach)");
        }
        if (beginner) {
            items.add("Trail guide or route description printout");
        }
        items.add("Emergency bivvy / space blanket");
        items.add("Fire starter (lighter + waterproof matches)");
        items.add("ID + emergency contact card (in waterproof bag)");
        return category("Navigation & Safety", "🧭", items);
    }

    private Category firstAid(boolean multiDay, boolean mountain) {
        List<String> items = new ArrayList<>(List.of(
                "Adhesive bandages (assorted sizes)",
                "Moleskin or blister pads",
                "Medical tape",
                "Gauze pads + elastic bandage wrap",
                "Antiseptic wipes",
                "Antibiotic ointment",
                "Pain reliever (ibuprofen / acetaminophen)",
                "Antihistamine (diphenhydramine)",
                "Personal prescription medications"
        ));
        if (multiDay)  items.add("SAM splint (for sprains/fractures)");
        if (mountain)  items.add("Blister lancet + needle");
        if (mountain)  items.add("Ace bandage (ankle wrap)");
        items.add("Emergency whistle (already in Essential Gear — confirm packed)");
        items.add("First-aid quick-reference card");
        return category("First Aid", "🩹", items);
    }

    private WhatIfScenario coldSnap(boolean alreadyCold) {
        List<String> items = new ArrayList<>(List.of(
                "Extra insulating layer (down jacket or puffy vest)",
                "Hand warmers (×4 pairs)",
                "Thermal hat and neck gaiter",
                "Emergency bivvy / space blanket"
        ));
        if (!alreadyCold) items.add("Heavier-weight gloves or over-mitts");
        return scenario("Cold Snap", "🥶",
                "If temperature drops 20°F+, layer up immediately and find or make shelter before nightfall.",
                items);
    }

    private WhatIfScenario suddenStorm(TripInput input) {
        List<String> items = new ArrayList<>(List.of(
                "Rain jacket and rain pants (already packed — put on early)",
                "Waterproof pack cover or pack liner bag",
                "Dry bag for electronics and sleeping bag",
                "Tarp or emergency shelter"
        ));
        if (input.isHasWind()) items.add("Balaclava to protect face from wind-driven rain");
        return scenario("Sudden Storm", "⛈️",
                "Descend below treeline immediately on mountains; avoid ridges and summits when lightning is possible.",
                items);
    }

    private WhatIfScenario injury(boolean multiDay) {
        List<String> items = new ArrayList<>(List.of(
                "SAM splint (improvise with trekking pole if needed)",
                "Ace bandage for ankle/knee support",
                "Pain reliever (ibuprofen for inflammation)",
                "Satellite communicator or PLB to call for rescue",
                "Whistle (3 blasts = international distress signal)"
        ));
        if (multiDay) items.add("Extra day of food in case evacuation is slow");
        return scenario("Injury / Emergency", "🚨",
                "Stop, assess, stabilise the injury; send two people for help and have one stay with the injured hiker.",
                items);
    }

    private WhatIfScenario gettingLost(boolean backcountry, boolean mountain) {
        List<String> items = new ArrayList<>(List.of(
                "Paper map + compass (don't rely solely on phone battery)",
                "Satellite communicator or PLB",
                "Emergency bivvy / space blanket",
                "Whistle + signal mirror",
                "Extra food (1–2 days buffer on multi-day trips)"
        ));
        if (backcountry || mountain) items.add("Bright-coloured gear or poncho for aerial visibility");
        return scenario("Getting Lost", "🗺️",
                "Stop and stay calm — retrace your steps or STOP (Stop, Think, Observe, Plan) before moving further.",
                items);
    }

    private List<String> buildTips(TripInput input, boolean cold, boolean hot,
                                   boolean multiDay, boolean mountain) {
        List<String> tips = new ArrayList<>();
        tips.add("Tell someone your exact route, trailhead, and expected return time before you leave.");
        if (hot)      tips.add("Start early (pre-dawn) to avoid peak heat; rest in shade during 11am–3pm.");
        if (cold)     tips.add("Layer up before you feel cold — it's much harder to rewarm a chilled body.");
        if (input.isHasRain()) tips.add("Put on rain gear before it rains; wet cotton loses 90% of its insulating value.");
        if (mountain) tips.add("Turn around by 1pm on exposed peaks regardless of how close the summit feels.");
        if (multiDay) tips.add("Dry your feet and change socks at lunch to prevent blisters on long trips.");
        tips.add("Eat and drink before you feel hungry or thirsty — both hunger and thirst lag behind need.");
        tips.add("Pack out everything you pack in; leave no trace.");
        // cap at 6
        return tips.subList(0, Math.min(tips.size(), 6));
    }

    private Category category(String name, String icon, List<String> items) {
        Category c = new Category();
        c.setName(name);
        c.setIcon(icon);
        c.setItems(items);
        return c;
    }

    private WhatIfScenario scenario(String name, String icon, String description, List<String> items) {
        WhatIfScenario s = new WhatIfScenario();
        s.setScenario(name);
        s.setIcon(icon);
        s.setDescription(description);
        s.setItems(items);
        return s;
    }
}
