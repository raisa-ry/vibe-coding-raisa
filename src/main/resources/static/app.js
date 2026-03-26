/* HikePack AI — Frontend */

document.addEventListener('DOMContentLoaded', () => {
    setupDurationSync();
    loadFromUrl();
    document.getElementById('trip-form').addEventListener('submit', generateList);
});

/* ── Duration suffix sync ─────────────────────────────── */
function setupDurationSync() {
    document.querySelectorAll('input[name="durationType"]').forEach(radio => {
        radio.addEventListener('change', e => {
            document.getElementById('duration-suffix').textContent = e.target.value;
        });
    });
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
    return {
        durationType: document.querySelector('input[name="durationType"]:checked').value,
        durationValue: parseInt(document.getElementById('durationValue').value, 10) || 1,
        tempMin: parseInt(document.getElementById('tempMin').value, 10),
        tempMax: parseInt(document.getElementById('tempMax').value, 10),
        hasRain: document.getElementById('hasRain').checked,
        hasWind: document.getElementById('hasWind').checked,
        hasSnow: document.getElementById('hasSnow').checked,
        hasHeat: document.getElementById('hasHeat').checked,
        terrain: document.getElementById('terrain').value,
        fitnessLevel: document.getElementById('fitnessLevel').value
    };
}

/* ── Fill form from saved inputs ─────────────────────── */
function applyInputs(inputs) {
    const radioSel = `input[name="durationType"][value="${inputs.durationType}"]`;
    const radio = document.querySelector(radioSel);
    if (radio) {
        radio.checked = true;
        document.getElementById('duration-suffix').textContent = inputs.durationType;
    }
    setVal('durationValue', inputs.durationValue);
    setVal('tempMin', inputs.tempMin);
    setVal('tempMax', inputs.tempMax);
    setChecked('hasRain',  inputs.hasRain);
    setChecked('hasWind',  inputs.hasWind);
    setChecked('hasSnow',  inputs.hasSnow);
    setChecked('hasHeat',  inputs.hasHeat);
    setVal('terrain',      inputs.terrain);
    setVal('fitnessLevel', inputs.fitnessLevel);
}
function setVal(id, v)     { if (v !== undefined && v !== null) document.getElementById(id).value = v; }
function setChecked(id, v) { if (v !== undefined) document.getElementById(id).checked = !!v; }

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

    /* summary line */
    const flags = [
        inputs.hasRain && 'Rain',
        inputs.hasWind && 'Wind',
        inputs.hasSnow && 'Snow',
        inputs.hasHeat && 'Extreme Heat'
    ].filter(Boolean);
    const summaryText =
        `${inputs.durationValue} ${inputs.durationType}` +
        ` · ${inputs.tempMin}°F–${inputs.tempMax}°F` +
        (flags.length ? ` · ${flags.join(', ')}` : '') +
        ` · ${inputs.terrain}`;

    document.getElementById('trip-summary').textContent  = summaryText;
    document.getElementById('print-summary').textContent = summaryText;

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
    btn.disabled = state === 'loading';
    btn.querySelector('.btn-text').textContent =
        state === 'loading' ? 'Generating\u2026' : 'Generate My Pack List';
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
            /* legacy fallback */
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
        /* auto-generate after paint */
        requestAnimationFrame(() =>
            document.getElementById('trip-form').dispatchEvent(new Event('submit'))
        );
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
