import { describe, expect, it } from 'vitest';
import { friendlyError, socketErrorLabel } from './errors';

describe('friendlyError', () => {
  it('maps login 401 to wrong-password label', () => {
    const cause = new Error('POST /api/players/login failed (401): {...}');
    expect(friendlyError(cause, { 401: 'Wrong username or password.' }))
      .toBe('Wrong username or password.');
  });

  it('falls back to generic labels without overrides', () => {
    expect(friendlyError(new Error('POST /x failed (401): {}')))
      .toBe('Not authorized — check the credentials.');
    expect(friendlyError(new Error('POST /x failed (500): {}'), {}, 'Down for now.'))
      .toBe('Down for now.');
  });

  it('detects network failures', () => {
    expect(friendlyError(new TypeError('fetch failed'))).toBe('Network error — is the server running?');
  });

  it('passes local validation messages through', () => {
    expect(friendlyError(new Error('passwords do not match'))).toBe('passwords do not match');
  });

  it('handles non-errors', () => {
    expect(friendlyError('boom')).toBe('Request failed — try again.');
  });
});

describe('socketErrorLabel', () => {
  it('maps session codes to labels', () => {
    expect(socketErrorLabel({ code: 'not-approved' })).toBe('Account not approved yet — wait for the game master.');
    expect(socketErrorLabel({ code: 'not_joined' })).toBe('Not joined to this round — rejoining…');
  });

  it('keeps server rejection reasons', () => {
    expect(socketErrorLabel({ code: 'rejected', message: 'market is not in continuous trading' }))
      .toBe('market is not in continuous trading');
  });

  it('falls back cleanly', () => {
    expect(socketErrorLabel({})).toBe('Request failed — try again.');
  });
});
