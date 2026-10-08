# Tasks

## 1. Ticket component (web)

- [x] 1.1 Build the ticket in the right column below news: side toggle, type selector with type-gated price fields, quantity input; verify `npm run build`.
- [x] 1.2 Add sizing memory, lot buttons and Max from buying power; verify a build plus a scripted check that reopening restores the last size.
- [x] 1.3 Prefill limit/stop from the touch and follow it until edited; verify against live depth frames in the browser.
- [x] 1.4 Show fee preview with buying-power guard disabling Transmit with reason; verify preview math matches the server formula on a known bank.

## 2. Transmit path (web + edge)

- [x] 2.1 Rewire `onOrder` (menu, Buy/Sell buttons) to prefill the ticket without sending; verify right-click fills side/type/price and sends nothing.
- [x] 2.2 Thread rejection `reason` onto the order frame at the broadcaster mapping and display it inline keeping typed values; verify a grid-violating order shows the reason.
- [x] 2.3 Style the ticket in the terminal theme with tabular preview alignment; verify on desktop and phone widths.

## 3. Verification and docs

- [x] 3.1 End-to-end over a live server: size, preview, transmit, modify via drag, cancel; verify each step lands server-side in order.
- [x] 3.2 Document the ticket in `docs/terminal.md`; verify the documented order flow reproduces from a fresh login.
