import {
  LayoutGrid,
  Triangle,
  Users,
  ClipboardCheck,
  PenSquare,
  FileText,
  Settings,
  Award,
  CircleCheckBig,
  Home,
  BookOpen,
  HelpCircle,
  Inbox,
  Bell,
  Eye,
  Monitor,
  Layers,
  type LucideIcon,
} from 'lucide-react';

/** Maps NavItem.icon string keys to Lucide components (15px in the sidebar). */
const ICONS: Record<string, LucideIcon> = {
  grid: LayoutGrid,
  triangle: Triangle,
  users: Users,
  'clipboard-check': ClipboardCheck,
  'pen-square': PenSquare,
  'file-text': FileText,
  settings: Settings,
  award: Award,
  'circle-check': CircleCheckBig,
  home: Home,
  'book-open': BookOpen,
  'help-circle': HelpCircle,
  inbox: Inbox,
  bell: Bell,
  eye: Eye,
  monitor: Monitor,
  layers: Layers,
};

export function NavIcon({ name, size = 15 }: { name: string; size?: number }) {
  const Icon = ICONS[name] ?? LayoutGrid;
  return <Icon size={size} strokeWidth={2} />;
}
