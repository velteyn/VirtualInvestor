/**
 * The launch pad: the screen a player sees before a session. It collects the
 * starting capital, session length and realism preset, creates the round on the
 * server, and hands the resulting round to the terminal.
 */
import type { RoundConfig, RoundResponse, ServerClient } from '../adapters/serverClient';

const PRESETS: Array<RoundConfig['preset']> = ['ARCADE', 'REALISTIC', 'HARDCORE'];
const CAPITALS = [25_000, 50_000, 100_000, 250_000, 1_000_000];
const DURATIONS = [30, 60, 120];

export interface LaunchPadHandlers {
  onStarted(round: RoundResponse): void;
}

export function mountLaunchPad(
  root: HTMLElement,
  client: ServerClient,
  handlers: LaunchPadHandlers,
): void {
  root.innerHTML = `
    <div class="vi-launch">
      <h1>VirtualInvestor</h1>
      <p class="vi-sub">Configure your session, then trade the market against five rivals.</p>
      <label>Starting capital
        <select id="vi-capital">
          ${CAPITALS.map((value) => `<option value="${value}"${value === 100_000 ? ' selected' : ''}>$${value.toLocaleString()}</option>`).join('')}
        </select>
      </label>
      <label>Session length
        <select id="vi-duration">
          ${DURATIONS.map((value) => `<option value="${value}"${value === 30 ? ' selected' : ''}>${value} minutes</option>`).join('')}
        </select>
      </label>
      <label>Realism
        <select id="vi-preset">
          ${PRESETS.map((value) => `<option value="${value}"${value === 'REALISTIC' ? ' selected' : ''}>${value}</option>`).join('')}
        </select>
      </label>
      <button id="vi-start">Start session</button>
      <p id="vi-error" class="vi-error" role="alert"></p>
    </div>`;

  const capital = root.querySelector<HTMLSelectElement>('#vi-capital')!;
  const duration = root.querySelector<HTMLSelectElement>('#vi-duration')!;
  const preset = root.querySelector<HTMLSelectElement>('#vi-preset')!;
  const start = root.querySelector<HTMLButtonElement>('#vi-start')!;
  const error = root.querySelector<HTMLParagraphElement>('#vi-error')!;

  start.addEventListener('click', async () => {
    start.disabled = true;
    error.textContent = '';
    try {
      const round = await client.createRound({
        startingCapital: Number(capital.value),
        durationMinutes: Number(duration.value),
        preset: preset.value as RoundConfig['preset'],
      });
      handlers.onStarted(round);
    } catch (cause) {
      error.textContent = cause instanceof Error ? cause.message : 'could not start the session';
    } finally {
      start.disabled = false;
    }
  });
}
