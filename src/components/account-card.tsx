import { Bookmark, ChevronDown, ChevronUp, Star } from "lucide-react";
import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { SiteIcon } from "@/components/site-icon";
import type { BonusMode, SortMode } from "@/components/vault";
import { moneyShort, timeAgo } from "@/lib/format";
import { bonusHasRealAmount, bonusTurnover, formatTurnover } from "@/lib/order";
import type { AccountErr, AccountOk, BonusView } from "@/lib/parse-account";

export type StatusName = "checking" | "live" | "slow" | "redirect" | "dead" | "unknown";

const STATUS_LABEL: Record<StatusName, string> = {
  checking: "Checking",
  live: "Live",
  slow: "Slow",
  redirect: "Redirect",
  dead: "Dead",
  unknown: "Unknown",
};

const WALLET_COLUMNS: {
  key: "commission" | "share" | "referral" | "new" | "rebate";
  mode: Exclude<BonusMode, "none">;
  label: string;
  bet: boolean;
}[] = [
  { key: "commission", mode: "commission", label: "COM", bet: true },
  { key: "share", mode: "share", label: "SHARE", bet: false },
  { key: "referral", mode: "referral", label: "REF", bet: false },
  { key: "new", mode: "new", label: "NEW", bet: false },
  { key: "rebate", mode: "rebate", label: "REBATE", bet: false },
];

function walletOf(account: AccountOk | null | undefined): number {
  if (!account) return 0;
  const held = account.wallets.filter((wallet) => wallet.balance > 0.001);
  if (held.length) return held.reduce((sum, wallet) => sum + wallet.balance, 0);
  return account.cash > 0.001 ? account.cash : 0;
}

function withdrawalValueText(value: number | null | undefined): string {
  return value != null && value > 0 ? moneyShort(value) : "—";
}

function selectedBonusFromAccount(account: AccountOk | null | undefined, mode: BonusMode): BonusView | null {
  if (!account || mode === "none") return null;
  return account[mode] ?? null;
}

function selectedBonusMeta(mode: Exclude<BonusMode, "none">): { emoji: string; label: string } {
  if (mode === "new") return { emoji: "🎁", label: "Signup" };
  if (mode === "share") return { emoji: "🤝", label: "Share" };
  if (mode === "commission") return { emoji: "💸", label: "Commission" };
  if (mode === "referral") return { emoji: "👥", label: "Referral" };
  return { emoji: "♻️", label: "Rebate" };
}

export function AccountCard({
  domain,
  rank,
  addedAt,
  status,
  account,
  errorRow,
  walletMode = true,
  bonusMode = "none",
  sortMode = "default",
  variant = "default",
  visited = false,
  showVisited = false,
  saved,
  onToggleSave,
  onVisit,
}: {
  domain: string;
  rank?: number;
  addedAt?: string | null;
  status?: StatusName;
  account?: AccountOk | null;
  errorRow?: AccountErr | null;
  walletMode?: boolean;
  bonusMode?: BonusMode;
  sortMode?: SortMode;
  variant?: "default" | "new" | "best" | "unverified" | "share";
  visited?: boolean;
  showVisited?: boolean;
  saved?: boolean;
  onToggleSave?: () => void;
  onVisit?: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [age, setAge] = useState("");
  const [iconSrc, setIconSrc] = useState("");

  useEffect(() => {
    setAge(timeAgo(addedAt));
  }, [addedAt]);

  const sortedBonuses = useMemo(() => {
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

  const favStyle = useMemo<CSSProperties | undefined>(
    () => (iconSrc ? ({ "--fav": `url("${iconSrc.replace(/"/g, "%22")}")` } as CSSProperties) : undefined),
    [iconSrc],
  );

  const cash = walletOf(account);
  const hasCash = cash > 0.001;
  const chosenBonus = selectedBonusFromAccount(account, bonusMode);
  const chosenActive = bonusMode !== "none" && bonusHasRealAmount(chosenBonus);

  const variantClass =
    variant === "new"
      ? " new-card"
      : variant === "best"
        ? " best-card"
        : variant === "unverified"
          ? " unverified-card"
          : variant === "share"
            ? " share-card"
            : "";

  const visitClass = showVisited ? (visited ? " visited" : " unvisited") : "";

  if (!walletMode) {
    return (
      <article className={`bpl-card bpl-domain-card${variantClass}${visitClass}`}>
        {iconSrc ? <span className="card-fav-bg" style={favStyle} aria-hidden="true" /> : null}
        <SiteIcon domain={domain} className="bpl-icon" onSrcChange={setIconSrc} />
        <div className="bpl-domain-body">
          <div className="bpl-title-row">
            {rank != null ? (
              <span className="bpl-rank" aria-hidden="true">
                {rank}.
              </span>
            ) : null}
            <a
              className="bpl-domain-link"
              href={`https://${domain}/`}
              target="_blank"
              rel="noreferrer"
              onClick={onVisit}
            >
              {domain}
            </a>
            {account?.unclaimed ? <span className="acct-unclaimed">Unclaimed</span> : null}
            {onToggleSave ? (
              <button
                type="button"
                className={saved ? "acct-save on" : "acct-save"}
                aria-label={saved ? `Remove ${domain} from my accounts` : `Save ${domain}`}
                onClick={onToggleSave}
              >
                <Bookmark size={14} fill={saved ? "currentColor" : "none"} />
              </button>
            ) : null}
            {showVisited ? (
              <span
                className="bpl-visit-indicator"
                title={visited ? "Visited" : "Not visited"}
                aria-label={visited ? "Visited" : "Not visited"}
              >
                {visited ? "✓" : "●"}
              </span>
            ) : null}
          </div>

          <div className="bpl-meta-row">
            <span className="bpl-added">{age || (account ? "Fetched" : "—")}</span>
            {status ? (
              <span className={`bpl-status ${status}`}>
                <i />
                {STATUS_LABEL[status]}
              </span>
            ) : null}
          </div>

          {sortMode === "errors" && errorRow ? (
            <div className="bpl-error-pill">⚠ {errorRow.message || "Fetch error"}</div>
          ) : null}

          {chosenActive && chosenBonus ? (
            <div className={`signup-bonus-line bonus-type-${bonusMode}`}>
              <span className="signup-amount">
                {selectedBonusMeta(bonusMode).emoji} {selectedBonusMeta(bonusMode).label}{" "}
                {chosenBonus.amountDisplay || moneyShort(chosenBonus.amount)}
              </span>
              <span className="signup-withdraw">
                {bonusMode === "commission"
                  ? `Min bet ${withdrawalValueText(chosenBonus.rollover)} · Max ${chosenBonus.maxDisplay ?? "—"}`
                  : `Min ${withdrawalValueText(chosenBonus.minWithdrawal)} · Max ${chosenBonus.maxDisplay ?? "—"}`}
              </span>
              {bonusMode === "new" && bonusTurnover(chosenBonus) != null ? (
                <span className="signup-turnover">Turnover {formatTurnover(bonusTurnover(chosenBonus))}</span>
              ) : null}
            </div>
          ) : null}
        </div>
      </article>
    );
  }

  return (
    <article
      className={`bpl-card wallet-card${errorRow && !account ? " wallet-error-card" : ""}${variantClass}${visitClass}${
        open ? " is-expanded" : ""
      }`}
    >
      {iconSrc ? <span className="card-fav-bg" style={favStyle} aria-hidden="true" /> : null}
      <header className="wc-head">
        <SiteIcon domain={domain} className="bpl-icon wc-icon" onSrcChange={setIconSrc} />
        <div className="wc-id">
          <div className="bpl-title-row">
            {rank != null ? (
              <span className="bpl-rank" aria-hidden="true">
                {rank}.
              </span>
            ) : null}
            <a
              className="bpl-domain-link"
              href={`https://${domain}/`}
              target="_blank"
              rel="noreferrer"
              onClick={onVisit}
            >
              {domain}
            </a>
            {account?.unclaimed ? <span className="acct-unclaimed">Unclaimed</span> : null}
            {onToggleSave ? (
              <button
                type="button"
                className={saved ? "acct-save on" : "acct-save"}
                aria-label={saved ? `Remove ${domain} from my accounts` : `Save ${domain}`}
                onClick={onToggleSave}
              >
                <Bookmark size={14} fill={saved ? "currentColor" : "none"} />
              </button>
            ) : null}
          </div>
          <div className="wc-facts">
            {status ? (
              <span className={`bpl-status ${status}`}>
                <i />
                {STATUS_LABEL[status]}
              </span>
            ) : null}
            {age ? <span className="bpl-added">{age}</span> : null}
            {showVisited ? (
              <span className="bpl-visit-indicator" title={visited ? "Visited" : "Not visited"}>
                {visited ? "✓" : "●"}
              </span>
            ) : null}
          </div>
        </div>

        <div className="wc-right">
          {account ? (
            <div className={`wc-balance${hasCash ? " wc-has-cash" : ""}`}>
              <span className="wallet-balance">{moneyShort(cash)}</span>
              <span className="wallet-withdraw">
                ↓ {withdrawalValueText(account.cashMinWithdrawal)} · ↑ {withdrawalValueText(account.cashMaxWithdrawal)}
              </span>
            </div>
          ) : errorRow ? (
            <span className="bpl-error-pill" title={errorRow.message}>
              ⚠ {errorRow.message || "Fetch error"}
            </span>
          ) : (
            <span className="wallet-no-fetch">Not in last fetch</span>
          )}
          <button
            type="button"
            className="acct-toggle"
            aria-expanded={open}
            aria-label={open ? "Hide bonuses" : "Show bonuses"}
            onClick={() => setOpen((value) => !value)}
          >
            {open ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
          </button>
        </div>
      </header>

      <div className="wc-panel">
        <div className="wc-grid">
          {WALLET_COLUMNS.map((col) => {
            const bonus = account?.[col.key] ?? null;
            const hot = Boolean(bonus?.lit);
            const turnText = !col.bet ? formatTurnover(bonusTurnover(bonus)) : "";
            const selected = bonusMode === col.mode;
            return (
              <div
                key={col.key}
                className={`wc-cell wc-${col.key}${hot ? " wc-yes" : ""}${selected ? " wc-selected" : ""}`}
              >
                <span className="wc-label">{col.label}</span>
                {hot ? (
                  <>
                    <span className="wc-amt">
                      {bonus?.amountDisplay || "?"}
                      {col.key === "new" && (bonus?.amount ?? 0) <= 0 ? <i className="wc-dot" /> : null}
                    </span>
                    <span className="wc-sub">
                      {col.bet
                        ? `Bet ${withdrawalValueText(bonus?.rollover)}`
                        : `↓ ${withdrawalValueText(bonus?.minWithdrawal)}`}
                    </span>
                    <span className="wc-sub">↑ {bonus?.maxDisplay ?? "—"}</span>
                    {turnText ? <span className="wc-turn">{turnText}</span> : null}
                  </>
                ) : (
                  <>
                    <span className="wc-none">—</span>
                    <span className="wc-sub">{col.bet ? "Bet —" : "↓ —"}</span>
                    <span className="wc-sub">↑ —</span>
                  </>
                )}
              </div>
            );
          })}
        </div>

        {open ? (
          <div className="wc-list">
            {sortedBonuses.length ? (
              sortedBonuses.map((bonus) => (
                <div
                  className={bonus.lit ? "wc-list-row" : "wc-list-row grey"}
                  key={`${bonus.host}-${bonus.id}-${bonus.transactionId}-${bonus.angpaoId}-${bonus.luckyId}`}
                  data-id={bonus.id}
                  data-transaction-id={bonus.transactionId}
                  data-angpao-id={bonus.angpaoId}
                  data-lucky-id={bonus.luckyId}
                  data-host={bonus.host}
                >
                  <span className="wc-list-name">
                    {bonus.lit ? <Star size={12} className="wc-star" /> : null}
                    {bonus.name}
                  </span>
                  <span className="wc-list-amt">{bonus.lit ? bonus.amountDisplay || "?" : "—"}</span>
                  <span className="wc-list-meta">
                    {bonus.detail ||
                      `Min ${withdrawalValueText(bonus.minWithdrawal)} · Max ${bonus.maxDisplay ?? "—"}`}
                  </span>
                </div>
              ))
            ) : (
              <div className="wc-list-row wc-list-empty">
                <span className="wc-list-meta">
                  {errorRow ? `Fetch error: ${errorRow.message}` : "No claimable bonuses in your last fetch."}
                </span>
              </div>
            )}
          </div>
        ) : null}
      </div>
    </article>
  );
}
