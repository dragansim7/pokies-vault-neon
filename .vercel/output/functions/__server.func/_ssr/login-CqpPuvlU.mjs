import { o as __toESM } from "../_runtime.mjs";
import { r as signIn, t as authClient } from "./client-DZaBa7Dm.mjs";
import { t as GROK_PROVIDERS } from "./server-x1MirbW5.mjs";
import { n as useCurrentUserState } from "./use-current-user-C1Cm1fMI.mjs";
import { Gt as require_react, x as Navigate } from "../_libs/@tanstack/react-router+[...].mjs";
import { t as require_jsx_dev_runtime } from "../_libs/react.mjs";
// node_modules/.nitro/vite/services/ssr/assets/login-CqpPuvlU.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_dev_runtime = require_jsx_dev_runtime();
var _jsxFileName = "/app/applet/src/routes/login.tsx?tsr-split=component";
var INTRO_KEY = "pokies_intro";
function readIntroSeen() {
	try {
		return sessionStorage.getItem(INTRO_KEY) === "1";
	} catch {
		return false;
	}
}
function markIntroSeen() {
	try {
		sessionStorage.setItem(INTRO_KEY, "1");
	} catch {}
}
function Login() {
	const { user, isPending } = useCurrentUserState();
	const [mode, setMode] = (0, import_react.useState)("in");
	const [name, setName] = (0, import_react.useState)("");
	const [email, setEmail] = (0, import_react.useState)("");
	const [password, setPassword] = (0, import_react.useState)("");
	const [busy, setBusy] = (0, import_react.useState)(false);
	const [error, setError] = (0, import_react.useState)("");
	const [intro, setIntro] = (0, import_react.useState)("show");
	(0, import_react.useEffect)(() => {
		if (readIntroSeen() || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
			setIntro("off");
			return;
		}
		const fade = window.setTimeout(() => setIntro("out"), 2600);
		const done = window.setTimeout(() => {
			markIntroSeen();
			setIntro("off");
		}, 3500);
		return () => {
			window.clearTimeout(fade);
			window.clearTimeout(done);
		};
	}, []);
	if (user) return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(Navigate, { to: "/" }, void 0, false, {
		fileName: _jsxFileName,
		lineNumber: 48,
		columnNumber: 20
	}, this);
	async function submitEmail(event) {
		event.preventDefault();
		setError("");
		setBusy(true);
		try {
			const result = mode === "up" ? await authClient.signUp.email({
				email: email.trim(),
				password,
				name: name.trim() || email.trim().split("@")[0] || "Member",
				callbackURL: "/"
			}) : await authClient.signIn.email({
				email: email.trim(),
				password,
				callbackURL: "/"
			});
			if (result.error) {
				setError(result.error.message || "That email and password were not accepted.");
				setBusy(false);
				return;
			}
			window.location.assign("/");
		} catch {
			setError("Could not reach sign-in. Try again.");
			setBusy(false);
		}
	}
	return /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("main", {
		className: intro === "out" || intro === "off" ? "login-page intro-reveal" : "login-page intro-hold",
		children: [intro !== "off" ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
			className: intro === "out" ? "welcome welcome-out" : "welcome",
			"aria-hidden": "true",
			children: /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("img", {
				className: "welcome-logo",
				src: "/__grok/icon-180.png",
				alt: ""
			}, void 0, false, {
				fileName: _jsxFileName,
				lineNumber: 78,
				columnNumber: 11
			}, this)
		}, void 0, false, {
			fileName: _jsxFileName,
			lineNumber: 77,
			columnNumber: 26
		}, this) : null, /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("section", {
			className: "login-card",
			"aria-busy": isPending || void 0,
			children: [
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("img", {
					className: "login-mark",
					src: "/__grok/icon-180.png",
					alt: "",
					width: 72,
					height: 72
				}, void 0, false, {
					fileName: _jsxFileName,
					lineNumber: 81,
					columnNumber: 9
				}, this),
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
					className: "login-kicker",
					children: "Private accounts"
				}, void 0, false, {
					fileName: _jsxFileName,
					lineNumber: 82,
					columnNumber: 9
				}, this),
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("h1", {
					className: "brand grad-text login-title",
					children: "Pokies Vault"
				}, void 0, false, {
					fileName: _jsxFileName,
					lineNumber: 83,
					columnNumber: 9
				}, this),
				/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
					className: "hint login-lead",
					children: "Sign in so your fetch history stays yours. Casino passwords stay on this phone."
				}, void 0, false, {
					fileName: _jsxFileName,
					lineNumber: 84,
					columnNumber: 9
				}, this),
				isPending ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
					className: "hint",
					children: "Opening Pokies Vault…"
				}, void 0, false, {
					fileName: _jsxFileName,
					lineNumber: 88,
					columnNumber: 22
				}, this) : null,
				!isPending && true ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)(import_jsx_dev_runtime.Fragment, { children: [
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("div", {
						className: "login-providers",
						children: GROK_PROVIDERS.map((provider) => /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
							type: "button",
							className: "btn login-provider",
							onClick: () => signIn(provider.providerId, { callbackURL: "/" }),
							children: ["Continue with ", provider.label]
						}, provider.providerId, true, {
							fileName: _jsxFileName,
							lineNumber: 92,
							columnNumber: 47
						}, this))
					}, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 91,
						columnNumber: 13
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
						className: "login-or",
						children: "or use email"
					}, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 99,
						columnNumber: 13
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("form", {
						className: "stack",
						onSubmit: submitEmail,
						children: [
							mode === "up" ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
								className: "field",
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "Name" }, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 103,
									columnNumber: 19
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
									className: "text-input",
									autoComplete: "name",
									value: name,
									onChange: (event) => setName(event.target.value)
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 104,
									columnNumber: 19
								}, this)]
							}, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 102,
								columnNumber: 32
							}, this) : null,
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
								className: "field",
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "Email" }, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 107,
									columnNumber: 17
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
									className: "text-input",
									type: "email",
									autoComplete: "email",
									required: true,
									value: email,
									onChange: (event) => setEmail(event.target.value)
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 108,
									columnNumber: 17
								}, this)]
							}, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 106,
								columnNumber: 15
							}, this),
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("label", {
								className: "field",
								children: [/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("span", { children: "Password" }, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 111,
									columnNumber: 17
								}, this), /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("input", {
									className: "text-input",
									type: "password",
									autoComplete: mode === "up" ? "new-password" : "current-password",
									required: true,
									minLength: 8,
									value: password,
									onChange: (event) => setPassword(event.target.value)
								}, void 0, false, {
									fileName: _jsxFileName,
									lineNumber: 112,
									columnNumber: 17
								}, this)]
							}, void 0, true, {
								fileName: _jsxFileName,
								lineNumber: 110,
								columnNumber: 15
							}, this),
							error ? /* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("p", {
								className: "error-line",
								children: error
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 114,
								columnNumber: 24
							}, this) : null,
							/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
								type: "submit",
								className: "btn btn-grad",
								disabled: busy,
								children: busy ? "Please wait…" : mode === "up" ? "Create account" : "Sign in"
							}, void 0, false, {
								fileName: _jsxFileName,
								lineNumber: 115,
								columnNumber: 15
							}, this)
						]
					}, void 0, true, {
						fileName: _jsxFileName,
						lineNumber: 101,
						columnNumber: 13
					}, this),
					/* @__PURE__ */ (0, import_jsx_dev_runtime.jsxDEV)("button", {
						type: "button",
						className: "btn btn-ghost login-switch",
						onClick: () => {
							setMode((value) => value === "in" ? "up" : "in");
							setError("");
						},
						children: mode === "in" ? "Need an account? Create one" : "Already have an account? Sign in"
					}, void 0, false, {
						fileName: _jsxFileName,
						lineNumber: 119,
						columnNumber: 13
					}, this)
				] }, void 0, true, {
					fileName: _jsxFileName,
					lineNumber: 90,
					columnNumber: 38
				}, this) : null,
				null
			]
		}, void 0, true, {
			fileName: _jsxFileName,
			lineNumber: 80,
			columnNumber: 7
		}, this)]
	}, void 0, true, {
		fileName: _jsxFileName,
		lineNumber: 76,
		columnNumber: 10
	}, this);
}
export { Login as component };
