/**
 * Rival dossiers (WST98-inspired): the five leaderboard rivals each get a
 * short fictional bio plus their trading signature, shown in a mini popup
 * when the player clicks the rival's name. Bios match the documented
 * archetypes in docs/bots.md — including the personality layer
 * (dispersion, trust tiers, delays) added by the rivals-news change.
 */
export interface RivalProfile {
  name: string;
  role: string;
  signature: string;
  blurb: string;
  tells: string;
}

const PROFILES: Record<string, RivalProfile> = {
  MARK: {
    name: 'MARK',
    role: 'Market maker',
    signature: 'Two-sided quotes',
    blurb: 'Posts two-sided quotes around fair value on every name, widening '
      + 'the spread when volatility rises and skewing against inventory. '
      + 'Never chases a trend — the house that fades everyone, a fraction at a time.',
    tells: 'Always quoted · never chases',
  },
  TIDE: {
    name: 'TIDE',
    role: 'Momentum',
    signature: 'Buys strength, sells weakness',
    blurb: 'Buys strength and sells weakness, amplifying whatever the tape is '
      + 'already doing. Catches every wave — and most of the wipeouts.',
    tells: 'Rides trends · late to turns',
  },
  LEDGER: {
    name: 'LEDGER',
    role: 'Value',
    signature: 'Buys cheap, sells rich',
    blurb: 'Buys below fair value and sells above it, dragging prices back '
      + 'toward fundamentals. Patient to a fault — early on every turn.',
    tells: 'Anchored to fair value · fades extremes',
  },
  BLITZ: {
    name: 'BLITZ',
    role: 'Scalper',
    signature: 'Fades the last wiggle',
    blurb: 'Sells the pop, buys the dip, flat by the bell. Lives on spread '
      + 'and speed — and dies on trends.',
    tells: 'Mean-reverts · hates breakouts',
  },
  VULTURE: {
    name: 'VULTURE',
    role: 'News hunter',
    signature: 'Pounces on headlines',
    blurb: 'Pounces on headlines the same tick they print, trading in the '
      + 'direction of the shock. Reads wire, patron and spy alike through a '
      + '5% misperception haze — the fastest gun in the room, wrong on the margins.',
    tells: 'First to headlines · trusts every tier · misreads by 5%',
  },
};

const FALLBACK_ROLE = 'Rival trader';

/** The dossier for a leaderboard display name, or a generic card for unknowns. */
export function rivalProfile(displayName: string): RivalProfile {
  const found = PROFILES[displayName.toUpperCase()];
  if (found !== undefined) {
    return found;
  }
  return {
    name: displayName,
    role: FALLBACK_ROLE,
    signature: 'Undisclosed style',
    blurb: 'A new face at the table. No dossier yet — watch the tape and find out.',
    tells: 'Unknown',
  };
}
