import { o as __toESM } from "../_runtime.mjs";
import { a as getServerFnById, i as TSS_SERVER_FUNCTION, r as createServerFn } from "./ssr.mjs";
import { i as signOut } from "./client-DZaBa7Dm.mjs";
import { a as hasGateSessionMarker } from "./server-x1MirbW5.mjs";
import { t as authMiddleware } from "./middleware-DWpfziPI.mjs";
import { r as useVault, u as THEMES } from "./router-yRR4M0i7.mjs";
import { n as useCurrentUserState, t as useCurrentUser } from "./use-current-user-C1Cm1fMI.mjs";
import { Gt as require_react, S as useRouter, Vt as isRedirect, b as Link, p as useRouterState, x as Navigate } from "../_libs/@tanstack/react-router+[...].mjs";
import { _ as Gift, f as Radar, g as House, i as UserRound, m as Menu, n as X, o as Trash2, p as Plus, t as Zap } from "../_libs/lucide-react.mjs";
import { t as require_jsx_dev_runtime } from "../_libs/react.mjs";
// node_modules/.nitro/vite/services/ssr/assets/shell-DWuSpmKc.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_dev_runtime = require_jsx_dev_runtime();
function useServerFn(serverFn) {
	const router = useRouter();
	return import_react.useCallback(async (...args) => {
		try {
			const res = await serverFn(...args);
			if (isRedirect(res)) throw res;
			return res;
		} catch (err) {
			if (isRedirect(err)) {
				err.options._fromLocation = router.stores.location.get();
				return router.navigate(router.resolveRedirect(err).options);
			}
			throw err;
		}
	}, [router, serverFn]);
}
var createSsrRpc = (functionId) => {
	const url = "/_serverFn/" + functionId;
	const serverFnMeta = { id: functionId };
	const fn = async (...args) => {
		return (await getServerFnById(functionId, { origin: "server" }))(...args);
	};
	return Object.assign(fn, {
		url,
		serverFnMeta,
		[TSS_SERVER_FUNCTION]: true
	});
};
var lookupSiteIconFn = createServerFn({ method: "POST" }).middleware([authMiddleware]).validator((data) => data).handler(createSsrRpc("d6dcb3ec797f6fb8fba45a87a42d4b110d1b41556623d96dd7ec64fcc2dbf4f7"));
var loadPublicCatalogueFn = createServerFn({ method: "POST" }).middleware([authMiddleware]).validator((data) => data).handler(createSsrRpc("8976c06a41e71ce80386f094b7ae1c94bf1e89802315a2bbba940c7383532984"));
createServerFn({ method: "POST" }).middleware([authMiddleware]).validator((data) => data).handler(createSsrRpc("31be5b99ae9ca7c2d3b8e9741a2c29430bf663b3f4938f9ae4aa643a22c14f45"));
var fetchCasinoBatchFn = createServerFn({ method: "POST" }).middleware([authMiddleware]).validator((data) => data).handler(createSsrRpc("461c07027b2a8c58a6fe2f0a00682e43be45789d096410e713e3e5b3f4767738"));
var lookupMerchantsFn = createServerFn({ method: "POST" }).middleware([authMiddleware]).validator((data) => data).handler(createSsrRpc("666782356d5383f418b7e241b73f5f992959ead3ef4d6113e41213063ae60709"));
createServerFn({ method: "POST" }).middleware([authMiddleware]).validator((data) => data).handler(createSsrRpc("ebe8291f863c16ed3e14ae9e7b736039ffc8d725902aaa7faa157aee4a434259"));
createServerFn({ method: "POST" }).middleware([authMiddleware]).validator((data) => data ?? {}).handler(createSsrRpc("d9ddaf3cd55cea64846bc1a1e505c6cac9b7643a2d5b411dac8b9b0034e31ace"));
var fetchAllStatusesFn = createServerFn({ method: "POST" }).middleware([authMiddleware]).validator((data) => data ?? {}).handler(createSsrRpc("bbaccc3e2239274bbee08332c5f8909ed44347b3bf93b9b721830ca8434ad3bf"));
var checkSiteStatus = createServerFn({ method: "POST" }).middleware([authMiddleware]).validator((data) => data).handler(createSsrRpc("74839dc983bbaacaaaed2f59d289f93c284acf8e34a8e5f8f2b0a8b1a890e640"));
var _jsxFileName$1 = "/app/applet/src/lib/auth/gates.tsx";
var subscribeToNothing = () => () => {};
var noGateSessionOnServer = () => false;
/**
* Auth state components — plain wrappers around `useCurrentUserState()`.
*
* With auth on, visitors are signed out until they authenticate — in the sandbox
* live preview too, which does real sign-in. The shared dev user appears only
* when auth is disabled (`VITE_AUTH_ENABLED=false`, the shipped default).
* While the session is still resolving, gates that care about signed-out state
* render nothing so there's no signed-out flash on hard reload.
*/
/** Where `RedirectToSignIn` sends signed-out visitors. Create this route. */
var SIGN_IN_PATH = "/login";
/**
* Client-side redirect to the sign-in route (TanStack `<Navigate>` — NOT a full
* `window.location` reload). A hard navigation re-bootstraps the SPA and re-runs
* session loading, which feels like a second "Loading…" on /login.
*
* Guard routes by waiting out `isPending` first (see `use-current-user`), then
* render this.
*/
function RedirectToSignIn({ to = SIGN_IN_PATH }) {
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Navigate, { to }, void 0, false, {
		fileName: _jsxFileName$1,
		lineNumber: 49,
		columnNumber: 10
	}, this);
}
/**
* Minimal signed-in identity chip + sign-out. Restyle freely (see the
* `design-ui` skill). Sign-out is only shown when auth is enabled (the
* disabled-auth dev user has nothing to sign out of) and the session is not
* gate-materialized — behind the gate the next request signs the viewer
* straight back in, so a sign-out control there is a broken loop.
*/
function UserButton() {
	const user = useCurrentUser();
	const [signingOut, setSigningOut] = (0, import_react.useState)(false);
	const gateSession = (0, import_react.useSyncExternalStore)(subscribeToNothing, hasGateSessionMarker, noGateSessionOnServer);
	if (!user) return null;
	const label = user.displayName ?? user.primaryEmail ?? "Account";
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
		className: "flex items-center gap-2",
		children: [
			user.profileImageUrl ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("img", {
				src: user.profileImageUrl,
				alt: "",
				className: "h-8 w-8 rounded-full object-cover"
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 105,
				columnNumber: 9
			}, this) : /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
				className: "grid h-8 w-8 place-items-center rounded-full bg-black/10 text-sm font-medium dark:bg-white/20",
				children: label.charAt(0).toUpperCase()
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 111,
				columnNumber: 9
			}, this),
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
				className: "text-sm font-medium",
				children: label
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 115,
				columnNumber: 7
			}, this),
			!gateSession && /* @__PURE__ */ (void 0)("button", {
				type: "button",
				disabled: signingOut,
				onClick: () => {
					setSigningOut(true);
					signOut().catch(() => setSigningOut(false));
				},
				className: "cursor-pointer text-sm underline-offset-4 opacity-70 hover:underline disabled:cursor-wait disabled:no-underline",
				children: signingOut ? "Signing out…" : "Sign out"
			}, void 0, false, {
				fileName: _jsxFileName$1,
				lineNumber: 117,
				columnNumber: 9
			}, this)
		]
	}, void 0, true, {
		fileName: _jsxFileName$1,
		lineNumber: 103,
		columnNumber: 5
	}, this);
}
var _jsxFileName = "/app/applet/src/components/shell.tsx";
function AppFrame({ children }) {
	const path = useRouterState({ select: (state) => state.location.pathname });
	const { state, addDomain, clearAll, patch } = useVault();
	const { user, isPending } = useCurrentUserState();
	const [menu, setMenu] = (0, import_react.useState)(false);
	const [adding, setAdding] = (0, import_react.useState)(false);
	const [domain, setDomain] = (0, import_react.useState)("");
	const [error, setError] = (0, import_react.useState)("");
	function submitDomain() {
		const message = addDomain(domain);
		if (message) {
			setError(message);
			return;
		}
		setDomain("");
		setError("");
		setAdding(false);
		setMenu(false);
	}
	if (isPending) return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("main", {
		className: "wrap",
		children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
			className: "hint",
			style: { padding: "28px 0" },
			children: "Opening Pokies Vault…"
		}, void 0, false, {
			fileName: _jsxFileName,
			lineNumber: 33,
			columnNumber: 9
		}, this)
	}, void 0, false, {
		fileName: _jsxFileName,
		lineNumber: 32,
		columnNumber: 7
	}, this);
	if (!user) return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(RedirectToSignIn, {}, void 0, false, {
		fileName: _jsxFileName,
		lineNumber: 39,
		columnNumber: 21
	}, this);
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("main", {
		className: "wrap",
		children: [
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("header", {
				className: "header-row",
				children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Link, {
					to: "/",
					className: "brand grad-text",
					children: "POKIES VAULT"
				}, void 0, false, {
					fileName: _jsxFileName,
					lineNumber: 44,
					columnNumber: 9
				}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "header-actions",
					children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Link, {
						to: "/fetch",
						className: "icon-action",
						"aria-label": "Open fetcher",
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Zap, { size: 16 }, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 49,
							columnNumber: 13
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "Fetcher" }, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 50,
							columnNumber: 13
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName,
						lineNumber: 48,
						columnNumber: 11
					}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: menu ? "icon-action menu-open" : "icon-action",
						"aria-expanded": menu,
						"aria-label": "Open menu",
						onClick: () => setMenu(true),
						children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Menu, { size: 16 }, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 59,
							columnNumber: 13
						}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "Menu" }, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 60,
							columnNumber: 13
						}, this)]
					}, void 0, true, {
						fileName: _jsxFileName,
						lineNumber: 52,
						columnNumber: 11
					}, this)]
				}, void 0, true, {
					fileName: _jsxFileName,
					lineNumber: 47,
					columnNumber: 9
				}, this)]
			}, void 0, true, {
				fileName: _jsxFileName,
				lineNumber: 43,
				columnNumber: 7
			}, this),
			children,
			/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("footer", {
				className: "foot",
				children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", { children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
						className: "age",
						children: "18+"
					}, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 67,
						columnNumber: 11
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: "Gamble responsibly." }, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 68,
						columnNumber: 11
					}, this),
					" Only use money you can afford to lose. Need help?",
					" ",
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("a", {
						href: "https://www.gamblinghelponline.org.au/",
						target: "_blank",
						rel: "noreferrer",
						children: "Gambling Help Online"
					}, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 70,
						columnNumber: 11
					}, this),
					" ",
					"— 1800 858 858 ·",
					" ",
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("a", {
						href: "https://www.betstop.gov.au/",
						target: "_blank",
						rel: "noreferrer",
						children: "BetStop"
					}, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 74,
						columnNumber: 11
					}, this)
				] }, void 0, true, {
					fileName: _jsxFileName,
					lineNumber: 66,
					columnNumber: 9
				}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
					style: { marginTop: 8 },
					children: ["Pokies Vault checks sites you already use. It does not take bets, sell fetches, or rent listings. Passwords stay in this browser.", path === "/fetch" ? "" : ` ${state.mine.length} saved account${state.mine.length === 1 ? "" : "s"}.`]
				}, void 0, true, {
					fileName: _jsxFileName,
					lineNumber: 78,
					columnNumber: 9
				}, this)]
			}, void 0, true, {
				fileName: _jsxFileName,
				lineNumber: 65,
				columnNumber: 7
			}, this),
			menu ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "drawer-backdrop",
				onClick: () => setMenu(false),
				children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("aside", {
					className: "drawer",
					onClick: (event) => event.stopPropagation(),
					children: [
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "header-row",
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h2", { children: "Menu" }, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 90,
								columnNumber: 17
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "menu-note",
								children: "Password stays on this device."
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 91,
								columnNumber: 17
							}, this)] }, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 89,
								columnNumber: 15
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "pin",
								"aria-label": "Close menu",
								onClick: () => setMenu(false),
								children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(X, { size: 18 }, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 94,
									columnNumber: 17
								}, this)
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 93,
								columnNumber: 15
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 88,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
							className: "menu-group",
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", {
								className: "menu-label",
								children: "Account"
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 99,
								columnNumber: 15
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
								className: "account-chip",
								children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(UserButton, {}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 101,
									columnNumber: 17
								}, this)
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 100,
								columnNumber: 15
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 98,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
							className: "menu-group",
							children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", {
									className: "menu-label",
									children: "Go"
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 106,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Link, {
									to: "/",
									className: path === "/" ? "drawer-item on" : "drawer-item",
									onClick: () => setMenu(false),
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(House, { size: 18 }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 108,
										columnNumber: 17
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: "Main list" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 110,
										columnNumber: 19
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("small", { children: "Domains and account cards" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 111,
										columnNumber: 19
									}, this)] }, void 0, true, {
										fileName: _jsxFileName,
										lineNumber: 109,
										columnNumber: 17
									}, this)]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 107,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Link, {
									to: "/fetch",
									className: path === "/fetch" ? "drawer-item on" : "drawer-item",
									onClick: () => setMenu(false),
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Zap, { size: 18 }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 115,
										columnNumber: 17
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: "Fetcher" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 117,
										columnNumber: 19
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("small", { children: "Balances, share, referral, rebate" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 118,
										columnNumber: 19
									}, this)] }, void 0, true, {
										fileName: _jsxFileName,
										lineNumber: 116,
										columnNumber: 17
									}, this)]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 114,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Link, {
									to: "/intel",
									className: path === "/intel" ? "drawer-item on" : "drawer-item",
									onClick: () => setMenu(false),
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Radar, { size: 18 }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 122,
										columnNumber: 17
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: "Domain Intel" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 124,
										columnNumber: 19
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("small", { children: "Add, remove, and check domains" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 125,
										columnNumber: 19
									}, this)] }, void 0, true, {
										fileName: _jsxFileName,
										lineNumber: 123,
										columnNumber: 17
									}, this)]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 121,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Link, {
									to: "/bonuses",
									className: path === "/bonuses" ? "drawer-item on" : "drawer-item",
									onClick: () => setMenu(false),
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Gift, { size: 18 }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 129,
										columnNumber: 17
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: "Bonus Finder" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 131,
										columnNumber: 19
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("small", { children: "Signup, share, daily, birthday" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 132,
										columnNumber: 19
									}, this)] }, void 0, true, {
										fileName: _jsxFileName,
										lineNumber: 130,
										columnNumber: 17
									}, this)]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 128,
									columnNumber: 15
								}, this)
							]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 105,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
							className: "menu-group",
							children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", {
									className: "menu-label",
									children: "List"
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 138,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
									type: "button",
									className: "drawer-item",
									onClick: () => {
										patch({ mineOnly: true });
										setMenu(false);
									},
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(UserRound, { size: 18 }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 147,
										columnNumber: 17
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: "My accounts" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 149,
										columnNumber: 19
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("small", { children: [
										state.mine.length,
										" site",
										state.mine.length === 1 ? "" : "s",
										" saved"
									] }, void 0, true, {
										fileName: _jsxFileName,
										lineNumber: 150,
										columnNumber: 19
									}, this)] }, void 0, true, {
										fileName: _jsxFileName,
										lineNumber: 148,
										columnNumber: 17
									}, this)]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 139,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
									type: "button",
									className: "drawer-item",
									onClick: () => {
										setAdding(true);
										setMenu(false);
									},
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Plus, { size: 18 }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 161,
										columnNumber: 17
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: "Add a domain" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 163,
										columnNumber: 19
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("small", { children: "Save a site into your accounts" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 164,
										columnNumber: 19
									}, this)] }, void 0, true, {
										fileName: _jsxFileName,
										lineNumber: 162,
										columnNumber: 17
									}, this)]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 153,
									columnNumber: 15
								}, this)
							]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 137,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
							className: "menu-group",
							children: [
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", {
									className: "menu-label",
									children: "Device"
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 170,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
									className: "theme-list",
									children: THEMES.map((theme) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
										type: "button",
										className: state.theme === theme.id ? "theme-card on" : "theme-card",
										onClick: () => patch({ theme: theme.id }),
										children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", {
											className: "theme-swatch",
											style: { background: `linear-gradient(135deg, ${theme.a}, ${theme.b} 46%, ${theme.c})` }
										}, void 0, false, {
											fileName: _jsxFileName,
											lineNumber: 179,
											columnNumber: 21
										}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: theme.name }, void 0, false, {
											fileName: _jsxFileName,
											lineNumber: 184,
											columnNumber: 23
										}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("small", { children: theme.hint }, void 0, false, {
											fileName: _jsxFileName,
											lineNumber: 185,
											columnNumber: 23
										}, this)] }, void 0, true, {
											fileName: _jsxFileName,
											lineNumber: 183,
											columnNumber: 21
										}, this)]
									}, theme.id, true, {
										fileName: _jsxFileName,
										lineNumber: 173,
										columnNumber: 19
									}, this))
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 171,
									columnNumber: 15
								}, this),
								/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
									type: "button",
									className: "drawer-item danger",
									onClick: () => {
										if (window.confirm("Clear saved sites, domain intel, visits, and the password stored on this device?")) {
											clearAll();
											setMenu(false);
										}
									},
									children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Trash2, { size: 18 }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 200,
										columnNumber: 17
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("strong", { children: "Clear this device" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 202,
										columnNumber: 19
									}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("small", { children: "Removes local vault data" }, void 0, false, {
										fileName: _jsxFileName,
										lineNumber: 203,
										columnNumber: 19
									}, this)] }, void 0, true, {
										fileName: _jsxFileName,
										lineNumber: 201,
										columnNumber: 17
									}, this)]
								}, void 0, true, {
									fileName: _jsxFileName,
									lineNumber: 190,
									columnNumber: 15
								}, this)
							]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 169,
							columnNumber: 13
						}, this)
					]
				}, void 0, true, {
					fileName: _jsxFileName,
					lineNumber: 87,
					columnNumber: 11
				}, this)
			}, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 86,
				columnNumber: 9
			}, this) : null,
			adding ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
				className: "modal-backdrop",
				onClick: () => setAdding(false),
				children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
					className: "modal-card stack",
					onClick: (event) => event.stopPropagation(),
					children: [
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h3", { children: "Add a domain" }, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 214,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
							className: "hint",
							children: "It is saved as one of your accounts and can be fetched with the same phone and password."
						}, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 215,
							columnNumber: 13
						}, this),
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
							className: "text-input",
							value: domain,
							placeholder: "9au.com",
							autoCapitalize: "none",
							autoCorrect: "off",
							spellCheck: false,
							onChange: (event) => setDomain(event.target.value),
							onKeyDown: (event) => {
								if (event.key === "Enter") submitDomain();
							}
						}, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 218,
							columnNumber: 13
						}, this),
						error ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
							className: "error-line",
							children: error
						}, void 0, false, {
							fileName: _jsxFileName,
							lineNumber: 230,
							columnNumber: 22
						}, this) : null,
						/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
							className: "actions",
							children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn",
								onClick: () => setAdding(false),
								children: "Cancel"
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 232,
								columnNumber: 15
							}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "button",
								className: "btn btn-grad",
								onClick: submitDomain,
								children: "Save site"
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 235,
								columnNumber: 15
							}, this)]
						}, void 0, true, {
							fileName: _jsxFileName,
							lineNumber: 231,
							columnNumber: 13
						}, this)
					]
				}, void 0, true, {
					fileName: _jsxFileName,
					lineNumber: 213,
					columnNumber: 11
				}, this)
			}, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 212,
				columnNumber: 9
			}, this) : null
		]
	}, void 0, true, {
		fileName: _jsxFileName,
		lineNumber: 42,
		columnNumber: 5
	}, this);
}
export { fetchCasinoBatchFn as a, lookupSiteIconFn as c, fetchAllStatusesFn as i, useServerFn as l, checkSiteStatus as n, loadPublicCatalogueFn as o, createSsrRpc as r, lookupMerchantsFn as s, AppFrame as t };
