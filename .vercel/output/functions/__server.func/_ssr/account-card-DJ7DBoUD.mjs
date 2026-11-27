import { o as __toESM } from "../_runtime.mjs";
import { a as timeAgo, i as moneyShort, t as SiteIcon } from "./format-Dbq6jmzA.mjs";
import { a as bonusTurnover, i as bonusHasRealAmount, s as formatTurnover } from "./router-yRR4M0i7.mjs";
import { Gt as require_react } from "../_libs/@tanstack/react-router+[...].mjs";
import { C as ChevronDown, s as Star, w as Bookmark, x as ChevronUp } from "../_libs/lucide-react.mjs";
import { t as require_jsx_dev_runtime } from "../_libs/react.mjs";
// node_modules/.nitro/vite/services/ssr/assets/account-card-DJ7DBoUD.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_dev_runtime = require_jsx_dev_runtime();
var _jsxFileName = "/app/applet/src/components/account-card.tsx";
var STATUS_LABEL = {
	checking: "Checking",
	live: "Live",
	slow: "Slow",
	redirect: "Redirect",
	dead: "Dead",
	unknown: "Unknown"
};
var WALLET_COLUMNS = [
	{
		key: "commission",
		mode: "commission",
		label: "COM",
		bet: true
	},
	{
		key: "share",
		mode: "share",
		label: "SHARE",
		bet: false
	},
	{
		key: "referral",
		mode: "referral",
		label: "REF",
		bet: false
	},
	{
		key: "new",
		mode: "new",
		label: "NEW",
		bet: false
	},
	{
		key: "rebate",
		mode: "rebate",
		label: "REBATE",
		bet: false
	}
];
function walletOf(account) {
	if (!account) return 0;
	const held = account.wallets.filter((wallet) => wallet.balance > .001);
	if (held.length) return held.reduce((sum, wallet) => sum + wallet.balance, 0);
	return account.cash > .001 ? account.cash : 0;
}
function withdrawalValueText(value) {
	return value != null && value > 0 ? moneyShort(value) : "—";
}
function selectedBonusFromAccount(account, mode) {
	if (!account || mode === "none") return null;
	return account[mode] ?? null;
}
function selectedBonusMeta(mode) {
	if (mode === "new") return {
		emoji: "🎁",
		label: "Signup"
	};
	if (mode === "share") return {
		emoji: "🤝",
		label: "Share"
	};
	if (mode === "commission") return {
		emoji: "💸",
		label: "Commission"
	};
	if (mode === "referral") return {
		emoji: "👥",
		label: "Referral"
	};
	return {
		emoji: "♻️",
		label: "Rebate"
	};
}
function AccountCard({ domain, rank, addedAt, status, account, errorRow, walletMode = true, bonusMode = "none", sortMode = "default", variant = "default", visited = false, showVisited = false, saved, onToggleSave, onVisit }) {
	const [open, setOpen] = (0, import_react.useState)(false);
	const [age, setAge] = (0, import_react.useState)("");
	const [iconSrc, setIconSrc] = (0, import_react.useState)("");
	(0, import_react.useEffect)(() => {
		setAge(timeAgo(addedAt));
	}, [addedAt]);
	const sortedBonuses = (0, import_react.useMemo)(() => {
		const list = account?.availableBonuses ?? [];
		if (!list.length) return [];
		return [...list].sort((a, b) => {
			const litDelta = Number(b.lit) - Number(a.lit);
			if (litDelta) return litDelta;
			const turnA = bonusTurnover(a) ?? Number.POSITIVE_INFINITY;
			const turnB = bonusTurnover(b) ?? Number.POSITIVE_INFINITY;
			if (turnA !== turnB) return turnA - turnB;
			return b.amount - a.amount || a.name.localeCompare(b.name);
		});
	}, [account]);
	const favStyle = (0, import_react.useMemo)(() => iconSrc ? { "--fav": `url("${iconSrc.replace(/"/g, "%22")}")` } : void 0, [iconSrc]);
	const cash = walletOf(account);
	const hasCash = cash > .001;
	const chosenBonus = selectedBonusFromAccount(account, bonusMode);
	const chosenActive = bonusMode !== "none" && bonusHasRealAmount(chosenBonus);
	const variantClass = variant === "new" ? " new-card" : variant === "best" ? " best-card" : variant === "unverified" ? " unverified-card" : variant === "share" ? " share-card" : "";
	const visitClass = showVisited ? visited ? " visited" : " unvisited" : "";
	if (!walletMode) return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("article", {
		className: `bpl-card bpl-domain-card${variantClass}${visitClass}`,
		children: [
			iconSrc ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
				className: "card-fav-bg",
				style: favStyle,
				"aria-hidden": "true"
			}, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 137,
				columnNumber: 20
			}, this) : null,
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(SiteIcon, {
				domain,
				className: "bpl-icon",
				onSrcChange: setIconSrc
			}, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 138,
				columnNumber: 9
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "bpl-domain-body",
				children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "bpl-title-row",
						children: [
							rank != null ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "bpl-rank",
								"aria-hidden": "true",
								children: [rank, "."]
							}, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 142,
								columnNumber: 15
							}, this) : null,
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("a", {
								className: "bpl-domain-link",
								href: `https://${domain}/`,
								target: "_blank",
								rel: "noreferrer",
								onClick: onVisit,
								children: domain
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 146,
								columnNumber: 13
							}, this),
							account?.unclaimed ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "acct-unclaimed",
								children: "Unclaimed"
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 155,
								columnNumber: 35
							}, this) : null,
							onToggleSave ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: saved ? "acct-save on" : "acct-save",
								"aria-label": saved ? `Remove ${domain} from my accounts` : `Save ${domain}`,
								onClick: onToggleSave,
								children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Bookmark, {
									size: 14,
									fill: saved ? "currentColor" : "none"
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 163,
									columnNumber: 17
								}, this)
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 157,
								columnNumber: 15
							}, this) : null,
							showVisited ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "bpl-visit-indicator",
								title: visited ? "Visited" : "Not visited",
								"aria-label": visited ? "Visited" : "Not visited",
								children: visited ? "✓" : "●"
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 167,
								columnNumber: 15
							}, this) : null
						]
					}, void 0, true, {
						fileName: _jsxFileName,
						lineNumber: 140,
						columnNumber: 11
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "bpl-meta-row",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
							className: "bpl-added",
							children: age || (account ? "Fetched" : "—")
						}, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 178,
							columnNumber: 13
						}, this), status ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
							className: `bpl-status ${status}`,
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", {}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 181,
								columnNumber: 17
							}, this), STATUS_LABEL[status]]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 180,
							columnNumber: 15
						}, this) : null]
					}, void 0, true, {
						fileName: _jsxFileName,
						lineNumber: 177,
						columnNumber: 11
					}, this),
					sortMode === "errors" && errorRow ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "bpl-error-pill",
						children: ["⚠ ", errorRow.message || "Fetch error"]
					}, void 0, true, {
						fileName: _jsxFileName,
						lineNumber: 188,
						columnNumber: 13
					}, this) : null,
					chosenActive && chosenBonus ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: `signup-bonus-line bonus-type-${bonusMode}`,
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "signup-amount",
								children: [
									selectedBonusMeta(bonusMode).emoji,
									" ",
									selectedBonusMeta(bonusMode).label,
									" ",
									chosenBonus.amountDisplay || moneyShort(chosenBonus.amount)
								]
							}, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 193,
								columnNumber: 15
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "signup-withdraw",
								children: bonusMode === "commission" ? `Min bet ${withdrawalValueText(chosenBonus.rollover)} · Max ${chosenBonus.maxDisplay ?? "—"}` : `Min ${withdrawalValueText(chosenBonus.minWithdrawal)} · Max ${chosenBonus.maxDisplay ?? "—"}`
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 197,
								columnNumber: 15
							}, this),
							bonusMode === "new" && bonusTurnover(chosenBonus) != null ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "signup-turnover",
								children: ["Turnover ", formatTurnover(bonusTurnover(chosenBonus))]
							}, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 203,
								columnNumber: 17
							}, this) : null
						]
					}, void 0, true, {
						fileName: _jsxFileName,
						lineNumber: 192,
						columnNumber: 13
					}, this) : null
				]
			}, void 0, true, {
				fileName: _jsxFileName,
				lineNumber: 139,
				columnNumber: 9
			}, this)
		]
	}, void 0, true, {
		fileName: _jsxFileName,
		lineNumber: 136,
		columnNumber: 7
	}, this);
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("article", {
		className: `bpl-card wallet-card${errorRow && !account ? " wallet-error-card" : ""}${variantClass}${visitClass}${open ? " is-expanded" : ""}`,
		children: [
			iconSrc ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
				className: "card-fav-bg",
				style: favStyle,
				"aria-hidden": "true"
			}, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 218,
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
						fileName: _jsxFileName,
						lineNumber: 220,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "wc-id",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "bpl-title-row",
							children: [
								rank != null ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "bpl-rank",
									"aria-hidden": "true",
									children: [rank, "."]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 224,
									columnNumber: 15
								}, this) : null,
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("a", {
									className: "bpl-domain-link",
									href: `https://${domain}/`,
									target: "_blank",
									rel: "noreferrer",
									onClick: onVisit,
									children: domain
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 228,
									columnNumber: 13
								}, this),
								account?.unclaimed ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "acct-unclaimed",
									children: "Unclaimed"
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 237,
									columnNumber: 35
								}, this) : null,
								onToggleSave ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
									type: "button",
									className: saved ? "acct-save on" : "acct-save",
									"aria-label": saved ? `Remove ${domain} from my accounts` : `Save ${domain}`,
									onClick: onToggleSave,
									children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Bookmark, {
										size: 14,
										fill: saved ? "currentColor" : "none"
									}, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 245,
										columnNumber: 17
									}, this)
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 239,
									columnNumber: 15
								}, this) : null
							]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 222,
							columnNumber: 11
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "wc-facts",
							children: [
								status ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: `bpl-status ${status}`,
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", {}, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 252,
										columnNumber: 17
									}, this), STATUS_LABEL[status]]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 251,
									columnNumber: 15
								}, this) : null,
								age ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "bpl-added",
									children: age
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 256,
									columnNumber: 20
								}, this) : null,
								showVisited ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "bpl-visit-indicator",
									title: visited ? "Visited" : "Not visited",
									children: visited ? "✓" : "●"
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 258,
									columnNumber: 15
								}, this) : null
							]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 249,
							columnNumber: 11
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName,
						lineNumber: 221,
						columnNumber: 9
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "wc-right",
						children: [account ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: `wc-balance${hasCash ? " wc-has-cash" : ""}`,
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "wallet-balance",
								children: moneyShort(cash)
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 268,
								columnNumber: 15
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "wallet-withdraw",
								children: [
									"↓ ",
									withdrawalValueText(account.cashMinWithdrawal),
									" · ↑ ",
									withdrawalValueText(account.cashMaxWithdrawal)
								]
							}, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 269,
								columnNumber: 15
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 267,
							columnNumber: 13
						}, this) : errorRow ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
							className: "bpl-error-pill",
							title: errorRow.message,
							children: ["⚠ ", errorRow.message || "Fetch error"]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 274,
							columnNumber: 13
						}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
							className: "wallet-no-fetch",
							children: "Not in last fetch"
						}, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 278,
							columnNumber: 13
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "acct-toggle",
							"aria-expanded": open,
							"aria-label": open ? "Hide bonuses" : "Show bonuses",
							onClick: () => setOpen((value) => !value),
							children: open ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(ChevronUp, { size: 18 }, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 287,
								columnNumber: 21
							}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(ChevronDown, { size: 18 }, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 287,
								columnNumber: 47
							}, this)
						}, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 280,
							columnNumber: 11
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName,
						lineNumber: 265,
						columnNumber: 9
					}, this)
				]
			}, void 0, true, {
				fileName: _jsxFileName,
				lineNumber: 219,
				columnNumber: 7
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "wc-panel",
				children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "wc-grid",
					children: WALLET_COLUMNS.map((col) => {
						const bonus = account?.[col.key] ?? null;
						const hot = Boolean(bonus?.lit);
						const turnText = !col.bet ? formatTurnover(bonusTurnover(bonus)) : "";
						const selected = bonusMode === col.mode;
						return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: `wc-cell wc-${col.key}${hot ? " wc-yes" : ""}${selected ? " wc-selected" : ""}`,
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "wc-label",
								children: col.label
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 304,
								columnNumber: 17
							}, this), hot ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-amt",
									children: [bonus?.amountDisplay || "?", col.key === "new" && (bonus?.amount ?? 0) <= 0 ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("i", { className: "wc-dot" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 309,
										columnNumber: 73
									}, this) : null]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 307,
									columnNumber: 21
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-sub",
									children: col.bet ? `Bet ${withdrawalValueText(bonus?.rollover)}` : `↓ ${withdrawalValueText(bonus?.minWithdrawal)}`
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 311,
									columnNumber: 21
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-sub",
									children: ["↑ ", bonus?.maxDisplay ?? "—"]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 316,
									columnNumber: 21
								}, this),
								turnText ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-turn",
									children: turnText
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 317,
									columnNumber: 33
								}, this) : null
							] }, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 306,
								columnNumber: 19
							}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-none",
									children: "—"
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 321,
									columnNumber: 21
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-sub",
									children: col.bet ? "Bet —" : "↓ —"
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 322,
									columnNumber: 21
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
									className: "wc-sub",
									children: "↑ —"
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 323,
									columnNumber: 21
								}, this)
							] }, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 320,
								columnNumber: 19
							}, this)]
						}, col.key, true, {
							fileName: _jsxFileName,
							lineNumber: 300,
							columnNumber: 15
						}, this);
					})
				}, void 0, false, {
					fileName: _jsxFileName,
					lineNumber: 293,
					columnNumber: 9
				}, this), open ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "wc-list",
					children: sortedBonuses.length ? sortedBonuses.map((bonus) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: bonus.lit ? "wc-list-row" : "wc-list-row grey",
						"data-id": bonus.id,
						"data-transaction-id": bonus.transactionId,
						"data-angpao-id": bonus.angpaoId,
						"data-lucky-id": bonus.luckyId,
						"data-host": bonus.host,
						children: [
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "wc-list-name",
								children: [bonus.lit ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Star, {
									size: 12,
									className: "wc-star"
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 345,
									columnNumber: 34
								}, this) : null, bonus.name]
							}, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 344,
								columnNumber: 19
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "wc-list-amt",
								children: bonus.lit ? bonus.amountDisplay || "?" : "—"
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 348,
								columnNumber: 19
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
								className: "wc-list-meta",
								children: bonus.detail || `Min ${withdrawalValueText(bonus.minWithdrawal)} · Max ${bonus.maxDisplay ?? "—"}`
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 349,
								columnNumber: 19
							}, this)
						]
					}, `${bonus.host}-${bonus.id}-${bonus.transactionId}-${bonus.angpaoId}-${bonus.luckyId}`, true, {
						fileName: _jsxFileName,
						lineNumber: 335,
						columnNumber: 17
					}, this)) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "wc-list-row wc-list-empty",
						children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
							className: "wc-list-meta",
							children: errorRow ? `Fetch error: ${errorRow.message}` : "No claimable bonuses in your last fetch."
						}, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 357,
							columnNumber: 17
						}, this)
					}, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 356,
						columnNumber: 15
					}, this)
				}, void 0, false, {
					fileName: _jsxFileName,
					lineNumber: 332,
					columnNumber: 11
				}, this) : null]
			}, void 0, true, {
				fileName: _jsxFileName,
				lineNumber: 292,
				columnNumber: 7
			}, this)
		]
	}, void 0, true, {
		fileName: _jsxFileName,
		lineNumber: 213,
		columnNumber: 5
	}, this);
}
export { AccountCard as t };
