import { o as __toESM } from "../_runtime.mjs";
import { i as fetchAllStatusesFn, l as useServerFn, n as checkSiteStatus, t as AppFrame } from "./shell-DWuSpmKc.mjs";
import { d as visibleSites, f as INTEL_KEY, p as domainsFromText, r as useVault } from "./router-yRR4M0i7.mjs";
import { n as normalizeDomain, t as isPublicDomain } from "./domain-DVT1Is7p.mjs";
import { Gt as require_react, b as Link } from "../_libs/@tanstack/react-router+[...].mjs";
import { S as ChevronLeft, h as LoaderCircle } from "../_libs/lucide-react.mjs";
import { t as require_jsx_dev_runtime } from "../_libs/react.mjs";
// node_modules/.nitro/vite/services/ssr/assets/intel-D6IfG-FC.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_dev_runtime = require_jsx_dev_runtime();
var BUILT_IN_DOMAINS = /* @__PURE__ */ new Set();
var _jsxFileName$1 = "/app/applet/src/components/domain-intel.tsx";
var KEY = INTEL_KEY;
var DIRECT_LIMIT = 40;
var LABEL = {
	checking: "Checking",
	live: "Up",
	slow: "Slow",
	redirect: "Redirect",
	dead: "Down",
	unknown: "Unknown"
};
function readSaved() {
	try {
		const raw = localStorage.getItem(KEY);
		if (!raw) return {
			domains: [],
			checks: {}
		};
		const parsed = JSON.parse(raw);
		const domains = Array.isArray(parsed.domains) ? parsed.domains.map((item) => normalizeDomain(String(item))).filter(isPublicDomain) : [];
		const checks = {};
		if (parsed.checks && typeof parsed.checks === "object") for (const [domain, value] of Object.entries(parsed.checks)) {
			if (!value || value.status === "checking") continue;
			checks[domain] = value;
		}
		return {
			domains: Array.from(new Set(domains)),
			checks
		};
	} catch {
		return {
			domains: [],
			checks: {}
		};
	}
}
function DomainIntel() {
	const { ready: vaultReady, state, addManaged, removeManaged } = useVault();
	const statusFn = useServerFn(checkSiteStatus);
	const allStatusesFn = useServerFn(fetchAllStatusesFn);
	const [ready, setReady] = (0, import_react.useState)(false);
	const [visibleCount, setVisibleCount] = (0, import_react.useState)(80);
	const [checks, setChecks] = (0, import_react.useState)({});
	const [selected, setSelected] = (0, import_react.useState)(/* @__PURE__ */ new Set());
	const [entry, setEntry] = (0, import_react.useState)("");
	const [filter, setFilter] = (0, import_react.useState)("");
	const [statusFilter, setStatusFilter] = (0, import_react.useState)(null);
	const [note, setNote] = (0, import_react.useState)("");
	const [error, setError] = (0, import_react.useState)("");
	const [running, setRunning] = (0, import_react.useState)(false);
	const [progress, setProgress] = (0, import_react.useState)({
		done: 0,
		total: 0
	});
	const stopRef = (0, import_react.useRef)(null);
	const fileRef = (0, import_react.useRef)(null);
	const allRef = (0, import_react.useRef)(null);
	const domains = (0, import_react.useMemo)(() => visibleSites(state).map((site) => site.domain), [state]);
	const shown = (0, import_react.useMemo)(() => {
		const q = filter.trim().toLowerCase();
		return domains.filter((domain) => {
			if (q && !domain.includes(q)) return false;
			if (!statusFilter) return true;
			return checks[domain]?.status === statusFilter;
		});
	}, [
		domains,
		filter,
		checks,
		statusFilter
	]);
	const migrated = (0, import_react.useRef)(false);
	(0, import_react.useEffect)(() => {
		if (!vaultReady || migrated.current) return;
		migrated.current = true;
		const saved = readSaved();
		setChecks(saved.checks);
		if (saved.domains.length) {
			addManaged(saved.domains.filter((domain) => !BUILT_IN_DOMAINS.has(domain)));
			try {
				localStorage.setItem(KEY, JSON.stringify({ checks: saved.checks }));
			} catch {}
		}
		setReady(true);
	}, [vaultReady, addManaged]);
	(0, import_react.useEffect)(() => {
		if (!ready) return;
		const id = window.setTimeout(() => {
			try {
				localStorage.setItem(KEY, JSON.stringify({ checks }));
			} catch {}
		}, 400);
		return () => window.clearTimeout(id);
	}, [ready, checks]);
	(0, import_react.useEffect)(() => {
		setVisibleCount(80);
	}, [filter, statusFilter]);
	const allSelected = shown.length > 0 && shown.every((domain) => selected.has(domain));
	const someSelected = shown.some((domain) => selected.has(domain)) && !allSelected;
	(0, import_react.useEffect)(() => {
		if (allRef.current) allRef.current.indeterminate = someSelected;
	}, [someSelected]);
	const counts = (0, import_react.useMemo)(() => {
		let up = 0;
		let slow = 0;
		let redirect = 0;
		let down = 0;
		for (const domain of domains) {
			const status = checks[domain]?.status;
			if (status === "live") up += 1;
			else if (status === "slow") slow += 1;
			else if (status === "redirect") redirect += 1;
			else if (status === "dead") down += 1;
		}
		return {
			up,
			slow,
			redirect,
			down
		};
	}, [domains, checks]);
	function submitOne() {
		const domain = normalizeDomain(entry);
		if (!isPublicDomain(domain)) {
			setError("Enter a domain like yeah96.com");
			setNote("");
			return;
		}
		const result = addManaged([domain]);
		if (result.error) {
			setError(result.error);
			setNote("");
			return;
		}
		if (!result.added) {
			setError("That domain is already in the list.");
			setNote("");
			return;
		}
		setEntry("");
		setError("");
		setNote(`Added ${domain}. It is on the main list and in the fetcher.`);
	}
	function onFile(file) {
		if (!file) return;
		const reader = new FileReader();
		reader.onload = () => {
			const text = typeof reader.result === "string" ? reader.result : "";
			const found = domainsFromText(text);
			if (!found.length) {
				setError("No domains found. Use one domain per line.");
				setNote("");
				return;
			}
			const result = addManaged(found);
			if (result.error) {
				setError(result.error);
				setNote("");
				return;
			}
			setError("");
			setNote(`Imported ${result.added} new domain${result.added === 1 ? "" : "s"} from ${found.length} in the file.`);
		};
		reader.readAsText(file);
		if (fileRef.current) fileRef.current.value = "";
	}
	function toggle(domain) {
		setSelected((current) => {
			const next = new Set(current);
			if (next.has(domain)) next.delete(domain);
			else next.add(domain);
			return next;
		});
	}
	function toggleShown() {
		setSelected((current) => {
			const next = new Set(current);
			if (allSelected) for (const domain of shown) next.delete(domain);
			else for (const domain of shown) next.add(domain);
			return next;
		});
	}
	function removeSelected() {
		if (!selected.size) return;
		const drop = [...selected];
		const message = removeManaged(drop);
		if (message) {
			setError(message);
			setNote("");
			return;
		}
		setChecks((current) => {
			const next = { ...current };
			for (const domain of drop) delete next[domain];
			return next;
		});
		setNote(`Removed ${drop.length}. Gone from the main list and the fetcher too.`);
		setError("");
		setSelected(/* @__PURE__ */ new Set());
	}
	async function runCheck() {
		const targets = selected.size ? domains.filter((domain) => selected.has(domain)) : [...domains];
		if (!targets.length) {
			setError("Add a domain before checking.");
			return;
		}
		stopRef.current?.abort();
		const controller = new AbortController();
		stopRef.current = controller;
		setRunning(true);
		setError("");
		if (targets.length > DIRECT_LIMIT) {
			setNote(`Checking ${targets.length} domains from the live status list…`);
			setProgress({
				done: 0,
				total: targets.length
			});
			try {
				const map = await allStatusesFn({ data: {} });
				if (controller.signal.aborted) return;
				const now = Date.now();
				setChecks((current) => {
					const next = { ...current };
					for (const domain of targets) next[domain] = {
						status: map[domain] ?? "unknown",
						ms: 0,
						at: now
					};
					return next;
				});
				setProgress({
					done: targets.length,
					total: targets.length
				});
				setNote(`Checked ${targets.length} domains.`);
			} catch {
				if (!controller.signal.aborted) setError("The live status list did not load.");
			}
			setRunning(false);
			return;
		}
		setNote(selected.size ? `Checking ${targets.length} selected…` : `Checking ${targets.length} domains…`);
		setProgress({
			done: 0,
			total: targets.length
		});
		let cursor = 0;
		let done = 0;
		const worker = async () => {
			for (;;) {
				if (controller.signal.aborted) return;
				const index = cursor;
				cursor += 1;
				if (index >= targets.length) return;
				const domain = targets[index] ?? "";
				setChecks((current) => ({
					...current,
					[domain]: {
						status: "checking",
						ms: 0,
						at: Date.now()
					}
				}));
				try {
					const result = await statusFn({
						data: { domain },
						signal: controller.signal
					});
					if (controller.signal.aborted) return;
					setChecks((current) => ({
						...current,
						[domain]: {
							status: result.status,
							ms: result.ms,
							at: Date.now()
						}
					}));
				} catch {
					if (controller.signal.aborted) return;
					setChecks((current) => ({
						...current,
						[domain]: {
							status: "dead",
							ms: 0,
							at: Date.now()
						}
					}));
				}
				done += 1;
				setProgress({
					done,
					total: targets.length
				});
			}
		};
		await Promise.all(Array.from({ length: Math.min(4, targets.length) }, () => worker()));
		const stopped = controller.signal.aborted;
		setRunning(false);
		if (!stopped) setNote(`Checked ${targets.length} domain${targets.length === 1 ? "" : "s"}.`);
		else setNote("Stopped. Finished checks stay in the list.");
	}
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
		className: "stack",
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
					lineNumber: 295,
					columnNumber: 9
				}, this), " Back to the list"]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 294,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h1", {
				className: "brand grad-text",
				style: { fontSize: "1.7rem" },
				children: "Domain Intel"
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 298,
				columnNumber: 9
			}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
				className: "hint",
				children: "This is the same list as the main page and the fetcher. After you add or remove a domain, that exact list is what comes back when you reopen the app."
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 301,
				columnNumber: 9
			}, this)] }, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 297,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "intel-counts",
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: statusFilter === null ? "on" : "",
						onClick: () => setStatusFilter(null),
						children: [domains.length, " total"]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 307,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: `up${statusFilter === "live" ? " on" : ""}`,
						onClick: () => setStatusFilter((value) => value === "live" ? null : "live"),
						children: [counts.up, " up"]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 314,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: `slow${statusFilter === "slow" ? " on" : ""}`,
						onClick: () => setStatusFilter((value) => value === "slow" ? null : "slow"),
						children: [counts.slow, " slow"]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 321,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: `redirect${statusFilter === "redirect" ? " on" : ""}`,
						onClick: () => setStatusFilter((value) => value === "redirect" ? null : "redirect"),
						children: [counts.redirect, " redirect"]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 328,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: `down${statusFilter === "dead" ? " on" : ""}`,
						onClick: () => setStatusFilter((value) => value === "dead" ? null : "dead"),
						children: [counts.down, " down"]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 335,
						columnNumber: 9
					}, this)
				]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 306,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "panel stack",
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "check-row",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
							className: "text-input",
							value: entry,
							placeholder: "yeah96.com or https://yeah96.com",
							autoCapitalize: "none",
							autoCorrect: "off",
							spellCheck: false,
							onChange: (event) => setEntry(event.target.value),
							onKeyDown: (event) => {
								if (event.key === "Enter") submitOne();
							}
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 346,
							columnNumber: 11
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "btn btn-grad",
							onClick: submitOne,
							children: "Add"
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 358,
							columnNumber: 11
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 345,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
						className: "text-input",
						value: filter,
						placeholder: "Find a domain",
						autoCapitalize: "none",
						autoCorrect: "off",
						spellCheck: false,
						onChange: (event) => setFilter(event.target.value)
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 362,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "intel-tools",
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn",
								onClick: () => fileRef.current?.click(),
								children: "Import text file"
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 372,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
								ref: fileRef,
								type: "file",
								accept: ".txt,text/plain",
								hidden: true,
								onChange: (event) => onFile(event.target.files?.[0])
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 375,
								columnNumber: 11
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn btn-grad",
								disabled: running || !domains.length,
								onClick: () => void runCheck(),
								children: [running ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(LoaderCircle, {
									size: 16,
									className: "spin"
								}, void 0, false, {
									fileName: _jsxFileName$1,
									lineNumber: 383,
									columnNumber: 24
								}, this) : null, running ? `${progress.done}/${progress.total}` : selected.size ? `Check ${selected.size}` : "Check status"]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 382,
								columnNumber: 11
							}, this),
							running ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn",
								onClick: () => stopRef.current?.abort(),
								children: "Stop"
							}, void 0, false, {
								fileName: _jsxFileName$1,
								lineNumber: 387,
								columnNumber: 13
							}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn",
								disabled: !selected.size,
								onClick: removeSelected,
								children: ["Remove", selected.size ? ` ${selected.size}` : ""]
							}, void 0, true, {
								fileName: _jsxFileName$1,
								lineNumber: 391,
								columnNumber: 13
							}, this)
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 371,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "hint",
						children: "One domain per line in a text file. Check status tests a selection directly. Checking the whole list uses the live status feed."
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 396,
						columnNumber: 9
					}, this),
					error ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "error-line",
						children: error
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 400,
						columnNumber: 18
					}, this) : null,
					note ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "hint",
						children: note
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 401,
						columnNumber: 17
					}, this) : null
				]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 344,
				columnNumber: 7
			}, this),
			shown.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "stack",
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
						className: "intel-select",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
							ref: allRef,
							type: "checkbox",
							checked: allSelected,
							onChange: toggleShown
						}, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 407,
							columnNumber: 13
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: filter.trim() ? `Select ${shown.length} shown` : "Select all" }, void 0, false, {
							fileName: _jsxFileName$1,
							lineNumber: 408,
							columnNumber: 13
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 406,
						columnNumber: 11
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "intel-list",
						children: shown.slice(0, visibleCount).map((domain) => {
							const check = checks[domain];
							const status = check?.status ?? "unknown";
							return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
								className: "intel-row",
								children: [
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
										type: "checkbox",
										checked: selected.has(domain),
										onChange: () => toggle(domain)
									}, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 416,
										columnNumber: 19
									}, this),
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: domain }, void 0, false, {
										fileName: _jsxFileName$1,
										lineNumber: 417,
										columnNumber: 19
									}, this),
									/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
										className: `site-meta ${status}`,
										children: [
											/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", {}, void 0, false, {
												fileName: _jsxFileName$1,
												lineNumber: 419,
												columnNumber: 21
											}, this),
											check ? LABEL[status] : "Not checked",
											check && check.status !== "checking" && check.ms > 0 ? ` · ${check.ms} ms` : ""
										]
									}, void 0, true, {
										fileName: _jsxFileName$1,
										lineNumber: 418,
										columnNumber: 19
									}, this)
								]
							}, domain, true, {
								fileName: _jsxFileName$1,
								lineNumber: 415,
								columnNumber: 17
							}, this);
						})
					}, void 0, false, {
						fileName: _jsxFileName$1,
						lineNumber: 410,
						columnNumber: 11
					}, this),
					visibleCount < shown.length ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: "btn",
						onClick: () => setVisibleCount((value) => value + 80),
						children: [
							"Show ",
							Math.min(80, shown.length - visibleCount),
							" more"
						]
					}, void 0, true, {
						fileName: _jsxFileName$1,
						lineNumber: 428,
						columnNumber: 13
					}, this) : null
				]
			}, void 0, true, {
				fileName: _jsxFileName$1,
				lineNumber: 405,
				columnNumber: 9
			}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "panel",
				children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
					className: "hint",
					style: { margin: 0 },
					children: domains.length ? statusFilter ? `No ${LABEL[statusFilter].toLowerCase()} domains.` : "No domains match that search." : "No domains yet. Add one above, or import a .txt file."
				}, void 0, false, {
					fileName: _jsxFileName$1,
					lineNumber: 435,
					columnNumber: 11
				}, this)
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 434,
				columnNumber: 9
			}, this)
		]
	}, void 0, true, {
		fileName: _jsxFileName$1,
		lineNumber: 293,
		columnNumber: 5
	}, this);
}
var _jsxFileName = "/app/applet/src/routes/intel.tsx?tsr-split=component";
function IntelPage() {
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(AppFrame, { children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(DomainIntel, {}, void 0, false, {
		fileName: _jsxFileName,
		lineNumber: 5,
		columnNumber: 7
	}, this) }, void 0, false, {
		fileName: _jsxFileName,
		lineNumber: 4,
		columnNumber: 10
	}, this);
}
export { IntelPage as component };
