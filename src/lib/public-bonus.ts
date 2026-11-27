export type BonusSlot = "deposit" | "signup" | "share" | "daily" | "birthday";
export type BonusKind = "fixed" | "percent" | "random" | "unset";
export type BonusSortMetric = "" | "az" | "za" | "deposit" | "amount" | "withdraw" | "turnover" | "count";

export type PublicBonus = {
  id: string;
  name: string;
  slot: BonusSlot | "other";
  kind: BonusKind;
  amount: number;
  amountDisplay: string;
  minWithdraw: number | null;
  maxWithdraw: number | null;
  rollover: number | null;
  minDeposit: number | null;
};

export type CatalogueHit = {
  domain: string;
  ok: boolean;
  bonuses: PublicBonus[];
  message?: string;
  host?: string;
  merchantId?: string;
};

export type CatalogueQuery = {
  domain: string;
  host?: string;
  merchantId?: string;
};

export const CORE_SLOTS: BonusSlot[] = ["deposit", "signup", "share", "daily", "birthday"];

export function isSlot(value: string): value is BonusSlot {
  return CORE_SLOTS.includes(value as BonusSlot);
}

function num(value: unknown): number | null {
  if (value == null || value === "") return null;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}

function cleanName(value: string): string {
  return value
    .replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function moneyCompact(amount: number): string {
  const rounded = Math.round(amount * 100) / 100;
  return Number.isInteger(rounded) ? `$${rounded}` : `$${rounded.toFixed(2)}`;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  return value as Record<string, unknown>;
}

function claimConfig(value: unknown): Record<string, unknown> {
  if (typeof value === "string") {
    try {
      return asRecord(JSON.parse(value)) ?? {};
    } catch {
      return {};
    }
  }
  return asRecord(value) ?? {};
}

function slotOf(name: string, custom: string, minTopup: number | null, percent: number): BonusSlot | "other" {
  const text = `${name} ${custom}`.toLowerCase();
  if (/birth|b-?day/.test(text)) return "birthday";
  if (/share|refer|invite|downline/.test(text)) return "share";
  const needsDeposit = minTopup != null && minTopup > 0;
  if (needsDeposit && percent > 0) return "deposit";
  if (/\bdaily\b|every day/.test(text)) return "daily";
  if (/sign\s?up|register|no deposit|welcome|new register|free credit/.test(text)) {
    return needsDeposit ? "deposit" : "signup";
  }
  return "other";
}

function randomRange(value: unknown): { min: number; max: number } | null {
  const row = typeof value === "string" ? asRecord(JSON.parse(value)) : asRecord(value);
  if (!row) return null;
  const min = num(row.min);
  const max = num(row.max);
  if (min == null || max == null) return null;
  return { min, max };
}

export function parsePublicPromotions(raw: unknown): PublicBonus[] {
  if (!Array.isArray(raw)) return [];
  const out: PublicBonus[] = [];
  for (const item of raw) {
    const row = asRecord(item);
    if (!row) continue;
    const name = cleanName(String(row.name ?? ""));
    if (!name) continue;
    const custom = String(claimConfig(row.claimConfig).customBonus ?? "");
    const fixed = num(row.bonusFixed) ?? 0;
    const percent = num(row.bonus) ?? 0;
    let range: { min: number; max: number } | null = null;
    try {
      range = randomRange(row.bonusRandom);
    } catch {
      range = null;
    }
    let kind: BonusKind = "unset";
    let amount = 0;
    let amountDisplay = "—";
    if (fixed > 0) {
      kind = "fixed";
      amount = fixed;
      amountDisplay = moneyCompact(fixed);
    } else if (percent > 0) {
      kind = "percent";
      amount = percent;
      amountDisplay = `${Number.isInteger(percent) ? percent : percent.toFixed(2)}%`;
    } else if (range && (range.min > 0 || range.max > 0)) {
      kind = "random";
      amount = range.max;
      amountDisplay = `${moneyCompact(range.min)}–${moneyCompact(range.max)}`;
    }
    const minWithdraw = num(row.minWithdraw);
    const maxWithdraw = num(row.maxWithdraw);
    const rollover = num(row.rollover);
    const minDeposit = num(row.minTopup);
    out.push({
      id: String(row.id ?? name),
      name,
      slot: slotOf(name, custom, minDeposit, percent),
      kind,
      amount,
      amountDisplay,
      minWithdraw: minWithdraw != null && minWithdraw > 0 ? minWithdraw : null,
      maxWithdraw: maxWithdraw != null && maxWithdraw > 0 ? maxWithdraw : null,
      rollover: rollover != null && rollover > 0 ? rollover : null,
      minDeposit: minDeposit != null && minDeposit > 0 ? minDeposit : null,
    });
  }
  return out;
}

function rank(bonus: PublicBonus): number {
  if (bonus.kind === "fixed") return 3_000_000 + bonus.amount;
  if (bonus.kind === "percent") return 2_000_000 + bonus.amount;
  if (bonus.kind === "random") return 1_000_000 + bonus.amount;
  return 0;
}

export function bonusMetricValue(bonus: PublicBonus | null, metric: BonusSortMetric): number | null {
  if (!bonus || bonus.kind === "unset") return null;
  if (metric === "deposit") {
    return bonus.slot === "deposit" && bonus.kind === "percent" && bonus.amount > 0 ? bonus.amount : null;
  }
  if (metric === "withdraw") {
    return bonus.maxWithdraw != null && bonus.maxWithdraw > 0 ? bonus.maxWithdraw : null;
  }
  if (metric === "turnover") {
    if (bonus.rollover != null && bonus.rollover > 0) return bonus.rollover;
    if (bonus.kind !== "percent" && bonus.amount > 0 && bonus.minWithdraw != null && bonus.minWithdraw > 0) {
      return bonus.minWithdraw / bonus.amount;
    }
    return null;
  }
  if (metric === "amount") {
    return bonus.amount > 0 ? bonus.amount : null;
  }
  return null;
}

export function bestBonus(bonuses: PublicBonus[], slot: BonusSlot): PublicBonus | null {
  const matches = bonuses.filter((bonus) => bonus.slot === slot);
  if (!matches.length) return null;
  return (
    [...matches].sort((a, b) => {
      if (slot === "deposit") {
        const match = (bonus: PublicBonus) => (bonus.kind === "percent" ? bonus.amount : -1);
        return match(b) - match(a) || rank(b) - rank(a);
      }
      return rank(b) - rank(a);
    })[0] ?? null
  );
}

/**
 * Picks the representative bonus for a slot in a way that stays synchronized
 * with the active sort metric so the displayed tile value always matches
 * the value used to rank the card.
 */
export function bestBonusForMetric(
  bonuses: PublicBonus[],
  slot: BonusSlot,
  metric: BonusSortMetric = "",
): PublicBonus | null {
  const matches = bonuses.filter((bonus) => bonus.slot === slot && bonus.kind !== "unset");
  if (!matches.length) return bestBonus(bonuses, slot);
  if (metric === "turnover") {
    return (
      [...matches].sort((a, b) => {
        const va = bonusMetricValue(a, "turnover");
        const vb = bonusMetricValue(b, "turnover");
        if (va == null && vb == null) return rank(b) - rank(a);
        if (va == null) return 1;
        if (vb == null) return -1;
        return va - vb || rank(b) - rank(a);
      })[0] ?? null
    );
  }
  if (metric === "withdraw") {
    return (
      [...matches].sort((a, b) => {
        const va = bonusMetricValue(a, "withdraw");
        const vb = bonusMetricValue(b, "withdraw");
        if (va == null && vb == null) return rank(b) - rank(a);
        if (va == null) return 1;
        if (vb == null) return -1;
        return vb - va || rank(b) - rank(a);
      })[0] ?? null
    );
  }
  return bestBonus(matches, slot);
}

/**
 * Computes the winning bonus and metric value for a domain given the active
 * filter slots, optional focus slot, and sort metric.
 */
export function domainBonusMetric(
  bonuses: PublicBonus[],
  activeSlots: BonusSlot[],
  focusSlot: BonusSlot | "",
  metric: BonusSortMetric,
): { bonus: PublicBonus | null; value: number | null; slot: BonusSlot | null } {
  if (!bonuses.length) return { bonus: null, value: null, slot: null };
  if (metric === "count") {
    const scope = activeSlots.length
      ? bonuses.filter((bonus) => bonus.slot !== "other" && activeSlots.includes(bonus.slot) && bonus.kind !== "unset")
      : bonuses.filter((bonus) => bonus.kind !== "unset");
    return { bonus: scope[0] ?? null, value: scope.length, slot: focusSlot || activeSlots[0] || null };
  }
  if (metric === "deposit") {
    const picked = bestBonusForMetric(bonuses, "deposit", "deposit");
    return { bonus: picked, value: bonusMetricValue(picked, "deposit"), slot: "deposit" };
  }
  const candidateSlots: BonusSlot[] = focusSlot
    ? [focusSlot]
    : activeSlots.length
      ? activeSlots
      : CORE_SLOTS;
  const ascending = metric === "turnover";
  let winner: { bonus: PublicBonus | null; value: number | null; slot: BonusSlot | null } = {
    bonus: null,
    value: null,
    slot: null,
  };
  for (const slot of candidateSlots) {
    const picked = bestBonusForMetric(bonuses, slot, metric);
    const val = bonusMetricValue(picked, metric);
    if (val == null) continue;
    if (
      winner.value == null ||
      (ascending ? val < winner.value : val > winner.value)
    ) {
      winner = { bonus: picked, value: val, slot };
    }
  }
  return winner;
}
