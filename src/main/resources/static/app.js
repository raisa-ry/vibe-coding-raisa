/* HikePack AI — Frontend */

document.addEventListener('DOMContentLoaded', () => {
    setupDurationSync();

    // Set default date to today
    const dateInput = document.getElementById('trip-date');
    dateInput.value = new Date().toISOString().slice(0, 10);

    loadFromUrl();
    document.getElementById('trip-form').addEventListener('submit', generateList);

    document.getElementById('fetch-btn').addEventListener('click', fetchLocation);
    document.getElementById('location-input').addEventListener('keydown', e => {
        if (e.key === 'Enter') { e.preventDefault(); fetchLocation(); }
    });
    document.getElementById('location-input').addEventListener('input', () => {
        if (window._locationData) clearLocationPreview();
    });
    dateInput.addEventListener('change', () => {
        if (window._locationData) clearLocationPreview();
    });

    document.getElementById('parse-btn').addEventListener('click', parseDescription);
    document.getElementById('hike-description').addEventListener('keydown', e => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); parseDescription(); }
    });
});

/* ── Duration suffix sync ─────────────────────────────── */
function setupDurationSync() {
    document.querySelectorAll('input[name="durationType"]').forEach(radio => {
        radio.addEventListener('change', e => {
            document.getElementById('duration-suffix').textContent = e.target.value;
        });
    });
}

/* ── Parse description (MCP) ─────────────────────────── */
async function parseDescription() {
    const description = document.getElementById('hike-description').value.trim();
    if (!description) return;

    const btn = document.getElementById('parse-btn');
    const status = document.getElementById('parse-status');
    btn.disabled = true;
    btn.textContent = 'Parsing\u2026';
    status.hidden = true;
    status.className = 'parse-status';

    try {
        const res = await fetch('/api/parse-description', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ description })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Parse failed');
        applyParsedConditions(data);
        status.textContent = 'Form filled from description';
        status.className = 'parse-status parse-status--ok';
        status.hidden = false;
        showToast('&#x2728; Form auto-filled from your description!');
    } catch (err) {
        status.textContent = '\u26A0\uFE0F ' + err.message;
        status.className = 'parse-status parse-status--err';
        status.hidden = false;
    } finally {
        btn.disabled = false;
        btn.textContent = '\u2728 Parse \u0026 Auto-Fill';
    }
}

function applyParsedConditions(data) {
    clearParsedHighlights();

    // Duration type radio
    const radio = document.querySelector(`input[name="durationType"][value="${data.durationType}"]`);
    if (radio) {
        radio.checked = true;
        const suffix = document.getElementById('duration-suffix');
        suffix.textContent = data.durationType;
        highlightField(suffix.closest('.duration-toggle'));
    }

    if (data.locationName) setValHighlighted('location-input', data.locationName);
    if (data.tripDate)     setValHighlighted('trip-date', data.tripDate);
    setValHighlighted('durationValue', data.durationValue);
    setValHighlighted('fitnessLevel', data.fitnessLevel);
    setValHighlighted('tempMin', data.tempMin);
    setValHighlighted('tempMax', data.tempMax);
    setValHighlighted('terrain', data.terrain);

    setCheckHighlighted('hasRain', data.hasRain);
    setCheckHighlighted('hasWind', data.hasWind);
    setCheckHighlighted('hasSnow', data.hasSnow);
    setCheckHighlighted('hasHeat', data.hasHeat);

    // Keep _locationData metadata if location was already fetched, but don't overwrite with nulls
    if (!window._locationData) window._locationData = {};

    document.getElementById('generate-btn').disabled = false;
}

function setValHighlighted(id, v) {
    if (v === undefined || v === null) return;
    const el = document.getElementById(id);
    if (!el) return;
    el.value = v;
    highlightField(el);
}

function setCheckHighlighted(id, v) {
    const el = document.getElementById(id);
    if (!el) return;
    el.checked = !!v;
    // Highlight the whole checkbox-card label, not the hidden input
    highlightField(el.closest('.checkbox-card'));
}

function highlightField(el) {
    if (!el) return;
    el.classList.remove('parsed-highlight');
    // Force reflow so re-adding the class restarts the animation
    void el.offsetWidth;
    el.classList.add('parsed-highlight');
    setTimeout(() => el.classList.remove('parsed-highlight'), 4000);
}

function clearParsedHighlights() {
    document.querySelectorAll('.parsed-highlight').forEach(el => el.classList.remove('parsed-highlight'));
}

/* ── Location fetch ───────────────────────────────────── */
async function fetchLocation() {
    const query = document.getElementById('location-input').value.trim();
    if (!query) return;

    const btn = document.getElementById('fetch-btn');
    btn.disabled = true;
    btn.textContent = 'Fetching\u2026';

    // Show inline spinner in preview area
    const preview = document.getElementById('location-preview');
    preview.hidden = false;
    preview.innerHTML = '<div class="location-spinner"></div>';

    try {
        const date = document.getElementById('trip-date').value;
        const res = await fetch('/api/location', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ query, date })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Location not found');
        showLocationPreview(data);
    } catch (err) {
        preview.innerHTML = `<p class="location-error">&#x26A0;&#xFE0F; ${esc(err.message)}</p>`;
        document.getElementById('generate-btn').disabled = true;
    } finally {
        btn.disabled = false;
        btn.textContent = 'Fetch Conditions';
    }
}

function showLocationPreview(data) {
    window._locationData = data;

    // Populate visible form fields from location data (location API returns °F → show °C)
    document.getElementById('tempMin').value   = fToC(data.tempMin ?? 50);
    document.getElementById('tempMax').value   = fToC(data.tempMax ?? 70);
    document.getElementById('hasRain').checked = !!data.hasRain;
    document.getElementById('hasWind').checked = !!data.hasWind;
    document.getElementById('hasSnow').checked = !!data.hasSnow;
    document.getElementById('hasHeat').checked = !!data.hasHeat;
    if (data.terrain) document.getElementById('terrain').value = data.terrain;

    const badges = [];
    if (data.hasRain)  badges.push('<span class="preview-badge badge-rain">&#x1F327;&#xFE0F; Rain</span>');
    if (data.hasWind)  badges.push('<span class="preview-badge badge-wind">&#x1F4A8; Wind</span>');
    if (data.hasSnow)  badges.push('<span class="preview-badge badge-snow">&#x2744;&#xFE0F; Snow</span>');
    if (data.hasHeat)  badges.push('<span class="preview-badge badge-heat">&#x2600;&#xFE0F; Heat</span>');
    if (data.terrain)  badges.push(`<span class="preview-badge badge-terrain">&#x26F0;&#xFE0F; ${esc(data.terrain)}</span>`);

    const metaParts = [
        data.elevationFt.toLocaleString() + ' ft',
        fToC(data.tempMin) + '\u2013' + fToC(data.tempMax) + '\u00B0C'
    ];
    if (data.tripDate) metaParts.push(data.tripDate);
    if (data.weatherNote) metaParts.push(data.weatherNote);

    const preview = document.getElementById('location-preview');
    preview.hidden = false;
    preview.innerHTML = `
        <div class="location-name">&#x1F4CD; ${esc(data.displayName)}</div>
        <div class="preview-meta">${metaParts.map(esc).join(' &middot; ')}</div>
        <div class="preview-badges">${badges.join('')}</div>`;

    document.getElementById('generate-btn').disabled = false;
}

function clearLocationPreview() {
    window._locationData = null;
    const preview = document.getElementById('location-preview');
    preview.hidden = true;
    preview.innerHTML = '';
    // Reset condition fields to defaults (°C)
    document.getElementById('tempMin').value   = 10;
    document.getElementById('tempMax').value   = 21;
    document.getElementById('hasRain').checked = false;
    document.getElementById('hasWind').checked = false;
    document.getElementById('hasSnow').checked = false;
    document.getElementById('hasHeat').checked = false;
    document.getElementById('terrain').value   = 'trail';
    document.getElementById('generate-btn').disabled = true;
}

/* ── Generate ─────────────────────────────────────────── */
async function generateList(e) {
    e.preventDefault();
    const inputs = collectInputs();
    showState('loading');

    try {
        const res = await fetch('/api/generate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(inputs)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Unknown error');

        renderResults(data, inputs);
        showState('results');

        if (window.innerWidth < 860) {
            document.getElementById('results-panel')
                .scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    } catch (err) {
        document.getElementById('error-message').textContent = err.message;
        showState('error');
    }
}

/* ── Collect form values ──────────────────────────────── */
function collectInputs() {
    const loc = window._locationData || {};
    return {
        // location metadata (only from fetched location)
        locationName:   loc.displayName    || null,
        latitude:       loc.latitude       || 0,
        longitude:      loc.longitude      || 0,
        elevationFt:    loc.elevationFt    || 0,
        weatherSummary: loc.weatherSummary || '',
        tripDate:       loc.tripDate       || document.getElementById('trip-date').value || null,
        weatherNote:    loc.weatherNote    || null,
        // conditions — form shows °C; convert to °F for the Java backend
        tempMin:        cToF(parseInt(document.getElementById('tempMin').value, 10)),
        tempMax:        cToF(parseInt(document.getElementById('tempMax').value, 10)),
        hasRain:        document.getElementById('hasRain').checked,
        hasWind:        document.getElementById('hasWind').checked,
        hasSnow:        document.getElementById('hasSnow').checked,
        hasHeat:        document.getElementById('hasHeat').checked,
        terrain:        document.getElementById('terrain').value,
        // trip fields
        durationType:   document.querySelector('input[name="durationType"]:checked').value,
        durationValue:  parseInt(document.getElementById('durationValue').value, 10) || 1,
        fitnessLevel:   document.getElementById('fitnessLevel').value
    };
}

/* ── Fill form from saved inputs ─────────────────────── */
function applyInputs(inputs) {
    // duration
    const radio = document.querySelector(`input[name="durationType"][value="${inputs.durationType}"]`);
    if (radio) {
        radio.checked = true;
        document.getElementById('duration-suffix').textContent = inputs.durationType;
    }
    setVal('durationValue', inputs.durationValue);
    setVal('fitnessLevel', inputs.fitnessLevel);

    if (inputs.tripDate) {
        setVal('trip-date', inputs.tripDate);
    }

    if (inputs.locationName) {
        // new-style payload: restore preview card
        document.getElementById('location-input').value = inputs.locationName;
        showLocationPreview(inputs);
    }
}

function setVal(id, v) { if (v !== undefined && v !== null) document.getElementById(id).value = v; }

/* ── Temperature conversion ───────────────────────────── */
function fToC(f) { return Math.round((f - 32) * 5 / 9); }
function cToF(c) { return Math.round(c * 9 / 5 + 32); }

/* ── Render results ───────────────────────────────────── */
function renderResults(data, inputs) {
    /* categories */
    document.getElementById('categories').innerHTML =
        (data.categories || []).map(cat => `
            <div class="category-card">
                <div class="category-header">
                    <span class="category-icon">${esc(cat.icon)}</span>
                    <h3>${esc(cat.name)}</h3>
                </div>
                <ul class="item-list">
                    ${(cat.items || []).map(item => `
                        <li>
                            <label class="item-label">
                                <input type="checkbox">
                                <span>${esc(item)}</span>
                            </label>
                        </li>`).join('')}
                </ul>
            </div>`).join('');

    /* what-if scenarios */
    document.getElementById('whatif-scenarios').innerHTML =
        (data.whatIfScenarios || []).map(s => `
            <div class="scenario-card">
                <div class="scenario-header" onclick="toggleScenario(this)">
                    <span class="scenario-icon">${esc(s.icon)}</span>
                    <div>
                        <h4>${esc(s.scenario)}</h4>
                        <p>${esc(s.description)}</p>
                    </div>
                    <span class="chevron">&#x25BC;</span>
                </div>
                <ul class="scenario-items">
                    ${(s.items || []).map(item => `
                        <li>
                            <label class="item-label">
                                <input type="checkbox">
                                <span>${esc(item)}</span>
                            </label>
                        </li>`).join('')}
                </ul>
            </div>`).join('');

    /* tips */
    document.getElementById('tips-list').innerHTML =
        (data.tips || []).map(tip => `
            <div class="tip-item">
                <span class="tip-icon">&#x1F4A1;</span>
                <span>${esc(tip)}</span>
            </div>`).join('');

    /* summary line — inputs has °F (from collectInputs), display in °C */
    const summaryTemp = `${fToC(inputs.tempMin)}\u00B0C\u2013${fToC(inputs.tempMax)}\u00B0C`;
    let summaryText;
    if (inputs.locationName) {
        const flags = [
            inputs.hasRain && 'Rain',
            inputs.hasWind && 'Wind',
            inputs.hasSnow && 'Snow',
            inputs.hasHeat && 'Extreme Heat'
        ].filter(Boolean);
        summaryText =
            `${esc(inputs.locationName)} \u00B7 ${inputs.elevationFt.toLocaleString()}ft` +
            ` \u00B7 ${inputs.durationValue} ${inputs.durationType}` +
            ` \u00B7 ${summaryTemp}` +
            (inputs.tripDate ? ` \u00B7 ${esc(inputs.tripDate)}` : '') +
            (flags.length ? ` \u00B7 ${flags.join(', ')}` : '');
    } else {
        const flags = [
            inputs.hasRain && 'Rain',
            inputs.hasWind && 'Wind',
            inputs.hasSnow && 'Snow',
            inputs.hasHeat && 'Extreme Heat'
        ].filter(Boolean);
        summaryText =
            `${inputs.durationValue} ${inputs.durationType}` +
            ` \u00B7 ${summaryTemp}` +
            (flags.length ? ` \u00B7 ${flags.join(', ')}` : '') +
            ` \u00B7 ${inputs.terrain}`;
    }

    document.getElementById('trip-summary').innerHTML  = summaryText;
    document.getElementById('print-summary').innerHTML = summaryText;

    /* save current inputs for share link */
    window._currentInputs = inputs;
}

/* ── Toggle what-if scenario open/closed ─────────────── */
function toggleScenario(header) {
    const items  = header.parentElement.querySelector('.scenario-items');
    const chev   = header.querySelector('.chevron');
    const isOpen = items.classList.contains('open');
    items.classList.toggle('open', !isOpen);
    chev.classList.toggle('open',  !isOpen);
}

/* ── UI state machine ─────────────────────────────────── */
function showState(state) {
    document.getElementById('empty-state').hidden    = state !== 'empty';
    document.getElementById('loading-state').hidden  = state !== 'loading';
    document.getElementById('error-state').hidden    = state !== 'error';
    document.getElementById('results-content').hidden = state !== 'results';

    const btn = document.getElementById('generate-btn');
    // only disable during loading; leave fetch-gate state intact otherwise
    if (state === 'loading') {
        btn.disabled = true;
        btn.querySelector('.btn-text').textContent = 'Generating\u2026';
    } else {
        // Enable if location was fetched OR conditions were parsed from description
        btn.disabled = !window._locationData;
        btn.querySelector('.btn-text').textContent = 'Generate My Pack List';
    }
}

function resetToEmpty() { showState('empty'); }

/* ── Shareable link ───────────────────────────────────── */
function copyShareLink() {
    if (!window._currentInputs) return;
    const payload = btoa(unescape(encodeURIComponent(JSON.stringify(window._currentInputs))));
    const url = `${location.origin}${location.pathname}#share=${payload}`;
    navigator.clipboard.writeText(url)
        .then(() => showToast('Share link copied! &#x1F517;'))
        .catch(() => {
            const ta = document.createElement('textarea');
            ta.value = url;
            document.body.appendChild(ta);
            ta.select();
            document.execCommand('copy');
            document.body.removeChild(ta);
            showToast('Share link copied! &#x1F517;');
        });
}

/* ── Load from URL hash ───────────────────────────────── */
function loadFromUrl() {
    const hash = location.hash;
    if (!hash.startsWith('#share=')) return;
    try {
        const inputs = JSON.parse(decodeURIComponent(escape(atob(hash.slice(7)))));
        applyInputs(inputs);
        /* auto-generate after paint (only if location data is present) */
        requestAnimationFrame(() => {
            if (window._locationData) {
                document.getElementById('trip-form').dispatchEvent(new Event('submit'));
            }
        });
    } catch (_) { /* bad hash — ignore */ }
}

/* ── Toast ────────────────────────────────────────────── */
function showToast(html) {
    const t = document.getElementById('toast');
    t.innerHTML = html;
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 3000);
}

/* ── XSS-safe text escaping ───────────────────────────── */
function esc(str) {
    if (!str) return '';
    const d = document.createElement('div');
    d.appendChild(document.createTextNode(String(str)));
    return d.innerHTML;
}
