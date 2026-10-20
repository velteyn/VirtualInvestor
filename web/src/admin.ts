/**
 * Game-master backyard entry (player-accounts 3.4): deliberately plain login,
 * approvals queue, player resets and round controls. No charting library here;
 * this bundle stays tiny and shares nothing with the terminal except the API
 * version header.
 */
import './styles.css';
import { API_VERSION } from './adapters/serverClient';
import { friendlyError } from './app/errors';

let adminToken: string | null = null;

async function call<T>(path: string, body: unknown): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-API-Version': API_VERSION,
  };
  if (adminToken !== null) {
    headers['X-Admin-Token'] = adminToken;
  }
  const response = await fetch(path, { method: 'POST', headers, body: JSON.stringify(body) });
  if (!response.ok) {
    throw new Error(`${path} failed (${response.status})`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

interface PlayerRow {
  playerId: string;
  username: string;
  kind: string;
  status: string;
  bankId: string;
}

interface RoundRow {
  roundId: string;
  state: string;
  tick: number;
}

function esc(text: string): string {
  const el = document.createElement('span');
  el.textContent = text;
  return el.innerHTML;
}

interface BankRow {
  bankId: string;
  displayName: string;
}

async function refreshPlayers(table: HTMLElement): Promise<void> {
  const players = await call<PlayerRow[]>('/api/admin/players', {});
  const body = table.querySelector('tbody')!;
  body.innerHTML = players
    .map((p) => `<tr><td><b>${esc(p.username)}</b></td><td>${p.kind}</td><td>${p.status}</td>
      <td>${esc(p.bankId)}</td>
      <td class="vi-actions"><details class="vi-menu">
      <summary>Actions</summary>
      <div class="vi-menu-list">
      <button data-act="approve" data-id="${p.playerId}">Approve</button>
      <button data-act="reject" data-id="${p.playerId}">Reject</button>
      <button data-act="edit" data-id="${p.playerId}" data-name="${esc(p.username)}" data-bank="${esc(p.bankId)}">Edit</button>
      <button data-act="delete" data-id="${p.playerId}" data-name="${esc(p.username)}">Delete</button>
      <button data-act="reset-pw" data-id="${p.playerId}">Reset password</button>
      <button data-act="reset-purse" data-id="${p.playerId}">Reset purse</button>
      </div></details></td></tr>`)
    .join('');
}

async function refreshBanks(select: HTMLSelectElement): Promise<void> {
  const banks = await call<BankRow[]>('/api/players/banks', {});
  select.innerHTML = banks
    .map((b) => `<option value="${b.bankId}">${esc(b.displayName)}</option>`)
    .join('');
}

async function refreshRounds(list: HTMLElement): Promise<void> {
  const rounds = await call<RoundRow[]>('/api/admin/rounds', {});
  list.innerHTML = rounds
    .map((r) => `<li><b>${esc(r.roundId)}</b> ${r.state} @${r.tick}
      <details class="vi-menu"><summary>Actions</summary><div class="vi-menu-list">
      <button data-act="pause" data-id="${r.roundId}">Pause</button>
      <button data-act="resume" data-id="${r.roundId}">Resume</button>
      <button data-act="settle" data-id="${r.roundId}">Settle</button>
      </div></details></li>`)
    .join('');
}

function mount(): void {
  const root = document.getElementById('admin');
  if (root === null) {
    throw new Error('VirtualInvestor admin: #admin container not found');
  }
  root.innerHTML = `
    <h1>Backyard</h1>
    <div id="adm-login"><input id="adm-pass" type="password" placeholder="master password" />
      <button id="adm-go">Log in</button></div>
    <p id="adm-error" role="alert"></p>
    <div id="adm-panels" hidden>
      <h2>Players</h2><button id="adm-reload-players">Reload</button>
      <table id="adm-players">
        <thead><tr><th>Player</th><th>Kind</th><th>Status</th><th>Bank</th><th>Actions</th></tr></thead>
        <tbody></tbody>
      </table>
      <h3>Add player</h3>
      <div class="vi-admin-add">
        <input id="adm-new-user" placeholder="username" autocomplete="off" />
        <input id="adm-new-pass" type="password" placeholder="initial password" autocomplete="new-password" />
        <select id="adm-new-bank"><option>loading…</option></select>
        <button id="adm-do-add">Add</button>
      </div>
      <h2>Rounds</h2><button id="adm-reload-rounds">Reload</button><ul id="adm-rounds"></ul>
    </div>`;

  const pass = root.querySelector<HTMLInputElement>('#adm-pass')!;
  const go = root.querySelector<HTMLButtonElement>('#adm-go')!;
  const error = root.querySelector<HTMLParagraphElement>('#adm-error')!;
  const panels = root.querySelector<HTMLDivElement>('#adm-panels')!;
  const playersList = root.querySelector<HTMLTableElement>('#adm-players')!;
  const roundsList = root.querySelector<HTMLUListElement>('#adm-rounds')!;

  const fail = (cause: unknown): void => {
    error.textContent = friendlyError(cause);
  };

  go.addEventListener('click', async () => {
    error.textContent = '';
    try {
      const response = await call<{ adminToken: string }>('/api/admin/login', { password: pass.value });
      adminToken = response.adminToken;
      panels.hidden = false;
      await refreshPlayers(playersList);
      await refreshBanks(root.querySelector<HTMLSelectElement>('#adm-new-bank')!);
      await refreshRounds(roundsList);
    } catch (cause) {
      error.textContent = friendlyError(cause, { 401: 'Wrong master password.' });
    }
  });
  root.querySelector('#adm-reload-players')!.addEventListener('click', () => {
    refreshPlayers(playersList).catch(fail);
  });
  root.querySelector<HTMLButtonElement>('#adm-do-add')!.addEventListener('click', async () => {
    const userEl = root.querySelector<HTMLInputElement>('#adm-new-user')!;
    const passEl = root.querySelector<HTMLInputElement>('#adm-new-pass')!;
    const bankEl = root.querySelector<HTMLSelectElement>('#adm-new-bank')!;
    try {
      await call('/api/admin/players/create',
        { username: userEl.value, password: passEl.value, bankId: bankEl.value });
      userEl.value = '';
      passEl.value = '';
      await refreshPlayers(playersList);
    } catch (cause) {
      fail(cause);
    }
  });
  root.querySelector('#adm-reload-rounds')!.addEventListener('click', () => {
    refreshRounds(roundsList).catch(fail);
  });

  root.addEventListener('click', async (event) => {
    const target = event.target as HTMLElement;
    const act = target.getAttribute('data-act');
    const id = target.getAttribute('data-id');
    if (act === null || id === null) {
      return;
    }
    // Collapse the dropdown once a choice is made (re-renders recreate it closed anyway).
    target.closest('details.vi-menu')?.removeAttribute('open');
    try {
      if (act === 'approve' || act === 'reject') {
        await call(`/api/admin/players/${act}`, { playerId: id });
        await refreshPlayers(playersList);
      } else if (act === 'edit') {
        const nextName = window.prompt('Username (blank keeps current):', target.getAttribute('data-name') ?? '');
        if (nextName === null) {
          return;
        }
        const nextBank = window.prompt('Bank id (blank keeps current):', target.getAttribute('data-bank') ?? '');
        if (nextBank === null) {
          return;
        }
        await call('/api/admin/players/update', { playerId: id, username: nextName, bankId: nextBank });
        await refreshPlayers(playersList);
      } else if (act === 'delete') {
        if (window.confirm(`Delete ${target.getAttribute('data-name')} with purse and sessions?`)) {
          await call('/api/admin/players/delete', { playerId: id });
          await refreshPlayers(playersList);
        }
      } else if (act === 'reset-pw') {
        const next = window.prompt(`New password for ${id}:`);
        if (next !== null && next !== '') {
          await call('/api/admin/players/reset-password', { playerId: id, newPassword: next });
        }
      } else if (act === 'reset-purse') {
        await call('/api/admin/players/reset-purse', { playerId: id, startingCapital: 100000 });
        await refreshPlayers(playersList);
      } else if (act === 'pause' || act === 'resume' || act === 'settle') {
        await call(`/api/admin/rounds/${act}`, { roundId: id });
        await refreshRounds(roundsList);
      }
    } catch (cause) {
      fail(cause);
    }
  });
}

mount();
