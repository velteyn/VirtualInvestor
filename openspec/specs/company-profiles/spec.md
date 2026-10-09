# company-profiles Specification

## Purpose

Give every instrument an identity: a readable profile with its fundamentals, so players research names before taking positions.

## Requirements

### Requirement: Per-instrument profiles

The system SHALL provide one profile per instrument: a short fictional
description plus its static fundamentals (sector, reference price, betas,
volatility, shares outstanding).

#### Scenario: Every ticker has a story

- **WHEN** a player opens any of the 21 instruments
- **THEN** a description and the full fundamentals row are shown, with no real company names anywhere

#### Scenario: Profiles match the market

- **WHEN** a profile names a sector, price, or business
- **THEN** they agree with the instrument's universe row (sector, reference price) and its news headlines

### Requirement: Company tab

The terminal SHALL offer a company tab with a symbol selector showing the
chosen instrument's profile and fundamentals.

#### Scenario: Research before trading

- **WHEN** a player picks PEAR in the company tab
- **THEN** Pear Computers' description, sector, reference price, and risk figures appear without leaving the terminal
