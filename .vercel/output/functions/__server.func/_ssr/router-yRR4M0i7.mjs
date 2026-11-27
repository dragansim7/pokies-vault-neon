import { o as __toESM } from "../_runtime.mjs";
import { t as __exportAll } from "./rolldown-runtime-D8jQpDG7.mjs";
import { n as normalizeDomain, t as isPublicDomain } from "./domain-DVT1Is7p.mjs";
import { L as string, N as number, P as object, R as union, j as literal } from "../_libs/@better-auth/core+[...].mjs";
import { n as auth } from "./server-x1MirbW5.mjs";
import { Gt as require_react, S as useRouter, _ as lazyRouteComponent, d as Scripts, f as HeadContent, g as Outlet, h as createRouter, n as require_react_dom, v as createFileRoute, y as createRootRoute } from "../_libs/@tanstack/react-router+[...].mjs";
import { a as TriangleAlert } from "../_libs/lucide-react.mjs";
import { t as require_jsx_dev_runtime } from "../_libs/react.mjs";
// node_modules/.nitro/vite/services/ssr/assets/router-yRR4M0i7.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_dev_runtime = require_jsx_dev_runtime();
var import_react_dom = require_react_dom();
var _jsxFileName$3 = "/app/applet/src/lib/error-component.tsx";
var FALLBACK_MESSAGE = "An unexpected error occurred. Try reloading the page.";
function errorMessage(error) {
	if (error instanceof Error && error.message) return error.message;
	if (typeof error === "string" && error) return error;
	return FALLBACK_MESSAGE;
}
function AppErrorComponent({ error }) {
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("main", {
		className: "flex min-h-screen flex-col items-center justify-center gap-3 px-6 text-center bg-zinc-50 text-zinc-900 dark:bg-zinc-950 dark:text-zinc-50",
		children: [
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
				className: "text-red-500",
				"aria-hidden": "true",
				children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(TriangleAlert, {
					className: "size-10",
					strokeWidth: 2
				}, void 0, false, {
					fileName: _jsxFileName$3,
					lineNumber: 21,
					columnNumber: 9
				}, this)
			}, void 0, false, {
				fileName: _jsxFileName$3,
				lineNumber: 20,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h1", {
				className: "text-lg font-semibold",
				children: "Something went wrong"
			}, void 0, false, {
				fileName: _jsxFileName$3,
				lineNumber: 23,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
				className: "max-w-md text-sm break-words text-zinc-500 dark:text-zinc-400",
				children: errorMessage(error)
			}, void 0, false, {
				fileName: _jsxFileName$3,
				lineNumber: 24,
				columnNumber: 7
			}, this)
		]
	}, void 0, true, {
		fileName: _jsxFileName$3,
		lineNumber: 14,
		columnNumber: 5
	}, this);
}
var _jsxFileName$2 = "/app/applet/src/lib/auth/provider.tsx";
/**
* App-wide client provider mounted once near the root (in `src/routes/__root.tsx`):
*
*   <AuthProvider><Outlet /></AuthProvider>
*
* Better Auth's React client (`@/lib/auth/client`) needs NO context provider —
* its `useSession()` works standalone — so this is a passthrough today. It's
* kept as the single, stable mount point for any future client-side providers
* (e.g. a toast or theme provider) without churning the root shell.
*/
function AuthProvider({ children }) {
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children }, void 0, false, {
		fileName: _jsxFileName$2,
		lineNumber: 14,
		columnNumber: 10
	}, this);
}
var CONNECTOR_TOKEN_READY_EVENT = "grok:connector-token-ready";
function isGrokEmbedderOrigin(origin) {
	try {
		const url = new URL(origin);
		if (url.protocol !== "https:" && url.protocol !== "http:") return false;
		const host = url.hostname.toLowerCase();
		if (host === "grok.com" || host.endsWith(".grok.com")) return true;
		if (host === "localhost" || host === "127.0.0.1" || host === "[::1]") return true;
		return false;
	} catch {
		return false;
	}
}
function isSandboxPreviewGuestHost(hostname) {
	const host = hostname.toLowerCase();
	return host === "grok-sandbox.com" || host.endsWith(".grok-sandbox.com");
}
function isRemintPreviewPair(guestHost, parentHost) {
	const guest = guestHost.toLowerCase();
	const parent = parentHost.toLowerCase();
	const i = guest.indexOf(".preview.");
	if (i <= 0) return false;
	const label = guest.slice(0, i);
	const rest = guest.slice(i + 9);
	if (label.includes(".") || !rest.includes(".")) return false;
	return parent === rest || parent === `grok.${rest}`;
}
function resolveParentEmbedderOrigin(parentIsSelf, referrer, ancestorOrigin, guestHostname = "") {
	if (parentIsSelf) return null;
	for (const candidate of [referrer, ancestorOrigin ?? ""].filter(Boolean)) try {
		const url = new URL(candidate.includes("://") ? candidate : `https://${candidate}`);
		if (url.protocol !== "https:" && url.protocol !== "http:") continue;
		if (isGrokEmbedderOrigin(url.origin)) return url.origin;
		if (isSandboxPreviewGuestHost(guestHostname) || isRemintPreviewPair(guestHostname, url.hostname)) return url.origin;
	} catch {}
	return null;
}
/**
* Guest side of the grok-web ↔ sandbox preview postMessage bridge.
*
* Activates only when this page is framed by an allowlisted Grok embedder.
* Top-level runs (download/export, local `npm run dev`, deployed sites) noop.
*/
var PREVIEW_BRIDGE_CHANNEL = "grok-preview-bridge";
var EnvelopeSchema = object({
	channel: literal(PREVIEW_BRIDGE_CHANNEL),
	version: number().int().positive(),
	type: string().min(1)
});
var HelloSchema = EnvelopeSchema.extend({ type: literal("hello") });
var NavigateSchema = EnvelopeSchema.extend({
	type: literal("navigate"),
	path: string().min(1)
});
var HistorySchema = EnvelopeSchema.extend({
	type: literal("history"),
	delta: union([literal(-1), literal(1)])
});
var ConnectorTokenReadySchema = EnvelopeSchema.extend({ type: literal("connector-token-ready") });
function isSafeBridgePath(path) {
	if (!path.startsWith("/") || path.startsWith("//") || path.includes("\\")) return false;
	try {
		return new URL(path, "https://preview.invalid").origin === "https://preview.invalid";
	} catch {
		return false;
	}
}
/**
* Origin of the Grok embedder framing this page, or null when the page runs
* top-level (download/export, local `npm run dev`, deployed sites) or under a
* non-Grok parent. Client-only; null during SSR.
*/
function resolveCurrentEmbedderOrigin() {
	if (typeof window === "undefined") return null;
	const ancestorOrigin = typeof location.ancestorOrigins !== "undefined" && location.ancestorOrigins.length > 0 ? location.ancestorOrigins[0] : null;
	return resolveParentEmbedderOrigin(window.parent === window, document.referrer, ancestorOrigin, window.location.hostname);
}
/**
* Install host↔guest messaging. Returns a dispose function.
* Noops (returns a no-op dispose) when not embedded under a Grok parent.
*/
function installPreviewHostBridge(options = {}) {
	const parentOrigin = resolveCurrentEmbedderOrigin();
	if (parentOrigin === null) return () => {};
	const ROOT_STATE_KEY = "__grokPreviewBridgeRoot";
	const originalPushState = window.history.pushState.bind(window.history);
	const originalReplaceState = window.history.replaceState.bind(window.history);
	const isAtHistoryRoot = () => {
		const state = window.history.state;
		return Boolean(state && typeof state === "object" && state[ROOT_STATE_KEY] === true);
	};
	try {
		const current = window.history.state;
		if (!(current !== null && typeof current === "object" && Object.prototype.hasOwnProperty.call(current, ROOT_STATE_KEY))) {
			const isRoot = window.history.length <= 1;
			originalReplaceState(current && typeof current === "object" ? {
				...current,
				[ROOT_STATE_KEY]: isRoot
			} : { [ROOT_STATE_KEY]: isRoot }, "", window.location.href);
		}
	} catch {}
	const post = (message) => {
		window.parent.postMessage(message, parentOrigin);
	};
	const reportLocation = () => {
		post({
			channel: PREVIEW_BRIDGE_CHANNEL,
			version: 1,
			type: "location",
			path: window.location.pathname || "/",
			search: window.location.search,
			hash: window.location.hash
		});
	};
	const reportRoutes = () => {
		const paths = options.getRoutePaths?.() ?? [];
		post({
			channel: PREVIEW_BRIDGE_CHANNEL,
			version: 1,
			type: "routes",
			paths
		});
	};
	const defaultNavigate = (path) => {
		if (!isSafeBridgePath(path)) return;
		try {
			const url = new URL(path, window.location.origin);
			if (url.origin !== window.location.origin) return;
			const next = `${url.pathname}${url.search}${url.hash}`;
			window.history.pushState(window.history.state, "", next);
			window.dispatchEvent(new PopStateEvent("popstate", { state: window.history.state }));
		} catch {}
	};
	const navigate = (path) => {
		if (!isSafeBridgePath(path)) return;
		if (options.navigate) {
			options.navigate(path);
			return;
		}
		defaultNavigate(path);
	};
	const announce = () => {
		reportLocation();
		reportRoutes();
		post({
			channel: PREVIEW_BRIDGE_CHANNEL,
			version: 1,
			type: "ready"
		});
	};
	const onHello = (data) => {
		if (!HelloSchema.safeParse(data).success) return;
		announce();
	};
	const onNavigate = (data) => {
		const parsed = NavigateSchema.safeParse(data);
		if (!parsed.success) return;
		navigate(parsed.data.path);
		queueMicrotask(reportLocation);
	};
	const onHistory = (data) => {
		const parsed = HistorySchema.safeParse(data);
		if (!parsed.success) return;
		if (parsed.data.delta === -1 && isAtHistoryRoot()) return;
		window.history.go(parsed.data.delta);
	};
	const onConnectorTokenReady = (data) => {
		if (!ConnectorTokenReadySchema.safeParse(data).success) return;
		window.dispatchEvent(new Event(CONNECTOR_TOKEN_READY_EVENT));
	};
	const hostMessageHandlers = /* @__PURE__ */ new Map([
		["hello", onHello],
		["navigate", onNavigate],
		["history", onHistory],
		["connector-token-ready", onConnectorTokenReady]
	]);
	const onMessage = (event) => {
		if (event.source !== window.parent) return;
		if (event.origin !== parentOrigin) return;
		const envelope = EnvelopeSchema.safeParse(event.data);
		if (!envelope.success || envelope.data.version !== 1) return;
		hostMessageHandlers.get(envelope.data.type)?.(event.data);
	};
	const onPopState = () => {
		reportLocation();
	};
	const onHashChange = () => {
		reportLocation();
	};
	window.history.pushState = (data, unused, url) => {
		const next = data && typeof data === "object" ? {
			...data,
			[ROOT_STATE_KEY]: false
		} : data;
		originalPushState(next, unused, url);
		reportLocation();
	};
	window.history.replaceState = (data, unused, url) => {
		const next = isAtHistoryRoot() ? {
			...data && typeof data === "object" ? data : {},
			[ROOT_STATE_KEY]: true
		} : data;
		originalReplaceState(next, unused, url);
		reportLocation();
	};
	window.addEventListener("message", onMessage);
	window.addEventListener("popstate", onPopState);
	window.addEventListener("hashchange", onHashChange);
	announce();
	return () => {
		window.removeEventListener("message", onMessage);
		window.removeEventListener("popstate", onPopState);
		window.removeEventListener("hashchange", onHashChange);
		window.history.pushState = originalPushState;
		window.history.replaceState = originalReplaceState;
	};
}
/** Collect static path patterns from a TanStack route tree (best-effort). */
function collectRoutePathsFromTree(routeTree) {
	const paths = /* @__PURE__ */ new Set();
	const walk = (node) => {
		if (!node || typeof node !== "object") return;
		const record = node;
		const full = typeof record.fullPath === "string" ? record.fullPath : typeof record.path === "string" ? record.path : null;
		if (full !== null && full !== "") paths.add(full.startsWith("/") ? full : `/${full}`);
		else if (full === "") paths.add("/");
		const children = record.children;
		if (Array.isArray(children)) for (const child of children) walk(child);
		else if (children && typeof children === "object") for (const child of Object.values(children)) walk(child);
	};
	walk(routeTree);
	return [...paths];
}
/**
* Mount once in `__root.tsx` so the Grok preview chrome can drive navigation
* (and later receive registered routes). Noops when the app is not embedded.
*/
function PreviewHostBridge() {
	const router = useRouter();
	(0, import_react.useEffect)(() => {
		return installPreviewHostBridge({
			navigate: (path) => {
				router.history.push(path);
			},
			getRoutePaths: () => collectRoutePathsFromTree(router.routeTree)
		});
	}, [router]);
	return null;
}
var LIST_KEY = "pokies_vault_domains_v1";
var CATALOG_STRIPPED_KEY = "pokies_vault_catalog_stripped_v1";
var GROUPS = /* @__PURE__ */ new Set([
	"new",
	"best",
	"unverified",
	"share",
	"latest",
	"custom"
]);
function siteRecord(domain, addedAt = null) {
	return {
		domain,
		group: "custom",
		addedAt
	};
}
function loadOwnedList() {
	try {
		const raw = localStorage.getItem(LIST_KEY);
		if (!raw) return null;
		const parsed = JSON.parse(raw);
		if (parsed.owned !== true || !Array.isArray(parsed.sites)) return null;
		const sites = [];
		const seen = /* @__PURE__ */ new Set();
		for (const item of parsed.sites) {
			if (!item || typeof item !== "object") continue;
			const row = item;
			const domain = normalizeDomain(String(row.domain ?? ""));
			if (!isPublicDomain(domain) || seen.has(domain)) continue;
			seen.add(domain);
			const addedAt = typeof row.addedAt === "string" ? row.addedAt : null;
			const group = GROUPS.has(row.group) ? row.group : null;
			sites.push(group ? {
				domain,
				group,
				addedAt
			} : siteRecord(domain, addedAt));
		}
		return sites;
	} catch {
		return null;
	}
}
function saveOwnedList(sites) {
	try {
		const payload = JSON.stringify({
			owned: true,
			sites
		});
		localStorage.setItem(LIST_KEY, payload);
		return localStorage.getItem(LIST_KEY) === payload;
	} catch {
		return false;
	}
}
function clearOwnedList() {
	try {
		localStorage.removeItem(LIST_KEY);
	} catch {}
}
var INTEL_KEY = "pokies_vault_intel_v1";
function domainsFromText(text) {
	const out = [];
	const seen = /* @__PURE__ */ new Set();
	for (const line of text.split(/\r?\n/)) {
		const cleaned = line.replace(/#.*/, "").trim();
		if (!cleaned) continue;
		for (const part of cleaned.split(/[\s,;]+/)) {
			const domain = normalizeDomain(part);
			if (!isPublicDomain(domain) || seen.has(domain)) continue;
			seen.add(domain);
			out.push(domain);
		}
	}
	return out;
}
function workingSites(custom, removed) {
	const gone = new Set(removed);
	const sites = [];
	const seen = /* @__PURE__ */ new Set();
	for (const site of custom) {
		const domain = normalizeDomain(site?.domain ?? "");
		if (!isPublicDomain(domain) || gone.has(domain) || seen.has(domain)) continue;
		sites.push({
			domain,
			group: "custom",
			addedAt: site.addedAt ?? null
		});
		seen.add(domain);
	}
	return sites;
}
function visibleSites(input) {
	if (input.listOwned) return input.sites;
	return workingSites(input.custom, input.removed);
}
var THEMES = [
	{
		id: "vault",
		name: "Vault",
		hint: "Cyan, violet, pink",
		bg: "#050507",
		panel: "#0c0c0e",
		panel2: "#141418",
		fg: "#f5f5f7",
		muted: "#92929e",
		faint: "#6f6f79",
		a: "#00e5ff",
		b: "#7b5cff",
		c: "#ff2fb0",
		ink: "#070708"
	},
	{
		id: "emerald",
		name: "Emerald",
		hint: "Mint and gold",
		bg: "#06110c",
		panel: "#0c1a14",
		panel2: "#12241c",
		fg: "#f3f7f4",
		muted: "#8ea398",
		faint: "#6d8074",
		a: "#3dffc5",
		b: "#1fa87a",
		c: "#d6b25e",
		ink: "#04110c"
	},
	{
		id: "arctic",
		name: "Arctic",
		hint: "Ice and steel",
		bg: "#070b12",
		panel: "#101722",
		panel2: "#172033",
		fg: "#f4f7fb",
		muted: "#93a0b5",
		faint: "#6e7c90",
		a: "#8fd6ff",
		b: "#6aa6ff",
		c: "#d7e4f5",
		ink: "#05080e"
	},
	{
		id: "ember",
		name: "Ember",
		hint: "Amber and copper",
		bg: "#100806",
		panel: "#1a100c",
		panel2: "#261812",
		fg: "#f8f3ef",
		muted: "#b5a197",
		faint: "#8a786c",
		a: "#ffb15a",
		b: "#ff6a3d",
		c: "#ff4d6a",
		ink: "#0c0604"
	},
	{
		id: "royal",
		name: "Royal",
		hint: "Gold and violet",
		bg: "#0a0812",
		panel: "#141022",
		panel2: "#1c1730",
		fg: "#f6f4fb",
		muted: "#a39bb8",
		faint: "#7c748f",
		a: "#e6c36a",
		b: "#9b6bff",
		c: "#f0a3ff",
		ink: "#07060e"
	},
	{
		id: "amber",
		name: "Amber",
		hint: "Full style · dark gold",
		bg: "#070605",
		panel: "#12100d",
		panel2: "#1b1814",
		fg: "#f4efe8",
		muted: "#a89886",
		faint: "#7d6e5e",
		a: "#f6d7a2",
		b: "#e0a04a",
		c: "#8d5a22",
		ink: "#0b0907"
	}
];
function isThemeId(value) {
	return THEMES.some((theme) => theme.id === value);
}
function themeById(id) {
	return THEMES.find((theme) => theme.id === id) ?? THEMES[0];
}
var SORT_IDS = [
	"default",
	"az",
	"za",
	"balance",
	"turnover",
	"cashout",
	"errors",
	"popular",
	"norefer",
	"shareAmount",
	"shareMax",
	"shareTurn",
	"new"
];
var DOMAIN_LOCALE_OPTS = {
	numeric: true,
	sensitivity: "base"
};
function isSortMode(value) {
	return typeof value === "string" && SORT_IDS.includes(value);
}
function sortRequiresSelectedBonus(mode) {
	return mode === "shareAmount" || mode === "shareMax" || mode === "shareTurn";
}
function sortModeShortLabel(mode) {
	if (mode === "az") return "A → Z";
	if (mode === "za") return "Z → A";
	if (mode === "balance") return "Balance ↓";
	if (mode === "turnover") return "Best turnover";
	if (mode === "cashout") return "Cash-out";
	if (mode === "errors") return "Errors";
	if (mode === "popular") return "Most popular";
	if (mode === "norefer") return "No referlink";
	if (mode === "shareAmount") return "Bonus amount ↓";
	if (mode === "shareMax") return "Bonus max ↓";
	if (mode === "shareTurn") return "Bonus turnover ↑";
	if (mode === "new") return "Newest";
	return "Sort / Filter";
}
function chosenBonus(row, mode) {
	if (mode === "none" || mode === "share") return row.share;
	return row[mode];
}
function bonusHasRealAmount(bonus) {
	if (!bonus || !bonus.lit) return false;
	if (Number.isFinite(bonus.amount) && bonus.amount > 0) return true;
	const disp = String(bonus.amountDisplay ?? "").trim();
	if (disp && !disp.includes("%") && disp !== "—" && disp !== "-" && /[1-9]/.test(disp)) return true;
	return false;
}
function bonusTurnover(bonus) {
	if (!bonus || !bonus.lit) return null;
	if (!(bonus.amount > 0) || bonus.minWithdrawal == null || bonus.minWithdrawal <= 0) return null;
	return bonus.minWithdrawal / bonus.amount;
}
function formatTurnover(value) {
	if (value == null || !Number.isFinite(value)) return "";
	return `${Number(value >= 10 ? value.toFixed(1) : value.toFixed(2))}×`;
}
function bestTurnover(row) {
	const values = [];
	for (const bonus of [
		row.commission,
		row.share,
		row.referral,
		row.new,
		row.rebate
	]) {
		const value = bonusTurnover(bonus);
		if (value != null) values.push(value);
	}
	for (const bonus of row.availableBonuses) {
		const value = bonusTurnover(bonus);
		if (value != null) values.push(value);
	}
	return values.length ? Math.min(...values) : null;
}
function rowHasAnyBonus(row) {
	if (!row || !row.ok) return false;
	for (const bonus of [
		row.commission,
		row.share,
		row.referral,
		row.new,
		row.rebate
	]) if (bonusHasRealAmount(bonus)) return true;
	return row.availableBonuses.some((bonus) => bonusHasRealAmount(bonus));
}
function sortBottomTier(has, row) {
	if (row && !row.ok) return 3;
	if (!has) return rowHasAnyBonus(row) ? 1 : 2;
	return 0;
}
function addedAtTs(value) {
	if (!value) return 0;
	const ts = Date.parse(value);
	return Number.isFinite(ts) ? ts : 0;
}
function compareSites(a, b, rowA, rowB, sort, bonusMode, deadDomains) {
	const azDelta = () => a.domain.localeCompare(b.domain, void 0, DOMAIN_LOCALE_OPTS);
	if (deadDomains && deadDomains.size) {
		const deadA = deadDomains.has(a.domain) ? 1 : 0;
		const deadB = deadDomains.has(b.domain) ? 1 : 0;
		if (deadA !== deadB) return deadA - deadB;
	}
	const okA = rowA?.ok ? rowA : null;
	const okB = rowB?.ok ? rowB : null;
	if (sort === "az") return azDelta();
	if (sort === "za") return b.domain.localeCompare(a.domain, void 0, DOMAIN_LOCALE_OPTS);
	if (sort === "errors") {
		const errA = Boolean(rowA && !rowA.ok);
		const errB = Boolean(rowB && !rowB.ok);
		if (errA !== errB) return errA ? -1 : 1;
		if (errA && errB && rowA && !rowA.ok && rowB && !rowB.ok) {
			const msgCmp = rowA.message.toLowerCase().localeCompare(rowB.message.toLowerCase());
			if (msgCmp) return msgCmp;
		}
		return azDelta();
	}
	if (sort === "norefer") {
		const rank = (row) => !row ? 1 : row.referral?.lit ? 2 : 0;
		return rank(okA) - rank(okB) || azDelta();
	}
	const primary = (item, ok) => {
		if (sort === "balance") {
			const v = ok ? ok.cash : NaN;
			return {
				val: v,
				has: ok != null && Number.isFinite(v),
				asc: false
			};
		}
		if (sort === "turnover") {
			const v = ok ? bestTurnover(ok) : null;
			return {
				val: v ?? Number.POSITIVE_INFINITY,
				has: v != null && Number.isFinite(v),
				asc: true
			};
		}
		if (sort === "cashout") {
			const v = ok?.cashMinWithdrawal ?? null;
			return {
				val: v ?? Number.POSITIVE_INFINITY,
				has: v != null && v > 0,
				asc: true
			};
		}
		if (sort === "shareAmount") {
			const bonus = ok ? chosenBonus(ok, bonusMode) : null;
			const v = bonusHasRealAmount(bonus) ? bonus.amount : NaN;
			return {
				val: v,
				has: Number.isFinite(v) && v > 0,
				asc: false
			};
		}
		if (sort === "shareMax") {
			const bonus = ok ? chosenBonus(ok, bonusMode) : null;
			const v = bonus?.lit && bonus.maxWithdrawal != null ? bonus.maxWithdrawal : NaN;
			return {
				val: v,
				has: Number.isFinite(v) && v > 0,
				asc: false
			};
		}
		if (sort === "shareTurn") {
			const v = bonusTurnover(ok ? chosenBonus(ok, bonusMode) : null);
			return {
				val: v ?? Number.POSITIVE_INFINITY,
				has: v != null && Number.isFinite(v),
				asc: true
			};
		}
		if (sort === "new") {
			const v = addedAtTs(item.addedAt);
			return {
				val: v,
				has: v > 0,
				asc: false
			};
		}
		if (sort === "popular") {
			const v = item.group === "best" ? 2 : item.group === "share" ? 1 : 0;
			return {
				val: v,
				has: v > 0,
				asc: false
			};
		}
		return null;
	};
	const pa = primary(a, okA);
	const pb = primary(b, okB);
	if (pa && pb) {
		const ta = sortBottomTier(pa.has, rowA);
		const tb = sortBottomTier(pb.has, rowB);
		if (ta !== tb) return ta - tb;
		if (ta !== 0) return azDelta();
		return (pa.asc ? pa.val - pb.val : pb.val - pa.val) || azDelta();
	}
	return azDelta();
}
var _jsxFileName$1 = "/app/applet/src/components/vault.tsx";
var KEY = "pokies_vault_v1";
var LIST_EPOCH = "pokies_vault_user_list_v1";
var LIST_ERROR = "The domain list could not be saved on this device, so that change was not kept.";
var CARD_COLORS = [
	{
		id: "classic",
		label: "Off",
		swatch: ""
	},
	{
		id: "pink",
		label: "Pink",
		swatch: "#ff4fd8"
	},
	{
		id: "blue",
		label: "Blue",
		swatch: "#3aa0ff"
	},
	{
		id: "purple",
		label: "Purple",
		swatch: "#a855f7"
	},
	{
		id: "green",
		label: "Green",
		swatch: "#3ddc97"
	},
	{
		id: "teal",
		label: "Teal",
		swatch: "#2ee6d6"
	},
	{
		id: "yellow",
		label: "Yellow",
		swatch: "#ffe14a"
	},
	{
		id: "gold",
		label: "Gold",
		swatch: "#f0b429"
	},
	{
		id: "orange",
		label: "Orange",
		swatch: "#ff8a2a"
	},
	{
		id: "red",
		label: "Red",
		swatch: "#ff4d6a"
	},
	{
		id: "white",
		label: "White",
		swatch: "#e8eeff"
	}
];
function isCardColor(value) {
	return typeof value === "string" && CARD_COLORS.some((item) => item.id === value);
}
function writeVault(state) {
	const { listOwned: _listOwned, sites: _sites, ...rest } = state;
	localStorage.setItem(KEY, JSON.stringify(rest));
}
function withSavedList(current, sites) {
	if (!saveOwnedList(sites)) return null;
	return {
		...current,
		listOwned: true,
		sites
	};
}
var EMPTY = {
	visited: [],
	mine: [],
	custom: [],
	removed: [],
	listOwned: false,
	sites: [],
	phone: "",
	password: "",
	fallbacks: [
		{
			phone: "",
			password: ""
		},
		{
			phone: "",
			password: ""
		},
		{
			phone: "",
			password: ""
		}
	],
	logins: {},
	lastFetch: null,
	attentionChecked: [],
	view: "default",
	notVisited: false,
	signupOnly: false,
	wallets: false,
	mineOnly: false,
	sort: "default",
	bonusMode: "none",
	cardColor: "classic",
	theme: "vault"
};
function usableLogin(value) {
	if (!value || typeof value !== "object") return null;
	const phone = String(value.phone ?? "").replace(/\s+/g, "");
	const password = String(value.password ?? "");
	if (!/^[0-9+]{6,20}$/.test(phone) || password.length < 4 || password.length > 64) return null;
	return {
		phone,
		password
	};
}
function cleanLogins(value) {
	if (!value || typeof value !== "object" || Array.isArray(value)) return {};
	const out = {};
	for (const [rawDomain, rawLogin] of Object.entries(value)) {
		const domain = normalizeDomain(rawDomain);
		const login = usableLogin(rawLogin);
		if (!isPublicDomain(domain) || !login) continue;
		out[domain] = login;
	}
	return out;
}
function load() {
	try {
		const raw = localStorage.getItem(KEY);
		if (!raw) return EMPTY;
		const parsed = JSON.parse(raw);
		return {
			...EMPTY,
			...parsed,
			visited: Array.isArray(parsed.visited) ? parsed.visited : [],
			mine: Array.isArray(parsed.mine) ? parsed.mine : [],
			custom: Array.isArray(parsed.custom) ? parsed.custom : [],
			removed: Array.isArray(parsed.removed) ? Array.from(new Set(parsed.removed.map((item) => normalizeDomain(String(item))).filter(isPublicDomain))) : [],
			listOwned: false,
			sites: [],
			fallbacks: Array.isArray(parsed.fallbacks) && parsed.fallbacks.length === 3 ? parsed.fallbacks : EMPTY.fallbacks,
			logins: cleanLogins(parsed.logins),
			signupOnly: Boolean(parsed.signupOnly),
			sort: isSortMode(parsed.sort) ? parsed.sort : EMPTY.sort,
			bonusMode: parsed.bonusMode ?? EMPTY.bonusMode,
			cardColor: isCardColor(parsed.cardColor) ? parsed.cardColor : EMPTY.cardColor,
			theme: isThemeId(parsed.theme) ? parsed.theme : EMPTY.theme,
			attentionChecked: Array.isArray(parsed.attentionChecked) ? parsed.attentionChecked.filter((item) => !!item && typeof item === "object" && typeof item.domain === "string" && typeof item.code === "string") : []
		};
	} catch {
		return EMPTY;
	}
}
var VaultContext = (0, import_react.createContext)(null);
function VaultProvider({ children }) {
	const [state, setState] = (0, import_react.useState)(EMPTY);
	const [ready, setReady] = (0, import_react.useState)(false);
	(0, import_react.useEffect)(() => {
		const saved = load();
		let custom = saved.custom;
		let removed = saved.removed;
		let mine = saved.mine;
		let visited = saved.visited;
		let lastFetch = saved.lastFetch;
		let attentionChecked = saved.attentionChecked;
		if (localStorage.getItem(LIST_EPOCH) !== "1") {
			clearOwnedList();
			try {
				localStorage.removeItem(INTEL_KEY);
				localStorage.removeItem(CATALOG_STRIPPED_KEY);
			} catch {}
			custom = [];
			removed = [];
			mine = [];
			visited = [];
			lastFetch = null;
			attentionChecked = [];
		}
		const sites = loadOwnedList() ?? workingSites(custom, removed);
		const next = {
			...saved,
			custom,
			removed,
			mine,
			visited,
			lastFetch,
			attentionChecked,
			listOwned: true,
			sites
		};
		if (saveOwnedList(sites)) try {
			localStorage.setItem(LIST_EPOCH, "1");
			writeVault(next);
		} catch {}
		setState(next);
		setReady(true);
	}, []);
	(0, import_react.useEffect)(() => {
		const theme = themeById(state.theme);
		document.documentElement.dataset.theme = theme.id;
		document.querySelector("meta[name=\"theme-color\"]")?.setAttribute("content", theme.bg);
	}, [state.theme]);
	(0, import_react.useEffect)(() => {
		document.documentElement.dataset.cardColor = state.cardColor;
	}, [state.cardColor]);
	const persist = (0, import_react.useCallback)((next) => {
		setState(next);
		try {
			writeVault(next);
		} catch {}
	}, []);
	const patch = (0, import_react.useCallback)((partial) => {
		setState((current) => {
			const next = {
				...current,
				...partial
			};
			try {
				writeVault(next);
			} catch {}
			return next;
		});
	}, []);
	const markVisited = (0, import_react.useCallback)((domain) => {
		setState((current) => {
			if (current.visited.includes(domain)) return current;
			const next = {
				...current,
				visited: [...current.visited, domain]
			};
			try {
				writeVault(next);
			} catch {}
			return next;
		});
	}, []);
	const toggleVisited = (0, import_react.useCallback)((domain) => {
		setState((current) => {
			const visited = current.visited.includes(domain) ? current.visited.filter((item) => item !== domain) : [...current.visited, domain];
			const next = {
				...current,
				visited
			};
			try {
				writeVault(next);
			} catch {}
			return next;
		});
	}, []);
	const toggleMine = (0, import_react.useCallback)((domain) => {
		setState((current) => {
			const mine = current.mine.includes(domain) ? current.mine.filter((item) => item !== domain) : [...current.mine, domain];
			const next = {
				...current,
				mine
			};
			try {
				writeVault(next);
			} catch {}
			return next;
		});
	}, []);
	const rememberAccount = (0, import_react.useCallback)((domain) => {
		setState((current) => {
			if (current.mine.includes(domain)) return current;
			const next = {
				...current,
				mine: [...current.mine, domain]
			};
			try {
				writeVault(next);
			} catch {}
			return next;
		});
	}, []);
	const rememberLogins = (0, import_react.useCallback)((updates) => {
		if (!updates.length) return;
		setState((current) => {
			const logins = { ...current.logins };
			let changed = false;
			for (const update of updates) {
				const domain = normalizeDomain(update.domain);
				if (!isPublicDomain(domain)) continue;
				const login = usableLogin(update.login);
				if (!login) {
					if (domain in logins) {
						delete logins[domain];
						changed = true;
					}
					continue;
				}
				const prev = logins[domain];
				if (prev && prev.phone === login.phone && prev.password === login.password) continue;
				logins[domain] = login;
				changed = true;
			}
			if (!changed) return current;
			const next = {
				...current,
				logins
			};
			try {
				writeVault(next);
			} catch {}
			return next;
		});
	}, []);
	const addDomain = (0, import_react.useCallback)((raw) => {
		const domain = normalizeDomain(raw);
		if (!isPublicDomain(domain)) return "Enter a domain like 9au.com";
		let error = null;
		setState((current) => {
			const base = current.listOwned ? current.sites : workingSites(current.custom, current.removed);
			const exists = base.some((site) => site.domain === domain);
			let next = current;
			if (!exists) {
				const saved = withSavedList(current, [siteRecord(domain, (/* @__PURE__ */ new Date()).toISOString()), ...base]);
				if (!saved) {
					error = LIST_ERROR;
					return current;
				}
				next = saved;
			}
			const custom = next.custom.some((item) => item.domain === domain) ? next.custom : [{
				domain,
				addedAt: (/* @__PURE__ */ new Date()).toISOString()
			}, ...next.custom];
			const mine = next.mine.includes(domain) ? next.mine : [...next.mine, domain];
			const removed = next.removed.filter((item) => item !== domain);
			const stored = {
				...next,
				custom,
				mine,
				removed
			};
			try {
				writeVault(stored);
			} catch {}
			return stored;
		});
		return error;
	}, []);
	const addManaged = (0, import_react.useCallback)((raws) => {
		const incoming = Array.from(new Set(raws.map((item) => normalizeDomain(item)).filter(isPublicDomain)));
		if (!incoming.length) return {
			added: 0,
			error: null
		};
		let added = 0;
		let error = null;
		setState((current) => {
			const base = current.listOwned ? current.sites : workingSites(current.custom, current.removed);
			const have = new Set(base.map((site) => site.domain));
			const sites = [...base];
			const stamp = (/* @__PURE__ */ new Date()).toISOString();
			for (const domain of incoming) {
				if (have.has(domain)) continue;
				sites.push(siteRecord(domain, stamp));
				have.add(domain);
				added += 1;
			}
			if (!added) return current;
			const saved = withSavedList(current, sites);
			if (!saved) {
				error = LIST_ERROR;
				added = 0;
				return current;
			}
			return saved;
		});
		return {
			added,
			error
		};
	}, []);
	const removeManaged = (0, import_react.useCallback)((domains) => {
		const drop = new Set(domains.map((item) => normalizeDomain(item)).filter(isPublicDomain));
		if (!drop.size) return null;
		let error = null;
		setState((current) => {
			const saved = withSavedList(current, (current.listOwned ? current.sites : workingSites(current.custom, current.removed)).filter((site) => !drop.has(site.domain)));
			if (!saved) {
				error = LIST_ERROR;
				return current;
			}
			const custom = current.custom.filter((item) => !drop.has(item.domain));
			const mine = current.mine.filter((item) => !drop.has(item));
			const lastFetch = current.lastFetch ? {
				...current.lastFetch,
				rows: current.lastFetch.rows.filter((row) => !drop.has(row.domain))
			} : current.lastFetch;
			const next = {
				...saved,
				custom,
				mine,
				lastFetch
			};
			try {
				writeVault(next);
			} catch {}
			return next;
		});
		return error;
	}, []);
	const saveOk = (0, import_react.useRef)(true);
	const saveFetch = (0, import_react.useCallback)((rows) => {
		const lastFetch = {
			at: Date.now(),
			rows
		};
		(0, import_react_dom.flushSync)(() => {
			setState((current) => {
				const found = rows.filter((row) => row.ok).map((row) => row.domain);
				const mine = Array.from(/* @__PURE__ */ new Set([...current.mine, ...found]));
				const next = {
					...current,
					lastFetch,
					mine
				};
				try {
					writeVault(next);
					saveOk.current = true;
				} catch {
					saveOk.current = false;
				}
				return next;
			});
		});
		return saveOk.current;
	}, []);
	const clearAll = (0, import_react.useCallback)(() => {
		clearOwnedList();
		try {
			localStorage.removeItem(INTEL_KEY);
		} catch {}
		persist(EMPTY);
	}, [persist]);
	const api = (0, import_react.useMemo)(() => ({
		ready,
		state,
		patch,
		toggleVisited,
		markVisited,
		toggleMine,
		addDomain,
		addManaged,
		removeManaged,
		rememberAccount,
		rememberLogins,
		saveFetch,
		clearAll
	}), [
		ready,
		state,
		patch,
		toggleVisited,
		markVisited,
		toggleMine,
		addDomain,
		addManaged,
		removeManaged,
		rememberAccount,
		rememberLogins,
		saveFetch,
		clearAll
	]);
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(VaultContext.Provider, {
		value: api,
		children
	}, void 0, false, {
		fileName: _jsxFileName$1,
		lineNumber: 532,
		columnNumber: 10
	}, this);
}
function useVault() {
	const ctx = (0, import_react.useContext)(VaultContext);
	if (!ctx) throw new Error("Vault missing");
	return ctx;
}
var styles_default = "/assets/styles-CW7H4OnH.css";
var _jsxFileName = "/app/applet/src/routes/__root.tsx";
var Route$7 = createRootRoute({
	head: () => ({
		meta: [
			{ charSet: "utf-8" },
			{
				name: "viewport",
				content: "width=device-width, initial-scale=1, viewport-fit=cover"
			},
			{ title: "Pokies Vault" },
			{
				name: "description",
				content: "A private list of pokie sites and a bonus fetcher for accounts that share your phone login."
			},
			{
				name: "theme-color",
				content: "#050507"
			}
		],
		links: [
			{
				rel: "icon",
				type: "image/png",
				href: "/__grok/icon-180.png"
			},
			{
				rel: "stylesheet",
				href: styles_default
			},
			{
				rel: "manifest",
				href: "/__grok/manifest.webmanifest"
			},
			{
				rel: "apple-touch-icon",
				href: "/__grok/icon-180.png"
			},
			{
				rel: "stylesheet",
				href: "https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Sora:wght@400;500;600;700&display=swap"
			}
		]
	}),
	component: () => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("html", {
		lang: "en",
		"data-theme": "vault",
		suppressHydrationWarning: true,
		children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("head", { children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(HeadContent, {}, void 0, false, {
			fileName: _jsxFileName,
			lineNumber: 34,
			columnNumber: 9
		}, void 0) }, void 0, false, {
			fileName: _jsxFileName,
			lineNumber: 33,
			columnNumber: 7
		}, void 0), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("body", { children: [
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("script", { dangerouslySetInnerHTML: { __html: "try{var t=JSON.parse(localStorage.getItem(\"pokies_vault_v1\")||\"null\");if(t&&typeof t.theme===\"string\")document.documentElement.dataset.theme=t.theme}catch(e){}try{if(sessionStorage.getItem(\"pokies_intro\")===\"1\")document.documentElement.dataset.intro=\"seen\"}catch(e){}" } }, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 37,
				columnNumber: 9
			}, void 0),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(PreviewHostBridge, {}, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 43,
				columnNumber: 9
			}, void 0),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(AuthProvider, { children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(VaultProvider, { children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Outlet, {}, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 46,
				columnNumber: 13
			}, void 0) }, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 45,
				columnNumber: 11
			}, void 0) }, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 44,
				columnNumber: 9
			}, void 0),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Scripts, {}, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 49,
				columnNumber: 9
			}, void 0)
		] }, void 0, true, {
			fileName: _jsxFileName,
			lineNumber: 36,
			columnNumber: 7
		}, void 0)]
	}, void 0, true, {
		fileName: _jsxFileName,
		lineNumber: 32,
		columnNumber: 5
	}, void 0)
});
var $$splitComponentImporter$4 = () => import("./routes-B3f_4UWE.mjs");
var Route$6 = createFileRoute("/")({ component: lazyRouteComponent($$splitComponentImporter$4, "component") });
var $$splitComponentImporter$3 = () => import("./bonuses-Bx8fu5S4.mjs");
var Route$5 = createFileRoute("/bonuses")({ component: lazyRouteComponent($$splitComponentImporter$3, "component") });
var $$splitComponentImporter$2 = () => import("./fetch-DIyfy758.mjs");
var Route$4 = createFileRoute("/fetch")({ component: lazyRouteComponent($$splitComponentImporter$2, "component") });
var $$splitComponentImporter$1 = () => import("./intel-D6IfG-FC.mjs");
var Route$3 = createFileRoute("/intel")({ component: lazyRouteComponent($$splitComponentImporter$1, "component") });
var $$splitComponentImporter = () => import("./login-CqpPuvlU.mjs");
var Route$2 = createFileRoute("/login")({ component: lazyRouteComponent($$splitComponentImporter, "component") });
var Route$1 = createFileRoute("/api/fetch-log")({ server: { handlers: { GET: async () => {
	const { requireUserId } = await import("./verify.server-D_H91_kE.mjs");
	const { listFetchRuns } = await import("./fetch-log.server-BJK_RrBv.mjs");
	try {
		const runs = await listFetchRuns(await requireUserId());
		return Response.json({ runs });
	} catch {
		return Response.json({ error: "Unauthorized" }, { status: 401 });
	}
} } } });
var Route = createFileRoute("/api/auth/$")({ server: { handlers: {
	GET: ({ request }) => auth.handler(request),
	POST: ({ request }) => auth.handler(request)
} } });
var rootRouteChildren = {
	IndexRoute: Route$6.update({
		id: "/",
		path: "/",
		getParentRoute: () => Route$7
	}),
	BonusesRoute: Route$5.update({
		id: "/bonuses",
		path: "/bonuses",
		getParentRoute: () => Route$7
	}),
	FetchRoute: Route$4.update({
		id: "/fetch",
		path: "/fetch",
		getParentRoute: () => Route$7
	}),
	IntelRoute: Route$3.update({
		id: "/intel",
		path: "/intel",
		getParentRoute: () => Route$7
	}),
	LoginRoute: Route$2.update({
		id: "/login",
		path: "/login",
		getParentRoute: () => Route$7
	}),
	ApiFetchLogRoute: Route$1.update({
		id: "/api/fetch-log",
		path: "/api/fetch-log",
		getParentRoute: () => Route$7
	}),
	ApiAuthSplatRoute: Route.update({
		id: "/api/auth/$",
		path: "/api/auth/$",
		getParentRoute: () => Route$7
	})
};
var routeTree = Route$7._addFileChildren(rootRouteChildren)._addFileTypes();
var router_exports = /* @__PURE__ */ __exportAll({ getRouter: () => getRouter });
function getRouter() {
	return createRouter({
		routeTree,
		defaultErrorComponent: AppErrorComponent
	});
}
export { bonusTurnover as a, sortModeShortLabel as c, visibleSites as d, INTEL_KEY as f, bonusHasRealAmount as i, sortRequiresSelectedBonus as l, CARD_COLORS as n, compareSites as o, domainsFromText as p, useVault as r, formatTurnover as s, router_exports as t, THEMES as u };
