import { describe, expect, it } from 'vitest';
import { secretInGroups } from './otpauth';
import { safeNextPath } from './roles';

describe('safeNextPath', () => {
  it('keeps a path inside the app, with its query', () => {
    expect(safeNextPath('/doctor')).toBe('/doctor');
    expect(safeNextPath('/admin?tab=staff')).toBe('/admin?tab=staff');
  });

  it.each([null, '', 'doctor', 'https://evil.example/', '//evil.example/', '/\\evil.example'])(
    'drops %s',
    (next) => {
      expect(safeNextPath(next)).toBeNull();
    },
  );
});

describe('secretInGroups', () => {
  it('splits the key into groups of four', () => {
    expect(
      secretInGroups('otpauth://totp/Sauti%3Aa%40b.test?secret=JBSWY3DPEHPK3PXPQ&issuer=Sauti'),
    ).toBe('JBSW Y3DP EHPK 3PXP Q');
  });
});
