/** The secret from an otpauth:// link, split into groups of four for typing in by hand. */
export function secretInGroups(otpauthUri: string): string {
  const secret = new URL(otpauthUri).searchParams.get('secret') ?? '';
  return secret.match(/.{1,4}/g)?.join(' ') ?? '';
}
