/** The icons Sauti uses, drawn inline so the app loads nothing from other sites. */
const PATHS = {
  activity: 'M3 12h4l3-8 4 16 3-8h4',
  card: 'M3 6.5A1.5 1.5 0 0 1 4.5 5h15A1.5 1.5 0 0 1 21 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5zM3 10h18',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  clock: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v5l3.5 2',
  desk: 'M3 9h18M5 9v10M19 9v10M8 5h8v4H8z',
  grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  logout: 'M15 4h3.5A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5H15M10 8l-4 4 4 4M6 12h10',
  mic: 'M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3',
  monitor:
    'M3 5.5A1.5 1.5 0 0 1 4.5 4h15A1.5 1.5 0 0 1 21 5.5v10a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 15.5zM9 21h6M12 17v4',
  plus: 'M12 5v14M5 12h14',
  shield: 'M12 3l7.5 3v5.5c0 4.6-3.1 8-7.5 9.5-4.4-1.5-7.5-4.9-7.5-9.5V6z',
  stethoscope:
    'M6 3H4.5v6a5 5 0 0 0 10 0V3H13M9.5 14v2.5a4.5 4.5 0 0 0 9 0V14M18.5 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  users:
    'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2.5 21v-1.5A5.5 5.5 0 0 1 8 14h2a5.5 5.5 0 0 1 5.5 5.5V21M16 3.3a4 4 0 0 1 0 7.4M18.5 14.3a5.5 5.5 0 0 1 3 4.9V21',
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({ name, size = 18 }: { name: IconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={PATHS[name]} />
    </svg>
  );
}

/** The Sauti mark and name. */
export function BrandMark() {
  return (
    <span className="brand-mark">
      <Icon name="mic" />
    </span>
  );
}
