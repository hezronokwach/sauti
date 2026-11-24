import { Icon } from './Icon';
import type { IconName } from './Icon';

interface StatCardProps {
  label: string;
  value: string | number;
  icon: IconName;
  /** The solid blue card that leads a row. */
  featured?: boolean;
  /** Shows the value in red, for something that needs attention. */
  alert?: boolean;
}

/** One number with its label. The label always says what the colour means. */
export function StatCard({ label, value, icon, featured = false, alert = false }: StatCardProps) {
  const classes = ['stat-card', featured ? 'featured' : '', alert ? 'alert' : '']
    .filter(Boolean)
    .join(' ');
  return (
    <div className={classes}>
      <span className="stat-icon">
        <Icon name={icon} size={22} />
      </span>
      <div>
        <span className="stat-value">{value}</span>
        <span className="stat-label">{label}</span>
      </div>
    </div>
  );
}
