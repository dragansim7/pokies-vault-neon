import { o as __toESM } from "../_runtime.mjs";
import { a as fetchCasinoBatchFn, l as useServerFn, r as createSsrRpc, s as lookupMerchantsFn, t as AppFrame } from "./shell-DWuSpmKc.mjs";
import { d as visibleSites, o as compareSites, r as useVault } from "./router-yRR4M0i7.mjs";
import { n as formatAdded, r as money, t as SiteIcon } from "./format-Dbq6jmzA.mjs";
import { t as AccountCard } from "./account-card-DJ7DBoUD.mjs";
import { r as createServerFn } from "./ssr.mjs";
import { t as authMiddleware } from "./middleware-DWpfziPI.mjs";
import { n as normalizeDomain, t as isPublicDomain } from "./domain-DVT1Is7p.mjs";
import { n as parseSyncUser, t as accountError } from "./parse-account-CVt5DAa3.mjs";
import { Gt as require_react, b as Link } from "../_libs/@tanstack/react-router+[...].mjs";
import { S as ChevronLeft, d as RefreshCw, h as LoaderCircle, v as Eye, y as EyeOff } from "../_libs/lucide-react.mjs";
import { t as require_jsx_dev_runtime } from "../_libs/react.mjs";
// node_modules/.nitro/vite/services/ssr/assets/fetch-DIyfy758.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_dev_runtime = require_jsx_dev_runtime();
var recordFetchRunFn = createServerFn({ method: "POST" }).middleware([authMiddleware]).validator((data) => data).handler(createSsrRpc("4f37c24d7e8a5fc6bb8ca7f63b49bf82b8fda1485c8f313c556d283d15bbaef1"));
var listFetchRunsFn = createServerFn({ method: "GET" }).middleware([authMiddleware]).handler(createSsrRpc("0b3a5afae0e015ef4f66aa85b7fb9200debb67476643ab437da977692aea2306"));
function messageOf(data) {
	if (!data || typeof data !== "object") return "";
	const message = data.message;
	return typeof message === "string" ? message : "";
}
function classifyFailure(domain, message, fallback) {
	const text = message.toLowerCase();
	if (text.includes("invalid login") || text.includes("invalid password")) return accountError(domain, "invalid_login", "Not your account");
	if (text.includes("captcha")) return accountError(domain, "captcha", "Site asked for a captcha");
	if (text.includes("bad-json")) return accountError(domain, "unreachable", "Site did not return account data");
	if (text.includes("abort") || text.includes("timeout")) return accountError(domain, "timeout", "Timed out");
	return accountError(domain, "other", fallback);
}
var CALL_MS = 18e3;
function callSignal(signal) {
	const timeout = AbortSignal.timeout(CALL_MS);
	return {
		signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
		timedOut: () => timeout.aborted && !signal?.aborted
	};
}
async function postApi(host, fields, signal) {
	const timed = callSignal(signal);
	let response;
	try {
		response = await fetch(`https://${host}/api/v1/index.php`, {
			method: "POST",
			signal: timed.signal,
			headers: {
				"content-type": "application/x-www-form-urlencoded; charset=UTF-8",
				accept: "application/json,text/plain,*/*"
			},
			body: new URLSearchParams(fields)
		});
	} catch (error) {
		if (signal?.aborted) throw error;
		if (timed.timedOut()) throw new Error("timeout");
		throw error;
	}
	const text = await response.text();
	try {
		return JSON.parse(text);
	} catch {
		throw new Error("bad-json");
	}
}
async function fetchOne(target, phone, password, signal, knownPromotions) {
	const domain = target.domain;
	if (!/^[0-9+]{6,20}$/.test(phone)) return accountError(domain, "other", "Enter the mobile number you use on these sites");
	if (password.length < 4 || password.length > 64) return accountError(domain, "other", "Password should be 4–64 characters");
	try {
		const login = await postApi(target.host, {
			module: "/users/login",
			merchantId: target.merchantId,
			accessId: "",
			accessToken: "",
			walletIsAdmin: "",
			mobile: phone,
			password
		}, signal);
		if (login.status !== "SUCCESS") return classifyFailure(domain, messageOf(login.data), "Could not sign in");
		const session = login.data;
		const accessId = session && typeof session.id === "string" ? session.id : "";
		const accessToken = session && typeof session.token === "string" ? session.token : "";
		if (!accessId || !accessToken) return accountError(domain, "other", "Sign-in did not return a session");
		const synced = await postApi(target.host, {
			module: "/users/syncData",
			merchantId: target.merchantId,
			accessId,
			accessToken,
			walletIsAdmin: "",
			background: "1"
		}, signal);
		if (synced.status !== "SUCCESS") return classifyFailure(domain, messageOf(synced.data), "Signed in, but balances did not load");
		const syncedRow = synced.data && typeof synced.data === "object" ? synced.data : null;
		let promotions = knownPromotions;
		if (!syncedRow || !Array.isArray(syncedRow.bonus)) {
			if (promotions === void 0) try {
				const pub = await postApi(target.host, {
					module: "/merchants/getPublicInfo",
					merchantId: target.merchantId,
					accessId: "",
					accessToken: "",
					walletIsAdmin: ""
				}, signal);
				promotions = (pub.data && typeof pub.data === "object" ? pub.data : null)?.promotions;
			} catch {
				promotions = void 0;
			}
		}
		return parseSyncUser(domain, synced.data, promotions);
	} catch (error) {
		if (signal?.aborted || error instanceof DOMException && error.name === "AbortError") throw error;
		return classifyFailure(domain, error instanceof Error ? error.message : "", "Could not reach site");
	}
}
async function probeSite(domain, signal) {
	let response;
	const timed = callSignal(signal);
	try {
		response = await fetch(`https://${domain}/api/v1/index.php`, {
			method: "POST",
			signal: timed.signal,
			headers: {
				"content-type": "application/x-www-form-urlencoded; charset=UTF-8",
				accept: "application/json,text/plain,*/*"
			},
			body: new URLSearchParams({
				module: "/merchants/getPublicInfo",
				merchantId: "",
				accessId: "",
				accessToken: "",
				walletIsAdmin: ""
			})
		});
	} catch (error) {
		if (signal?.aborted) throw error;
		const message = error instanceof Error ? error.message.toLowerCase() : "";
		if (timed.timedOut() || message.includes("timeout") || message.includes("abort")) return {
			ok: false,
			code: "timeout",
			message: "Timed out"
		};
		return {
			ok: false,
			code: "unreachable",
			message: "Could not reach site"
		};
	}
	const trimmed = (await response.text()).trim();
	if (response.status === 401 || response.status === 403 || response.status === 429 || /just a moment|cf-browser-verification|challenge-platform|attention required/i.test(trimmed)) return {
		ok: false,
		code: "blocked",
		message: "Page blocked"
	};
	let body = null;
	try {
		const parsed = JSON.parse(trimmed);
		if (parsed && (parsed.status === "SUCCESS" || parsed.status === "ERROR")) body = parsed;
	} catch {
		body = null;
	}
	if (!body) return {
		ok: false,
		code: "unsupported",
		message: "Different login system"
	};
	if (body.status === "SUCCESS") return {
		ok: true,
		promotions: (body.data && typeof body.data === "object" ? body.data : null)?.promotions
	};
	if (messageOf(body.data).toLowerCase().includes("captcha")) return {
		ok: false,
		code: "captcha",
		message: "Site asked for a captcha"
	};
	return { ok: true };
}
async function fetchAccountOnDevice(input) {
	const domain = normalizeDomain(input.domain);
	let promotions;
	if (!input.knownSystem) {
		const probed = await probeSite(domain, input.signal);
		if (!probed.ok) return {
			row: accountError(domain, probed.code, probed.message),
			confirmed: false,
			mode: null
		};
		promotions = probed.promotions;
	}
	const host = input.host && isPublicDomain(normalizeDomain(input.host)) ? normalizeDomain(input.host) : domain;
	const merchantId = input.merchantId && /^\d{1,12}$/.test(input.merchantId) ? input.merchantId : "";
	const id = (input.only ?? (input.preferBlank || !merchantId ? "blank" : "id")) === "id" && merchantId ? merchantId : "";
	const row = await fetchOne({
		domain,
		ok: true,
		host,
		merchantId: id
	}, input.phone, input.password, input.signal, promotions);
	if (row.ok) return {
		row,
		confirmed: true,
		mode: id ? "id" : "blank"
	};
	return {
		row,
		confirmed: true,
		mode: null
	};
}
var _jsxFileName$1 = "/app/applet/src/components/fetcher.tsx";
var PAGE = 12;
var BATCH = 10;
var RUN_KEY = "vault-run";
var KNOWN_KEY = "pokies_vault_known_systems_v1";
var MERCHANT_KEY = "pokies_vault_merchants_v1";
var MODE_KEY = "pokies_vault_merchant_mode_v1";
var START_WORKERS = 16;
var START_GAP_MS = 500;
var RETRY_GAP_MS = 7e3;
var IDLE_LIVE = {
	phase: "idle",
	fromPhone: false,
	done: 0,
	total: 0,
	startedAt: null,
	finishedAt: null,
	fetchMs: 0,
	attempts: 0,
	retries: 0,
	peakQueue: 0,
	workers: 0
};
function formatSpan(ms) {
	const seconds = Math.max(0, Math.round(ms / 1e3));
	const minutes = Math.floor(seconds / 60);
	const rest = seconds % 60;
	if (minutes <= 0) return `${rest}s`;
	return `${minutes}m ${rest}s`;
}
function viaLabel(fromPhone) {
	return fromPhone ? "From this phone" : "From the vault";
}
function readRun() {
	if (typeof localStorage === "undefined") return null;
	try {
		const raw = localStorage.getItem(RUN_KEY);
		if (!raw) return null;
		const parsed = JSON.parse(raw);
		if (!parsed || !Array.isArray(parsed.done) || !Array.isArray(parsed.rows)) return null;
		return {
			at: typeof parsed.at === "number" ? parsed.at : Date.now(),
			done: parsed.done,
			rows: parsed.rows
		};
	} catch {
		return null;
	}
}
function loadKnown(logins, rows) {
	const known = /* @__PURE__ */ new Set();
	try {
		const raw = JSON.parse(localStorage.getItem(KNOWN_KEY) || "[]");
		if (Array.isArray(raw)) for (const item of raw) {
			const domain = normalizeDomain(String(item));
			if (isPublicDomain(domain)) known.add(domain);
		}
	} catch {}
	for (const domain of Object.keys(logins)) {
		const clean = normalizeDomain(domain);
		if (isPublicDomain(clean)) known.add(clean);
	}
	for (const row of rows) if (row.ok) known.add(row.domain);
	return known;
}
function saveKnown(known) {
	try {
		localStorage.setItem(KNOWN_KEY, JSON.stringify([...known]));
	} catch {}
}
function loadMerchantMap() {
	try {
		const raw = JSON.parse(localStorage.getItem(MERCHANT_KEY) || "{}");
		if (!raw || typeof raw !== "object" || Array.isArray(raw)) return {};
		const out = {};
		for (const [key, value] of Object.entries(raw)) {
			const domain = normalizeDomain(key);
			if (!isPublicDomain(domain) || !value || typeof value !== "object") continue;
			const merchantId = String(value.merchantId ?? "");
			const host = normalizeDomain(String(value.host ?? domain));
			if (!/^\d{1,12}$/.test(merchantId)) continue;
			out[domain] = {
				host: isPublicDomain(host) ? host : domain,
				merchantId
			};
		}
		return out;
	} catch {
		return {};
	}
}
function loadModeMap() {
	try {
		const raw = JSON.parse(localStorage.getItem(MODE_KEY) || "{}");
		if (!raw || typeof raw !== "object" || Array.isArray(raw)) return {};
		const out = {};
		for (const [key, value] of Object.entries(raw)) {
			const domain = normalizeDomain(key);
			if (!isPublicDomain(domain)) continue;
			if (value === "id" || value === "blank") out[domain] = value;
		}
		return out;
	} catch {
		return {};
	}
}
function credsFor(domain, form, saved) {
	const ordered = [];
	const seen = /* @__PURE__ */ new Set();
	const push = (login) => {
		if (!login || seen.has(loginKey(login))) return;
		seen.add(loginKey(login));
		ordered.push(login);
	};
	push(usableLogin(saved[domain]));
	for (const login of form) push(login);
	return ordered;
}
function writeRun(checkpoint) {
	const slim = {
		at: checkpoint.at,
		done: checkpoint.done,
		rows: []
	};
	try {
		localStorage.setItem(RUN_KEY, JSON.stringify(checkpoint));
	} catch {
		try {
			localStorage.setItem(RUN_KEY, JSON.stringify(slim));
		} catch {}
	}
}
function overlayRows(base, updates) {
	const map = new Map(base.map((row) => [row.domain, row]));
	for (const row of updates) map.set(row.domain, row);
	return [...map.values()];
}
function chunkDomains(domains) {
	const batches = [];
	for (let i = 0; i < domains.length; i += BATCH) batches.push(domains.slice(i, i + BATCH));
	return batches;
}
function loginKey(login) {
	return `${login.phone}\0${login.password}`;
}
function usableLogin(login) {
	if (!login) return null;
	const phone = login.phone.replace(/\s+/g, "");
	if (!/^[0-9+]{6,20}$/.test(phone) || login.password.length < 4 || login.password.length > 64) return null;
	return {
		phone,
		password: login.password
	};
}
function planBatches(domains, form, saved) {
	const groups = /* @__PURE__ */ new Map();
	const fresh = [];
	for (const domain of domains) {
		const known = usableLogin(saved[domain]);
		if (!known) {
			fresh.push(domain);
			continue;
		}
		const key = loginKey(known);
		const group = groups.get(key);
		if (group) group.domains.push(domain);
		else groups.set(key, {
			first: known,
			domains: [domain]
		});
	}
	const batches = [];
	for (const group of groups.values()) {
		const rest = form.filter((cred) => loginKey(cred) !== loginKey(group.first));
		for (const slice of chunkDomains(group.domains)) batches.push({
			domains: slice,
			creds: [group.first, ...rest]
		});
	}
	if (fresh.length && form.length) for (const slice of chunkDomains(fresh)) batches.push({
		domains: slice,
		creds: form
	});
	return {
		batches,
		unseen: form.length ? 0 : fresh.length
	};
}
function cents(amount) {
	return Math.round(amount * 100) / 100;
}
function boxesOf(row) {
	const wallets = row.wallets.filter((wallet) => wallet.balance > .001);
	const walletBoxes = wallets.length ? wallets.map((wallet) => ({
		label: wallet.name,
		amount: wallet.balance,
		kind: "wallet"
	})) : row.cash > .001 ? [{
		label: row.cashWalletName,
		amount: row.cash,
		kind: "wallet"
	}] : [];
	const promos = (row.promoBoxes ?? []).filter((box) => box.amount > .001).map((box) => ({
		label: box.label,
		amount: box.amount,
		kind: "promo"
	}));
	return [...walletBoxes, ...promos];
}
function totalsOf(row) {
	let wallets = 0;
	let claimable = 0;
	for (const box of boxesOf(row)) if (box.kind === "wallet") wallets += box.amount;
	else claimable += box.amount;
	wallets = cents(wallets);
	claimable = cents(claimable);
	return {
		wallets,
		claimable,
		total: cents(wallets + claimable)
	};
}
function boxMap(row) {
	const map = /* @__PURE__ */ new Map();
	for (const box of boxesOf(row)) {
		const key = `${box.kind}\0${box.label}`;
		const current = map.get(key);
		if (current) current.amount = cents(current.amount + box.amount);
		else map.set(key, {
			...box,
			amount: cents(box.amount)
		});
	}
	return map;
}
function changesFor(before, after) {
	if (!before?.ok) {
		const total = totalsOf(after).total;
		if (total <= 0) return [];
		return [{
			id: `${after.domain}:new`,
			domain: after.domain,
			label: "Now showing",
			from: null,
			to: total
		}];
	}
	const prev = boxMap(before);
	const next = boxMap(after);
	const notes = [];
	const keys = /* @__PURE__ */ new Set([...prev.keys(), ...next.keys()]);
	for (const key of keys) {
		const oldBox = prev.get(key);
		const newBox = next.get(key);
		const from = oldBox?.amount ?? null;
		const to = newBox?.amount ?? null;
		if (from != null && to != null && Math.abs(from - to) < .01) continue;
		if (from == null && to == null) continue;
		const label = newBox?.label ?? oldBox?.label ?? "Balance";
		notes.push({
			id: `${after.domain}:${key}`,
			domain: after.domain,
			label,
			from,
			to
		});
	}
	notes.sort((a, b) => Math.abs((b.to ?? 0) - (b.from ?? 0)) - Math.abs((a.to ?? 0) - (a.from ?? 0)));
	return notes;
}
function deltaText(note) {
	if (note.from == null && note.to != null) return note.label === "Now showing" ? money(note.to) : `New ${money(note.to)}`;
	if (note.to == null) return `${money(note.from)} → ${money(0)}`;
	return `${money(note.from)} → ${money(note.to)}`;
}
function deltaClass(note) {
	const from = note.from ?? 0;
	const to = note.to ?? 0;
	if (to > from + .009) return "delta up";
	if (to < from - .009) return "delta down";
	return "delta";
}
function PageList({ rows, addedAt }) {
	const [page, setPage] = (0, import_react.useState)(0);
	const pages = Math.max(1, Math.ceil(rows.length / PAGE));
	const safe = Math.min(page, pages - 1);
	const slice = rows.slice(safe * PAGE, safe * PAGE + PAGE);
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [pages > 1 ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
		className: "pager",
		children: [
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
				type: "button",
				className: "btn",
				disabled: safe === 0,
				onClick: () => setPage(safe - 1),
				children: "Previous"
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 322,
				columnNumber: 11
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [
				"Page ",
				safe + 1,
				" / ",
				pages
			] }, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 325,
				columnNumber: 11
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
				type: "button",
				className: "btn",
				disabled: safe >= pages - 1,
				onClick: () => setPage(safe + 1),
				children: "Next"
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 328,
				columnNumber: 11
			}, this)
		]
	}, void 0, true, {
		fileName: _jsxFileName$1,
		lineNumber: 321,
		columnNumber: 9
	}, this) : null, /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
		className: "stack",
		children: slice.map((row, index) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(AccountCard, {
			domain: row.domain,
			rank: safe * PAGE + index + 1,
			addedAt: addedAt(row.domain),
			account: row
		}, row.domain, false, {
			fileName: _jsxFileName$1,
			lineNumber: 340,
			columnNumber: 11
		}, this))
	}, void 0, false, {
		fileName: _jsxFileName$1,
		lineNumber: 338,
		columnNumber: 7
	}, this)] }, void 0, true, {
		fileName: _jsxFileName$1,
		lineNumber: 319,
		columnNumber: 5
	}, this);
}
var CODE_LABEL = {
	captcha: "Captcha",
	invalid_login: "Not your account",
	timeout: "Timed out",
	unreachable: "Unreachable",
	blocked: "Page blocked",
	empty: "Empty page",
	unsupported: "Different login",
	other: "Other"
};
var CODE_ORDER = [
	"captcha",
	"invalid_login",
	"timeout",
	"unreachable",
	"blocked",
	"empty",
	"unsupported",
	"other"
];
function isMarked(marks, domain, code) {
	return marks.some((mark) => mark.domain === domain && mark.code === code);
}
function NeedsAttention({ savedAt, rows, marks, onToggle }) {
	const [showChecked, setShowChecked] = (0, import_react.useState)(false);
	const [expanded, setExpanded] = (0, import_react.useState)({});
	const stamp = savedAt ? formatAdded(new Date(savedAt).toISOString()) : "";
	const counts = CODE_ORDER.map((code) => ({
		code,
		count: rows.filter((row) => row.code === code).length
	})).filter((item) => item.count > 0);
	const hidden = rows.filter((row) => isMarked(marks, row.domain, row.code)).length;
	const visible = showChecked ? rows : rows.filter((row) => !isMarked(marks, row.domain, row.code));
	const groups = counts.map((item) => ({
		...item,
		rows: visible.filter((row) => row.code === item.code)
	})).filter((group) => group.rows.length > 0);
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
		className: "panel attention",
		children: [
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h2", {
				className: "section-label",
				children: ["Needs attention · ", rows.length]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 408,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
				className: "hint",
				children: "Rejected sites are saved with the fetch. A checked row stays hidden until that site comes back with a different code."
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 409,
				columnNumber: 7
			}, this),
			counts.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
				className: "attention-counts",
				children: counts.map((item) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [
					CODE_LABEL[item.code],
					" ",
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("b", { children: item.count }, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 414,
						columnNumber: 39
					}, this)
				] }, item.code, true, {
					fileName: _jsxFileName$1,
					lineNumber: 413,
					columnNumber: 13
				}, this))
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 411,
				columnNumber: 9
			}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
				className: "hint",
				children: "No rejected sites saved yet."
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 419,
				columnNumber: 9
			}, this),
			hidden > 0 ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
				type: "button",
				className: "btn",
				onClick: () => setShowChecked((value) => !value),
				children: showChecked ? "Hide checked" : `Show checked (${hidden})`
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 422,
				columnNumber: 9
			}, this) : null,
			groups.map((group) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "attention-group",
				children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", { children: [
					CODE_LABEL[group.code],
					" · ",
					group.rows.length
				] }, void 0, true, {
					fileName: _jsxFileName$1,
					lineNumber: 428,
					columnNumber: 11
				}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "attention-table",
					children: [(expanded[group.code] ? group.rows : group.rows.slice(0, 30)).map((row) => {
						const checked = isMarked(marks, row.domain, row.code);
						return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "attention-row",
							children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
									className: "attention-check",
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
										type: "checkbox",
										checked,
										onChange: () => onToggle(row.domain, row.code)
									}, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 437,
										columnNumber: 21
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
										className: "sr",
										children: "Checked"
									}, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 442,
										columnNumber: 21
									}, this)]
								}, void 0, true, {
									fileName: _jsxFileName$1,
									lineNumber: 436,
									columnNumber: 19
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", {
									className: "attention-domain",
									children: row.domain
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 444,
									columnNumber: 19
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "attention-code",
									children: CODE_LABEL[row.code]
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 445,
									columnNumber: 19
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "attention-message",
									children: row.message
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 446,
									columnNumber: 19
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("time", {
									dateTime: savedAt ? new Date(savedAt).toISOString() : void 0,
									children: stamp
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 447,
									columnNumber: 19
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("a", {
									href: `https://${row.domain}/`,
									target: "_blank",
									rel: "noreferrer",
									children: "Open"
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 448,
									columnNumber: 19
								}, this)
							]
						}, `${row.domain}-${row.code}`, true, {
							fileName: _jsxFileName$1,
							lineNumber: 435,
							columnNumber: 17
						}, this);
					}), group.rows.length > 30 && !expanded[group.code] ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: "btn",
						onClick: () => setExpanded((current) => ({
							...current,
							[group.code]: true
						})),
						children: ["Show all ", group.rows.length]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 455,
						columnNumber: 15
					}, this) : null]
				}, void 0, true, {
					fileName: _jsxFileName$1,
					lineNumber: 431,
					columnNumber: 11
				}, this)]
			}, group.code, true, {
				fileName: _jsxFileName$1,
				lineNumber: 427,
				columnNumber: 9
			}, this))
		]
	}, void 0, true, {
		fileName: _jsxFileName$1,
		lineNumber: 407,
		columnNumber: 5
	}, this);
}
function Fetcher() {
	const { state, patch, rememberAccount, rememberLogins, saveFetch } = useVault();
	const fetchBatch = useServerFn(fetchCasinoBatchFn);
	const lookupMerchants = useServerFn(lookupMerchantsFn);
	const recordFetchRun = useServerFn(recordFetchRunFn);
	const listFetchRuns = useServerFn(listFetchRunsFn);
	const [onDevice, setOnDevice] = (0, import_react.useState)(false);
	const [showPassword, setShowPassword] = (0, import_react.useState)(false);
	const [advanced, setAdvanced] = (0, import_react.useState)(false);
	const [single, setSingle] = (0, import_react.useState)("");
	const [status, setStatus] = (0, import_react.useState)("Enter the phone number and password you use on these sites.");
	const [running, setRunning] = (0, import_react.useState)(false);
	const [progress, setProgress] = (0, import_react.useState)({
		done: 0,
		total: 0
	});
	const [live, setLive] = (0, import_react.useState)(IDLE_LIVE);
	const [now, setNow] = (0, import_react.useState)(() => Date.now());
	const [rows, setRows] = (0, import_react.useState)(state.lastFetch?.rows ?? []);
	const [showErrors, setShowErrors] = (0, import_react.useState)(false);
	const [confirmScan, setConfirmScan] = (0, import_react.useState)(false);
	const [diffs, setDiffs] = (0, import_react.useState)([]);
	const [comparedAt, setComparedAt] = (0, import_react.useState)(null);
	const [pendingRun, setPendingRun] = (0, import_react.useState)(null);
	const [outgoing, setOutgoing] = (0, import_react.useState)(null);
	const [outgoingError, setOutgoingError] = (0, import_react.useState)("");
	const [outgoingLoading, setOutgoingLoading] = (0, import_react.useState)(false);
	const [outgoingTick, setOutgoingTick] = (0, import_react.useState)(0);
	const [sharedRuns, setSharedRuns] = (0, import_react.useState)([]);
	const stopRef = (0, import_react.useRef)(null);
	const onDeviceRef = (0, import_react.useRef)(false);
	const liveRef = (0, import_react.useRef)(live);
	const rowsRef = (0, import_react.useRef)(rows);
	rowsRef.current = rows;
	(0, import_react.useEffect)(() => {
		onDeviceRef.current = true;
		setOnDevice(true);
	}, []);
	(0, import_react.useEffect)(() => {
		listFetchRuns().then((runs) => setSharedRuns(runs)).catch(() => setSharedRuns([]));
	}, []);
	(0, import_react.useEffect)(() => {
		let cancelled = false;
		setOutgoingLoading(true);
		setOutgoingError("");
		const load = async () => {
			const data = await (await fetch("https://ipwho.is/", { cache: "no-store" })).json();
			if (!data.success || !data.ip) throw new Error("no ip");
			return {
				ip: data.ip,
				isp: data.connection?.isp || data.connection?.org || "Unknown network",
				place: [data.city, data.country_code].filter(Boolean).join(", ")
			};
		};
		load().then((info) => {
			if (!cancelled) setOutgoing(info);
		}).catch(() => {
			if (!cancelled) {
				setOutgoing(null);
				setOutgoingError("Could not read the outgoing IP");
			}
		}).finally(() => {
			if (!cancelled) setOutgoingLoading(false);
		});
		return () => {
			cancelled = true;
		};
	}, [outgoingTick]);
	(0, import_react.useEffect)(() => {
		if (live.phase !== "running") return;
		const id = window.setInterval(() => setNow(Date.now()), 500);
		return () => window.clearInterval(id);
	}, [live.phase]);
	const sites = (0, import_react.useMemo)(() => visibleSites(state), [state]);
	(0, import_react.useEffect)(() => {
		const saved = readRun();
		if (saved && saved.done.length < sites.length) setPendingRun(saved);
	}, [sites.length]);
	const creds = (0, import_react.useMemo)(() => {
		const list = [usableLogin({
			phone: state.phone,
			password: state.password
		})];
		for (const item of state.fallbacks) list.push(usableLogin(item));
		const seen = /* @__PURE__ */ new Set();
		const out = [];
		for (const item of list) {
			if (!item || seen.has(loginKey(item))) continue;
			seen.add(loginKey(item));
			out.push(item);
		}
		return out;
	}, [
		state.phone,
		state.password,
		state.fallbacks
	]);
	const valid = rows.filter((row) => row.ok && row.hasData);
	const empty = rows.filter((row) => row.ok && !row.hasData);
	const errors = rows.filter((row) => !row.ok);
	const unsupportedDomains = errors.filter((row) => row.code === "unsupported").map((row) => row.domain);
	const siteMeta = (0, import_react.useMemo)(() => {
		const map = new Map(sites.map((site) => [site.domain, site]));
		return (domain) => map.get(domain) ?? {
			domain,
			group: "custom",
			addedAt: null
		};
	}, [sites]);
	const orderOf = (a, b) => compareSites(siteMeta(a.domain), siteMeta(b.domain), a, b, state.sort, state.bonusMode);
	const ranked = [...valid].sort(orderOf);
	const quiet = [...empty].sort(orderOf);
	const walletTotal = cents(ranked.reduce((sum, row) => sum + totalsOf(row).wallets, 0));
	const claimableTotal = cents(ranked.reduce((sum, row) => sum + totalsOf(row).claimable, 0));
	const waiting = cents(walletTotal + claimableTotal);
	function setFallback(index, partial) {
		const fallbacks = state.fallbacks.map((item, i) => i === index ? {
			...item,
			...partial
		} : item);
		patch({ fallbacks });
	}
	async function run(domains, resumeFrom, mergeBase) {
		const unique = Array.from(new Set(domains));
		if (!unique.length) {
			setStatus("No sites to check. Save accounts from the list, add a domain, or scan.");
			return;
		}
		const { batches, unseen } = planBatches(unique, creds, state.logins);
		if (!batches.length) {
			setStatus("Enter your phone number and password before fetching.");
			return;
		}
		const fromPhone = onDeviceRef.current;
		const merging = Boolean(mergeBase);
		const previousAt = state.lastFetch?.at ?? null;
		const previous = new Map((state.lastFetch?.rows ?? []).filter((row) => row.ok).map((row) => [row.domain, row]));
		stopRef.current?.abort();
		const controller = new AbortController();
		stopRef.current = controller;
		const collected = resumeFrom ? [...resumeFrom.rows] : mergeBase ? [...mergeBase] : [];
		const done = resumeFrom ? [...resumeFrom.done] : [];
		const total = resumeFrom ? sites.length : unique.length - unseen;
		const started = {
			phase: "running",
			fromPhone,
			done: done.length,
			total,
			startedAt: Date.now(),
			finishedAt: null,
			fetchMs: 0,
			attempts: 0,
			retries: 0,
			peakQueue: 0,
			workers: fromPhone ? START_WORKERS : Math.min(2, batches.length)
		};
		liveRef.current = started;
		setLive(started);
		setNow(started.startedAt ?? Date.now());
		setRunning(true);
		setShowErrors(false);
		setDiffs([]);
		setComparedAt(null);
		setRows(collected);
		setProgress({
			done: done.length,
			total
		});
		setStatus(resumeFrom ? `Resuming ${done.length} of ${sites.length}…` : merging ? `Retrying ${unique.length} unsupported sites…` : unique.length === 1 ? `Checking ${unique[0]}…` : unseen ? `Checking ${total} sites that already have a saved login…` : `Checking ${unique.length} sites…`);
		const priorRows = overlayRows(state.lastFetch?.rows ?? [], mergeBase ?? resumeFrom?.rows ?? []);
		let paintTimer = null;
		let stored = true;
		const store = () => {
			const ok = saveFetch(overlayRows(priorRows, collected));
			if (!ok) stored = false;
			return ok;
		};
		const paint = () => {
			const snapshot = [...collected].sort((a, b) => a.domain.localeCompare(b.domain));
			setRows(snapshot);
			setProgress({
				done: done.length,
				total
			});
			liveRef.current = {
				...liveRef.current,
				done: done.length,
				total
			};
			setLive(liveRef.current);
			if (!merging) {
				const checkpoint = {
					at: Date.now(),
					done: [...done],
					rows: snapshot
				};
				writeRun(checkpoint);
				setPendingRun(done.length < sites.length ? checkpoint : null);
			}
			if (collected.length) store();
		};
		const publish = (batchRows, loginNotes) => {
			rememberLogins(loginNotes);
			for (const row of batchRows) {
				if (row.ok) rememberAccount(row.domain);
				const index = collected.findIndex((item) => item.domain === row.domain);
				if (index >= 0) collected[index] = row;
				else collected.push(row);
				if (!done.includes(row.domain)) done.push(row.domain);
			}
			if (fromPhone) {
				liveRef.current = {
					...liveRef.current,
					done: done.length,
					total
				};
				setLive(liveRef.current);
				setProgress({
					done: done.length,
					total
				});
				if (paintTimer == null) paintTimer = window.setTimeout(() => {
					paintTimer = null;
					paint();
				}, 400);
				return;
			}
			paint();
		};
		let cursor = 0;
		if (fromPhone) {
			const known = loadKnown(state.logins, state.lastFetch?.rows ?? []);
			const merchants = loadMerchantMap();
			const modes = loadModeMap();
			let merchantTimer = null;
			const saveMerchantState = () => {
				try {
					localStorage.setItem(MERCHANT_KEY, JSON.stringify(merchants));
					localStorage.setItem(MODE_KEY, JSON.stringify(modes));
				} catch {}
			};
			const queueMerchantState = () => {
				if (merchantTimer != null) return;
				merchantTimer = window.setTimeout(() => {
					merchantTimer = null;
					saveMerchantState();
				}, 1e3);
			};
			const waiters = [];
			let flushTimer = null;
			const flushMerchants = () => {
				flushTimer = null;
				const batch = waiters.splice(0, 12);
				if (!batch.length) return;
				lookupMerchants({
					data: { domains: batch.map((item) => item.domain) },
					signal: controller.signal
				}).then((rows) => {
					const byDomain = new Map(rows.map((row) => [normalizeDomain(row.domain), row]));
					for (const waiter of batch) {
						const row = byDomain.get(waiter.domain);
						if (row?.merchantId && /^\d{1,12}$/.test(row.merchantId)) {
							const hit = {
								host: normalizeDomain(row.host) || waiter.domain,
								merchantId: row.merchantId
							};
							merchants[waiter.domain] = hit;
							waiter.resolve(hit);
						} else waiter.resolve(null);
					}
					queueMerchantState();
				}).catch(() => {
					for (const waiter of batch) waiter.resolve(null);
				}).finally(() => {
					if (waiters.length && !controller.signal.aborted) flushMerchants();
				});
			};
			const merchantFor = (domain) => {
				const cached = merchants[domain];
				if (cached?.merchantId) return Promise.resolve(cached);
				return new Promise((resolve) => {
					const timer = window.setTimeout(() => resolve(null), 12e3);
					waiters.push({
						domain,
						resolve: (hit) => {
							window.clearTimeout(timer);
							resolve(hit);
						}
					});
					if (waiters.length >= 8) {
						if (flushTimer != null) window.clearTimeout(flushTimer);
						flushMerchants();
						return;
					}
					if (flushTimer != null) return;
					flushTimer = window.setTimeout(flushMerchants, 40);
				});
			};
			const jobs = unique.map((domain) => ({
				domain,
				creds: credsFor(domain, creds, state.logins)
			})).filter((job) => job.creds.length);
			let limit = START_WORKERS;
			let active = 0;
			let lastStart = 0;
			let slot = Promise.resolve();
			const pause = (ms) => new Promise((resolve, reject) => {
				if (controller.signal.aborted) {
					reject(new DOMException("Aborted", "AbortError"));
					return;
				}
				const onAbort = () => {
					window.clearTimeout(timer);
					reject(new DOMException("Aborted", "AbortError"));
				};
				const timer = window.setTimeout(() => {
					controller.signal.removeEventListener("abort", onAbort);
					resolve();
				}, ms);
				controller.signal.addEventListener("abort", onAbort, { once: true });
			});
			const takeSlot = () => {
				const run = slot.then(async () => {
					const wait = Math.max(0, START_GAP_MS - (Date.now() - lastStart));
					if (wait) await pause(wait);
					lastStart = Date.now();
				});
				slot = run.then(() => void 0, () => void 0);
				return run;
			};
			let knownTimer = null;
			const queueKnown = () => {
				if (knownTimer != null) return;
				knownTimer = window.setTimeout(() => {
					knownTimer = null;
					saveKnown(known);
				}, 1e3);
			};
			let spawn = () => {};
			await new Promise((resolve) => {
				const finish = () => {
					if (active === 0 && (controller.signal.aborted || cursor >= jobs.length)) resolve();
				};
				spawn = () => {
					while (!controller.signal.aborted && active < limit && cursor < jobs.length) {
						const job = jobs[cursor];
						cursor += 1;
						if (!job) break;
						active += 1;
						liveRef.current = {
							...liveRef.current,
							workers: limit,
							peakQueue: Math.max(liveRef.current.peakQueue, active)
						};
						setLive(liveRef.current);
						(async () => {
							const startedTry = Date.now();
							let attempts = 0;
							let retries = 0;
							let last = {
								domain: job.domain,
								ok: false,
								code: "invalid_login",
								message: "Not your account"
							};
							const finishStats = () => {
								liveRef.current = {
									...liveRef.current,
									fetchMs: liveRef.current.fetchMs + (Date.now() - startedTry),
									attempts: liveRef.current.attempts + attempts,
									retries: liveRef.current.retries + retries,
									workers: limit
								};
							};
							const attempt = async (cred, only) => {
								await takeSlot();
								if (controller.signal.aborted) return null;
								attempts += 1;
								const hit = await merchantFor(job.domain);
								if (controller.signal.aborted) return null;
								const merchantId = hit?.merchantId && /^\d{1,12}$/.test(hit.merchantId) ? hit.merchantId : "";
								const used = only === "id" && merchantId ? "id" : only === "blank" || !merchantId ? "blank" : "id";
								try {
									return {
										result: await fetchAccountOnDevice({
											domain: job.domain,
											phone: cred.phone,
											password: cred.password,
											signal: controller.signal,
											knownSystem: known.has(job.domain),
											host: hit?.host,
											merchantId,
											preferBlank: used === "blank",
											only: used
										}),
										used,
										merchantId
									};
								} catch (error) {
									if (controller.signal.aborted || error instanceof DOMException && error.name === "AbortError") return null;
									return {
										result: {
											row: {
												domain: job.domain,
												ok: false,
												code: "other",
												message: "Check failed"
											},
											confirmed: false,
											mode: null
										},
										used,
										merchantId
									};
								}
							};
							try {
								for (let i = 0; i < job.creds.length; i += 1) {
									const cred = job.creds[i];
									if (!cred || controller.signal.aborted) return;
									if (i > 0) {
										retries += 1;
										await pause(RETRY_GAP_MS);
									}
									const remembered = modes[job.domain];
									const first = await attempt(cred, remembered === "id" || remembered === "blank" ? remembered : void 0);
									if (!first) return;
									if (first.result.mode) {
										modes[job.domain] = first.result.mode;
										queueMerchantState();
									}
									if (first.result.confirmed) {
										known.add(job.domain);
										queueKnown();
									}
									const row = first.result.row;
									if (row.ok) {
										finishStats();
										publish([row], [{
											domain: job.domain,
											login: cred
										}]);
										return;
									}
									if (row.code === "captcha" || row.code === "timeout" || row.code !== "invalid_login") {
										finishStats();
										publish([row], []);
										return;
									}
									last = row;
								}
								finishStats();
								publish([last], [{
									domain: job.domain,
									login: null
								}]);
							} catch (error) {
								if (controller.signal.aborted || error instanceof DOMException && error.name === "AbortError") return;
								finishStats();
								publish([{
									domain: job.domain,
									ok: false,
									code: "other",
									message: "Check failed"
								}], []);
							}
						})().finally(() => {
							active -= 1;
							spawn();
							finish();
						});
					}
					finish();
				};
				spawn();
			});
			if (knownTimer != null) window.clearTimeout(knownTimer);
			if (merchantTimer != null) window.clearTimeout(merchantTimer);
			if (flushTimer != null) window.clearTimeout(flushTimer);
			saveKnown(known);
			saveMerchantState();
		} else {
			const vaultLimit = Math.min(2, batches.length);
			let vaultActive = 0;
			const noteVault = (delta) => {
				vaultActive = Math.max(0, vaultActive + delta);
				liveRef.current = {
					...liveRef.current,
					workers: vaultActive,
					peakQueue: Math.max(liveRef.current.peakQueue, vaultActive)
				};
				setLive(liveRef.current);
			};
			const worker = async () => {
				for (;;) {
					if (controller.signal.aborted) return;
					const index = cursor;
					cursor += 1;
					if (index >= batches.length) return;
					const batch = batches[index];
					if (!batch) return;
					noteVault(1);
					try {
						let pending = batch.domains;
						const settled = /* @__PURE__ */ new Map();
						const loginNotes = [];
						try {
							for (const cred of batch.creds) {
								if (controller.signal.aborted || !pending.length) break;
								const startedTry = Date.now();
								const result = await fetchBatch({
									data: {
										domains: pending,
										phone: cred.phone,
										password: cred.password
									},
									signal: controller.signal
								});
								const retry = [];
								const returned = new Set(result.map((row) => row.domain));
								for (const row of result) if (row.ok) {
									settled.set(row.domain, row);
									loginNotes.push({
										domain: row.domain,
										login: cred
									});
								} else if (row.code !== "invalid_login") settled.set(row.domain, row);
								else retry.push(row.domain);
								for (const domain of pending) if (!returned.has(domain) && !settled.has(domain)) retry.push(domain);
								liveRef.current = {
									...liveRef.current,
									fetchMs: liveRef.current.fetchMs + (Date.now() - startedTry),
									attempts: liveRef.current.attempts + Math.max(result.length, pending.length),
									retries: liveRef.current.retries + retry.length,
									workers: vaultActive,
									peakQueue: Math.max(liveRef.current.peakQueue, vaultActive)
								};
								setLive(liveRef.current);
								pending = retry;
							}
						} catch (error) {
							if (controller.signal.aborted || error instanceof DOMException && error.name === "AbortError") return;
							for (const domain of pending) if (!settled.has(domain)) settled.set(domain, {
								domain,
								ok: false,
								code: "other",
								message: "Check failed"
							});
							pending = [];
						}
						if (controller.signal.aborted) return;
						for (const domain of pending) {
							settled.set(domain, {
								domain,
								ok: false,
								code: "invalid_login",
								message: "Not your account"
							});
							loginNotes.push({
								domain,
								login: null
							});
						}
						publish(batch.domains.map((domain) => settled.get(domain) ?? {
							domain,
							ok: false,
							code: "other",
							message: "Check failed"
						}), loginNotes);
					} finally {
						noteVault(-1);
					}
				}
			};
			await Promise.all(Array.from({ length: vaultLimit }, () => worker()));
		}
		if (paintTimer != null) {
			window.clearTimeout(paintTimer);
			paintTimer = null;
		}
		paint();
		const stopped = controller.signal.aborted;
		const finalRows = [...collected].sort((a, b) => a.domain.localeCompare(b.domain));
		setRows(finalRows);
		if (stored && !merging && !stopped && done.length >= sites.length) {
			try {
				localStorage.removeItem(RUN_KEY);
			} catch {}
			setPendingRun(null);
		}
		const notes = [];
		for (const row of finalRows) {
			if (!row.ok) continue;
			notes.push(...changesFor(previous.get(row.domain), row));
		}
		notes.sort((a, b) => Math.abs((b.to ?? 0) - (b.from ?? 0)) - Math.abs((a.to ?? 0) - (a.from ?? 0)) || a.domain.localeCompare(b.domain));
		setDiffs(notes);
		setComparedAt(previousAt);
		const signedIn = finalRows.filter((row) => row.ok).length;
		liveRef.current = {
			...liveRef.current,
			phase: stopped ? "stopped" : "done",
			finishedAt: Date.now(),
			done: done.length
		};
		setLive(liveRef.current);
		const counts = {};
		for (const row of finalRows) {
			if (row.ok) continue;
			counts[row.code] = (counts[row.code] ?? 0) + 1;
		}
		const report = {
			at: liveRef.current.finishedAt ?? Date.now(),
			source: fromPhone ? "phone" : "vault",
			total: finalRows.length,
			signedIn,
			rejected: finalRows.length - signedIn,
			attempts: liveRef.current.attempts,
			retries: liveRef.current.retries,
			workers: liveRef.current.workers,
			peak: liveRef.current.peakQueue,
			wallMs: liveRef.current.startedAt ? (liveRef.current.finishedAt ?? Date.now()) - liveRef.current.startedAt : 0,
			counts,
			signedInDomains: finalRows.filter((row) => row.ok).map((row) => row.domain).slice(0, 40)
		};
		recordFetchRun({ data: report }).then(() => listFetchRuns()).then((runs) => setSharedRuns(runs)).catch(() => void 0);
		setStatus(!stored ? "Finished, but this phone could not store the result. Leave the app open and try the fetch again." : stopped ? "Stopped. Successful sign-ins stay in My accounts." : `Done. ${signedIn} of ${finalRows.length} signed in. Only those are kept as your accounts.`);
		setRunning(false);
	}
	const allowed = new Set(sites.map((site) => site.domain));
	const mineTargets = (state.mine.length ? state.mine : sites.filter((site) => state.visited.includes(site.domain)).map((site) => site.domain)).filter((domain) => allowed.has(domain));
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
		className: "stack",
		"data-fetch-from": onDevice ? "phone" : "server",
		children: [
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Link, {
				to: "/",
				className: "hint",
				style: {
					display: "inline-flex",
					alignItems: "center",
					gap: 4
				},
				children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(ChevronLeft, { size: 16 }, void 0, false, {
					fileName: _jsxFileName$1,
					lineNumber: 1098,
					columnNumber: 9
				}, this), " Back to the list"]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1097,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", { children: [
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h1", {
					className: "brand grad-text",
					style: { fontSize: "1.7rem" },
					children: "Fetch bonus data"
				}, void 0, false, {
					fileName: _jsxFileName$1,
					lineNumber: 1101,
					columnNumber: 9
				}, this),
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
					className: "hint",
					children: "Sign-in stays on this device. Four sites run at once. The vault only reads each site's public merchant number, then this phone signs in. If that number is rejected, it tries once without it and remembers which one worked. The password is not sent through the vault."
				}, void 0, false, {
					fileName: _jsxFileName$1,
					lineNumber: 1104,
					columnNumber: 9
				}, this),
				Object.keys(state.logins).length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
					className: "hint",
					children: [Object.keys(state.logins).length, " sites have a saved login."]
				}, void 0, true, {
					fileName: _jsxFileName$1,
					lineNumber: 1110,
					columnNumber: 11
				}, this) : null
			] }, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1100,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "panel stack",
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
						className: "field",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "Phone number" }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1116,
							columnNumber: 11
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
							className: "text-input",
							inputMode: "tel",
							autoComplete: "username",
							value: state.phone,
							placeholder: "04…",
							onChange: (event) => patch({ phone: event.target.value })
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1117,
							columnNumber: 11
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1115,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
						className: "field",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "Password" }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1127,
							columnNumber: 11
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "pass-row",
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
								className: "text-input",
								type: showPassword ? "text" : "password",
								autoComplete: "current-password",
								value: state.password,
								onChange: (event) => patch({ password: event.target.value })
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1129,
								columnNumber: 13
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn",
								"aria-label": showPassword ? "Hide password" : "Show password",
								onClick: () => setShowPassword((value) => !value),
								children: showPassword ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(EyeOff, { size: 16 }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 1142,
									columnNumber: 31
								}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Eye, { size: 16 }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 1142,
									columnNumber: 54
								}, this)
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1136,
								columnNumber: 13
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 1128,
							columnNumber: 11
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1126,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: "btn btn-ghost",
						onClick: () => setAdvanced((value) => !value),
						children: advanced ? "Hide fallback logins" : "Fallback logins"
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1146,
						columnNumber: 9
					}, this),
					advanced ? state.fallbacks.map((item, index) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "stack",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
							className: "field",
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [
								"Fallback ",
								index + 2,
								" phone"
							] }, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1153,
								columnNumber: 19
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
								className: "text-input",
								value: item.phone,
								inputMode: "tel",
								onChange: (event) => setFallback(index, { phone: event.target.value })
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1154,
								columnNumber: 19
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 1152,
							columnNumber: 17
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
							className: "field",
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [
								"Fallback ",
								index + 2,
								" password"
							] }, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1162,
								columnNumber: 19
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
								className: "text-input",
								type: "password",
								value: item.password,
								onChange: (event) => setFallback(index, { password: event.target.value })
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1163,
								columnNumber: 19
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 1161,
							columnNumber: 17
						}, this)]
					}, index, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1151,
						columnNumber: 15
					}, this)) : null,
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "hint",
						children: "Saved only in this browser. Sign-in goes from this browser, so the casino sees the address below."
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1173,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "actions",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn btn-grad",
								disabled: running,
								onClick: () => void run(mineTargets),
								children: [
									running ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(LoaderCircle, { size: 16 }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 1183,
										columnNumber: 24
									}, this) : null,
									" Fetch my accounts",
									mineTargets.length ? ` (${mineTargets.length})` : ""
								]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1177,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn",
								disabled: running || unsupportedDomains.length === 0,
								onClick: () => void run(unsupportedDomains, void 0, rows),
								children: ["Retry unsupported", unsupportedDomains.length ? ` (${unsupportedDomains.length})` : ""]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1186,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn",
								disabled: !running,
								onClick: () => stopRef.current?.abort(),
								children: "Stop"
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1194,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn",
								disabled: running || !state.lastFetch,
								onClick: () => {
									setRows(state.lastFetch?.rows ?? []);
									setDiffs([]);
									setComparedAt(null);
									setStatus("Showing the last fetch saved on this device.");
								},
								children: "View last fetch"
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1197,
								columnNumber: 11
							}, this)
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1176,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "check-row",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
							className: "text-input",
							value: single,
							placeholder: "Check one site, e.g. 9au.com",
							autoCapitalize: "none",
							autoCorrect: "off",
							spellCheck: false,
							onChange: (event) => setSingle(event.target.value)
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1212,
							columnNumber: 11
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "btn",
							disabled: running,
							onClick: () => {
								const domain = normalizeDomain(single);
								if (!isPublicDomain(domain)) {
									setStatus("Enter a domain like 9au.com");
									return;
								}
								run([domain]);
							},
							children: "Check"
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1221,
							columnNumber: 11
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1211,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: "btn btn-ghost",
						disabled: running,
						onClick: () => setConfirmScan(true),
						children: "Scan the full list to find my accounts"
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1237,
						columnNumber: 9
					}, this),
					pendingRun && !running ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: "btn",
						onClick: () => {
							const done = new Set(pendingRun.done);
							run(sites.map((site) => site.domain).filter((domain) => !done.has(domain)), pendingRun);
						},
						children: [
							"Resume scan (",
							pendingRun.done.length,
							" of ",
							sites.length,
							")"
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1242,
						columnNumber: 13
					}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: "btn",
						onClick: () => {
							try {
								localStorage.removeItem(RUN_KEY);
							} catch {}
							setPendingRun(null);
							setRows(state.lastFetch?.rows ?? []);
							setDiffs([]);
							setComparedAt(null);
							setProgress({
								done: 0,
								total: 0
							});
							setLive(IDLE_LIVE);
							setStatus("Cleared the stopped run. Saved accounts are unchanged.");
						},
						children: "Clear current run"
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1253,
						columnNumber: 13
					}, this)] }, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1241,
						columnNumber: 11
					}, this) : null,
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "ip-card ip-card-local",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
								className: "ip-card-top",
								children: [
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
										className: "ip-card-title",
										children: ["Outgoing IP: ", outgoingLoading ? "Checking…" : outgoing?.ip || "—"]
									}, void 0, true, {
										fileName: _jsxFileName$1,
										lineNumber: 1277,
										columnNumber: 13
									}, this),
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
										className: "ip-badge",
										children: "Local device IP"
									}, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 1280,
										columnNumber: 13
									}, this),
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
										type: "button",
										className: "ip-refresh",
										"aria-label": "Check the outgoing IP again",
										onClick: () => setOutgoingTick((value) => value + 1),
										children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(RefreshCw, {
											size: 16,
											className: outgoingLoading ? "spin" : void 0
										}, void 0, false, {
											fileName: _jsxFileName$1,
											lineNumber: 1287,
											columnNumber: 15
										}, this)
									}, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 1281,
										columnNumber: 13
									}, this)
								]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1276,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "ip-card-line",
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "ip-dot",
									"aria-hidden": "true"
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 1291,
									columnNumber: 13
								}, this), outgoing ? [outgoing.isp, outgoing.place].filter(Boolean).join(" · ") : outgoingError || "Reading the address casinos will see"]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1290,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "ip-card-line",
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "ip-check",
									"aria-hidden": "true"
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 1295,
									columnNumber: 13
								}, this), "Sign-in from this browser · casinos see this IP"]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1294,
								columnNumber: 11
							}, this)
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1275,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "panel live-fetch",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h2", {
								className: "section-label",
								children: "Live fetch"
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1300,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "live-via",
								children: viaLabel(live.phase === "idle" ? onDevice : live.fromPhone)
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1301,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "live-count",
								children: [
									live.phase === "idle" ? 0 : live.done,
									" / ",
									live.total,
									" processed · ",
									live.total ? Math.round((live.phase === "idle" ? 0 : live.done) / live.total * 100) : 0,
									"%"
								]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1302,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "live-meta",
								children: live.phase === "running" ? "Running" : live.phase === "done" ? "Done" : live.phase === "stopped" ? "Stopped" : "Idle"
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1305,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "live-meta",
								children: [
									live.phase === "idle" ? 0 : rows.filter((row) => row.ok).length,
									" signed in · ",
									live.phase === "idle" ? 0 : errors.length,
									" rejected · ",
									live.phase === "idle" ? 0 : ranked.length,
									" with a balance · ",
									live.phase === "idle" ? 0 : quiet.length,
									" nothing waiting"
								]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1308,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "live-meta",
								children: [
									"Queue ",
									Math.max(0, live.total - (live.phase === "idle" ? 0 : live.done)),
									" · workers ",
									live.workers,
									" · peak ",
									live.peakQueue
								]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1311,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "live-meta",
								children: [
									"Wall ",
									live.startedAt ? formatSpan((live.finishedAt ?? now) - live.startedAt) : "0s",
									" · sign-in ",
									formatSpan(live.fetchMs)
								]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1314,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "live-meta",
								children: [
									"Retries ",
									live.retries,
									" · attempts ",
									live.attempts
								]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1317,
								columnNumber: 11
							}, this)
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1299,
						columnNumber: 9
					}, this),
					sharedRuns.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "panel",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h2", {
							className: "section-label",
							children: "Runs from phone and browser"
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1323,
							columnNumber: 13
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("ul", {
							className: "diff",
							children: sharedRuns.slice(0, 8).map((run) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("li", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "diff-copy",
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: run.source === "phone" ? "Phone" : "Vault" }, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 1328,
									columnNumber: 21
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [
									run.signedIn,
									" signed in · ",
									run.rejected,
									" rejected · ",
									run.total,
									" sites"
								] }, void 0, true, {
									fileName: _jsxFileName$1,
									lineNumber: 1329,
									columnNumber: 21
								}, this)]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1327,
								columnNumber: 19
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: formatAdded(new Date(run.at).toISOString()) }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1333,
								columnNumber: 19
							}, this)] }, run.at, true, {
								fileName: _jsxFileName$1,
								lineNumber: 1326,
								columnNumber: 17
							}, this))
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1324,
							columnNumber: 13
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1322,
						columnNumber: 11
					}, this) : null,
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "progress-line",
						children: [
							viaLabel(live.phase === "idle" ? onDevice : live.fromPhone),
							". ",
							status
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1339,
						columnNumber: 9
					}, this),
					progress.total > 0 ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "bar",
						"aria-hidden": "true",
						children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { style: { width: `${Math.round(progress.done / progress.total * 100)}%` } }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1342,
							columnNumber: 13
						}, this)
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1341,
						columnNumber: 11
					}, this) : null
				]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1114,
				columnNumber: 7
			}, this),
			comparedAt != null ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "panel",
				children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: ["Changes since ", formatAdded(new Date(comparedAt).toISOString())] }, void 0, true, {
					fileName: _jsxFileName$1,
					lineNumber: 1349,
					columnNumber: 11
				}, this), diffs.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("ul", {
					className: "diff",
					children: diffs.map((note) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("li", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
						className: "diff-copy",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: note.domain }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1355,
							columnNumber: 21
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: note.label }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1356,
							columnNumber: 21
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1354,
						columnNumber: 19
					}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
						className: deltaClass(note),
						children: deltaText(note)
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1358,
						columnNumber: 19
					}, this)] }, note.id, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1353,
						columnNumber: 17
					}, this))
				}, void 0, false, {
					fileName: _jsxFileName$1,
					lineNumber: 1351,
					columnNumber: 13
				}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
					className: "hint",
					children: "No change in wallets or claimable bonuses."
				}, void 0, false, {
					fileName: _jsxFileName$1,
					lineNumber: 1363,
					columnNumber: 13
				}, this)]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1348,
				columnNumber: 9
			}, this) : null,
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "result-summary",
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h2", {
						className: "section-label",
						children: ["Your accounts · ", ranked.length]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1369,
						columnNumber: 9
					}, this),
					ranked.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "waiting",
						children: [
							money(waiting),
							" ",
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "waiting" }, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1373,
								columnNumber: 32
							}, this)
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1372,
						columnNumber: 13
					}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "hint waiting-split",
						children: [
							"Wallets ",
							money(walletTotal),
							" + claimable ",
							money(claimableTotal)
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1375,
						columnNumber: 13
					}, this)] }, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1371,
						columnNumber: 11
					}, this) : null,
					state.lastFetch && !running ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "hint waiting-split",
						children: ["Saved ", formatAdded(new Date(state.lastFetch.at).toISOString())]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1381,
						columnNumber: 11
					}, this) : null,
					ranked.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(PageList, {
						rows: ranked,
						addedAt: (domain) => siteMeta(domain).addedAt
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1383,
						columnNumber: 26
					}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "hint",
						children: "Nothing with a balance or bonus yet."
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1383,
						columnNumber: 102
					}, this)
				]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1368,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h2", {
				className: "section-label",
				children: ["Signed in, nothing waiting · ", empty.length]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1386,
				columnNumber: 9
			}, this), quiet.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(PageList, {
				rows: quiet,
				addedAt: (domain) => siteMeta(domain).addedAt
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 1387,
				columnNumber: 25
			}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
				className: "hint",
				children: "None."
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 1387,
				columnNumber: 100
			}, this)] }, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1385,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h2", {
				className: "section-label",
				children: ["Not your account or unreachable · ", errors.length]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1390,
				columnNumber: 9
			}, this), errors.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
				type: "button",
				className: "btn",
				onClick: () => setShowErrors((value) => !value),
				children: [showErrors ? "Hide" : "Show", " rejected sites"]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1393,
				columnNumber: 13
			}, this), showErrors ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "stack",
				style: { marginTop: 8 },
				children: errors.map((row) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "result-card error-row",
					children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(SiteIcon, { domain: row.domain }, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1400,
						columnNumber: 21
					}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: row.domain }, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1402,
						columnNumber: 23
					}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "hint",
						children: row.message
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 1403,
						columnNumber: 23
					}, this)] }, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 1401,
						columnNumber: 21
					}, this)]
				}, row.domain, true, {
					fileName: _jsxFileName$1,
					lineNumber: 1399,
					columnNumber: 19
				}, this))
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 1397,
				columnNumber: 15
			}, this) : null] }, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1392,
				columnNumber: 11
			}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
				className: "hint",
				children: "None this fetch. Rejected sites stay saved under Needs attention."
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 1411,
				columnNumber: 11
			}, this)] }, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 1389,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(NeedsAttention, {
				savedAt: state.lastFetch?.at ?? null,
				rows: (state.lastFetch?.rows ?? []).filter((row) => !row.ok),
				marks: state.attentionChecked,
				onToggle: (domain, code) => {
					const marks = state.attentionChecked;
					const exists = marks.some((mark) => mark.domain === domain && mark.code === code);
					patch({ attentionChecked: exists ? marks.filter((mark) => !(mark.domain === domain && mark.code === code)) : [...marks, {
						domain,
						code
					}] });
				}
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 1415,
				columnNumber: 7
			}, this),
			confirmScan ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "modal-backdrop",
				onClick: () => setConfirmScan(false),
				children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "modal-card stack",
					onClick: (event) => event.stopPropagation(),
					children: [
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", { children: "Scan every listed site?" }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1433,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
							className: "hint",
							children: "Sites that already signed in use that saved login. The rest try the phone and password above. You can stop it at any time."
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 1434,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "actions",
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn",
								onClick: () => setConfirmScan(false),
								children: "Cancel"
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1439,
								columnNumber: 15
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn btn-grad",
								onClick: () => {
									setConfirmScan(false);
									run(sites.map((site) => site.domain));
								},
								children: "Scan my login"
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 1442,
								columnNumber: 15
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName$1,
							lineNumber: 1438,
							columnNumber: 13
						}, this)
					]
				}, void 0, true, {
					fileName: _jsxFileName$1,
					lineNumber: 1432,
					columnNumber: 11
				}, this)
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 1431,
				columnNumber: 9
			}, this) : null
		]
	}, void 0, true, {
		fileName: _jsxFileName$1,
		lineNumber: 1096,
		columnNumber: 5
	}, this);
}
var _jsxFileName = "/app/applet/src/routes/fetch.tsx?tsr-split=component";
function FetchPage() {
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(AppFrame, { children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Fetcher, {}, void 0, false, {
		fileName: _jsxFileName,
		lineNumber: 5,
		columnNumber: 7
	}, this) }, void 0, false, {
		fileName: _jsxFileName,
		lineNumber: 4,
		columnNumber: 10
	}, this);
}
export { FetchPage as component };
