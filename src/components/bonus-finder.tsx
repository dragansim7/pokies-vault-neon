import { ArrowUpDown, Download, Search, Square, X } from "lucide-react";
import { useServerFn } from "@tanstack/react-start";
import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { BonusCard } from "@/components/bonus-card";
import { CARD_COLORS, useVault } from "@/components/vault";
import { loadPublicCatalogueFn } from "@/lib/casino.functions";
import { visibleSites } from "@/lib/working-sites";
import {
  bestBonusForMetric,
  domainBonusMetric,
  type BonusSlot,
  type BonusSortMetric,
  type PublicBonus,
} from "@/lib/public-bonus";

const CACHE_KEY = "pokies_vault_bonus_catalogue_v1";
const PAGE = 20;
const DOMAIN_LOCALE_OPTS: Intl.CollatorOptions = { numeric: true, sensitivity: "base" };

type CacheRow = { bonuses: PublicBonus[]; message?: string; host?: string; merchantId?: string };

type Filters = {
  slots: BonusSlot[];
  focus: BonusSlot | "";
  sort: BonusSortMetric;
  loadedOnly: boolean;
};

const FILTER_SLOTS: { id: BonusSlot; label: string; emoji: string }[] = [
  { id: "deposit", label: "Deposit Match", emoji: "💳" },
  { id: "signup", label: "Free Signup", emoji: "🎁" },
  { id: "share", label: "Share/Invite", emoji: "🤝" },
  { id: "daily", label: "Daily Free", emoji: "📅" },
  { id: "birthday", label: "Birthday", emoji: "🎂" },
];

const SORT_OPTIONS: { id: BonusSortMetric; label: string; short: string }[] = [
  { id: "", label: "Domain name — A → Z", short: "A → Z" },
  { id: "za", label: "Domain name — Z → A", short: "Z → A" },
  { id: "deposit", label: "Highest Deposit Match (%)", short: "Deposit ↓" },
  { id: "amount", label: "Highest Bonus Amount", short: "Amount ↓" },
  { id: "withdraw", label: "Highest Max Withdrawal", short: "Max Cashout ↓" },
  { id: "turnover", label: "Lowest Turnover / Rollover", short: "Turnover ↑" },
  { id: "count", label: "Most Bonuses Found", short: "Offers ↓" },
];

const EMPTY_FILTERS: Filters = {
  slots: [],
  focus: "",
  sort: "",
  loadedOnly: false,
};

function readCache(): Record<string, CacheRow> {
  if (typeof localStorage === "undefined") return {};
  try {
    const parsed = JSON.parse(localStorage.getItem(CACHE_KEY) || "{}") as Record<string, CacheRow>;
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function writeCache(cache: Record<string, CacheRow>) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(cache));
  } catch {
    /* quota */
  }
}

function normalizeSearch(value: string): string {
  return value
    .toLowerCase()
    .trim()
    .replace(/^https?:\/\//i, "")
    .replace(/^www\./i, "")
    .split(/[/?#]/, 1)[0]
    .replace(/[^a-z0-9]/g, "");
}

function domainMatchesSearch(domain: string, query: string): boolean {
  const raw = query.toLowerCase().trim();
  if (!raw) return true;
  const normQuery = normalizeSearch(raw);
  if (!normQuery) return true;
  const normDomain = normalizeSearch(domain);
  if (normDomain.includes(normQuery)) return true;
  const tokens = raw
    .replace(/^https?:\/\//i, "")
    .replace(/^www\./i, "")
    .split(/[^a-z0-9]+/)
    .map(normalizeSearch)
    .filter(Boolean);
  return !tokens.length || tokens.every((token) => normDomain.includes(token));
}

function hasUsableSlot(row: CacheRow | undefined, slot: BonusSlot): boolean {
  if (!row || !row.bonuses.length) return false;
  const found = bestBonusForMetric(row.bonuses, slot, "");
  return Boolean(found && found.kind !== "unset");
}

export function BonusFinder() {
  const { state, patch } = useVault();
  const loadFn = useServerFn(loadPublicCatalogueFn);
  const loadRef = useRef(loadFn);
  loadRef.current = loadFn;

  const [query, setQuery] = useState("");
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [draft, setDraft] = useState<Filters>(EMPTY_FILTERS);
  const [sortOpen, setSortOpen] = useState(false);
  const [columns, setColumns] = useState<"one" | "two">("one");
  const [cache, setCache] = useState<Record<string, CacheRow>>({});
  const cacheRef = useRef(cache);
  cacheRef.current = cache;
  const [page, setPage] = useState(0);
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState({ done: 0, total: 0 });
  const [status, setStatus] = useState("Load domains to grab live public bonuses and activate sorting/filtering.");
  const stopRef = useRef<AbortController | null>(null);
  const ready = useRef(false);

  const domains = useMemo(() => visibleSites(state).map((site) => site.domain), [state]);

  useEffect(() => {
    const saved = readCache();
    const allowed = new Set(domains);
    const next: Record<string, CacheRow> = {};
    for (const [domain, row] of Object.entries(saved)) {
      if (allowed.has(domain)) next[domain] = row;
    }
    setCache(next);
    if (Object.keys(next).length !== Object.keys(saved).length) writeCache(next);
    ready.current = true;
  }, [domains]);

  useEffect(() => {
    if (!ready.current) return;
    writeCache(cache);
  }, [cache]);

  const slotCounts = useMemo(() => {
    const counts: Record<BonusSlot, number> = {
      deposit: 0,
      signup: 0,
      share: 0,
      daily: 0,
      birthday: 0,
    };
    for (const domain of domains) {
      const row = cache[domain];
      if (!row?.bonuses.length) continue;
      for (const slot of FILTER_SLOTS) {
        if (hasUsableSlot(row, slot.id)) counts[slot.id] += 1;
      }
    }
    return counts;
  }, [domains, cache]);

  const filtered = useMemo(() => {
    const q = query.trim();
    let list = domains.filter((domain) => domainMatchesSearch(domain, q));

    if (filters.loadedOnly) {
      list = list.filter((domain) => Boolean(cache[domain] && !cache[domain].message));
    }

    if (filters.slots.length) {
      list = list.filter((domain) => {
        const row = cache[domain];
        if (!row) return false;
        return filters.slots.every((slot) => hasUsableSlot(row, slot));
      });
    }

    const az = (a: string, b: string) => a.localeCompare(b, undefined, DOMAIN_LOCALE_OPTS);

    if (filters.sort === "za") {
      return [...list].sort((a, b) => b.localeCompare(a, undefined, DOMAIN_LOCALE_OPTS));
    }
    if (!filters.sort || filters.sort === "az") {
      return [...list].sort(az);
    }

    const ascending = filters.sort === "turnover";
    return [...list].sort((a, b) => {
      const rowA = cache[a];
      const rowB = cache[b];
      const hitA = domainBonusMetric(rowA?.bonuses ?? [], filters.slots, filters.focus, filters.sort);
      const hitB = domainBonusMetric(rowB?.bonuses ?? [], filters.slots, filters.focus, filters.sort);

      const tier = (row: CacheRow | undefined, val: number | null) => {
        if (val != null) return 0;
        if (row && !row.message && row.bonuses.length) return 1;
        if (row && !row.message) return 2;
        if (row?.message) return 4;
        return 3;
      };

      const ta = tier(rowA, hitA.value);
      const tb = tier(rowB, hitB.value);
      if (ta !== tb) return ta - tb;
      if (ta !== 0 || hitA.value == null || hitB.value == null) return az(a, b);
      const delta = ascending ? hitA.value - hitB.value : hitB.value - hitA.value;
      return delta || az(a, b);
    });
  }, [domains, query, filters, cache]);

  const pages = Math.max(1, Math.ceil(filtered.length / PAGE));
  const safePage = Math.min(page, pages - 1);
  const slice = filtered.slice(safePage * PAGE, safePage * PAGE + PAGE);

  const loadedCount = domains.filter((domain) => Boolean(cache[domain])).length;
  const activeFilterCount =
    filters.slots.length + Number(Boolean(filters.focus)) + Number(filters.loadedOnly);
  const sortFilterActive = Boolean(filters.sort) || activeFilterCount > 0;

  const sortButtonLabel = useMemo(() => {
    const parts: string[] = [];
    if (filters.sort) {
      const found = SORT_OPTIONS.find((item) => item.id === filters.sort);
      if (found) parts.push(found.short);
    }
    if (filters.focus) {
      const found = FILTER_SLOTS.find((item) => item.id === filters.focus);
      if (found) parts.push(found.label);
    }
    if (filters.slots.length) {
      parts.push(`${filters.slots.length} Filter${filters.slots.length === 1 ? "" : "s"}`);
    }
    if (filtered.length > 0) parts.push(String(filtered.length));
    if (parts.length === 1 && filtered.length > 0) return `Sort / Filter · ${filtered.length}`;
    return parts.length ? parts.join(" · ") : "Sort / Filter";
  }, [filters, filtered.length]);

  function toggleSlot(slot: BonusSlot) {
    setPage(0);
    setFilters((current) => {
      const exists = current.slots.includes(slot);
      const nextSlots = exists
        ? current.slots.filter((item) => item !== slot)
        : [...current.slots, slot];
      // Keep focus synchronized when a single slot is toggled on or removed
      const nextFocus =
        !exists && !current.focus
          ? slot
          : exists && current.focus === slot
            ? nextSlots[0] ?? ""
            : current.focus;
      return { ...current, slots: nextSlots, focus: nextFocus };
    });
  }

  function openSortModal() {
    setDraft(filters);
    setSortOpen(true);
  }

  function applySortModal() {
    setPage(0);
    setFilters(draft);
    setSortOpen(false);
  }

  function resetSortModal() {
    setPage(0);
    setDraft(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
    setSortOpen(false);
  }

  async function load(domainsToLoad: string[]) {
    const unique = Array.from(new Set(domainsToLoad));
    if (!unique.length) {
      setStatus("No domains to load.");
      return;
    }
    stopRef.current?.abort();
    const controller = new AbortController();
    stopRef.current = controller;
    setRunning(true);
    setProgress({ done: 0, total: unique.length });
    setStatus(`Loading public bonuses for ${unique.length} domains…`);
    let done = 0;
    let cursor = 0;
    const worker = async () => {
      for (;;) {
        if (controller.signal.aborted) return;
        const index = cursor;
        cursor += 4;
        const names = unique.slice(index, index + 4);
        if (!names.length) return;
        const items = names.map((domain) => {
          const saved = cacheRef.current[domain];
          return { domain, host: saved?.host, merchantId: saved?.merchantId };
        });
        try {
          const result = await loadRef.current({ data: { items }, signal: controller.signal });
          if (controller.signal.aborted) return;
          setCache((current) => {
            const next = { ...current };
            for (const row of result) {
              next[row.domain] = {
                bonuses: row.ok ? row.bonuses : [],
                message: row.ok ? undefined : row.message,
                host: row.host || current[row.domain]?.host,
                merchantId: row.merchantId || current[row.domain]?.merchantId,
              };
            }
            return next;
          });
        } catch {
          if (controller.signal.aborted) return;
          setCache((current) => {
            const next = { ...current };
            for (const domain of names) {
              if (!next[domain]) next[domain] = { bonuses: [], message: "Could not load" };
            }
            return next;
          });
        }
        done += names.length;
        setProgress({ done: Math.min(done, unique.length), total: unique.length });
      }
    };
    await Promise.all(Array.from({ length: Math.min(5, Math.ceil(unique.length / 4)) }, () => worker()));
    setRunning(false);
    setStatus(controller.signal.aborted ? "Stopped." : "Public bonuses loaded. Sorting and filters reflect live domain data.");
  }

  const pct = progress.total > 0 ? Math.round((progress.done / progress.total) * 100) : 0;

  return (
    <section className="stack">
      <div className="toolbar bpl-toolbar">
        <div className="tool-row finder-tool-row">
          <button
            type="button"
            className="tool bpl-tool"
            onClick={() => setColumns((c) => (c === "one" ? "two" : "one"))}
          >
            View: {columns === "one" ? "1 Column" : "2 Columns"}
          </button>
          <button
            type="button"
            className="tool bpl-tool"
            disabled={running || slice.length === 0}
            onClick={() => void load(slice)}
          >
            <Download size={12} style={{ display: "inline", marginRight: 3 }} />
            Load page
          </button>
          {running ? (
            <button
              type="button"
              className="tool bpl-tool active"
              onClick={() => stopRef.current?.abort()}
            >
              <Square size={11} style={{ display: "inline", marginRight: 3 }} />
              Stop ({progress.done}/{progress.total})
            </button>
          ) : (
            <button
              type="button"
              className="tool bpl-tool wallet-btn"
              disabled={filtered.length === 0}
              onClick={() => void load(filtered)}
            >
              <Download size={12} style={{ display: "inline", marginRight: 3 }} />
              Load all ({filtered.length})
            </button>
          )}
          <button
            type="button"
            className={sortFilterActive ? "tool bpl-tool sort-btn active-filter" : "tool bpl-tool sort-btn"}
            onClick={openSortModal}
          >
            <ArrowUpDown size={12} style={{ display: "inline", marginRight: 3 }} />
            <span className="tool-truncate">{sortButtonLabel}</span>
          </button>
        </div>

        <label className="search">
          <Search size={16} />
          <input
            value={query}
            placeholder="Search domains in Bonus Finder…"
            aria-label="Search domains"
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
          />
          {query ? (
            <button
              type="button"
              className="pin"
              aria-label="Clear search"
              onClick={() => {
                setQuery("");
                setPage(0);
              }}
            >
              <X size={16} />
            </button>
          ) : null}
        </label>

        <div className="finder-chip-bar" role="group" aria-label="Filter by bonus type">
          {FILTER_SLOTS.map((slot) => {
            const on = filters.slots.includes(slot.id);
            const count = slotCounts[slot.id];
            return (
              <button
                key={slot.id}
                type="button"
                className={on ? "finder-pill active" : "finder-pill"}
                aria-pressed={on}
                onClick={() => toggleSlot(slot.id)}
              >
                <span aria-hidden="true">{slot.emoji}</span>
                <span>{slot.label}</span>
                {loadedCount > 0 ? <small>{count}</small> : null}
              </button>
            );
          })}
          {sortFilterActive ? (
            <button type="button" className="finder-pill reset" onClick={resetSortModal}>
              Reset
            </button>
          ) : null}
        </div>

        {running ? (
          <div className="finder-progress-bar" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
            <span style={{ width: `${pct}%` }} />
          </div>
        ) : null}

        <div className="stats">
          <span>
            Domains <strong>{domains.length}</strong>
          </span>
          <span>
            Loaded <strong>{loadedCount}</strong>
          </span>
          <span>
            Showing <strong>{filtered.length}</strong>
          </span>
          <span className="finder-status-note">{status}</span>
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="empty">
          <p className="empty-title">
            {domains.length
              ? loadedCount === 0 && filters.slots.length > 0
                ? "Load domains first to filter by live bonuses."
                : "No domains match the current bonus filters."
              : "No domains yet. Add them in Domain Intel."}
          </p>
          {domains.length && (query || sortFilterActive) ? (
            <button
              type="button"
              className="btn"
              style={{ marginTop: 10 }}
              onClick={() => {
                setQuery("");
                resetSortModal();
              }}
            >
              Clear search &amp; filters
            </button>
          ) : null}
        </div>
      ) : null}

      <div className={columns === "two" ? "cards view-two" : "cards view-one"}>
        {slice.map((domain, index) => {
          const row = cache[domain];
          return (
            <BonusCard
              key={domain}
              domain={domain}
              rank={safePage * PAGE + index + 1}
              bonuses={row ? row.bonuses : null}
              message={row?.message}
              activeSlots={filters.slots}
              focusSlot={filters.focus}
              sortMetric={filters.sort}
              onToggleSlot={toggleSlot}
            />
          );
        })}
      </div>

      {pages > 1 ? (
        <div className="pager">
          <button type="button" className="btn" disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>
            Previous
          </button>
          <span>
            Page {safePage + 1} / {pages}
          </span>
          <button type="button" className="btn" disabled={safePage >= pages - 1} onClick={() => setPage(safePage + 1)}>
            Next
          </button>
        </div>
      ) : null}

      {sortOpen ? (
        <div className="modal-backdrop" onClick={() => setSortOpen(false)}>
          <div className="modal-card bpl-sort-modal" onClick={(event) => event.stopPropagation()}>
            <div className="header-row">
              <h3>Sort and filter bonuses</h3>
              <button type="button" className="pin" aria-label="Close" onClick={() => setSortOpen(false)}>
                <X size={18} />
              </button>
            </div>

            <div className="sort-filter-section">
              <h4>Sort by (live loaded data)</h4>
              <p className="hint">
                Orders cards directly from the bonuses grabbed on load. Cards update their highlight to match.
              </p>
              {SORT_OPTIONS.map((opt) => (
                <button
                  key={opt.id || "az"}
                  type="button"
                  className={draft.sort === opt.id ? "choice on" : "choice"}
                  onClick={() => setDraft((c) => ({ ...c, sort: opt.id }))}
                >
                  <i className="radio" />
                  <span>{opt.label}</span>
                </button>
              ))}
            </div>

            <div className="sort-filter-section">
              <h4>Focus bonus type</h4>
              <p className="hint">
                Highlights that tile on every card and focuses the Amount, Max Withdrawal, and Turnover sorts on it.
              </p>
              <button
                type="button"
                className={draft.focus === "" ? "choice on" : "choice"}
                onClick={() => setDraft((c) => ({ ...c, focus: "" }))}
              >
                <i className="radio" />
                <span>All / Best matching</span>
              </button>
              {FILTER_SLOTS.map((slot) => (
                <button
                  key={slot.id}
                  type="button"
                  className={draft.focus === slot.id ? "choice on" : "choice"}
                  onClick={() => setDraft((c) => ({ ...c, focus: slot.id }))}
                >
                  <i className="radio" />
                  <span aria-hidden="true">{slot.emoji}</span>
                  <span>
                    {slot.label}
                    {loadedCount > 0 ? ` (${slotCounts[slot.id]})` : ""}
                  </span>
                </button>
              ))}
            </div>

            <div className="sort-filter-section">
              <h4>Filter by available bonuses</h4>
              {FILTER_SLOTS.map((slot) => {
                const checked = draft.slots.includes(slot.id);
                return (
                  <button
                    key={slot.id}
                    type="button"
                    className={checked ? "choice on" : "choice"}
                    onClick={() =>
                      setDraft((c) => ({
                        ...c,
                        slots: checked ? c.slots.filter((item) => item !== slot.id) : [...c.slots, slot.id],
                      }))
                    }
                  >
                    <i className="checkbox" />
                    <span aria-hidden="true">{slot.emoji}</span>
                    <span>
                      {slot.label}
                      {loadedCount > 0 ? ` (${slotCounts[slot.id]})` : ""}
                    </span>
                  </button>
                );
              })}
              <button
                type="button"
                className={draft.loadedOnly ? "choice on" : "choice"}
                onClick={() => setDraft((c) => ({ ...c, loadedOnly: !c.loadedOnly }))}
              >
                <i className="checkbox" />
                <span>Loaded domains only</span>
              </button>
            </div>

            <div className="sort-filter-section">
              <h4>Card color</h4>
              <p className="hint">Shared glass &amp; glow style with the Main Page.</p>
              <div className="card-color-row">
                {CARD_COLORS.map((color) => (
                  <button
                    key={color.id}
                    type="button"
                    className={`card-color-swatch${color.id === "classic" ? " is-classic" : ""}${
                      state.cardColor === color.id ? " active" : ""
                    }`}
                    style={color.swatch ? ({ "--swatch": color.swatch } as CSSProperties) : undefined}
                    aria-label={color.label}
                    aria-pressed={state.cardColor === color.id}
                    onClick={() => patch({ cardColor: color.id })}
                  >
                    {color.id === "classic" ? "Off" : null}
                  </button>
                ))}
              </div>
            </div>

            <div className="sort-filter-actions">
              <button type="button" className="btn" onClick={resetSortModal}>
                Reset
              </button>
              <button type="button" className="btn btn-grad" onClick={applySortModal}>
                Apply
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}
