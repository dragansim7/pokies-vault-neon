import type { AccountOk, AccountRow, BonusView } from "@/lib/parse-account";
import type { BonusMode, SortMode } from "@/components/vault";
import type { SiteGroup } from "@/data/sites";

export const SORT_IDS = [
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
  "new",
] as const;

const DOMAIN_LOCALE_OPTS: Intl.CollatorOptions = { numeric: true, sensitivity: "base" };

export function isSortMode(value: unknown): value is SortMode {
  return typeof value === "string" && (SORT_IDS as readonly string[]).includes(value);
}

export function sortRequiresSelectedBonus(mode: SortMode): boolean {
  return mode === "shareAmount" || mode === "shareMax" || mode === "shareTurn";
}

export function sortRequiresAccountData(mode: SortMode): boolean {
  return (
    mode === "balance" ||
    mode === "turnover" ||
    mode === "cashout" ||
    mode === "errors" ||
    mode === "norefer" ||
    sortRequiresSelectedBonus(mode)
  );
}

export function sortModeShortLabel(mode: SortMode): string {
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

export function chosenBonus(row: AccountOk, mode: BonusMode): BonusView | null {
  if (mode === "none" || mode === "share") return row.share;
  return row[mode];
}

export function bonusHasRealAmount(bonus: BonusView | null | undefined): boolean {
  if (!bonus || !bonus.lit) return false;
  if (Number.isFinite(bonus.amount) && bonus.amount > 0) return true;
  const disp = String(bonus.amountDisplay ?? "").trim();
  if (disp && !disp.includes("%") && disp !== "—" && disp !== "-" && /[1-9]/.test(disp)) return true;
  return false;
}

export function bonusTurnover(bonus: BonusView | null | undefined): number | null {
  if (!bonus || !bonus.lit) return null;
  if (!(bonus.amount > 0) || bonus.minWithdrawal == null || bonus.minWithdrawal <= 0) return null;
  return bonus.minWithdrawal / bonus.amount;
}

export function formatTurnover(value: number | null): string {
  if (value == null || !Number.isFinite(value)) return "";
  return `${Number(value >= 10 ? value.toFixed(1) : value.toFixed(2))}×`;
}

function bestTurnover(row: AccountOk): number | null {
  const values: number[] = [];
  for (const bonus of [row.commission, row.share, row.referral, row.new, row.rebate]) {
    const value = bonusTurnover(bonus);
    if (value != null) values.push(value);
  }
  for (const bonus of row.availableBonuses) {
    const value = bonusTurnover(bonus);
    if (value != null) values.push(value);
  }
  return values.length ? Math.min(...values) : null;
}

function rowHasAnyBonus(row: AccountRow | undefined): boolean {
  if (!row || !row.ok) return false;
  for (const bonus of [row.commission, row.share, row.referral, row.new, row.rebate]) {
    if (bonusHasRealAmount(bonus)) return true;
  }
  return row.availableBonuses.some((bonus) => bonusHasRealAmount(bonus));
}

function sortBottomTier(has: boolean, row: AccountRow | undefined): number {
  if (row && !row.ok) return 3;
  if (!has) return rowHasAnyBonus(row) ? 1 : 2;
  return 0;
}

function addedAtTs(value: string | null | undefined): number {
  if (!value) return 0;
  const ts = Date.parse(value);
  return Number.isFinite(ts) ? ts : 0;
}

export function compareSites(
  a: { domain: string; group: SiteGroup; addedAt: string | null },
  b: { domain: string; group: SiteGroup; addedAt: string | null },
  rowA: AccountRow | undefined,
  rowB: AccountRow | undefined,
  sort: SortMode,
  bonusMode: BonusMode,
  deadDomains?: ReadonlySet<string>,
): number {
  const azDelta = () => a.domain.localeCompare(b.domain, undefined, DOMAIN_LOCALE_OPTS);

  if (deadDomains && deadDomains.size) {
    const deadA = deadDomains.has(a.domain) ? 1 : 0;
    const deadB = deadDomains.has(b.domain) ? 1 : 0;
    if (deadA !== deadB) return deadA - deadB;
  }

  const okA = rowA?.ok ? rowA : null;
  const okB = rowB?.ok ? rowB : null;

  if (sort === "az") return azDelta();
  if (sort === "za") return b.domain.localeCompare(a.domain, undefined, DOMAIN_LOCALE_OPTS);

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
    const rank = (row: AccountOk | null) => (!row ? 1 : row.referral?.lit ? 2 : 0);
    return rank(okA) - rank(okB) || azDelta();
  }

  type Primary = { val: number; has: boolean; asc: boolean };
  const primary = (
    item: { domain: string; group: SiteGroup; addedAt: string | null },
    ok: AccountOk | null,
  ): Primary | null => {
    if (sort === "balance") {
      const v = ok ? ok.cash : Number.NaN;
      return { val: v, has: ok != null && Number.isFinite(v), asc: false };
    }
    if (sort === "turnover") {
      const v = ok ? bestTurnover(ok) : null;
      return { val: v ?? Number.POSITIVE_INFINITY, has: v != null && Number.isFinite(v), asc: true };
    }
    if (sort === "cashout") {
      const v = ok?.cashMinWithdrawal ?? null;
      return { val: v ?? Number.POSITIVE_INFINITY, has: v != null && v > 0, asc: true };
    }
    if (sort === "shareAmount") {
      const bonus = ok ? chosenBonus(ok, bonusMode) : null;
      const v = bonusHasRealAmount(bonus) ? bonus!.amount : Number.NaN;
      return { val: v, has: Number.isFinite(v) && v > 0, asc: false };
    }
    if (sort === "shareMax") {
      const bonus = ok ? chosenBonus(ok, bonusMode) : null;
      const v = bonus?.lit && bonus.maxWithdrawal != null ? bonus.maxWithdrawal : Number.NaN;
      return { val: v, has: Number.isFinite(v) && v > 0, asc: false };
    }
    if (sort === "shareTurn") {
      const bonus = ok ? chosenBonus(ok, bonusMode) : null;
      const v = bonusTurnover(bonus);
      return { val: v ?? Number.POSITIVE_INFINITY, has: v != null && Number.isFinite(v), asc: true };
    }
    if (sort === "new") {
      const v = addedAtTs(item.addedAt);
      return { val: v, has: v > 0, asc: false };
    }
    if (sort === "popular") {
      const v = item.group === "best" ? 2 : item.group === "share" ? 1 : 0;
      return { val: v, has: v > 0, asc: false };
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
    const delta = pa.asc ? pa.val - pb.val : pb.val - pa.val;
    return delta || azDelta();
  }

  return azDelta();
}
