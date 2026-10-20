/**
 * The lobby: the meeting place after login. Lists live solo sessions and
 * waiting rooms, starts single-player sessions, creates rooms, and shows
 * waiting rooms with seats until the master starts the clock.
 */
import type {
  GameListing,
  RoundConfig,
  RoundResponse,
  ServerClient,
} from '../adapters/serverClient';
import { friendlyError } from './errors';

const PRESETS: Array<RoundConfig['preset']> = ['ARCADE', 'REALISTIC', 'HARDCORE'];
const CAPITALS = [25_000, 50_000, 100_000, 250_000, 1_000_000];
const DURATIONS = [30, 60, 120];

export interface LobbyHandlers {
  onStarted(round: RoundResponse, participant: string): void;
}

export interface LobbyOptions {
  /** Logged-in username, shown in the banner with a log-out action. */
  username?: string;
}

function formatLeft(millis: number): string {
  const total = Math.max(0, Math.floor(millis / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

function asRound(game: GameListing): RoundResponse {
  return {
    roundId: game.roundId,
    seed: 0,
    startingCapital: game.startingCapital,
    durationMinutes: game.durationMinutes,
    preset: game.preset,
    state: game.state,
    durationTicks: 0,
    sessionToken: '',
  };
}

export function mountLobby(
  root: HTMLElement,
  client: ServerClient,
  handlers: LobbyHandlers,
  options: LobbyOptions = {},
): void {
  const username = options.username ?? '';
  root.innerHTML = `
    <div class="vi-lobby">
      <div class="vi-lobby-head">
        <h2>Lobby</h2>
        <span class="vi-sub">Playing as ${username}</span>
        <button id="vi-logout" title="Log out">Log out</button>
      </div>
      <div class="vi-lobby-row">
        <button id="vi-refresh">Refresh</button>
        <button id="vi-solo">Single-player session</button>
        <button id="vi-create">Create room</button>
      </div>
      <div id="vi-dialog"></div>
      <div class="vi-table-wrap">
      <table class="vi-table" id="vi-games">
        <thead><tr>
          <th>Game</th><th>State</th><th>Preset</th><th>Time left</th>
          <th>Seats</th><th>Leader</th><th></th>
        </tr></thead>
        <tbody id="vi-games-body"><tr><td colspan="7">loading…</td></tr></tbody>
      </table>
      </div>
      <div id="vi-waiting"></div>
      <p id="vi-lobby-error" class="vi-error" role="alert"></p>
    </div>`;

  const body = root.querySelector<HTMLTableSectionElement>('#vi-games-body')!;  const dialog = root.querySelector<HTMLDivElement>('#vi-dialog')!;
  const waiting = root.querySelector<HTMLDivElement>('#vi-waiting')!;
  const error = root.querySelector<HTMLParagraphElement>('#vi-lobby-error')!;
  const fail = (cause: unknown, labels: Record<number, string> = {}): void => {
    error.textContent = friendlyError(cause, labels);
  };

  const configFields = (prefix: string): string => `
    <label>Starting capital
      <select id="${prefix}-capital">
        ${CAPITALS.map((value) => `<option value="${value}"${value === 100_000 ? ' selected' : ''}>$${value.toLocaleString()}</option>`).join('')}
      </select>
    </label>
    <label>Session length
      <select id="${prefix}-duration">
        ${DURATIONS.map((value) => `<option value="${value}"${value === 30 ? ' selected' : ''}>${value} minutes</option>`).join('')}
      </select>
    </label>
    <label>Realism
      <select id="${prefix}-preset">
        ${PRESETS.map((value) => `<option value="${value}"${value === 'REALISTIC' ? ' selected' : ''}>${value}</option>`).join('')}
      </select>
    </label>`;

  const readConfig = (prefix: string): RoundConfig => ({
    startingCapital: Number(root.querySelector<HTMLSelectElement>(`#${prefix}-capital`)!.value),
    durationMinutes: Number(root.querySelector<HTMLSelectElement>(`#${prefix}-duration`)!.value),
    preset: root.querySelector<HTMLSelectElement>(`#${prefix}-preset`)!.value as RoundConfig['preset'],
  });

  const refresh = async (): Promise<void> => {
    error.textContent = '';
    try {
      const games = await client.liveGames();
      if (games.length === 0) {
        body.innerHTML = '<tr><td colspan="7">No live games — start one above.</td></tr>';
        return;
      }
      body.innerHTML = '';
      for (const game of games) {
        const row = document.createElement('tr');
        row.innerHTML = `
          <td>${game.kind === 'room' ? 'Room' : 'Solo'} <span class="vi-dim">${game.roundId}</span></td>
          <td>${game.state}</td>
          <td>${game.preset}</td>
          <td>${formatLeft(game.timeLeftMillis)}</td>
          <td>${game.seatsTaken}/${game.seatsTotal}</td>
          <td>${game.leader}</td>
          <td></td>`;
        // Join only where the server says this player may sit: own solo seat,
        // a free or held room seat — never another player's solo world.
        if (game.joinable) {
          const join = document.createElement('button');
          join.textContent = 'Join';
          join.addEventListener('click', async () => {
            join.disabled = true;
            try {
              if (game.kind === 'solo') {
                handlers.onStarted(asRound(game), 'human');
              } else {
                const response = await client.joinRoom(game.roundId);
                if (response.state === 'RUNNING') {
                  handlers.onStarted(asRound(game), response.seat);
                } else {
                  showWaiting(game.roundId, response.seat);
                }
              }
            } catch (cause) {
              fail(cause, {
                404: 'Game not found — it may have ended.',
                409: 'Seat taken — refresh and pick another.',
                403: 'Join refused for this seat.',
              });
            } finally {
              join.disabled = false;
            }
          });
          row.lastElementChild!.appendChild(join);
        }
        body.appendChild(row);
      }
    } catch (cause) {
      error.textContent = friendlyError(cause, {}, 'Could not load games — is the server running?');
    }
  };

  /** Waiting-room screen: seats, humans, master start, refresh-until-live. */
  const showWaiting = async (roundId: string, seat: string): Promise<void> => {
    error.textContent = '';
    try {
      const games = await client.liveGames();
      const game = games.find((entry) => entry.roundId === roundId);
      if (game === undefined) {
        waiting.innerHTML = '<p class="vi-sub">Room expired before it started.</p>';
        return;
      }
      if (game.state === 'RUNNING') {
        handlers.onStarted(asRound(game), seat);
        return;
      }
      const isMaster = game.masterPlayerId !== null && game.masterPlayerId === client.playerId();
      const seats = Array.from({ length: game.seatsTotal }, (_, index) => {
        const name = game.humans[index];
        return `<li>${name === undefined ? `Seat ${index + 1} — open` : `${name}${index === 0 ? ' (master)' : ''}`}</li>`;
      }).join('');
      waiting.innerHTML = `
        <div class="vi-waiting">
          <h3>Room ${game.roundId} — waiting (${game.seatsTaken}/${game.seatsTotal})</h3>
          <ul>${seats}</ul>
          <div class="vi-lobby-row">
            ${isMaster ? '<button id="vi-start-room">Start room</button>' : '<span class="vi-sub">Waiting for the master to start…</span>'}
            <button id="vi-wait-refresh">Refresh</button>
            <button id="vi-wait-leave">Back to lobby</button>
          </div>
        </div>`;
      root.querySelector<HTMLButtonElement>('#vi-wait-refresh')!.addEventListener('click', () => {
        void showWaiting(roundId, seat);
      });
      root.querySelector<HTMLButtonElement>('#vi-wait-leave')!.addEventListener('click', () => {
        waiting.innerHTML = '';
        void refresh();
      });
      const startButton = root.querySelector<HTMLButtonElement>('#vi-start-room');
      if (startButton !== null) {
        startButton.addEventListener('click', async () => {
          startButton.disabled = true;
          try {
            await client.startRoom(roundId);
            await showWaiting(roundId, seat);
          } catch (cause) {
            fail(cause, {
              403: 'Only the master can start the room.',
              404: 'Room not found — it may have expired.',
            });
          } finally {
            startButton.disabled = false;
          }
        });
      }
    } catch (cause) {
      error.textContent = friendlyError(cause, {}, 'Could not load the room — is the server running?');
    }
  };

  root.querySelector<HTMLButtonElement>('#vi-refresh')!.addEventListener('click', () => {
    void refresh();
  });

  root.querySelector<HTMLButtonElement>('#vi-logout')!.addEventListener('click', () => {
    window.location.reload();
  });

  root.querySelector<HTMLButtonElement>('#vi-solo')!.addEventListener('click', () => {
    waiting.innerHTML = '';
    dialog.innerHTML = `
      <div class="vi-dialog">
        <h3>Single-player session</h3>
        ${configFields('vi-solo')}
        <button id="vi-solo-start">Start session</button>
      </div>`;
    root.querySelector<HTMLButtonElement>('#vi-solo-start')!.addEventListener('click', async (event) => {
      const button = event.currentTarget as HTMLButtonElement;
      button.disabled = true;
      try {
        const round = await client.createRound(readConfig('vi-solo'));
        handlers.onStarted(round, 'human');
      } catch (cause) {
        fail(cause, { 400: 'Could not start the session — check the settings.' });
        button.disabled = false;
      }
    });
  });

  root.querySelector<HTMLButtonElement>('#vi-create')!.addEventListener('click', () => {
    waiting.innerHTML = '';
    dialog.innerHTML = `
      <div class="vi-dialog">
        <h3>Create room — you are master, 6 seats</h3>
        ${configFields('vi-room')}
        <button id="vi-room-create">Create room</button>
      </div>`;
    root.querySelector<HTMLButtonElement>('#vi-room-create')!.addEventListener('click', async (event) => {
      const button = event.currentTarget as HTMLButtonElement;
      button.disabled = true;
      try {
        const room = await client.createRoom(readConfig('vi-room'));
        dialog.innerHTML = '';
        await showWaiting(room.roundId, 'human');
      } catch (cause) {
        fail(cause, {
          400: 'Could not create the room — check the settings.',
          403: 'Room creation refused for this account.',
        });
      } finally {
        button.disabled = false;
      }
    });
  });

  void refresh();
}
