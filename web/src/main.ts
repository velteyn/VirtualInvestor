import { createChart, generateBars } from 'openalgo-charts';

const container = document.getElementById('app');
if (container === null) {
  throw new Error('VirtualInvestor: #app container not found');
}

container.style.width = '100%';
container.style.height = '100vh';

const chart = createChart(container);
chart.addSeries('candlestick').setData(generateBars(1_700_000_000, 200, 3600));
chart.fitContent();
