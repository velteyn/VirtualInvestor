/**
 * Company tab (company-profiles 2.1): static per-instrument profiles with a
 * symbol selector, mounted as a third tab in the positions-strip dock that
 * mountPortfolio creates. The content is fully static (ships as a JSON asset),
 * so the tab never calls the server — it only reads the round's tickers.
 *
 * Tab coexistence: portfolio's own tab listener manages the Positions and
 * Portfolio views; this component adds a parallel listener that manages only
 * its own view while mirroring the same `vi-active` convention. The two never
 * fight because each hides its own views for any tab that is not its own.
 */
import companies from '../assets/companies.json';

export interface CompanyProfile {
  ticker: string;
  name: string;
  sector: string;
  type: string;
  referencePrice: number;
  baseVolatility: number;
  marketBeta: number;
  sectorBeta: number;
  sharesOutstanding: number;
  description: string;
}

export interface Company {
  /** Show a symbol's profile (used to follow watchlist clicks). */
  show(symbol: string): void;
  destroy(): void;
}

const PROFILES = companies as CompanyProfile[];

function fmtShares(shares: number): string {
  if (shares <= 0) {
    return '—';
  }
  return Math.round(shares).toLocaleString('en-US');
}

function renderBody(body: HTMLElement, profile: CompanyProfile | undefined): void {
  if (profile === undefined) {
    body.innerHTML = `<p class="vi-empty">—</p>`;
    return;
  }
  body.innerHTML = `
    <h2>${profile.ticker} <span class="vi-pf-count">${profile.name}</span></h2>
    <p class="vi-co-desc">${profile.description}</p>
    <div class="vi-pf-head">
      <div class="vi-pf-stat"><label>Sector</label><span>${profile.sector}</span></div>
      <div class="vi-pf-stat"><label>Type</label><span>${profile.type === 'INDEX' ? 'Index' : 'Equity'}</span></div>
      <div class="vi-pf-stat"><label>Reference</label><span>$${profile.referencePrice.toFixed(2)}</span></div>
      <div class="vi-pf-stat"><label>Beta</label><span>${profile.marketBeta.toFixed(2)}</span></div>
      <div class="vi-pf-stat"><label>Sector β</label><span>${profile.sectorBeta.toFixed(2)}</span></div>
      <div class="vi-pf-stat"><label>Base vol</label><span>${(profile.baseVolatility * 100).toFixed(0)}%</span></div>
      <div class="vi-pf-stat"><label>Shares out</label><span>${fmtShares(profile.sharesOutstanding)}</span></div>
    </div>`;
}

export function mountCompany(strip: HTMLElement): Company | null {
  // The strip lives inside the positions view inside the dock once
  // mountPortfolio has wrapped it — bail out if that never happened.
  const dock = strip.parentElement?.parentElement;
  const tabs = dock?.querySelector('.vi-tabs');
  if (dock == null || tabs == null) {
    return null;
  }
  const byTicker = new Map(PROFILES.map((profile) => [profile.ticker, profile]));

  const button = document.createElement('button');
  button.setAttribute('data-tab', 'company');
  button.textContent = 'Company';
  tabs.appendChild(button);

  const view = document.createElement('div');
  view.className = 'vi-tab';
  view.hidden = true;
  view.innerHTML = `
    <div class="vi-co-bar"><label for="vi-co-symbol">Symbol</label>
      <select id="vi-co-symbol">
        ${PROFILES.map((profile) => `<option value="${profile.ticker}">${profile.ticker}</option>`).join('')}
      </select></div>
    <div class="vi-co-body"></div>`;
  dock.appendChild(view);

  const select = view.querySelector('#vi-co-symbol') as HTMLSelectElement;
  const body = view.querySelector('.vi-co-body') as HTMLElement;

  const show = (symbol: string): void => {
    if (!byTicker.has(symbol)) {
      return;
    }
    select.value = symbol;
    renderBody(body, byTicker.get(symbol));
  };
  select.addEventListener('change', () => show(select.value));

  const onTabs = (event: Event): void => {
    const target = event.target as HTMLElement;
    if (target.getAttribute('data-tab') === null) {
      return;
    }
    tabs.querySelectorAll('button').forEach((b) => b.classList.toggle('vi-active', b === target));
    view.hidden = target.getAttribute('data-tab') !== 'company';
  };
  tabs.addEventListener('click', onTabs);

  show('NVDO');
  return {
    show,
    destroy() {
      tabs.removeEventListener('click', onTabs);
      button.remove();
      view.remove();
    },
  };
}
