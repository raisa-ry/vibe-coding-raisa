import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import express from 'express';
import { z } from 'zod';

const app = express();
app.use(express.json());

app.post('/mcp', async (req, res) => {
    const server = new McpServer({
        name: 'hikepack-mcp-server',
        version: '1.0.0'
    });

    server.tool(
        'parse_hike_conditions',
        'Parse a plain-English hike description into structured trip conditions',
        { description: z.string().describe('Plain-English description of the hike') },
        async ({ description }) => {
            const apiKey = process.env.ANTHROPIC_API_KEY;
            if (!apiKey) {
                return { content: [{ type: 'text', text: buildStubConditions(description) }] };
            }

            const prompt = `Parse this hike description into ONLY valid JSON (no markdown):
{
  "locationName": "<place name or empty string>",
  "tripDate": "<YYYY-MM-DD or empty string>",
  "durationValue": <int>, "durationType": "<hours|days>",
  "tempMin": <int°C>, "tempMax": <int°C>,
  "hasRain": <bool>, "hasWind": <bool>, "hasSnow": <bool>, "hasHeat": <bool>,
  "terrain": "<trail|mountain|forest|lakeside|coastal|desert>",
  "fitnessLevel": "<exact select-option string>"
}
Rules: default duration=4 hours; infer temp from season/location; hasHeat only when tempMax >30°C; all fields required.
locationName: extract the specific place name if mentioned (e.g. "Mt. Whitney", "Yosemite", "Kazbegi", "kazbegi"), always return it in Title Case; otherwise empty string.
tripDate: if a month or season is mentioned, return the 1st of that month in YYYY-MM-DD using year 2026 (or 2027 if the month is before April); otherwise empty string.
fitnessLevel must be exactly one of:
  "Beginner (first few hikes)"
  "Intermediate (regular day hiker)"
  "Experienced (multi-day trips, some scrambling)"
  "Expert (technical mountaineering, long solo trips)"

Hike description: ${description}`;

            const response = await fetch('https://api.anthropic.com/v1/messages', {
                method: 'POST',
                headers: {
                    'x-api-key': apiKey,
                    'anthropic-version': '2023-06-01',
                    'content-type': 'application/json'
                },
                body: JSON.stringify({
                    model: 'claude-sonnet-4-6',
                    max_tokens: 512,
                    messages: [{ role: 'user', content: prompt }]
                })
            });

            if (!response.ok) {
                const err = await response.text();
                throw new Error(`Claude API error (${response.status}): ${err}`);
            }

            const data = await response.json();
            let text = data.content[0].text;

            // Strip markdown fences if present
            text = text.replace(/```json\s*/g, '').replace(/```\s*/g, '').trim();
            const start = text.indexOf('{');
            const end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }

            // Validate it parses as JSON
            JSON.parse(text);

            return { content: [{ type: 'text', text }] };
        }
    );

    const transport = new StreamableHTTPServerTransport({
        sessionIdGenerator: undefined  // stateless
    });

    res.on('close', () => transport.close());
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
});

function buildStubConditions(description) {
    const d = description.toLowerCase();

    // Duration
    const daysMatch = d.match(/(\d+)\s*-?\s*day/);
    const hoursMatch = d.match(/(\d+)\s*-?\s*hour/);
    const durationValue = daysMatch ? parseInt(daysMatch[1]) : hoursMatch ? parseInt(hoursMatch[1]) : 4;
    const durationType = daysMatch ? 'days' : 'hours';

    // Season / temperature inference
    const isSummer = /july|august|june|summer/.test(d);
    const isWinter = /december|january|february|winter/.test(d);
    const isSpring = /march|april|may|spring/.test(d);
    const isFall   = /september|october|november|fall|autumn/.test(d);
    const isAlpine = /alpine|summit|peak|mountain|whitney|rainier|glacier/.test(d);

    let tempMin = 10, tempMax = 21;  // °C
    if (isSummer && isAlpine) { tempMin = 2;  tempMax = 24; }
    else if (isSummer)         { tempMin = 18; tempMax = 32; }
    else if (isWinter)         { tempMin = -9; tempMax = 2;  }
    else if (isSpring || isFall) { tempMin = 4; tempMax = 17; }

    // Weather flags
    const hasRain = /rain|storm|thunder|wet|drizzle|shower/.test(d);
    const hasWind = /wind|gust|breezy/.test(d);
    const hasSnow = /snow|ice|blizzard|frozen/.test(d);
    const hasHeat = tempMax > 30;

    // Terrain
    let terrain = 'trail';
    if (/mountain|alpine|summit|peak|ridge/.test(d))  terrain = 'mountain';
    else if (/forest|wood/.test(d))                   terrain = 'forest';
    else if (/lake|lakeside/.test(d))                 terrain = 'lakeside';
    else if (/coast|beach|ocean|shore/.test(d))       terrain = 'coastal';
    else if (/desert|canyon|arid/.test(d))            terrain = 'desert';

    // Fitness level
    let fitnessLevel = 'Intermediate (regular day hiker)';
    if (/beginner|first.*(hike|time)|easy/.test(d))   fitnessLevel = 'Beginner (first few hikes)';
    else if (/expert|technical|mountaineer|solo/.test(d)) fitnessLevel = 'Expert (technical mountaineering, long solo trips)';
    else if (/experienced|multi.?day|scrambl/.test(d))    fitnessLevel = 'Experienced (multi-day trips, some scrambling)';
    else if (daysMatch && durationValue >= 3)              fitnessLevel = 'Experienced (multi-day trips, some scrambling)';

    // Location name: find a capitalized proper noun (not a month/season/common word)
    const SKIP = new Set(['january','february','march','april','may','june','july','august',
        'september','october','november','december','winter','summer','spring','fall','autumn',
        'a','an','the','in','at','on','up','to','near','and','or','for','with','by','from',
        'day','hike','hiking','trip','trail','mountain','forest','lake','coast','desert']);
    // Try after a location preposition first ("up Mt. Whitney", "near kazbegi"), then leading words
    // Regexes are case-insensitive so lowercase input works too
    const afterPrep = description.match(/\b(?:in|at|near|up|on|to|around)\s+([a-zA-Z][a-zA-Z.]+(?:\s+[a-zA-Z][a-zA-Z.]+)*)/i);
    const locFromPrep = afterPrep && !SKIP.has(afterPrep[1].toLowerCase().split(' ')[0]) ? afterPrep[1] : null;
    // Leading word(s) before "in/at/on/," — handles "kazbegi in may" or "Kazbegi in May"
    const leadingCap = description.match(/^([a-zA-Z]+(?:\s+[a-zA-Z]+)*?)(?:\s+in\b|\s+at\b|\s+on\b|,|$)/i);
    const locFromLead = leadingCap && !SKIP.has(leadingCap[1].toLowerCase()) ? leadingCap[1] : null;
    const rawName = locFromLead || locFromPrep || '';
    // Title-case so "kazbegi" → "Kazbegi", "mt. whitney" → "Mt. Whitney"
    const locationName = rawName.replace(/\b\w/g, c => c.toUpperCase());

    // Trip date: find a month name and map to YYYY-MM-01
    const MONTH_MAP = { january:'01', february:'02', march:'03', april:'04', may:'05', june:'06',
        july:'07', august:'08', september:'09', october:'10', november:'11', december:'12',
        spring:'04', summer:'07', fall:'09', autumn:'09', winter:'01' };
    let tripDate = '';
    for (const [word, mm] of Object.entries(MONTH_MAP)) {
        if (d.includes(word)) {
            const now = new Date();
            const targetMonth = parseInt(mm, 10);
            const y = (targetMonth < now.getMonth() + 1) ? now.getFullYear() + 1 : now.getFullYear();
            tripDate = `${y}-${mm}-01`;
            break;
        }
    }

    return JSON.stringify({ locationName, tripDate, durationValue, durationType, tempMin, tempMax,
        hasRain, hasWind, hasSnow, hasHeat, terrain, fitnessLevel });
}

const PORT = 3001;
app.listen(PORT, () => {
    const mode = process.env.ANTHROPIC_API_KEY ? 'Claude AI' : 'stub (no API key)';
    console.log(`HikePack MCP server listening on http://localhost:${PORT}/mcp [${mode}]`);
});
