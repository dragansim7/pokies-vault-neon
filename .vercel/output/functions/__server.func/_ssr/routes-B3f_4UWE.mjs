import { o as __toESM } from "../_runtime.mjs";
import { i as fetchAllStatusesFn, l as useServerFn, n as checkSiteStatus, t as AppFrame } from "./shell-DWuSpmKc.mjs";
import { c as sortModeShortLabel, d as visibleSites, i as bonusHasRealAmount, l as sortRequiresSelectedBonus, n as CARD_COLORS, o as compareSites, r as useVault } from "./router-yRR4M0i7.mjs";
import { t as AccountCard } from "./account-card-DJ7DBoUD.mjs";
import { Gt as require_react } from "../_libs/@tanstack/react-router+[...].mjs";
import { T as ArrowUpDown, n as X, r as Wallet, u as Search, v as Eye } from "../_libs/lucide-react.mjs";
import { t as require_jsx_dev_runtime } from "../_libs/react.mjs";
// node_modules/.nitro/vite/services/ssr/assets/routes-B3f_4UWE.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_dev_runtime = require_jsx_dev_runtime();
var _jsxFileName$1 = "/app/applet/src/components/directory.tsx";
var GROUP_LABEL = {
	new: "New",
	best: "Best",
	unverified: "Unverified",
	share: "Instant share bonuses",
	latest: "Latest",
	custom: "Added by you"
};
var BONUS_DISPLAY_OPTIONS = [
	{
		id: "none",
		label: "None",
		emoji: "",
		short: ""
	},
	{
		id: "new",
		label: "Signup bonus",
		emoji: "🎁",
		short: "Signup"
	},
	{
		id: "share",
		label: "Share bonus",
		emoji: "🤝",
		short: "Share"
	},
	{
		id: "commission",
		label: "Commission",
		emoji: "💸",
		short: "Commission"
	},
	{
		id: "referral",
		label: "Referral bonus",
		emoji: "👥",
		short: "Referral"
	},
	{
		id: "rebate",
		label: "Rebate bonus",
		emoji: "♻️",
		short: "Rebate"
	}
];
var STATUS_CACHE_KEY = "vault-statuses";
var STATUS_CACHE_TTL = 9e5;
var WINDOW = 48;
function readStatusCache() {
	if (typeof localStorage === "undefined") return null;
	try {
		const raw = localStorage.getItem(STATUS_CACHE_KEY);
		if (!raw) return null;
		const parsed = JSON.parse(raw);
		if (!parsed.at || !parsed.map || Date.now() - parsed.at >= STATUS_CACHE_TTL) return null;
		return parsed.map;
	} catch {
		return null;
	}
}
function writeStatusCache(map) {
	try {
		localStorage.setItem(STATUS_CACHE_KEY, JSON.stringify({
			at: Date.now(),
			map
		}));
	} catch {}
}
function normalizeSearch(value) {
	return value.toLowerCase().trim().replace(/^https?:\/\//i, "").replace(/^www\./i, "").split(/[/?#]/, 1)[0].replace(/[^a-z0-9]/g, "");
}
function domainMatchesSearch(domain, query) {
	const raw = query.toLowerCase().trim();
	if (!raw) return true;
	const normQuery = normalizeSearch(raw);
	if (!normQuery) return true;
	const normDomain = normalizeSearch(domain);
	if (normDomain.includes(normQuery)) return true;
	const tokens = raw.replace(/^https?:\/\//i, "").replace(/^www\./i, "").split(/[^a-z0-9]+/).map(normalizeSearch).filter(Boolean);
	if (!tokens.length) return true;
	return tokens.every((token) => normDomain.includes(token));
}
function walletFreshnessText(timestampMs) {
	if (!timestampMs || !Number.isFinite(timestampMs)) return "No saved fetch";
	const seconds = Math.max(0, (Date.now() - timestampMs) / 1e3);
	const mins = Math.floor(seconds / 60);
	const hours = Math.floor(mins / 60);
	const days = Math.floor(hours / 24);
	if (mins < 1) return "Fetched just now";
	if (mins < 60) return `Fetched ${mins}m ago`;
	if (hours < 24) return `Fetched ${hours}h ago`;
	return `Fetched ${days}d ago`;
}
function bonusSortName(mode) {
	if (mode === "new") return "Signup bonus";
	if (mode === "commission") return "Commission";
	if (mode === "referral") return "Referral bonus";
	if (mode === "rebate") return "Rebate bonus";
	if (mode === "share") return "Share bonus";
	return "Chosen bonus";
}
function viewModeLabel(mode) {
	if (mode === "one") return "1 Column";
	if (mode === "two") return "2 Columns";
	return "Default";
}
function Directory() {
	const { state, patch, markVisited, toggleMine } = useVault();
	const statusFn = useServerFn(checkSiteStatus);
	const allStatusesFn = useServerFn(fetchAllStatusesFn);
	const statusFnRef = (0, import_react.useRef)(statusFn);
	const allStatusesFnRef = (0, import_react.useRef)(allStatusesFn);
	statusFnRef.current = statusFn;
	allStatusesFnRef.current = allStatusesFn;
	const [query, setQuery] = (0, import_react.useState)("");
	const [searchReady, setSearchReady] = (0, import_react.useState)(false);
	const [sortOpen, setSortOpen] = (0, import_react.useState)(false);
	const [statuses, setStatuses] = (0, import_react.useState)(() => readStatusCache() ?? {});
	const [extra, setExtra] = (0, import_react.useState)(0);
	const [toast, setToast] = (0, import_react.useState)("");
	const toastTimer = (0, import_react.useRef)(null);
	const [draftSort, setDraftSort] = (0, import_react.useState)(state.sort);
	const [draftBonus, setDraftBonus] = (0, import_react.useState)(state.bonusMode);
	const [draftNotVisited, setDraftNotVisited] = (0, import_react.useState)(state.notVisited);
	const [draftSignupOnly, setDraftSignupOnly] = (0, import_react.useState)(state.signupOnly);
	const [draftMineOnly, setDraftMineOnly] = (0, import_react.useState)(state.mineOnly);
	function showToast(message) {
		setToast(message);
		if (toastTimer.current != null) window.clearTimeout(toastTimer.current);
		toastTimer.current = window.setTimeout(() => setToast(""), 2600);
	}
	(0, import_react.useEffect)(() => {
		return () => {
			if (toastTimer.current != null) window.clearTimeout(toastTimer.current);
		};
	}, []);
	(0, import_react.useEffect)(() => {
		setExtra(0);
	}, [
		query,
		state.sort,
		state.bonusMode,
		state.mineOnly,
		state.notVisited,
		state.signupOnly,
		state.view,
		state.wallets
	]);
	(0, import_react.useEffect)(() => {
		setSearchReady(true);
	}, []);
	const fetchMap = (0, import_react.useMemo)(() => {
		const map = /* @__PURE__ */ new Map();
		for (const row of state.lastFetch?.rows ?? []) map.set(row.domain, row);
		return map;
	}, [state.lastFetch]);
	const deadSet = (0, import_react.useMemo)(() => {
		const set = /* @__PURE__ */ new Set();
		for (const [domain, status] of Object.entries(statuses)) if (status === "dead") set.add(domain);
		return set;
	}, [statuses]);
	const sites = (0, import_react.useMemo)(() => visibleSites(state), [state]);
	const sortFilterActive = state.sort !== "default" || state.notVisited || state.signupOnly || state.mineOnly || state.bonusMode !== "none";
	const filtered = (0, import_react.useMemo)(() => {
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
		return [...list].sort((a, b) => compareSites(a, b, fetchMap.get(a.domain), fetchMap.get(b.domain), state.sort, state.bonusMode, deadSet));
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
		deadSet
	]);
	const grouped = !sortFilterActive && !query.trim() && state.view === "default" && !state.wallets;
	const domainKey = sites.map((site) => site.domain).join("\n");
	(0, import_react.useEffect)(() => {
		let cancelled = false;
		const domains = domainKey ? domainKey.split("\n") : [];
		const probeMissing = async (map) => {
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
			if (domains.filter((domain) => !(domain in cached)).length) probeMissing(cached).then((filled) => {
				if (!cancelled) writeStatusCache(filled);
			});
			return () => {
				cancelled = true;
			};
		}
		(async () => {
			let map = {};
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
		if (bonusActive && state.view !== "one") nextView = "one";
		patch({
			sort: draftSort,
			bonusMode: draftBonus,
			notVisited: draftNotVisited,
			signupOnly: draftSignupOnly,
			mineOnly: draftMineOnly,
			view: nextView
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
			mineOnly: false
		});
		setSortOpen(false);
	}
	function cycleView() {
		const order = [
			"default",
			"one",
			"two"
		];
		const next = order[(order.indexOf(state.view) + 1) % order.length];
		if (next === "default") {
			patch({
				view: "default",
				wallets: false,
				sort: "default",
				bonusMode: "none",
				notVisited: false,
				signupOnly: false
			});
			return;
		}
		if (next === "two" && (state.bonusMode !== "none" || sortRequiresSelectedBonus(state.sort))) {
			patch({
				view: "two",
				bonusMode: "none",
				sort: sortRequiresSelectedBonus(state.sort) ? "default" : state.sort
			});
			showToast("Bonus set to None — bonus view needs 1 column");
			return;
		}
		patch({ view: next });
	}
	function toggleWallets() {
		const nextWallets = !state.wallets;
		if (nextWallets && state.view === "default") patch({
			wallets: true,
			view: "one"
		});
		else patch({ wallets: nextWallets });
	}
	const sortButtonLabel = (0, import_react.useMemo)(() => {
		const filterCount = Number(state.notVisited) + Number(state.signupOnly) + Number(state.mineOnly);
		const parts = [];
		if (state.sort !== "default") parts.push(sortModeShortLabel(state.sort));
		if (state.bonusMode !== "none") {
			const found = BONUS_DISPLAY_OPTIONS.find((item) => item.id === state.bonusMode);
			if (found?.short) parts.push(found.short);
		}
		if (filterCount) parts.push(`${filterCount} Filter${filterCount === 1 ? "" : "s"}`);
		if (filtered.length > 0) parts.push(String(filtered.length));
		if (parts.length === 1 && filtered.length > 0) return `Sort / Filter · ${filtered.length}`;
		return parts.length ? parts.join(" · ") : "Sort / Filter";
	}, [
		state.sort,
		state.bonusMode,
		state.notVisited,
		state.signupOnly,
		state.mineOnly,
		filtered.length
	]);
	function renderCard(site, index, variant = "default") {
		const row = fetchMap.get(site.domain);
		const account = row?.ok ? row : null;
		const errorRow = row && !row.ok ? row : null;
		return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(AccountCard, {
			domain: site.domain,
			rank: index + 1,
			addedAt: site.addedAt,
			status: statuses[site.domain] ?? "checking",
			account,
			errorRow,
			walletMode: state.wallets,
			bonusMode: state.bonusMode,
			sortMode: state.sort,
			variant,
			visited: state.visited.includes(site.domain),
			showVisited: state.notVisited,
			saved: state.mine.includes(site.domain),
			onVisit: () => markVisited(site.domain),
			onToggleSave: () => toggleMine(site.domain)
		}, site.domain, false, {
			fileName: _jsxFileName$1,
			lineNumber: 393,
			columnNumber: 7
		}, this);
	}
	const visitedCount = sites.filter((site) => state.visited.includes(site.domain)).length;
	const notVisitedCount = Math.max(0, sites.length - visitedCount);
	const gridClass = state.view === "one" ? "cards view-one" : state.view === "two" ? "cards view-two" : state.wallets ? "cards view-one" : "cards bpl-latest-grid";
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [
		/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
			className: "toolbar bpl-toolbar",
			children: [
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "tool-row",
					children: [
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: state.notVisited ? "tool bpl-tool active" : "tool bpl-tool",
							onClick: () => patch({ notVisited: !state.notVisited }),
							children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Eye, {
									size: 12,
									style: {
										display: "inline",
										marginRight: 3
									}
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 434,
									columnNumber: 13
								}, this),
								"Not Visited: ",
								state.notVisited ? "On" : "Off"
							]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 429,
							columnNumber: 11
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: state.view !== "default" ? "tool bpl-tool active" : "tool bpl-tool",
							onClick: cycleView,
							children: ["View: ", viewModeLabel(state.view)]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 437,
							columnNumber: 11
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: state.wallets ? "tool bpl-tool active-wallet" : "tool bpl-tool wallet-btn",
							onClick: toggleWallets,
							title: state.wallets ? walletFreshnessText(state.lastFetch?.at) : "Show saved wallet balances on domain cards",
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "wallet-btn-main",
								children: [
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Wallet, {
										size: 12,
										style: {
											display: "inline",
											marginRight: 3
										}
									}, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 451,
										columnNumber: 15
									}, this),
									"Wallets: ",
									state.wallets ? "On" : "Off"
								]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 450,
								columnNumber: 13
							}, this), state.wallets ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("small", {
								className: "wallet-freshness",
								children: walletFreshnessText(state.lastFetch?.at)
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 455,
								columnNumber: 15
							}, this) : null]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 444,
							columnNumber: 11
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: sortFilterActive ? "tool bpl-tool sort-btn active-filter" : "tool bpl-tool sort-btn",
							onClick: openSortModal,
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(ArrowUpDown, {
								size: 12,
								style: {
									display: "inline",
									marginRight: 3
								}
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 463,
								columnNumber: 13
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "tool-truncate",
								children: sortButtonLabel
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 464,
								columnNumber: 13
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 458,
							columnNumber: 11
						}, this)
					]
				}, void 0, true, {
					fileName: _jsxFileName$1,
					lineNumber: 428,
					columnNumber: 9
				}, this),
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
					className: "search",
					children: [
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Search, { size: 16 }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 469,
							columnNumber: 11
						}, this),
						searchReady ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
							value: query,
							placeholder: "Search casinos or domains…",
							"aria-label": "Search casinos or domains",
							onChange: (event) => setQuery(event.target.value)
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 471,
							columnNumber: 13
						}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
							className: "search-ghost",
							children: "Search casinos or domains…"
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 478,
							columnNumber: 13
						}, this),
						query ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "pin",
							"aria-label": "Clear search",
							onClick: () => setQuery(""),
							children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(X, { size: 16 }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 482,
								columnNumber: 15
							}, this)
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 481,
							columnNumber: 13
						}, this) : null
					]
				}, void 0, true, {
					fileName: _jsxFileName$1,
					lineNumber: 468,
					columnNumber: 9
				}, this),
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "stats",
					children: [
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: ["Total sites ", /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: sites.length }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 489,
							columnNumber: 25
						}, this)] }, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 488,
							columnNumber: 11
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: ["Visited ", /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: visitedCount }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 492,
							columnNumber: 21
						}, this)] }, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 491,
							columnNumber: 11
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: ["Not Yet Visited ", /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: notVisitedCount }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 495,
							columnNumber: 29
						}, this)] }, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 494,
							columnNumber: 11
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: ["My accounts ", /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: state.mine.length }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 498,
							columnNumber: 25
						}, this)] }, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 497,
							columnNumber: 11
						}, this)
					]
				}, void 0, true, {
					fileName: _jsxFileName$1,
					lineNumber: 487,
					columnNumber: 9
				}, this)
			]
		}, void 0, true, {
			fileName: _jsxFileName$1,
			lineNumber: 427,
			columnNumber: 7
		}, this),
		/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
			className: "banner",
			children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: "How your accounts work" }, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 505,
				columnNumber: 11
			}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", { children: "Bookmark a site, then open the fetcher and sign in with the phone number and password you already use. Only sites that accept that login are kept. Nothing here is paid — no fetch packs and no rented cards." }, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 506,
				columnNumber: 11
			}, this)] }, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 504,
				columnNumber: 9
			}, this)
		}, void 0, false, {
			fileName: _jsxFileName$1,
			lineNumber: 503,
			columnNumber: 7
		}, this),
		filtered.length === 0 ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
			className: "empty",
			children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
				className: "empty-title",
				children: sites.length ? "No matching domains found." : "No domains yet. Add them in Domain Intel."
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 515,
				columnNumber: 11
			}, this), sites.length && (query || sortFilterActive) ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
				type: "button",
				className: "btn",
				style: { marginTop: 10 },
				onClick: () => {
					setQuery("");
					resetSortModal();
				},
				children: "Clear search & filters"
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 517,
				columnNumber: 13
			}, this) : null]
		}, void 0, true, {
			fileName: _jsxFileName$1,
			lineNumber: 514,
			columnNumber: 9
		}, this) : null,
		grouped ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [[
			"new",
			"best",
			"unverified"
		].some((group) => filtered.some((site) => site.group === group)) ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
			className: "feature-frame bpl-top-block",
			children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "top-cols",
				children: [
					"new",
					"best",
					"unverified"
				].map((group) => {
					const items = filtered.filter((site) => site.group === group);
					if (!items.length) return null;
					return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h2", {
						className: `bpl-group-heading heading-${group}`,
						children: [
							GROUP_LABEL[group],
							" · ",
							items.length
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 542,
						columnNumber: 23
					}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "top-col",
						children: items.map((site, index) => renderCard(site, index, group))
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 545,
						columnNumber: 23
					}, this)] }, group, true, {
						fileName: _jsxFileName$1,
						lineNumber: 541,
						columnNumber: 21
					}, this);
				})
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 536,
				columnNumber: 15
			}, this)
		}, void 0, false, {
			fileName: _jsxFileName$1,
			lineNumber: 535,
			columnNumber: 13
		}, this) : null, [
			"share",
			"custom",
			"latest"
		].map((group) => {
			const items = filtered.filter((site) => site.group === group);
			if (!items.length) return null;
			const visible = group === "latest" ? items.slice(0, WINDOW + extra) : items;
			return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
				className: group === "share" ? "bpl-bonus-section" : void 0,
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h2", {
						className: "section-label",
						children: [
							GROUP_LABEL[group],
							" · ",
							items.length
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 560,
						columnNumber: 17
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "cards bpl-latest-grid",
						children: visible.map((site, index) => renderCard(site, index, group === "share" ? "share" : "default"))
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 563,
						columnNumber: 17
					}, this),
					visible.length < items.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "pager",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "btn",
							onClick: () => setExtra((value) => value + WINDOW),
							children: [
								"Show ",
								Math.min(WINDOW, items.length - visible.length),
								" more"
							]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 568,
							columnNumber: 21
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [
							visible.length,
							" of ",
							items.length
						] }, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 571,
							columnNumber: 21
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 567,
						columnNumber: 19
					}, this) : null
				]
			}, group, true, {
				fileName: _jsxFileName$1,
				lineNumber: 559,
				columnNumber: 15
			}, this);
		})] }, void 0, true, {
			fileName: _jsxFileName$1,
			lineNumber: 533,
			columnNumber: 9
		}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
			className: gridClass,
			children: filtered.slice(0, WINDOW + extra).map((site, index) => renderCard(site, index))
		}, void 0, false, {
			fileName: _jsxFileName$1,
			lineNumber: 582,
			columnNumber: 11
		}, this), WINDOW + extra < filtered.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
			className: "pager",
			children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
				type: "button",
				className: "btn",
				onClick: () => setExtra((value) => value + WINDOW),
				children: [
					"Show ",
					Math.min(WINDOW, filtered.length - (WINDOW + extra)),
					" more"
				]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 587,
				columnNumber: 15
			}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [
				Math.min(WINDOW + extra, filtered.length),
				" of ",
				filtered.length
			] }, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 590,
				columnNumber: 15
			}, this)]
		}, void 0, true, {
			fileName: _jsxFileName$1,
			lineNumber: 586,
			columnNumber: 13
		}, this) : null] }, void 0, true, {
			fileName: _jsxFileName$1,
			lineNumber: 581,
			columnNumber: 9
		}, this),
		toast ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
			className: "bpl-toast",
			role: "status",
			"aria-live": "polite",
			children: toast
		}, void 0, false, {
			fileName: _jsxFileName$1,
			lineNumber: 599,
			columnNumber: 9
		}, this) : null,
		sortOpen ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
			className: "modal-backdrop",
			onClick: () => setSortOpen(false),
			children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "modal-card bpl-sort-modal",
				onClick: (event) => event.stopPropagation(),
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "header-row",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", { children: "Sort and filter" }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 608,
							columnNumber: 15
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "pin",
							"aria-label": "Close",
							onClick: () => setSortOpen(false),
							children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(X, { size: 18 }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 610,
								columnNumber: 17
							}, this)
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 609,
							columnNumber: 15
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 607,
						columnNumber: 13
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "sort-filter-section",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h4", { children: "Sort by" }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 615,
								columnNumber: 15
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "hint",
								children: "Same order for the homepage cards and the bonus fetcher results."
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 616,
								columnNumber: 15
							}, this),
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
								["new", "Newest"]
							].map(([id, label]) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: draftSort === id ? "choice on" : "choice",
								onClick: () => setDraftSort(id),
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "radio" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 640,
									columnNumber: 19
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: label }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 641,
									columnNumber: 19
								}, this)]
							}, id, true, {
								fileName: _jsxFileName$1,
								lineNumber: 634,
								columnNumber: 17
							}, this))
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 614,
						columnNumber: 13
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "sort-filter-section",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h4", { children: "Bonus display" }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 647,
								columnNumber: 15
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "hint",
								children: "Highlights that bonus on every card. The three “chosen bonus” sorts above use it."
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 648,
								columnNumber: 15
							}, this),
							BONUS_DISPLAY_OPTIONS.map((opt) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: draftBonus === opt.id ? "choice on" : "choice",
								onClick: () => setDraftBonus(opt.id),
								children: [
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "radio" }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 658,
										columnNumber: 19
									}, this),
									opt.emoji ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
										"aria-hidden": "true",
										children: opt.emoji
									}, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 659,
										columnNumber: 32
									}, this) : null,
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: opt.label }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 660,
										columnNumber: 19
									}, this)
								]
							}, opt.id, true, {
								fileName: _jsxFileName$1,
								lineNumber: 652,
								columnNumber: 17
							}, this))
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 646,
						columnNumber: 13
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "sort-filter-section",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h4", { children: "Card color" }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 666,
								columnNumber: 15
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "hint",
								children: "Glow on the domain cards. Changes straight away."
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 667,
								columnNumber: 15
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
								className: "card-color-row",
								children: CARD_COLORS.map((color) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
									type: "button",
									className: `card-color-swatch${color.id === "classic" ? " is-classic" : ""}${state.cardColor === color.id ? " active" : ""}`,
									style: color.swatch ? { "--swatch": color.swatch } : void 0,
									"aria-label": color.label,
									"aria-pressed": state.cardColor === color.id,
									onClick: () => patch({ cardColor: color.id }),
									children: color.id === "classic" ? "Off" : null
								}, color.id, false, {
									fileName: _jsxFileName$1,
									lineNumber: 670,
									columnNumber: 19
								}, this))
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 668,
								columnNumber: 15
							}, this)
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 665,
						columnNumber: 13
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "sort-filter-section",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h4", { children: "Filter" }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 688,
								columnNumber: 15
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: draftNotVisited ? "choice on" : "choice",
								onClick: () => setDraftNotVisited((v) => !v),
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "checkbox" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 694,
									columnNumber: 17
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "Not visited domains only" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 695,
									columnNumber: 17
								}, this)]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 689,
								columnNumber: 15
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: draftSignupOnly ? "choice on" : "choice",
								onClick: () => setDraftSignupOnly((v) => !v),
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "checkbox" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 702,
									columnNumber: 17
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "Signup bonuses only" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 703,
									columnNumber: 17
								}, this)]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 697,
								columnNumber: 15
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: draftMineOnly ? "choice on" : "choice",
								onClick: () => setDraftMineOnly((v) => !v),
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "checkbox" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 710,
									columnNumber: 17
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "My accounts only" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 711,
									columnNumber: 17
								}, this)]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 705,
								columnNumber: 15
							}, this)
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 687,
						columnNumber: 13
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "sort-filter-actions",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "btn",
							onClick: resetSortModal,
							children: "Reset"
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 716,
							columnNumber: 15
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "btn btn-grad",
							onClick: applySortModal,
							children: "Apply"
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 719,
							columnNumber: 15
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 715,
						columnNumber: 13
					}, this)
				]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 606,
				columnNumber: 11
			}, this)
		}, void 0, false, {
			fileName: _jsxFileName$1,
			lineNumber: 605,
			columnNumber: 9
		}, this) : null
	] }, void 0, true, {
		fileName: _jsxFileName$1,
		lineNumber: 426,
		columnNumber: 5
	}, this);
}
var _jsxFileName = "/app/applet/src/routes/index.tsx?tsr-split=component";
function Home() {
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(AppFrame, { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Directory, {}, void 0, false, {
		fileName: _jsxFileName,
		lineNumber: 5,
		columnNumber: 7
	}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
		className: "story",
		children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h2", { children: "One place for the sites you actually use" }, void 0, false, {
			fileName: _jsxFileName,
			lineNumber: 7,
			columnNumber: 9
		}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
			className: "story-grid",
			children: [
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("article", {
					className: "story-card",
					children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", { children: "The list" }, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 10,
						columnNumber: 13
					}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", { children: "The list starts empty. Add the sites you use in Domain Intel. Nothing is loaded for you." }, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 11,
						columnNumber: 13
					}, this)]
				}, void 0, true, {
					fileName: _jsxFileName,
					lineNumber: 9,
					columnNumber: 11
				}, this),
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("article", {
					className: "story-card",
					children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", { children: "The fetch" }, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 16,
						columnNumber: 13
					}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", { children: "The login that worked is remembered for that site and tried first next time. New sites use the phone and password you enter. A hit keeps the site. A rejection does not." }, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 17,
						columnNumber: 13
					}, this)]
				}, void 0, true, {
					fileName: _jsxFileName,
					lineNumber: 15,
					columnNumber: 11
				}, this),
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("article", {
					className: "story-card",
					children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", { children: "What you see" }, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 23,
						columnNumber: 13
					}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", { children: "Cash, commission, share, referral, welcome, and rebate — plus the rest of the bonuses sitting on the account. Unlimited checks. No credits to buy." }, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 24,
						columnNumber: 13
					}, this)]
				}, void 0, true, {
					fileName: _jsxFileName,
					lineNumber: 22,
					columnNumber: 11
				}, this)
			]
		}, void 0, true, {
			fileName: _jsxFileName,
			lineNumber: 8,
			columnNumber: 9
		}, this)]
	}, void 0, true, {
		fileName: _jsxFileName,
		lineNumber: 6,
		columnNumber: 7
	}, this)] }, void 0, true, {
		fileName: _jsxFileName,
		lineNumber: 4,
		columnNumber: 10
	}, this);
}
export { Home as component };
