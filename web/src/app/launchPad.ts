/**
 * The launch pad: the screen a player sees before a session. Approved players log
 * in first (new players register with a bank and wait for game-master approval),
 * then land in the lobby: live games, single-player start, room creation.
 */
import type { BankResponse, RoundResponse, ServerClient } from '../adapters/serverClient';
import { friendlyError } from './errors';
import { mountLobby } from './lobby';

export interface LaunchPadHandlers {
  onStarted(round: RoundResponse, participant: string): void;
}

export function mountLaunchPad(
  root: HTMLElement,
  client: ServerClient,
  handlers: LaunchPadHandlers,
): void {
  root.innerHTML = `
    <div class="vi-launch">
      <h1 class="vi-brand">Virtual Inve$tor<span class="vi-tm">tm</span></h1>
      <p class="vi-sub">Log in to trade the market against five rivals.</p>
      <div class="vi-auth">
        <label>Player <input id="vi-user" autocomplete="username" /></label>
        <label>Password <input id="vi-pass" type="password" autocomplete="current-password" /></label>
        <div class="vi-auth-row">
          <button id="vi-login">Log in</button>
          <button id="vi-show-register">Register</button>
        </div>
        <div id="vi-register" hidden>
          <label>Bank
            <select id="vi-bank"><option>loading banks…</option></select>
          </label>
          <button id="vi-do-register">Create account</button>
        </div>
      </div>
      <p id="vi-auth-note" class="vi-sub"></p>
      <div id="vi-change" hidden>
        <p class="vi-sub">Choose a new password to continue.</p>
        <label>New password <input id="vi-new-pass" type="password" autocomplete="new-password" /></label>
        <label>Confirm <input id="vi-confirm-pass" type="password" autocomplete="new-password" /></label>
        <button id="vi-do-change">Set password</button>
      </div>
      <p id="vi-error" class="vi-error" role="alert"></p>
    </div>`;

  const user = root.querySelector<HTMLInputElement>('#vi-user')!;
  const pass = root.querySelector<HTMLInputElement>('#vi-pass')!;
  const login = root.querySelector<HTMLButtonElement>('#vi-login')!;
  const showRegister = root.querySelector<HTMLButtonElement>('#vi-show-register')!;
  const registerBox = root.querySelector<HTMLDivElement>('#vi-register')!;
  const bank = root.querySelector<HTMLSelectElement>('#vi-bank')!;
  const doRegister = root.querySelector<HTMLButtonElement>('#vi-do-register')!;
  const note = root.querySelector<HTMLParagraphElement>('#vi-auth-note')!;
  const error = root.querySelector<HTMLParagraphElement>('#vi-error')!;

  const fail = (cause: unknown, labels: Record<number, string> = {}): void => {
    error.textContent = friendlyError(cause, labels);
  };

  /** Logged in: the login page goes away entirely, the lobby owns the page. */
  const enterLobby = (username: string): void => {
    root.innerHTML = '';
    mountLobby(root, client, handlers, { username });
  };

  const changeBox = root.querySelector<HTMLDivElement>('#vi-change')!;
  const newPass = root.querySelector<HTMLInputElement>('#vi-new-pass')!;
  const confirmPass = root.querySelector<HTMLInputElement>('#vi-confirm-pass')!;
  const doChange = root.querySelector<HTMLButtonElement>('#vi-do-change')!;
  let lastUsername = '';
  let lastPassword = '';

  login.addEventListener('click', async () => {
    login.disabled = true;
    error.textContent = '';
    try {
      const response = await client.login(user.value, pass.value);
      lastUsername = user.value;
      lastPassword = pass.value;
      pass.value = '';
      if (response.mustChangePassword) {
        note.textContent = `Welcome ${response.username} — set a password to continue.`;
        changeBox.hidden = false;
      } else {
        enterLobby(response.username);
      }
    } catch (cause) {
      fail(cause, {
        401: 'Wrong username or password — check the name and try again.',
        403: 'Account pending game-master approval — try again once approved.',
      });
    } finally {
      login.disabled = false;
    }
  });

  doChange.addEventListener('click', async () => {
    doChange.disabled = true;
    error.textContent = '';
    try {
      if (newPass.value === '' || newPass.value !== confirmPass.value) {
        throw new Error('passwords do not match');
      }
      await client.changePassword(lastUsername, lastPassword, newPass.value);
      lastPassword = '';
      newPass.value = '';
      confirmPass.value = '';
      changeBox.hidden = true;
      enterLobby(lastUsername);
    } catch (cause) {
      fail(cause, { 401: 'Current password is wrong.' });
    } finally {
      doChange.disabled = false;
    }
  });

  showRegister.addEventListener('click', async () => {
    registerBox.hidden = !registerBox.hidden;
    if (!registerBox.hidden && bank.options.length <= 1) {
      try {
        const banks: BankResponse[] = await client.banks();
        bank.innerHTML = banks
          .map((entry) => `<option value="${entry.bankId}">${entry.displayName} — ${entry.feePerShare}/share, min $${entry.ticketMinimum}, deriv ${(entry.seriesRate * 100).toFixed(2)}% min $${entry.seriesMinimum}</option>`)
          .join('');
      } catch (cause) {
        error.textContent = friendlyError(cause, {}, 'Could not load banks — is the server running?');
      }
    }
  });

  doRegister.addEventListener('click', async () => {
    doRegister.disabled = true;
    error.textContent = '';
    try {
      await client.register(user.value, pass.value, bank.value);
      note.textContent = 'Account created — waiting for game-master approval. Log in once approved.';
    } catch (cause) {
      fail(cause, {
        400: 'Could not create the account — check the name, password and bank.',
        409: 'That player name is taken — pick another.',
      });
    } finally {
      doRegister.disabled = false;
    }
  });
}
