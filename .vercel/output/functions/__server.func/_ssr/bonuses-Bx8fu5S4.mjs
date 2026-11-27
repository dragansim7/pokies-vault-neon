import { o as __toESM } from "../_runtime.mjs";
import { l as useServerFn, o as loadPublicCatalogueFn, t as AppFrame } from "./shell-DWuSpmKc.mjs";
import { i as moneyShort, t as SiteIcon } from "./format-Dbq6jmzA.mjs";
import { n as bonusMetricValue, r as domainBonusMetric, t as bestBonusForMetric } from "./public-bonus-CyJnVOiu.mjs";
import { d as visibleSites, n as CARD_COLORS, r as useVault } from "./router-yRR4M0i7.mjs";
import { Gt as require_react } from "../_libs/@tanstack/react-router+[...].mjs";
import { C as ChevronDown, T as ArrowUpDown, b as Download, c as Square, l as Sparkles, n as X, u as Search, x as ChevronUp } from "../_libs/lucide-react.mjs";
import { t as require_jsx_dev_runtime } from "../_libs/react.mjs";
// node_modules/.nitro/vite/services/ssr/assets/bonuses-Bx8fu5S4.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_dev_runtime = require_jsx_dev_runtime();
var _jsxFileName$2 = "/app/applet/src/components/bonus-card.tsx";
var SLOTS = [
	{
		key: "deposit",
		short: "DEPOSIT",
		tone: "commission"
	},
	{
		key: "signup",
		short: "SIGNUP",
		tone: "new"
	},
	{
		key: "share",
		short: "SHARE",
		tone: "share"
	},
	{
		key: "daily",
		short: "DAILY",
		tone: "referral"
	},
	{
		key: "birthday",
		short: "BDAY",
		tone: "rebate"
	}
];
var SLOT_NAME = {
	deposit: "Deposit Match",
	signup: "Free Signup",
	share: "Share / Invite",
	daily: "Daily Free",
	birthday: "Birthday",
	other: "Other"
};
function bound(value) {
	return value != null && value > 0 ? moneyShort(value) : "—";
}
function rolloverBadge(bonus) {
	if (!bonus || bonus.kind === "unset") return "";
	if (bonus.rollover != null && bonus.rollover > 0) return `${Number(bonus.rollover >= 10 ? bonus.rollover.toFixed(1) : bonus.rollover.toFixed(2))}×`;
	if (bonus.kind !== "percent" && bonus.amount > 0 && bonus.minWithdraw != null && bonus.minWithdraw > 0) {
		const ratio = bonus.minWithdraw / bonus.amount;
		return `${Number(ratio >= 10 ? ratio.toFixed(1) : ratio.toFixed(2))}×`;
	}
	return "";
}
function BonusCard({ domain, rank, bonuses, message, activeSlots = [], focusSlot = "", sortMetric = "", onToggleSlot }) {
	const [open, setOpen] = (0, import_react.useState)(false);
	const [iconSrc, setIconSrc] = (0, import_react.useState)("");
	const listed = bonuses ?? [];
	const favStyle = (0, import_react.useMemo)(() => iconSrc ? { "--fav": `url("${iconSrc.replace(/"/g, "%22")}")` } : void 0, [iconSrc]);
	const highlight = (0, import_react.useMemo)(() => {
		if (!listed.length) return null;
		if (sortMetric && sortMetric !== "az" && sortMetric !== "za") {
			const hit = domainBonusMetric(listed, activeSlots, focusSlot, sortMetric);
			if (sortMetric === "count") return {
				value: `${hit.value ?? 0} offers`,
				sub: activeSlots.length ? "Matched filters" : "Published",
				hot: (hit.value ?? 0) > 0
			};
			if (hit.bonus && hit.value != null) {
				const slotLabel = hit.slot ? SLOT_NAME[hit.slot] : SLOT_NAME[hit.bonus.slot];
				if (sortMetric === "turnover") return {
					value: `${Number(hit.value >= 10 ? hit.value.toFixed(1) : hit.value.toFixed(2))}×`,
					sub: `${slotLabel} · ${hit.bonus.amountDisplay}`,
					hot: true
				};
				if (sortMetric === "withdraw") return {
					value: moneyShort(hit.value),
					sub: `${slotLabel} max`,
					hot: true
				};
				return {
					value: hit.bonus.amountDisplay,
					sub: slotLabel,
					hot: true
				};
			}
		}
		if (focusSlot) {
			const picked = bestBonusForMetric(listed, focusSlot, sortMetric);
			if (picked && picked.kind !== "unset") return {
				value: picked.amountDisplay,
				sub: SLOT_NAME[focusSlot],
				hot: true
			};
		}
		const publishedCount = listed.filter((b) => b.kind !== "unset").length;
		return {
			value: `${publishedCount} offers`,
			sub: `${listed.length} total`,
			hot: publishedCount > 0
		};
	}, [
		listed,
		activeSlots,
		focusSlot,
		sortMetric
	]);
	const orderedBonuses = (0, import_react.useMemo)(() => {
		if (!listed.length) return [];
		const slotMatch = (bonus) => activeSlots.length > 0 && bonus.slot !== "other" && activeSlots.includes(bonus.slot);
		return [...listed].sort((a, b) => {
			const focusDelta = Number(focusSlot !== "" && b.slot === focusSlot) - Number(focusSlot !== "" && a.slot === focusSlot);
			if (focusDelta) return focusDelta;
			const matchDelta = Number(slotMatch(b)) - Number(slotMatch(a));
			if (matchDelta) return matchDelta;
			if (sortMetric === "turnover" || sortMetric === "withdraw" || sortMetric === "amount" || sortMetric === "deposit") {
				const va = bonusMetricValue(a, sortMetric);
				const vb = bonusMetricValue(b, sortMetric);
				if (va != null && vb != null && va !== vb) return sortMetric === "turnover" ? va - vb : vb - va;
				if (va != null && vb == null) return -1;
				if (va == null && vb != null) return 1;
			}
			return b.amount - a.amount || a.name.localeCompare(b.name);
		});
	}, [
		listed,
		activeSlots,
		focusSlot,
		sortMetric
	]);
	const isHighlightedSlot = (slot) => focusSlot === slot || activeSlots.includes(slot) || sortMetric === "deposit" && slot === "deposit";
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("article", {
		className: `bpl-card wallet-card bonus-finder-card${message && bonuses != null ? " wallet-error-card" : ""}${open ? " is-expanded" : ""}`,
		children: [
			iconSrc ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
				className: "card-fav-bg",
				style: favStyle,
				"aria-hidden": "true"
			}, void 0, false, {
				fileName: _jsxFileName$2,
				lineNumber: 163,
				columnNumber: 18
			}, this) : null,
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("header", {
				className: "wc-head",
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(SiteIcon, {
						domain,
						className: "bpl-icon wc-icon",
						onSrcChange: setIconSrc
					}, void 0, false, {
						fileName: _jsxFileName$2,
						lineNumber: 165,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "wc-id",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "bpl-title-row",
							children: [rank != null ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "bpl-rank",
								"aria-hidden": "true",
								children: [rank, "."]
							}, void 0, true, {
								fileName: _jsxFileName$2,
								lineNumber: 169,
								columnNumber: 15
							}, this) : null, /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("a", {
								className: "bpl-domain-link",
								href: `https://${domain}/`,
								target: "_blank",
								rel: "noreferrer",
								children: domain
							}, void 0, false, {
								fileName: _jsxFileName$2,
								lineNumber: 173,
								columnNumber: 13
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName$2,
							lineNumber: 167,
							columnNumber: 11
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "wc-facts",
							children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: `bpl-status ${bonuses == null ? "unknown" : message ? "dead" : "live"}`,
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", {}, void 0, false, {
									fileName: _jsxFileName$2,
									lineNumber: 179,
									columnNumber: 15
								}, this), bonuses == null ? "Not loaded" : message ? "Load failed" : `${listed.length} published`]
							}, void 0, true, {
								fileName: _jsxFileName$2,
								lineNumber: 178,
								columnNumber: 13
							}, this)
						}, void 0, false, {
							fileName: _jsxFileName$2,
							lineNumber: 177,
							columnNumber: 11
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$2,
						lineNumber: 166,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "wc-right",
						children: [bonuses == null ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
							className: "wallet-no-fetch",
							children: "Not loaded"
						}, void 0, false, {
							fileName: _jsxFileName$2,
							lineNumber: 187,
							columnNumber: 13
						}, this) : message ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
							className: "bpl-error-pill",
							title: message,
							children: ["⚠ ", message]
						}, void 0, true, {
							fileName: _jsxFileName$2,
							lineNumber: 189,
							columnNumber: 13
						}, this) : highlight ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: `wc-balance${highlight.hot ? " wc-has-cash" : ""}`,
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "wallet-balance",
								children: highlight.value
							}, void 0, false, {
								fileName: _jsxFileName$2,
								lineNumber: 194,
								columnNumber: 15
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "wallet-withdraw",
								children: highlight.sub
							}, void 0, false, {
								fileName: _jsxFileName$2,
								lineNumber: 195,
								columnNumber: 15
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName$2,
							lineNumber: 193,
							columnNumber: 13
						}, this) : null, /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "acct-toggle",
							"aria-expanded": open,
							"aria-label": open ? "Hide bonuses" : "Show bonuses",
							onClick: () => setOpen((value) => !value),
							children: open ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(ChevronUp, { size: 18 }, void 0, false, {
								fileName: _jsxFileName$2,
								lineNumber: 205,
								columnNumber: 21
							}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(ChevronDown, { size: 18 }, void 0, false, {
								fileName: _jsxFileName$2,
								lineNumber: 205,
								columnNumber: 47
							}, this)
						}, void 0, false, {
							fileName: _jsxFileName$2,
							lineNumber: 198,
							columnNumber: 11
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$2,
						lineNumber: 185,
						columnNumber: 9
					}, this)
				]
			}, void 0, true, {
				fileName: _jsxFileName$2,
				lineNumber: 164,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "wc-panel",
				children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "wc-grid",
					children: SLOTS.map((slot) => {
						const found = bestBonusForMetric(listed, slot.key, sortMetric);
						const bonus = found && found.kind !== "unset" ? found : null;
						const selected = isHighlightedSlot(slot.key);
						const turnText = rolloverBadge(bonus);
						return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(onToggleSlot ? "button" : "div", {
							...onToggleSlot ? {
								type: "button",
								onClick: () => onToggleSlot(slot.key),
								title: `Filter by ${SLOT_NAME[slot.key]}`
							} : {},
							className: `wc-cell wc-${slot.tone}${bonus ? " wc-yes" : ""}${selected ? " wc-selected" : ""}${onToggleSlot ? " wc-clickable" : ""}`,
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "wc-label",
								children: slot.short
							}, void 0, false, {
								fileName: _jsxFileName$2,
								lineNumber: 232,
								columnNumber: 17
							}, this), bonus ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-amt",
									children: bonus.amountDisplay
								}, void 0, false, {
									fileName: _jsxFileName$2,
									lineNumber: 235,
									columnNumber: 21
								}, this),
								slot.key === "deposit" ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-sub",
									children: bonus.minDeposit != null ? `Dep ${moneyShort(bonus.minDeposit)}` : "Dep —"
								}, void 0, false, {
									fileName: _jsxFileName$2,
									lineNumber: 238,
									columnNumber: 25
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-sub",
									children: ["↑ ", bound(bonus.maxWithdraw)]
								}, void 0, true, {
									fileName: _jsxFileName$2,
									lineNumber: 241,
									columnNumber: 25
								}, this)] }, void 0, true, {
									fileName: _jsxFileName$2,
									lineNumber: 237,
									columnNumber: 23
								}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-sub",
									children: ["↓ ", bound(bonus.minWithdraw)]
								}, void 0, true, {
									fileName: _jsxFileName$2,
									lineNumber: 245,
									columnNumber: 25
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-sub",
									children: ["↑ ", bound(bonus.maxWithdraw)]
								}, void 0, true, {
									fileName: _jsxFileName$2,
									lineNumber: 246,
									columnNumber: 25
								}, this)] }, void 0, true, {
									fileName: _jsxFileName$2,
									lineNumber: 244,
									columnNumber: 23
								}, this),
								turnText ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-turn",
									children: turnText
								}, void 0, false, {
									fileName: _jsxFileName$2,
									lineNumber: 249,
									columnNumber: 33
								}, this) : null
							] }, void 0, true, {
								fileName: _jsxFileName$2,
								lineNumber: 234,
								columnNumber: 19
							}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-none",
									children: "—"
								}, void 0, false, {
									fileName: _jsxFileName$2,
									lineNumber: 253,
									columnNumber: 21
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-sub",
									children: slot.key === "deposit" ? "Dep —" : "↓ —"
								}, void 0, false, {
									fileName: _jsxFileName$2,
									lineNumber: 254,
									columnNumber: 21
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-sub",
									children: "↑ —"
								}, void 0, false, {
									fileName: _jsxFileName$2,
									lineNumber: 255,
									columnNumber: 21
								}, this)
							] }, void 0, true, {
								fileName: _jsxFileName$2,
								lineNumber: 252,
								columnNumber: 19
							}, this)]
						}, slot.key, true, {
							fileName: _jsxFileName$2,
							lineNumber: 219,
							columnNumber: 15
						}, this);
					})
				}, void 0, false, {
					fileName: _jsxFileName$2,
					lineNumber: 211,
					columnNumber: 9
				}, this), open ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "wc-list",
					children: orderedBonuses.length ? orderedBonuses.map((bonus) => {
						const matched = focusSlot !== "" && bonus.slot === focusSlot || activeSlots.length > 0 && bonus.slot !== "other" && activeSlots.includes(bonus.slot);
						return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: matched ? "wc-list-row matched" : "wc-list-row",
							children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-list-name",
									children: [matched ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Sparkles, {
										size: 12,
										className: "wc-star"
									}, void 0, false, {
										fileName: _jsxFileName$2,
										lineNumber: 273,
										columnNumber: 34
									}, this) : null, bonus.name]
								}, void 0, true, {
									fileName: _jsxFileName$2,
									lineNumber: 272,
									columnNumber: 21
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-list-amt",
									children: bonus.amountDisplay
								}, void 0, false, {
									fileName: _jsxFileName$2,
									lineNumber: 276,
									columnNumber: 21
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-list-meta",
									children: [
										SLOT_NAME[bonus.slot],
										" · ",
										bonus.kind,
										bonus.rollover != null ? ` · Rollover ${bonus.rollover}×` : "",
										bonus.minDeposit != null ? ` · Deposit ${moneyShort(bonus.minDeposit)}` : "",
										` · Min ${bound(bonus.minWithdraw)} · Max ${bound(bonus.maxWithdraw)}`
									]
								}, void 0, true, {
									fileName: _jsxFileName$2,
									lineNumber: 277,
									columnNumber: 21
								}, this)
							]
						}, `${bonus.id}-${bonus.name}`, true, {
							fileName: _jsxFileName$2,
							lineNumber: 271,
							columnNumber: 19
						}, this);
					}) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "wc-list-row wc-list-empty",
						children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
							className: "wc-list-meta",
							children: message || "No public bonuses loaded for this domain yet."
						}, void 0, false, {
							fileName: _jsxFileName$2,
							lineNumber: 288,
							columnNumber: 17
						}, this)
					}, void 0, false, {
						fileName: _jsxFileName$2,
						lineNumber: 287,
						columnNumber: 15
					}, this)
				}, void 0, false, {
					fileName: _jsxFileName$2,
					lineNumber: 264,
					columnNumber: 11
				}, this) : null]
			}, void 0, true, {
				fileName: _jsxFileName$2,
				lineNumber: 210,
				columnNumber: 7
			}, this)
		]
	}, void 0, true, {
		fileName: _jsxFileName$2,
		lineNumber: 158,
		columnNumber: 5
	}, this);
}
var _jsxFileName$1 = "/app/applet/src/components/bonus-finder.tsx";
var CACHE_KEY = "pokies_vault_bonus_catalogue_v1";
var PAGE = 20;
var DOMAIN_LOCALE_OPTS = {
	numeric: true,
	sensitivity: "base"
};
var FILTER_SLOTS = [
	{
		id: "deposit",
		label: "Deposit Match",
		emoji: "💳"
	},
	{
		id: "signup",
		label: "Free Signup",
		emoji: "🎁"
	},
	{
		id: "share",
		label: "Share/Invite",
		emoji: "🤝"
	},
	{
		id: "daily",
		label: "Daily Free",
		emoji: "📅"
	},
	{
		id: "birthday",
		label: "Birthday",
		emoji: "🎂"
	}
];
var SORT_OPTIONS = [
	{
		id: "",
		label: "Domain name — A → Z",
		short: "A → Z"
	},
	{
		id: "za",
		label: "Domain name — Z → A",
		short: "Z → A"
	},
	{
		id: "deposit",
		label: "Highest Deposit Match (%)",
		short: "Deposit ↓"
	},
	{
		id: "amount",
		label: "Highest Bonus Amount",
		short: "Amount ↓"
	},
	{
		id: "withdraw",
		label: "Highest Max Withdrawal",
		short: "Max Cashout ↓"
	},
	{
		id: "turnover",
		label: "Lowest Turnover / Rollover",
		short: "Turnover ↑"
	},
	{
		id: "count",
		label: "Most Bonuses Found",
		short: "Offers ↓"
	}
];
var EMPTY_FILTERS = {
	slots: [],
	focus: "",
	sort: "",
	loadedOnly: false
};
function readCache() {
	if (typeof localStorage === "undefined") return {};
	try {
		const parsed = JSON.parse(localStorage.getItem(CACHE_KEY) || "{}");
		return parsed && typeof parsed === "object" ? parsed : {};
	} catch {
		return {};
	}
}
function writeCache(cache) {
	try {
		localStorage.setItem(CACHE_KEY, JSON.stringify(cache));
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
	return !tokens.length || tokens.every((token) => normDomain.includes(token));
}
function hasUsableSlot(row, slot) {
	if (!row || !row.bonuses.length) return false;
	const found = bestBonusForMetric(row.bonuses, slot, "");
	return Boolean(found && found.kind !== "unset");
}
function BonusFinder() {
	const { state, patch } = useVault();
	const loadFn = useServerFn(loadPublicCatalogueFn);
	const loadRef = (0, import_react.useRef)(loadFn);
	loadRef.current = loadFn;
	const [query, setQuery] = (0, import_react.useState)("");
	const [filters, setFilters] = (0, import_react.useState)(EMPTY_FILTERS);
	const [draft, setDraft] = (0, import_react.useState)(EMPTY_FILTERS);
	const [sortOpen, setSortOpen] = (0, import_react.useState)(false);
	const [columns, setColumns] = (0, import_react.useState)("one");
	const [cache, setCache] = (0, import_react.useState)({});
	const cacheRef = (0, import_react.useRef)(cache);
	cacheRef.current = cache;
	const [page, setPage] = (0, import_react.useState)(0);
	const [running, setRunning] = (0, import_react.useState)(false);
	const [progress, setProgress] = (0, import_react.useState)({
		done: 0,
		total: 0
	});
	const [status, setStatus] = (0, import_react.useState)("Load domains to grab live public bonuses and activate sorting/filtering.");
	const stopRef = (0, import_react.useRef)(null);
	const ready = (0, import_react.useRef)(false);
	const domains = (0, import_react.useMemo)(() => visibleSites(state).map((site) => site.domain), [state]);
	(0, import_react.useEffect)(() => {
		const saved = readCache();
		const allowed = new Set(domains);
		const next = {};
		for (const [domain, row] of Object.entries(saved)) if (allowed.has(domain)) next[domain] = row;
		setCache(next);
		if (Object.keys(next).length !== Object.keys(saved).length) writeCache(next);
		ready.current = true;
	}, [domains]);
	(0, import_react.useEffect)(() => {
		if (!ready.current) return;
		writeCache(cache);
	}, [cache]);
	const slotCounts = (0, import_react.useMemo)(() => {
		const counts = {
			deposit: 0,
			signup: 0,
			share: 0,
			daily: 0,
			birthday: 0
		};
		for (const domain of domains) {
			const row = cache[domain];
			if (!row?.bonuses.length) continue;
			for (const slot of FILTER_SLOTS) if (hasUsableSlot(row, slot.id)) counts[slot.id] += 1;
		}
		return counts;
	}, [domains, cache]);
	const filtered = (0, import_react.useMemo)(() => {
		const q = query.trim();
		let list = domains.filter((domain) => domainMatchesSearch(domain, q));
		if (filters.loadedOnly) list = list.filter((domain) => Boolean(cache[domain] && !cache[domain].message));
		if (filters.slots.length) list = list.filter((domain) => {
			const row = cache[domain];
			if (!row) return false;
			return filters.slots.every((slot) => hasUsableSlot(row, slot));
		});
		const az = (a, b) => a.localeCompare(b, void 0, DOMAIN_LOCALE_OPTS);
		if (filters.sort === "za") return [...list].sort((a, b) => b.localeCompare(a, void 0, DOMAIN_LOCALE_OPTS));
		if (!filters.sort || filters.sort === "az") return [...list].sort(az);
		const ascending = filters.sort === "turnover";
		return [...list].sort((a, b) => {
			const rowA = cache[a];
			const rowB = cache[b];
			const hitA = domainBonusMetric(rowA?.bonuses ?? [], filters.slots, filters.focus, filters.sort);
			const hitB = domainBonusMetric(rowB?.bonuses ?? [], filters.slots, filters.focus, filters.sort);
			const tier = (row, val) => {
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
			return (ascending ? hitA.value - hitB.value : hitB.value - hitA.value) || az(a, b);
		});
	}, [
		domains,
		query,
		filters,
		cache
	]);
	const pages = Math.max(1, Math.ceil(filtered.length / PAGE));
	const safePage = Math.min(page, pages - 1);
	const slice = filtered.slice(safePage * PAGE, safePage * PAGE + PAGE);
	const loadedCount = domains.filter((domain) => Boolean(cache[domain])).length;
	const activeFilterCount = filters.slots.length + Number(Boolean(filters.focus)) + Number(filters.loadedOnly);
	const sortFilterActive = Boolean(filters.sort) || activeFilterCount > 0;
	const sortButtonLabel = (0, import_react.useMemo)(() => {
		const parts = [];
		if (filters.sort) {
			const found = SORT_OPTIONS.find((item) => item.id === filters.sort);
			if (found) parts.push(found.short);
		}
		if (filters.focus) {
			const found = FILTER_SLOTS.find((item) => item.id === filters.focus);
			if (found) parts.push(found.label);
		}
		if (filters.slots.length) parts.push(`${filters.slots.length} Filter${filters.slots.length === 1 ? "" : "s"}`);
		if (filtered.length > 0) parts.push(String(filtered.length));
		if (parts.length === 1 && filtered.length > 0) return `Sort / Filter · ${filtered.length}`;
		return parts.length ? parts.join(" · ") : "Sort / Filter";
	}, [filters, filtered.length]);
	function toggleSlot(slot) {
		setPage(0);
		setFilters((current) => {
			const exists = current.slots.includes(slot);
			const nextSlots = exists ? current.slots.filter((item) => item !== slot) : [...current.slots, slot];
			const nextFocus = !exists && !current.focus ? slot : exists && current.focus === slot ? nextSlots[0] ?? "" : current.focus;
			return {
				...current,
				slots: nextSlots,
				focus: nextFocus
			};
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
	async function load(domainsToLoad) {
		const unique = Array.from(new Set(domainsToLoad));
		if (!unique.length) {
			setStatus("No domains to load.");
			return;
		}
		stopRef.current?.abort();
		const controller = new AbortController();
		stopRef.current = controller;
		setRunning(true);
		setProgress({
			done: 0,
			total: unique.length
		});
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
					return {
						domain,
						host: saved?.host,
						merchantId: saved?.merchantId
					};
				});
				try {
					const result = await loadRef.current({
						data: { items },
						signal: controller.signal
					});
					if (controller.signal.aborted) return;
					setCache((current) => {
						const next = { ...current };
						for (const row of result) next[row.domain] = {
							bonuses: row.ok ? row.bonuses : [],
							message: row.ok ? void 0 : row.message,
							host: row.host || current[row.domain]?.host,
							merchantId: row.merchantId || current[row.domain]?.merchantId
						};
						return next;
					});
				} catch {
					if (controller.signal.aborted) return;
					setCache((current) => {
						const next = { ...current };
						for (const domain of names) if (!next[domain]) next[domain] = {
							bonuses: [],
							message: "Could not load"
						};
						return next;
					});
				}
				done += names.length;
				setProgress({
					done: Math.min(done, unique.length),
					total: unique.length
				});
			}
		};
		await Promise.all(Array.from({ length: Math.min(5, Math.ceil(unique.length / 4)) }, () => worker()));
		setRunning(false);
		setStatus(controller.signal.aborted ? "Stopped." : "Public bonuses loaded. Sorting and filters reflect live domain data.");
	}
	const pct = progress.total > 0 ? Math.round(progress.done / progress.total * 100) : 0;
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
		className: "stack",
		children: [
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "toolbar bpl-toolbar",
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "tool-row finder-tool-row",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "tool bpl-tool",
								onClick: () => setColumns((c) => c === "one" ? "two" : "one"),
								children: ["View: ", columns === "one" ? "1 Column" : "2 Columns"]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 339,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "tool bpl-tool",
								disabled: running || slice.length === 0,
								onClick: () => void load(slice),
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Download, {
									size: 12,
									style: {
										display: "inline",
										marginRight: 3
									}
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 352,
									columnNumber: 13
								}, this), "Load page"]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 346,
								columnNumber: 11
							}, this),
							running ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "tool bpl-tool active",
								onClick: () => stopRef.current?.abort(),
								children: [
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Square, {
										size: 11,
										style: {
											display: "inline",
											marginRight: 3
										}
									}, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 361,
										columnNumber: 15
									}, this),
									"Stop (",
									progress.done,
									"/",
									progress.total,
									")"
								]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 356,
								columnNumber: 13
							}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "tool bpl-tool wallet-btn",
								disabled: filtered.length === 0,
								onClick: () => void load(filtered),
								children: [
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Download, {
										size: 12,
										style: {
											display: "inline",
											marginRight: 3
										}
									}, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 371,
										columnNumber: 15
									}, this),
									"Load all (",
									filtered.length,
									")"
								]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 365,
								columnNumber: 13
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
									lineNumber: 380,
									columnNumber: 13
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "tool-truncate",
									children: sortButtonLabel
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 381,
									columnNumber: 13
								}, this)]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 375,
								columnNumber: 11
							}, this)
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 338,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
						className: "search",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Search, { size: 16 }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 386,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
								value: query,
								placeholder: "Search domains in Bonus Finder…",
								"aria-label": "Search domains",
								onChange: (event) => {
									setQuery(event.target.value);
									setPage(0);
								}
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 387,
								columnNumber: 11
							}, this),
							query ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "pin",
								"aria-label": "Clear search",
								onClick: () => {
									setQuery("");
									setPage(0);
								},
								children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(X, { size: 16 }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 406,
									columnNumber: 15
								}, this)
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 397,
								columnNumber: 13
							}, this) : null
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 385,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "finder-chip-bar",
						role: "group",
						"aria-label": "Filter by bonus type",
						children: [FILTER_SLOTS.map((slot) => {
							const on = filters.slots.includes(slot.id);
							const count = slotCounts[slot.id];
							return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: on ? "finder-pill active" : "finder-pill",
								"aria-pressed": on,
								onClick: () => toggleSlot(slot.id),
								children: [
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
										"aria-hidden": "true",
										children: slot.emoji
									}, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 423,
										columnNumber: 17
									}, this),
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: slot.label }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 424,
										columnNumber: 17
									}, this),
									loadedCount > 0 ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("small", { children: count }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 425,
										columnNumber: 36
									}, this) : null
								]
							}, slot.id, true, {
								fileName: _jsxFileName$1,
								lineNumber: 416,
								columnNumber: 15
							}, this);
						}), sortFilterActive ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "finder-pill reset",
							onClick: resetSortModal,
							children: "Reset"
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 430,
							columnNumber: 13
						}, this) : null]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 411,
						columnNumber: 9
					}, this),
					running ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "finder-progress-bar",
						role: "progressbar",
						"aria-valuenow": pct,
						"aria-valuemin": 0,
						"aria-valuemax": 100,
						children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { style: { width: `${pct}%` } }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 438,
							columnNumber: 13
						}, this)
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 437,
						columnNumber: 11
					}, this) : null,
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "stats",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: ["Domains ", /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: domains.length }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 444,
								columnNumber: 21
							}, this)] }, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 443,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: ["Loaded ", /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: loadedCount }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 447,
								columnNumber: 20
							}, this)] }, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 446,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: ["Showing ", /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: filtered.length }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 450,
								columnNumber: 21
							}, this)] }, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 449,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "finder-status-note",
								children: status
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 452,
								columnNumber: 11
							}, this)
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 442,
						columnNumber: 9
					}, this)
				]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 337,
				columnNumber: 7
			}, this),
			filtered.length === 0 ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "empty",
				children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
					className: "empty-title",
					children: domains.length ? loadedCount === 0 && filters.slots.length > 0 ? "Load domains first to filter by live bonuses." : "No domains match the current bonus filters." : "No domains yet. Add them in Domain Intel."
				}, void 0, false, {
					fileName: _jsxFileName$1,
					lineNumber: 458,
					columnNumber: 11
				}, this), domains.length && (query || sortFilterActive) ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
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
					lineNumber: 466,
					columnNumber: 13
				}, this) : null]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 457,
				columnNumber: 9
			}, this) : null,
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: columns === "two" ? "cards view-two" : "cards view-one",
				children: slice.map((domain, index) => {
					const row = cache[domain];
					return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(BonusCard, {
						domain,
						rank: safePage * PAGE + index + 1,
						bonuses: row ? row.bonuses : null,
						message: row?.message,
						activeSlots: filters.slots,
						focusSlot: filters.focus,
						sortMetric: filters.sort,
						onToggleSlot: toggleSlot
					}, domain, false, {
						fileName: _jsxFileName$1,
						lineNumber: 485,
						columnNumber: 13
					}, this);
				})
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 481,
				columnNumber: 7
			}, this),
			pages > 1 ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "pager",
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: "btn",
						disabled: safePage === 0,
						onClick: () => setPage(safePage - 1),
						children: "Previous"
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 502,
						columnNumber: 11
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [
						"Page ",
						safePage + 1,
						" / ",
						pages
					] }, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 505,
						columnNumber: 11
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: "btn",
						disabled: safePage >= pages - 1,
						onClick: () => setPage(safePage + 1),
						children: "Next"
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 508,
						columnNumber: 11
					}, this)
				]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 501,
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
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", { children: "Sort and filter bonuses" }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 518,
								columnNumber: 15
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "pin",
								"aria-label": "Close",
								onClick: () => setSortOpen(false),
								children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(X, { size: 18 }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 520,
									columnNumber: 17
								}, this)
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 519,
								columnNumber: 15
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 517,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "sort-filter-section",
							children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h4", { children: "Sort by (live loaded data)" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 525,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
									className: "hint",
									children: "Orders cards directly from the bonuses grabbed on load. Cards update their highlight to match."
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 526,
									columnNumber: 15
								}, this),
								SORT_OPTIONS.map((opt) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
									type: "button",
									className: draft.sort === opt.id ? "choice on" : "choice",
									onClick: () => setDraft((c) => ({
										...c,
										sort: opt.id
									})),
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "radio" }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 536,
										columnNumber: 19
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: opt.label }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 537,
										columnNumber: 19
									}, this)]
								}, opt.id || "az", true, {
									fileName: _jsxFileName$1,
									lineNumber: 530,
									columnNumber: 17
								}, this))
							]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 524,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "sort-filter-section",
							children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h4", { children: "Focus bonus type" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 543,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
									className: "hint",
									children: "Highlights that tile on every card and focuses the Amount, Max Withdrawal, and Turnover sorts on it."
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 544,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
									type: "button",
									className: draft.focus === "" ? "choice on" : "choice",
									onClick: () => setDraft((c) => ({
										...c,
										focus: ""
									})),
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "radio" }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 552,
										columnNumber: 17
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "All / Best matching" }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 553,
										columnNumber: 17
									}, this)]
								}, void 0, true, {
									fileName: _jsxFileName$1,
									lineNumber: 547,
									columnNumber: 15
								}, this),
								FILTER_SLOTS.map((slot) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
									type: "button",
									className: draft.focus === slot.id ? "choice on" : "choice",
									onClick: () => setDraft((c) => ({
										...c,
										focus: slot.id
									})),
									children: [
										/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "radio" }, void 0, false, {
											fileName: _jsxFileName$1,
											lineNumber: 562,
											columnNumber: 19
										}, this),
										/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
											"aria-hidden": "true",
											children: slot.emoji
										}, void 0, false, {
											fileName: _jsxFileName$1,
											lineNumber: 563,
											columnNumber: 19
										}, this),
										/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [slot.label, loadedCount > 0 ? ` (${slotCounts[slot.id]})` : ""] }, void 0, true, {
											fileName: _jsxFileName$1,
											lineNumber: 564,
											columnNumber: 19
										}, this)
									]
								}, slot.id, true, {
									fileName: _jsxFileName$1,
									lineNumber: 556,
									columnNumber: 17
								}, this))
							]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 542,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "sort-filter-section",
							children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h4", { children: "Filter by available bonuses" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 573,
									columnNumber: 15
								}, this),
								FILTER_SLOTS.map((slot) => {
									const checked = draft.slots.includes(slot.id);
									return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
										type: "button",
										className: checked ? "choice on" : "choice",
										onClick: () => setDraft((c) => ({
											...c,
											slots: checked ? c.slots.filter((item) => item !== slot.id) : [...c.slots, slot.id]
										})),
										children: [
											/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "checkbox" }, void 0, false, {
												fileName: _jsxFileName$1,
												lineNumber: 588,
												columnNumber: 21
											}, this),
											/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
												"aria-hidden": "true",
												children: slot.emoji
											}, void 0, false, {
												fileName: _jsxFileName$1,
												lineNumber: 589,
												columnNumber: 21
											}, this),
											/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [slot.label, loadedCount > 0 ? ` (${slotCounts[slot.id]})` : ""] }, void 0, true, {
												fileName: _jsxFileName$1,
												lineNumber: 590,
												columnNumber: 21
											}, this)
										]
									}, slot.id, true, {
										fileName: _jsxFileName$1,
										lineNumber: 577,
										columnNumber: 19
									}, this);
								}),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
									type: "button",
									className: draft.loadedOnly ? "choice on" : "choice",
									onClick: () => setDraft((c) => ({
										...c,
										loadedOnly: !c.loadedOnly
									})),
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "checkbox" }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 602,
										columnNumber: 17
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "Loaded domains only" }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 603,
										columnNumber: 17
									}, this)]
								}, void 0, true, {
									fileName: _jsxFileName$1,
									lineNumber: 597,
									columnNumber: 15
								}, this)
							]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 572,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "sort-filter-section",
							children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h4", { children: "Card color" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 608,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
									className: "hint",
									children: "Shared glass & glow style with the Main Page."
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 609,
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
										lineNumber: 612,
										columnNumber: 19
									}, this))
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 610,
									columnNumber: 15
								}, this)
							]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 607,
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
								lineNumber: 630,
								columnNumber: 15
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn btn-grad",
								onClick: applySortModal,
								children: "Apply"
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 633,
								columnNumber: 15
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 629,
							columnNumber: 13
						}, this)
					]
				}, void 0, true, {
					fileName: _jsxFileName$1,
					lineNumber: 516,
					columnNumber: 11
				}, this)
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 515,
				columnNumber: 9
			}, this) : null
		]
	}, void 0, true, {
		fileName: _jsxFileName$1,
		lineNumber: 336,
		columnNumber: 5
	}, this);
}
var _jsxFileName = "/app/applet/src/routes/bonuses.tsx?tsr-split=component";
function BonusesPage() {
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(AppFrame, { children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(BonusFinder, {}, void 0, false, {
		fileName: _jsxFileName,
		lineNumber: 5,
		columnNumber: 7
	}, this) }, void 0, false, {
		fileName: _jsxFileName,
		lineNumber: 4,
		columnNumber: 10
	}, this);
}
export { BonusesPage as component };
