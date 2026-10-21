import { describe, expect, it } from 'vitest';
import { rivalProfile } from './rivals';

describe('rivalProfile', () => {
  it('returns the dossier for a known rival', () => {
    const vulture = rivalProfile('VULTURE');
    expect(vulture.role).toBe('News hunter');
    expect(vulture.blurb.length).toBeGreaterThan(20);
  });

  it('matches case-insensitively and falls back for unknowns', () => {
    expect(rivalProfile('mark').name).toBe('MARK');
    const unknown = rivalProfile('Somebody New');
    expect(unknown.name).toBe('Somebody New');
    expect(unknown.blurb.length).toBeGreaterThan(0);
  });
});
