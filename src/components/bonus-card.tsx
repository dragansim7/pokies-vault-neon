import { ChevronDown, ChevronUp, Sparkles } from "lucide-react";
import { useMemo, useState, type CSSProperties } from "react";
import { SiteIcon } from "@/components/site-icon";
import { moneyShort } from "@/lib/format";
import {
  bestBonusForMetric,
  bonusMetricValue,
  domainBonusMetric,
  type BonusSlot,
  type BonusSortMetric,
  type PublicBonus,
} from "@/lib/public-bonus";

const SLOTS: { key: BonusSlot; short: string; tone: string }[] = [
  { key: "deposit", short: "DEPOSIT", tone: "commission" },
  { key: "signup", short: "SIGNUP", tone: "new" },
  { key: "share", short: "SHARE", tone: "share" },
  { key: "daily", short: "DAILY", tone: "referral" },
  { key: "birthday", short: "BDAY", tone: "rebate" },
];

const SLOT_NAME: Record<BonusSlot | "other", string> = {
  deposit: "Deposit Match",
  signup: "Free Signup",
  share: "Share / Invite",
  daily: "Daily Free",
  birthday: "Birthday",
  other: "Other",
};

function bound(value: number | null): string {
  return value != null && value > 0 ? moneyShort(value) : "—";
}

function rolloverBadge(bonus: PublicBonus | null): string {
  if (!bonus || bonus.kind === "unset") return "";
  if (bonus.rollover != null && bonus.rollover > 0) {
    return `${Number(bonus.rollover >= 10 ? bonus.rollover.toFixed(1) : bonus.rollover.toFixed(2))}×`;
  }
  if (bonus.kind !== "percent" && bonus.amount > 0 && bonus.minWithdraw != null && bonus.minWithdraw > 0) {
    const ratio = bonus.minWithdraw / bonus.amount;
    return `${Number(ratio >= 10 ? ratio.toFixed(1) : ratio.toFixed(2))}×`;
  }
  return "";
}

export function BonusCard({
  domain,
  rank,
  bonuses,
  message,
  activeSlots = [],
  focusSlot = "",
  sortMetric = "",
  onToggleSlot,
}: {
  domain: string;
  rank?: number;
  bonuses: PublicBonus[] | null;
  message?: string;
  activeSlots?: BonusSlot[];
  focusSlot?: BonusSlot | "";
  sortMetric?: BonusSortMetric;
  onToggleSlot?: (slot: BonusSlot) => void;
}) {
  const [open, setOpen] = useState(false);
  const [iconSrc, setIconSrc] = useState("");
  const listed = bonuses ?? [];

  const favStyle = useMemo<CSSProperties | undefined>(
    () => (iconSrc ? ({ "--fav": `url("${iconSrc.replace(/"/g, "%22")}")` } as CSSProperties) : undefined),
    [iconSrc],
  );

  // Synchronize the card's top-right highlight with the active sort & filter state
  const highlight = useMemo(() => {
    if (!listed.length) return null;
    if (sortMetric && sortMetric !== "az" && sortMetric !== "za") {
      const hit = domainBonusMetric(listed, activeSlots, focusSlot, sortMetric);
      if (sortMetric === "count") {
        return {
          value: `${hit.value ?? 0} offers`,
          sub: activeSlots.length ? "Matched filters" : "Published",
          hot: (hit.value ?? 0) > 0,
        };
      }
      if (hit.bonus && hit.value != null) {
        const slotLabel = hit.slot ? SLOT_NAME[hit.slot] : SLOT_NAME[hit.bonus.slot];
        if (sortMetric === "turnover") {
          return {
            value: `${Number(hit.value >= 10 ? hit.value.toFixed(1) : hit.value.toFixed(2))}×`,
            sub: `${slotLabel} · ${hit.bonus.amountDisplay}`,
            hot: true,
          };
        }
        if (sortMetric === "withdraw") {
          return {
            value: moneyShort(hit.value),
            sub: `${slotLabel} max`,
            hot: true,
          };
        }
        return {
          value: hit.bonus.amountDisplay,
          sub: slotLabel,
          hot: true,
        };
      }
    }
    if (focusSlot) {
      const picked = bestBonusForMetric(listed, focusSlot, sortMetric);
      if (picked && picked.kind !== "unset") {
        return {
          value: picked.amountDisplay,
          sub: SLOT_NAME[focusSlot],
          hot: true,
        };
      }
    }
    const publishedCount = listed.filter((b) => b.kind !== "unset").length;
    return {
      value: `${publishedCount} offers`,
      sub: `${listed.length} total`,
      hot: publishedCount > 0,
    };
  }, [listed, activeSlots, focusSlot, sortMetric]);

  // Sort expanded bonus list to reflect active filter slots & sort metric
  const orderedBonuses = useMemo(() => {
    if (!listed.length) return [];
    const slotMatch = (bonus: PublicBonus) =>
      activeSlots.length > 0 && bonus.slot !== "other" && activeSlots.includes(bonus.slot);
    return [...listed].sort((a, b) => {
      const focusDelta =
        Number(focusSlot !== "" && b.slot === focusSlot) - Number(focusSlot !== "" && a.slot === focusSlot);
      if (focusDelta) return focusDelta;
      const matchDelta = Number(slotMatch(b)) - Number(slotMatch(a));
      if (matchDelta) return matchDelta;
      if (sortMetric === "turnover" || sortMetric === "withdraw" || sortMetric === "amount" || sortMetric === "deposit") {
        const va = bonusMetricValue(a, sortMetric);
        const vb = bonusMetricValue(b, sortMetric);
        if (va != null && vb != null && va !== vb) {
          return sortMetric === "turnover" ? va - vb : vb - va;
        }
        if (va != null && vb == null) return -1;
        if (va == null && vb != null) return 1;
      }
      return b.amount - a.amount || a.name.localeCompare(b.name);
    });
  }, [listed, activeSlots, focusSlot, sortMetric]);

  const isHighlightedSlot = (slot: BonusSlot) =>
    focusSlot === slot ||
    activeSlots.includes(slot) ||
    (sortMetric === "deposit" && slot === "deposit");

  return (
    <article
      className={`bpl-card wallet-card bonus-finder-card${message && bonuses != null ? " wallet-error-card" : ""}${
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
            <a className="bpl-domain-link" href={`https://${domain}/`} target="_blank" rel="noreferrer">
              {domain}
            </a>
          </div>
          <div className="wc-facts">
            <span className={`bpl-status ${bonuses == null ? "unknown" : message ? "dead" : "live"}`}>
              <i />
              {bonuses == null ? "Not loaded" : message ? "Load failed" : `${listed.length} published`}
            </span>
          </div>
        </div>

        <div className="wc-right">
          {bonuses == null ? (
            <span className="wallet-no-fetch">Not loaded</span>
          ) : message ? (
            <span className="bpl-error-pill" title={message}>
              ⚠ {message}
            </span>
          ) : highlight ? (
            <div className={`wc-balance${highlight.hot ? " wc-has-cash" : ""}`}>
              <span className="wallet-balance">{highlight.value}</span>
              <span className="wallet-withdraw">{highlight.sub}</span>
            </div>
          ) : null}
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
          {SLOTS.map((slot) => {
            const found = bestBonusForMetric(listed, slot.key, sortMetric);
            const bonus = found && found.kind !== "unset" ? found : null;
            const selected = isHighlightedSlot(slot.key);
            const turnText = rolloverBadge(bonus);
            const Tag = onToggleSlot ? "button" : "div";
            return (
              <Tag
                key={slot.key}
                {...(onToggleSlot
                  ? {
                      type: "button" as const,
                      onClick: () => onToggleSlot(slot.key),
                      title: `Filter by ${SLOT_NAME[slot.key]}`,
                    }
                  : {})}
                className={`wc-cell wc-${slot.tone}${bonus ? " wc-yes" : ""}${selected ? " wc-selected" : ""}${
                  onToggleSlot ? " wc-clickable" : ""
                }`}
              >
                <span className="wc-label">{slot.short}</span>
                {bonus ? (
                  <>
                    <span className="wc-amt">{bonus.amountDisplay}</span>
                    {slot.key === "deposit" ? (
                      <>
                        <span className="wc-sub">
                          {bonus.minDeposit != null ? `Dep ${moneyShort(bonus.minDeposit)}` : "Dep —"}
                        </span>
                        <span className="wc-sub">↑ {bound(bonus.maxWithdraw)}</span>
                      </>
                    ) : (
                      <>
                        <span className="wc-sub">↓ {bound(bonus.minWithdraw)}</span>
                        <span className="wc-sub">↑ {bound(bonus.maxWithdraw)}</span>
                      </>
                    )}
                    {turnText ? <span className="wc-turn">{turnText}</span> : null}
                  </>
                ) : (
                  <>
                    <span className="wc-none">—</span>
                    <span className="wc-sub">{slot.key === "deposit" ? "Dep —" : "↓ —"}</span>
                    <span className="wc-sub">↑ —</span>
                  </>
                )}
              </Tag>
            );
          })}
        </div>

        {open ? (
          <div className="wc-list">
            {orderedBonuses.length ? (
              orderedBonuses.map((bonus) => {
                const matched =
                  (focusSlot !== "" && bonus.slot === focusSlot) ||
                  (activeSlots.length > 0 && bonus.slot !== "other" && activeSlots.includes(bonus.slot));
                return (
                  <div className={matched ? "wc-list-row matched" : "wc-list-row"} key={`${bonus.id}-${bonus.name}`}>
                    <span className="wc-list-name">
                      {matched ? <Sparkles size={12} className="wc-star" /> : null}
                      {bonus.name}
                    </span>
                    <span className="wc-list-amt">{bonus.amountDisplay}</span>
                    <span className="wc-list-meta">
                      {SLOT_NAME[bonus.slot]} · {bonus.kind}
                      {bonus.rollover != null ? ` · Rollover ${bonus.rollover}×` : ""}
                      {bonus.minDeposit != null ? ` · Deposit ${moneyShort(bonus.minDeposit)}` : ""}
                      {` · Min ${bound(bonus.minWithdraw)} · Max ${bound(bonus.maxWithdraw)}`}
                    </span>
                  </div>
                );
              })
            ) : (
              <div className="wc-list-row wc-list-empty">
                <span className="wc-list-meta">
                  {message || "No public bonuses loaded for this domain yet."}
                </span>
              </div>
            )}
          </div>
        ) : null}
      </div>
    </article>
  );
}
