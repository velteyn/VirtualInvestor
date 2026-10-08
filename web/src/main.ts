/**
 * VirtualInvestor terminal entry point: launch pad → live terminal. The launch
 * pad creates a round on the server; the terminal then mounts openalgo-charts
 * over the server's data, order and news feeds.
 */
import './styles.css';
import { ServerClient } from './adapters/serverClient';
import { mountLaunchPad } from './app/launchPad';
import { mountTerminal } from './app/terminal';

const app = document.getElementById('app');
if (app === null) {
  throw new Error('VirtualInvestor: #app container not found');
}

const client = new ServerClient();

mountLaunchPad(app, client, {
  onStarted(round, participant) {
    app.innerHTML = '';
    const terminalRoot = document.createElement('div');
    terminalRoot.className = 'vi-terminal';
    app.appendChild(terminalRoot);
    mountTerminal(terminalRoot, client, round, participant);
  },
});
