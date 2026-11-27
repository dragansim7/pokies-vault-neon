import { useServerFn } from "@tanstack/react-start";
import { ArrowUpDown, Eye, Search, Wallet, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { type SiteGroup } from "@/data/sites";
import { visibleSites, type ListedSite } from "@/lib/working-sites";
import { checkSiteStatus, fetchAllStatusesFn } from "@/lib/casino.functions";
import { AccountCard, type StatusName } from "@/components/account-card";
import { CARD_COLORS, useVault, type BonusMode, type SortMode, type ViewMode } from "@/components/vault";
import {
  bonusHasRealAmount,
  compareSites,
  sortModeShortLabel,
  sortRequiresSelectedBonus,
} from "@/lib/order";
import type { AccountRow } from "@/lib/parse-account";

type Listed = ListedSite;

const GROUP_LABEL: Record<SiteGroup, string> = {
  new: "New",
  best: "Best",
  unverified: "Unverified",
  share: "Instant share bonuses",
  latest: "Latest",
  custom: "Added by you",
};

const BONUS_DISPLAY_OPTIONS: { id: BonusMode; label: string; emoji: string; short: string }[] = [
  { id: "none", label: "None", emoji: "", short: "" },
  { id: "new", label: "Signup bonus", emoji: "🎁", short: "Signup" },
  { id: "share", label: "Share bonus", emoji: "🤝", short: "Share" },
  { id: "commission", label: "Commission", emoji: "💸", short: "Commission" },
  { id: "referral", label: "Referral bonus", emoji: "👥", short: "Referral" },
  { id: "rebate", label: "Rebate bonus", emoji: "♻️", short: "Rebate" },
];

const STATUS_CACHE_KEY = "vault-statuses";
const STATUS_CACHE_TTL = 15 * 60 * 1000;
const WINDOW = 48;

function readStatusCache(): Record<string, StatusName> | null {
  if (typeof localStorage === "undefined") return null;
  try {
    const raw = localStorage.getItem(STATUS_CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { at?: number; map?: Record<string, StatusName> };
    if (!parsed.at || !parsed.map || Date.now() - parsed.at >= STATUS_CACHE_TTL) return null;
    return parsed.map;
  } catch {
    return null;
  }
}

function writeStatusCache(map: Record<string, StatusName>) {
  try {
    localStorage.setItem(STATUS_CACHE_KEY, JSON.stringify({ at: Date.now(), map }));
  } catch {
    /* ignore quota */
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
  if (!tokens.length) return true;
  return tokens.every((token) => normDomain.includes(token));
}

function walletFreshnessText(timestampMs: number | undefined): string {
  if (!timestampMs || !Number.isFinite(timestampMs)) return "No saved fetch";
  const seconds = Math.max(0, (Date.now() - timestampMs) / 1000);
  const mins = Math.floor(seconds / 60);
  const hours = Math.floor(mins / 60);
  const days = Math.floor(hours / 24);
  if (mins < 1) return "Fetched just now";
  if (mins < 60) return `Fetched ${mins}m ago`;
  if (hours < 24) return `Fetched ${hours}h ago`;
  return `Fetched ${days}d ago`;
}

function bonusSortName(mode: BonusMode): string {
  if (mode === "new") return "Signup bonus";
  if (mode === "commission") return "Commission";
  if (mode === "referral") return "Referral bonus";
  if (mode === "rebate") return "Rebate bonus";
  if (mode === "share") return "Share bonus";
  return "Chosen bonus";
}

function viewModeLabel(mode: ViewMode): string {
  if (mode === "one") return "1 Column";
  if (mode === "two") return "2 Columns";
  return "Default";
}

export function Directory() {
  const { state, patch, markVisited, toggleMine } = useVault();
  const statusFn = useServerFn(checkSiteStatus);
  const allStatusesFn = useServerFn(fetchAllStatusesFn);
  const statusFnRef = useRef(statusFn);
  const allStatusesFnRef = useRef(allStatusesFn);
  statusFnRef.current = statusFn;
  allStatusesFnRef.current = allStatusesFn;

  const [query, setQuery] = useState("");
  const [searchReady, setSearchReady] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);
  const [statuses, setStatuses] = useState<Record<string, StatusName>>(() => readStatusCache() ?? {});
  const [extra, setExtra] = useState(0);
  const [toast, setToast] = useState("");
  const toastTimer = useRef<number | null>(null);

  // Staged modal state (matches bestpokieslist.com Reset / Apply workflow)
  const [draftSort, setDraftSort] = useState<SortMode>(state.sort);
  const [draftBonus, setDraftBonus] = useState<BonusMode>(state.bonusMode);
  const [draftNotVisited, setDraftNotVisited] = useState<boolean>(state.notVisited);
  const [draftSignupOnly, setDraftSignupOnly] = useState<boolean>(state.signupOnly);
  const [draftMineOnly, setDraftMineOnly] = useState<boolean>(state.mineOnly);

  function showToast(message: string) {
    setToast(message);
    if (toastTimer.current != null) window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(""), 2600);
  }

  useEffect(() => {
    return () => {
      if (toastTimer.current != null) window.clearTimeout(toastTimer.current);
    };
  }, []);

  useEffect(() => {
    setExtra(0);
  }, [query, state.sort, state.bonusMode, state.mineOnly, state.notVisited, state.signupOnly, state.view, state.wallets]);

  useEffect(() => {
    setSearchReady(true);
  }, []);

  const fetchMap = useMemo(() => {
    const map = new Map<string, AccountRow>();
    for (const row of state.lastFetch?.rows ?? []) map.set(row.domain, row);
    return map;
  }, [state.lastFetch]);

  const deadSet = useMemo(() => {
    const set = new Set<string>();
    for (const [domain, status] of Object.entries(statuses)) {
      if (status === "dead") set.add(domain);
    }
    return set;
  }, [statuses]);

  const sites = useMemo<Listed[]>(() => visibleSites(state), [state]);

  const sortFilterActive =
    state.sort !== "default" ||
    state.notVisited ||
    state.signupOnly ||
    state.mineOnly ||
    state.bonusMode !== "none";

  const filtered = useMemo(() => {
    const q = query.trim();
    const list = sites.filter((site) => {
      if (state.mineOnly && !state.mine.includes(site.domain)) return false;
      if (state.notVisited && state.visited.includes(site.domain)) return false;
      if (state.signupOnly) {
        const row = fetchMap.get(site.domain);
        if (!row?.ok || !bonusHasRealAmount(row.new)) return false;
      }
      if (q && !domainMatchesSearch(site.domain, q)) return false;
      return true;
    });

    if (state.sort === "default") {
      if (!deadSet.size) return list;
      return [...list].sort((a, b) => Number(deadSet.has(a.domain)) - Number(deadSet.has(b.domain)));
    }

    return [...list].sort((a, b) =>
      compareSites(
        a,
        b,
        fetchMap.get(a.domain),
        fetchMap.get(b.domain),
        state.sort,
        state.bonusMode,
        deadSet,
      ),
    );
  }, [
    sites,
    query,
    state.mineOnly,
    state.mine,
    state.notVisited,
    state.visited,
    state.signupOnly,
    state.sort,
    state.bonusMode,
    fetchMap,
    deadSet,
  ]);

  const grouped = !sortFilterActive && !query.trim() && state.view === "default" && !state.wallets;
  const domainKey = sites.map((site) => site.domain).join("\n");

  useEffect(() => {
    let cancelled = false;
    const domains = domainKey ? domainKey.split("\n") : [];

    const probeMissing = async (map: Record<string, StatusName>) => {
      const missing = domains.filter((domain) => !(domain in map));
      const probe = missing.slice(0, 40);
      const next = { ...map };
      for (const domain of missing.slice(40)) next[domain] = "unknown";
      if (!probe.length || cancelled) return next;
      if (!cancelled) setStatuses({ ...next });
      let cursor = 0;
      const worker = async () => {
        for (;;) {
          if (cancelled) return;
          const index = cursor;
          cursor += 1;
          if (index >= probe.length) return;
          const domain = probe[index] ?? "";
          try {
            const result = await statusFnRef.current({ data: { domain } });
            next[domain] = result.status;
          } catch {
            next[domain] = "dead";
          }
          if (!cancelled) setStatuses({ ...next });
        }
      };
      await Promise.all(Array.from({ length: Math.min(4, probe.length) }, () => worker()));
      return next;
    };

    const cached = readStatusCache();
    if (cached) {
      setStatuses(cached);
      const missing = domains.filter((domain) => !(domain in cached));
      if (missing.length) {
        void probeMissing(cached).then((filled) => {
          if (!cancelled) writeStatusCache(filled);
        });
      }
      return () => {
        cancelled = true;
      };
    }

    void (async () => {
      let map: Record<string, StatusName> = {};
      try {
        map = await allStatusesFnRef.current({ data: {} });
      } catch {
        map = {};
      }
      if (cancelled) return;
      setStatuses(map);
      const filled = await probeMissing(map);
      if (!cancelled) writeStatusCache(filled);
    })();

    return () => {
      cancelled = true;
    };
  }, [domainKey]);

  function openSortModal() {
    setDraftSort(state.sort);
    setDraftBonus(state.bonusMode);
    setDraftNotVisited(state.notVisited);
    setDraftSignupOnly(state.signupOnly);
    setDraftMineOnly(state.mineOnly);
    setSortOpen(true);
  }

  function applySortModal() {
    const bonusActive = draftBonus !== "none" || sortRequiresSelectedBonus(draftSort);
    let nextView = state.view;
    if (bonusActive && state.view !== "one") {
      nextView = "one";
    }
    patch({
      sort: draftSort,
      bonusMode: draftBonus,
      notVisited: draftNotVisited,
      signupOnly: draftSignupOnly,
      mineOnly: draftMineOnly,
      view: nextView,
    });
    setSortOpen(false);
  }

  function resetSortModal() {
    setDraftSort("default");
    setDraftBonus("none");
    setDraftNotVisited(false);
    setDraftSignupOnly(false);
    setDraftMineOnly(false);
    patch({
      sort: "default",
      bonusMode: "none",
      notVisited: false,
      signupOnly: false,
      mineOnly: false,
    });
    setSortOpen(false);
  }

  function cycleView() {
    const order: ViewMode[] = ["default", "one", "two"];
    const next = order[(order.indexOf(state.view) + 1) % order.length];
    if (next === "default") {
      patch({
        view: "default",
        wallets: false,
        sort: "default",
        bonusMode: "none",
        notVisited: false,
        signupOnly: false,
      });
      return;
    }
    if (next === "two" && (state.bonusMode !== "none" || sortRequiresSelectedBonus(state.sort))) {
      patch({
        view: "two",
        bonusMode: "none",
        sort: sortRequiresSelectedBonus(state.sort) ? "default" : state.sort,
      });
      showToast("Bonus set to None — bonus view needs 1 column");
      return;
    }
    patch({ view: next });
  }

  function toggleWallets() {
    const nextWallets = !state.wallets;
    if (nextWallets && state.view === "default") {
      patch({ wallets: true, view: "one" });
    } else {
      patch({ wallets: nextWallets });
    }
  }

  const sortButtonLabel = useMemo(() => {
    const filterCount = Number(state.notVisited) + Number(state.signupOnly) + Number(state.mineOnly);
    const parts: string[] = [];
    if (state.sort !== "default") parts.push(sortModeShortLabel(state.sort));
    if (state.bonusMode !== "none") {
      const found = BONUS_DISPLAY_OPTIONS.find((item) => item.id === state.bonusMode);
      if (found?.short) parts.push(found.short);
    }
    if (filterCount) parts.push(`${filterCount} Filter${filterCount === 1 ? "" : "s"}`);
    if (filtered.length > 0) parts.push(String(filtered.length));
    if (parts.length === 1 && filtered.length > 0) return `Sort / Filter · ${filtered.length}`;
    return parts.length ? parts.join(" · ") : "Sort / Filter";
  }, [state.sort, state.bonusMode, state.notVisited, state.signupOnly, state.mineOnly, filtered.length]);

  function renderCard(
    site: Listed,
    index: number,
    variant: "default" | "new" | "best" | "unverified" | "share" = "default",
  ) {
    const row = fetchMap.get(site.domain);
    const account = row?.ok ? row : null;
    const errorRow = row && !row.ok ? row : null;
    return (
      <AccountCard
        key={site.domain}
        domain={site.domain}
        rank={index + 1}
        addedAt={site.addedAt}
        status={statuses[site.domain] ?? "checking"}
        account={account}
        errorRow={errorRow}
        walletMode={state.wallets}
        bonusMode={state.bonusMode}
        sortMode={state.sort}
        variant={variant}
        visited={state.visited.includes(site.domain)}
        showVisited={state.notVisited}
        saved={state.mine.includes(site.domain)}
        onVisit={() => markVisited(site.domain)}
        onToggleSave={() => toggleMine(site.domain)}
      />
    );
  }

  const visitedCount = sites.filter((site) => state.visited.includes(site.domain)).length;
  const notVisitedCount = Math.max(0, sites.length - visitedCount);
  const gridClass =
    state.view === "one"
      ? "cards view-one"
      : state.view === "two"
        ? "cards view-two"
        : state.wallets
          ? "cards view-one"
          : "cards bpl-latest-grid";

  return (
    <>
      <section className="toolbar bpl-toolbar">
        <div className="tool-row">
          <button
            type="button"
            className={state.notVisited ? "tool bpl-tool active" : "tool bpl-tool"}
            onClick={() => patch({ notVisited: !state.notVisited })}
          >
            <Eye size={12} style={{ display: "inline", marginRight: 3 }} />
            Not Visited: {state.notVisited ? "On" : "Off"}
          </button>
          <button
            type="button"
            className={state.view !== "default" ? "tool bpl-tool active" : "tool bpl-tool"}
            onClick={cycleView}
          >
            View: {viewModeLabel(state.view)}
          </button>
          <button
            type="button"
            className={state.wallets ? "tool bpl-tool active-wallet" : "tool bpl-tool wallet-btn"}
            onClick={toggleWallets}
            title={state.wallets ? walletFreshnessText(state.lastFetch?.at) : "Show saved wallet balances on domain cards"}
          >
            <span className="wallet-btn-main">
              <Wallet size={12} style={{ display: "inline", marginRight: 3 }} />
              Wallets: {state.wallets ? "On" : "Off"}
            </span>
            {state.wallets ? (
              <small className="wallet-freshness">{walletFreshnessText(state.lastFetch?.at)}</small>
            ) : null}
          </button>
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
          {searchReady ? (
            <input
              value={query}
              placeholder="Search casinos or domains…"
              aria-label="Search casinos or domains"
              onChange={(event) => setQuery(event.target.value)}
            />
          ) : (
            <span className="search-ghost">Search casinos or domains…</span>
          )}
          {query ? (
            <button type="button" className="pin" aria-label="Clear search" onClick={() => setQuery("")}>
              <X size={16} />
            </button>
          ) : null}
        </label>

        <div className="stats">
          <span>
            Total sites <strong>{sites.length}</strong>
          </span>
          <span>
            Visited <strong>{visitedCount}</strong>
          </span>
          <span>
            Not Yet Visited <strong>{notVisitedCount}</strong>
          </span>
          <span>
            My accounts <strong>{state.mine.length}</strong>
          </span>
        </div>
      </section>

      <section className="banner">
        <div>
          <strong>How your accounts work</strong>
          <p>
            Bookmark a site, then open the fetcher and sign in with the phone number and password you already
            use. Only sites that accept that login are kept. Nothing here is paid — no fetch packs and no rented cards.
          </p>
        </div>
      </section>

      {filtered.length === 0 ? (
        <div className="empty">
          <p className="empty-title">{sites.length ? "No matching domains found." : "No domains yet. Add them in Domain Intel."}</p>
          {sites.length && (query || sortFilterActive) ? (
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

      {grouped ? (
        <>
          {(["new", "best", "unverified"] as const).some((group) => filtered.some((site) => site.group === group)) ? (
            <div className="feature-frame bpl-top-block">
              <div className="top-cols">
                {(["new", "best", "unverified"] as const).map((group) => {
                  const items = filtered.filter((site) => site.group === group);
                  if (!items.length) return null;
                  return (
                    <div key={group}>
                      <h2 className={`bpl-group-heading heading-${group}`}>
                        {GROUP_LABEL[group]} · {items.length}
                      </h2>
                      <div className="top-col">
                        {items.map((site, index) => renderCard(site, index, group))}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ) : null}
          {(["share", "custom", "latest"] as const).map((group) => {
            const items = filtered.filter((site) => site.group === group);
            if (!items.length) return null;
            const visible = group === "latest" ? items.slice(0, WINDOW + extra) : items;
            return (
              <section key={group} className={group === "share" ? "bpl-bonus-section" : undefined}>
                <h2 className="section-label">
                  {GROUP_LABEL[group]} · {items.length}
                </h2>
                <div className="cards bpl-latest-grid">
                  {visible.map((site, index) => renderCard(site, index, group === "share" ? "share" : "default"))}
                </div>
                {visible.length < items.length ? (
                  <div className="pager">
                    <button type="button" className="btn" onClick={() => setExtra((value) => value + WINDOW)}>
                      Show {Math.min(WINDOW, items.length - visible.length)} more
                    </button>
                    <span>
                      {visible.length} of {items.length}
                    </span>
                  </div>
                ) : null}
              </section>
            );
          })}
        </>
      ) : (
        <>
          <div className={gridClass}>
            {filtered.slice(0, WINDOW + extra).map((site, index) => renderCard(site, index))}
          </div>
          {WINDOW + extra < filtered.length ? (
            <div className="pager">
              <button type="button" className="btn" onClick={() => setExtra((value) => value + WINDOW)}>
                Show {Math.min(WINDOW, filtered.length - (WINDOW + extra))} more
              </button>
              <span>
                {Math.min(WINDOW + extra, filtered.length)} of {filtered.length}
              </span>
            </div>
          ) : null}
        </>
      )}

      {toast ? (
        <div className="bpl-toast" role="status" aria-live="polite">
          {toast}
        </div>
      ) : null}

      {sortOpen ? (
        <div className="modal-backdrop" onClick={() => setSortOpen(false)}>
          <div className="modal-card bpl-sort-modal" onClick={(event) => event.stopPropagation()}>
            <div className="header-row">
              <h3>Sort and filter</h3>
              <button type="button" className="pin" aria-label="Close" onClick={() => setSortOpen(false)}>
                <X size={18} />
              </button>
            </div>

            <div className="sort-filter-section">
              <h4>Sort by</h4>
              <p className="hint">Same order for the homepage cards and the bonus fetcher results.</p>
              {(
                [
                  ["default", "Default order"],
                  ["az", "Domain name — A → Z"],
                  ["za", "Domain name — Z → A"],
                  ["balance", "Highest balance"],
                  ["turnover", "Best turnover"],
                  ["cashout", "Lowest cash-out"],
                  ["errors", "Errors"],
                  ["popular", "Most popular"],
                  ["norefer", "Accounts without referlinks"],
                  ["shareAmount", `${bonusSortName(draftBonus)} — biggest amount`],
                  ["shareMax", `${bonusSortName(draftBonus)} — highest max`],
                  ["shareTurn", `${bonusSortName(draftBonus)} — lowest turnover`],
                  ["new", "Newest"],
                ] as const
              ).map(([id, label]) => (
                <button
                  key={id}
                  type="button"
                  className={draftSort === id ? "choice on" : "choice"}
                  onClick={() => setDraftSort(id)}
                >
                  <i className="radio" />
                  <span>{label}</span>
                </button>
              ))}
            </div>

            <div className="sort-filter-section">
              <h4>Bonus display</h4>
              <p className="hint">
                Highlights that bonus on every card. The three “chosen bonus” sorts above use it.
              </p>
              {BONUS_DISPLAY_OPTIONS.map((opt) => (
                <button
                  key={opt.id}
                  type="button"
                  className={draftBonus === opt.id ? "choice on" : "choice"}
                  onClick={() => setDraftBonus(opt.id)}
                >
                  <i className="radio" />
                  {opt.emoji ? <span aria-hidden="true">{opt.emoji}</span> : null}
                  <span>{opt.label}</span>
                </button>
              ))}
            </div>

            <div className="sort-filter-section">
              <h4>Card color</h4>
              <p className="hint">Glow on the domain cards. Changes straight away.</p>
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

            <div className="sort-filter-section">
              <h4>Filter</h4>
              <button
                type="button"
                className={draftNotVisited ? "choice on" : "choice"}
                onClick={() => setDraftNotVisited((v) => !v)}
              >
                <i className="checkbox" />
                <span>Not visited domains only</span>
              </button>
              <button
                type="button"
                className={draftSignupOnly ? "choice on" : "choice"}
                onClick={() => setDraftSignupOnly((v) => !v)}
              >
                <i className="checkbox" />
                <span>Signup bonuses only</span>
              </button>
              <button
                type="button"
                className={draftMineOnly ? "choice on" : "choice"}
                onClick={() => setDraftMineOnly((v) => !v)}
              >
                <i className="checkbox" />
                <span>My accounts only</span>
              </button>
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
    </>
  );
}
