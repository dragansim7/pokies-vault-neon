import { n as normalizeDomain, t as isPublicDomain } from "./domain-DVT1Is7p.mjs";
import { i as parsePublicPromotions } from "./public-bonus-CyJnVOiu.mjs";
import { n as parseSyncUser, t as accountError } from "./parse-account-CVt5DAa3.mjs";
// node_modules/.nitro/vite/services/ssr/assets/casino.impl.server-P0Waj4hn.js
function iconFromHtml(html, pageUrl) {
	const tags = html.match(/<link\b[^>]*>/gi) ?? [];
	let icon = "";
	let apple = "";
	for (const tag of tags) {
		const rel = (/rel=["']([^"']+)["']/i.exec(tag)?.[1] ?? "").toLowerCase();
		const href = /href=["']([^"']+)["']/i.exec(tag)?.[1] ?? "";
		if (!href || href.startsWith("data:")) continue;
		if (rel.split(/\s+/).includes("icon") && !rel.includes("apple")) icon = href;
		else if (rel.includes("apple-touch-icon") && !apple) apple = href;
	}
	const chosen = icon || apple;
	if (!chosen) return "";
	try {
		const url = new URL(chosen, pageUrl);
		return url.protocol === "https:" ? url.href : "";
	} catch {
		return "";
	}
}
var UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36";
function messageOf(data) {
	if (!data || typeof data !== "object") return "";
	const message = data.message;
	return typeof message === "string" ? message : "";
}
async function postApi(host, fields, signal, mode = "self") {
	const headers = {
		"content-type": "application/x-www-form-urlencoded; charset=UTF-8",
		accept: "application/json,text/plain,*/*",
		"user-agent": UA
	};
	if (mode === "self") {
		headers.origin = `https://${host}`;
		headers.referer = `https://${host}/`;
	}
	const text = await (await fetch(`https://${host}/api/v1/index.php`, {
		method: "POST",
		redirect: "follow",
		signal,
		headers,
		body: new URLSearchParams(fields)
	})).text();
	try {
		return JSON.parse(text);
	} catch {
		throw new Error("bad-json");
	}
}
function classifyFailure(domain, message, fallback) {
	const text = message.toLowerCase();
	if (text.includes("invalid login") || text.includes("invalid password")) return accountError(domain, "invalid_login", "Not your account");
	if (text.includes("captcha")) return accountError(domain, "captcha", "Site asked for a captcha");
	if (text.includes("bad-json")) return accountError(domain, "unreachable", "Site did not return account data");
	if (text.includes("abort") || text.includes("timeout")) return accountError(domain, "timeout", "Timed out");
	return accountError(domain, "other", fallback);
}
var iconCache = /* @__PURE__ */ new Map();
async function lookupSiteIcon(rawDomain) {
	const domain = normalizeDomain(rawDomain);
	if (!isPublicDomain(domain)) return "";
	const cached = iconCache.get(domain);
	if (cached && Date.now() - cached.at < 864e5) return cached.url;
	const url = await readSiteIcon(domain);
	iconCache.set(domain, {
		url,
		at: Date.now()
	});
	return url;
}
async function readSiteIcon(domain) {
	try {
		const response = await fetch(`https://${domain}/`, {
			redirect: "follow",
			signal: AbortSignal.timeout(8e3),
			headers: {
				accept: "text/html",
				"user-agent": UA
			}
		});
		if (!response.ok || !response.body) return "";
		const reader = response.body.getReader();
		const decoder = new TextDecoder();
		let html = "";
		while (html.length < 12e4) {
			const { done, value } = await reader.read();
			if (done) break;
			html += decoder.decode(value, { stream: true });
			if (/<\/head>/i.test(html)) break;
		}
		await reader.cancel().catch(() => void 0);
		return iconFromHtml(html, response.url || `https://${domain}/`);
	} catch {
		return "";
	}
}
async function lookupEgress() {
	const data = await (await fetch("https://ipwho.is/", { signal: AbortSignal.timeout(8e3) })).json();
	if (!data.success || !data.ip) throw new Error("no ip");
	const isp = data.connection?.isp || data.connection?.org || "Unknown network";
	const place = [data.city, data.country_code].filter(Boolean).join(", ");
	return {
		ip: data.ip,
		isp,
		place
	};
}
async function inspectCasino(rawDomain) {
	const domain = normalizeDomain(rawDomain);
	if (!isPublicDomain(domain)) return {
		domain: domain || "domain",
		ok: false,
		code: "other",
		message: "Enter a real site domain"
	};
	const signal = AbortSignal.timeout(18e3);
	try {
		const home = await fetch(`https://${domain}/`, {
			redirect: "follow",
			signal,
			headers: {
				accept: "text/html",
				"user-agent": UA
			}
		});
		const finalHost = normalizeDomain(new URL(home.url).hostname);
		const host = isPublicDomain(finalHost) ? finalHost : domain;
		const html = (await home.text()).slice(0, 2e6);
		const merchantId = html.match(/MERCHANTID\s*=\s*(\d+)/)?.[1] ?? "";
		if (merchantId || /api\/v1\/index\.php|cdn\.vefrop\.com\/mobile\/wallet\/app\.js/.test(html)) return {
			domain,
			ok: true,
			host,
			merchantId
		};
		if (home.status === 401 || home.status === 403 || home.status === 429 || /just a moment|cf-browser-verification|challenge-platform|attention required/i.test(html)) return {
			domain,
			ok: false,
			code: "blocked",
			message: "Page blocked"
		};
		if (html.trim().length < 400) return {
			domain,
			ok: false,
			code: "empty",
			message: "Empty page"
		};
		return {
			domain,
			ok: false,
			code: "unsupported",
			message: "Different login system"
		};
	} catch (error) {
		const row = classifyFailure(domain, error instanceof Error ? error.message : "", "Could not reach site");
		if (row.ok) return {
			domain,
			ok: false,
			code: "other",
			message: "Could not reach site"
		};
		return {
			domain,
			ok: false,
			code: row.code,
			message: row.message
		};
	}
}
async function resolveCasinoTargets(domains) {
	const out = new Array(domains.length);
	let cursor = 0;
	await Promise.all(Array.from({ length: Math.min(6, domains.length) }, async () => {
		for (;;) {
			const i = cursor++;
			if (i >= domains.length) return;
			out[i] = await inspectCasino(domains[i] ?? "");
		}
	}));
	return out;
}
var merchantMemory = /* @__PURE__ */ new Map();
async function readMerchant(domain) {
	const response = await fetch(`https://${domain}/`, {
		redirect: "follow",
		signal: AbortSignal.timeout(1e4),
		headers: {
			accept: "text/html",
			"user-agent": UA
		}
	});
	if (!response.ok || !response.body) return null;
	const finalHost = normalizeDomain(new URL(response.url).hostname);
	const host = isPublicDomain(finalHost) ? finalHost : domain;
	const reader = response.body.getReader();
	const decoder = new TextDecoder();
	let html = "";
	let merchantId = "";
	while (html.length < 12e5) {
		const { done, value } = await reader.read();
		if (done) break;
		html += decoder.decode(value, { stream: true });
		const found = html.match(/MERCHANTID\s*=\s*(\d+)/);
		if (found?.[1]) {
			merchantId = found[1];
			break;
		}
	}
	await reader.cancel().catch(() => void 0);
	return merchantId ? {
		host,
		merchantId
	} : null;
}
async function lookupMerchants(domains) {
	const list = Array.from(new Set(domains.map((item) => normalizeDomain(item)).filter(isPublicDomain))).slice(0, 20);
	const out = new Array(list.length);
	let cursor = 0;
	await Promise.all(Array.from({ length: Math.min(4, list.length) }, async () => {
		for (;;) {
			const index = cursor++;
			if (index >= list.length) return;
			const domain = list[index] ?? "";
			try {
				const found = await readMerchant(domain);
				out[index] = {
					domain,
					host: found?.host || domain,
					merchantId: found?.merchantId || ""
				};
			} catch {
				out[index] = {
					domain,
					host: domain,
					merchantId: ""
				};
			}
		}
	}));
	return out;
}
async function loadPublicCatalogue(raw) {
	const items = raw.slice(0, 4);
	return Promise.all(items.map(async (item) => {
		const domain = normalizeDomain(item.domain);
		if (!isPublicDomain(domain)) return {
			domain: domain || "domain",
			ok: false,
			bonuses: [],
			message: "Enter a real site domain"
		};
		const givenId = item.merchantId && /^\d{1,12}$/.test(item.merchantId) ? item.merchantId : "";
		const givenHost = item.host ? normalizeDomain(item.host) : "";
		let host = "";
		let merchantId = "";
		if (givenId && isPublicDomain(givenHost || domain)) {
			host = isPublicDomain(givenHost) ? givenHost : domain;
			merchantId = givenId;
		} else {
			const remembered = merchantMemory.get(domain);
			if (remembered) {
				host = remembered.host;
				merchantId = remembered.merchantId;
			}
		}
		if (!merchantId) try {
			const streamed = await readMerchant(domain);
			if (streamed) {
				host = streamed.host;
				merchantId = streamed.merchantId;
			} else {
				const inspected = await inspectCasino(domain);
				if (!inspected.ok) return {
					domain,
					ok: false,
					bonuses: [],
					message: inspected.message
				};
				host = inspected.host;
				merchantId = inspected.merchantId;
			}
		} catch {
			return {
				domain,
				ok: false,
				bonuses: [],
				message: "Could not reach site"
			};
		}
		if (!merchantId) return {
			domain,
			ok: false,
			bonuses: [],
			message: "This site did not publish a bonus list"
		};
		merchantMemory.set(domain, {
			host,
			merchantId
		});
		try {
			const pub = await postApi(host, {
				module: "/merchants/getPublicInfo",
				merchantId,
				accessId: "",
				accessToken: "",
				walletIsAdmin: ""
			}, AbortSignal.timeout(1e4));
			if (pub.status !== "SUCCESS") return {
				domain,
				ok: false,
				bonuses: [],
				message: "Bonus list did not load",
				host,
				merchantId
			};
			const body = pub.data && typeof pub.data === "object" ? pub.data : null;
			return {
				domain,
				ok: true,
				bonuses: parsePublicPromotions(body?.promotions),
				host,
				merchantId
			};
		} catch {
			return {
				domain,
				ok: false,
				bonuses: [],
				message: "Could not reach the bonus list",
				host,
				merchantId
			};
		}
	}));
}
async function fetchCasinoAccount(input) {
	const domain = normalizeDomain(input.domain);
	if (!isPublicDomain(domain)) return accountError(domain || "domain", "other", "Enter a real site domain");
	const phone = input.phone.replace(/\s+/g, "");
	const password = input.password;
	if (!/^[0-9+]{6,20}$/.test(phone)) return accountError(domain, "other", "Enter the mobile number you use on these sites");
	if (password.length < 4 || password.length > 64) return accountError(domain, "other", "Password should be 4–64 characters");
	const target = {
		domain,
		ok: true,
		host: domain,
		merchantId: ""
	};
	try {
		const inspected = await inspectCasino(domain);
		if (inspected.ok && inspected.merchantId) {
			target.host = inspected.host;
			target.merchantId = inspected.merchantId;
		}
	} catch {}
	const signal = AbortSignal.timeout(18e3);
	try {
		const usedMerchant = target.merchantId;
		const usedMode = target.merchantId ? "self" : "plain";
		const login = await postApi(target.host, {
			module: "/users/login",
			merchantId: usedMerchant,
			accessId: "",
			accessToken: "",
			walletIsAdmin: "",
			mobile: phone,
			password
		}, signal, usedMode);
		if (login.status !== "SUCCESS") return classifyFailure(domain, messageOf(login.data), "Could not sign in");
		const session = login.data;
		const accessId = session && typeof session.id === "string" ? session.id : "";
		const accessToken = session && typeof session.token === "string" ? session.token : "";
		if (!accessId || !accessToken) return accountError(domain, "other", "Sign-in did not return a session");
		const synced = await postApi(target.host, {
			module: "/users/syncData",
			merchantId: usedMerchant,
			accessId,
			accessToken,
			walletIsAdmin: "",
			background: "1"
		}, signal, usedMode);
		if (synced.status !== "SUCCESS") return classifyFailure(domain, messageOf(synced.data), "Signed in, but balances did not load");
		const syncedRow = synced.data && typeof synced.data === "object" ? synced.data : null;
		let promotions;
		if (!syncedRow || !Array.isArray(syncedRow.bonus)) try {
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
		return parseSyncUser(domain, synced.data, promotions);
	} catch (error) {
		return classifyFailure(domain, error instanceof Error ? error.message : "", "Could not reach site");
	}
}
async function fetchCasinoBatch(input) {
	const { domains, phone, password } = input;
	const out = new Array(domains.length);
	let cursor = 0;
	await Promise.all(Array.from({ length: Math.min(2, domains.length) }, async () => {
		for (;;) {
			const i = cursor++;
			if (i >= domains.length) return;
			out[i] = await fetchCasinoAccount({
				domain: domains[i] ?? "",
				phone,
				password
			});
		}
	}));
	return out;
}
async function fetchAllStatuses() {
	const body = await (await fetch("https://intel.bestpokieslist.online/public/statuses", {
		headers: { accept: "application/json" },
		signal: AbortSignal.timeout(12e3)
	})).json();
	const list = Array.isArray(body) ? body : body && typeof body === "object" && Array.isArray(body.domains) ? body.domains : body && typeof body === "object" && Array.isArray(body.statuses) ? body.statuses : [];
	const map = {};
	for (const item of list) {
		if (!item || typeof item !== "object") continue;
		const domain = normalizeDomain(String(item.domain ?? ""));
		if (!isPublicDomain(domain)) continue;
		const raw = String(item.status ?? "").toLowerCase();
		map[domain] = raw === "live" || raw === "up" || raw === "ok" ? "live" : raw === "slow" ? "slow" : raw === "redirect" ? "redirect" : raw === "unknown" ? "unknown" : "dead";
	}
	return map;
}
async function checkSiteStatus(rawDomain) {
	const domain = normalizeDomain(rawDomain);
	if (!isPublicDomain(domain)) return {
		domain,
		status: "dead",
		ms: 0
	};
	const started = Date.now();
	try {
		const response = await fetch(`https://${domain}/`, {
			method: "GET",
			redirect: "manual",
			signal: AbortSignal.timeout(7e3),
			headers: {
				accept: "text/html",
				"user-agent": UA
			}
		});
		await response.body?.cancel();
		const ms = Date.now() - started;
		if (response.status >= 300 && response.status < 400) return {
			domain,
			status: "redirect",
			ms
		};
		if (response.status >= 200 && response.status < 400) return {
			domain,
			status: ms > 2800 ? "slow" : "live",
			ms
		};
		return {
			domain,
			status: "dead",
			ms
		};
	} catch {
		return {
			domain,
			status: "dead",
			ms: Date.now() - started
		};
	}
}
export { checkSiteStatus, fetchAllStatuses, fetchCasinoAccount, fetchCasinoBatch, loadPublicCatalogue, lookupEgress, lookupMerchants, lookupSiteIcon, resolveCasinoTargets };
