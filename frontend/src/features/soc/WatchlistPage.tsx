import { useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { Button, Drawer, Input, Select, useToast } from '@/shared/ui';
import { useT } from '@/shared/i18n/I18nProvider';
import { useBlacklist, useAddBlacklist, type BlacklistEntry } from './watchlistApi';

const ADD_TYPES = ['phone', 'domain', 'url', 'email', 'ip'] as const;

/**
 * WatchlistPage — suspicious accounts / numbers / domains watchlist (`/soc/watchlist`).
 *
 * Quick-check bar + filter tabs + table (value / type / source / delete).
 *
 * Data comes from the live backend via `useBlacklist()` (`GET /blacklist`,
 * `BlacklistEntryDto`). Loading/error/empty states handled below.
 */

type WlType = 'bank' | 'phone' | 'domain';

interface WatchEntry {
  id: string;
  value: string;
  valueMuted: string;
  type: WlType;
  typeLabel: string;
  source: string;
  /** highlight the value in red (e.g. fraud domain) */
  danger?: boolean;
}

const TYPE_LABELS: Record<WlType, { typeLabel: string; valueMuted: string }> = {
  bank: { typeLabel: 'Tài khoản', valueMuted: 'Tài khoản ngân hàng' },
  phone: { typeLabel: 'SĐT', valueMuted: 'Số điện thoại' },
  domain: { typeLabel: 'Domain', valueMuted: 'Domain / URL' },
};

/**
 * Coerce the BE `type` (lower-case, e.g. "url"/"phone"/"domain"/"bank"/"account")
 * onto one of the three watchlist buckets used by the UI.
 */
function normalizeType(raw: string | null | undefined): WlType {
  const t = (raw ?? '').toLowerCase();
  if (t.includes('phone') || t.includes('sms') || t.includes('sđt')) return 'phone';
  if (t.includes('bank') || t.includes('account') || t.includes('iban')) return 'bank';
  // url / domain / link / everything else → treated as domain
  return 'domain';
}

/** Map a backend `BlacklistEntryDto` onto the table view model. */
function toEntry(dto: BlacklistEntry): WatchEntry {
  const type = normalizeType(dto.type);
  const meta = TYPE_LABELS[type];
  return {
    id: dto.id,
    value: dto.value ?? '—',
    valueMuted: meta.valueMuted,
    type,
    typeLabel: meta.typeLabel,
    source: dto.source ?? '—',
    danger: type === 'domain',
  };
}

const GRID = '1fr 110px 160px 70px';
type Filter = 'all' | WlType;

export default function WatchlistPage() {
  const t = useT();
  const toast = useToast();
  const [filter, setFilter] = useState<Filter>('all');
  const { data, isLoading, isError, refetch } = useBlacklist();
  const addMut = useAddBlacklist();

  const entries = useMemo<WatchEntry[]>(() => (data ?? []).map(toEntry), [data]);

  // Quick check: look the value up in the loaded blacklist (client-side).
  const [query, setQuery] = useState('');
  const runCheck = () => {
    const q = query.trim().toLowerCase();
    if (!q) return;
    const hit = (data ?? []).find((e) => (e.value ?? '').toLowerCase().includes(q));
    if (hit) {
      toast({ msg: t('⚠ Có trong watchlist: {value} (nguồn {src})', { value: hit.value ?? q, src: hit.source ?? '—' }), variant: 'warning' });
    } else {
      toast({ msg: t('✓ Không có trong watchlist: {value}', { value: query.trim() }), variant: 'success' });
    }
  };

  // Add-manual drawer.
  const [addOpen, setAddOpen] = useState(false);
  const [addType, setAddType] = useState<string>('phone');
  const [addValue, setAddValue] = useState('');
  const [addSource, setAddSource] = useState('Thủ công');
  const submitAdd = () => {
    const value = addValue.trim();
    if (!value) {
      toast({ msg: t('Vui lòng nhập giá trị.'), variant: 'warning' });
      return;
    }
    addMut.mutate(
      { type: addType, value, source: addSource.trim() || 'Thủ công' },
      {
        onSuccess: () => {
          toast({ msg: t('Đã thêm vào watchlist.'), variant: 'success' });
          setAddOpen(false);
          setAddValue('');
        },
        onError: () => toast({ msg: t('Thêm thất bại, thử lại.'), variant: 'error' }),
      },
    );
  };

  const counts = useMemo(
    () => ({
      all: entries.length,
      bank: entries.filter((e) => e.type === 'bank').length,
      phone: entries.filter((e) => e.type === 'phone').length,
      domain: entries.filter((e) => e.type === 'domain').length,
    }),
    [entries],
  );

  const visible = useMemo(
    () => (filter === 'all' ? entries : entries.filter((e) => e.type === filter)),
    [entries, filter],
  );

  const filters: { id: Filter; label: string }[] = [
    { id: 'all', label: t('Tất cả ({n})', { n: counts.all }) },
    { id: 'bank', label: t('Tài khoản ngân hàng ({n})', { n: counts.bank }) },
    { id: 'phone', label: t('Số điện thoại ({n})', { n: counts.phone }) },
    { id: 'domain', label: t('Domain ({n})', { n: counts.domain }) },
  ];

  return (
    <div style={{ animation: 'fadeUp .3s ease' }}>
      <header
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            marginBottom: 20,
          }}
        >
          <div>
            <h1 style={pageTitle}>{t('Watchlist tài khoản nghi ngờ')}</h1>
            <p style={{ fontSize: 13, color: 'var(--muted)', marginTop: 4 }}>
              {t('Theo dõi số tài khoản, SĐT và domain nghi ngờ từ nhiều nguồn')}
            </p>
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <Button
              type="button"
              variant="secondary"
              onClick={() =>
                toast({ msg: t('Đồng bộ NCSC cần tích hợp nguồn dữ liệu NCSC (chưa khả dụng).'), variant: 'info' })
              }
            >
              {t('Đồng bộ NCSC')}
            </Button>
            <Button type="button" variant="primary" onClick={() => setAddOpen(true)}>
              {t('+ Thêm thủ công')}
            </Button>
          </div>
        </header>

        {/* Quick check */}
        <section
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 12,
            padding: 20,
            marginBottom: 14,
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: 'var(--text)', margin: '0 0 12px' }}>
            {t('Tra cứu nhanh · Quick Check')}
          </h2>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              runCheck();
            }}
            style={{ display: 'flex', gap: 10 }}
          >
            <div
              style={{
                flex: 1,
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                background: 'var(--bg)',
                border: '1px solid var(--input-bdr, #C0CBDC)',
                borderRadius: 8,
                padding: '10px 14px',
              }}
            >
              <Search size={14} color="var(--muted)" aria-hidden="true" />
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label={t('Tra cứu watchlist')}
                placeholder={t('Nhập số tài khoản, SĐT, hoặc domain...')}
                style={{
                  flex: 1,
                  border: 'none',
                  background: 'transparent',
                  outline: 'none',
                  fontSize: 13.5,
                  color: 'var(--text)',
                  fontFamily: 'inherit',
                }}
              />
            </div>
            <Button type="submit" variant="primary">
              {t('Kiểm tra')}
            </Button>
          </form>
        </section>

        {/* Table */}
        <div
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 12,
            overflow: 'hidden',
          }}
        >
          {/* Filters */}
          <div
            role="tablist"
            aria-label={t('Lọc watchlist')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '12px 16px',
              borderBottom: '1px solid var(--border)',
              flexWrap: 'wrap',
            }}
          >
            {filters.map((f) => {
              const active = filter === f.id;
              return (
                <button
                  key={f.id}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => setFilter(f.id)}
                  style={{
                    fontSize: 12.5,
                    fontWeight: active ? 600 : 400,
                    borderRadius: 7,
                    padding: '5px 12px',
                    cursor: 'pointer',
                    color: active ? '#1A4FD0' : 'var(--muted)',
                    background: active ? 'rgba(37,102,235,.08)' : 'var(--bg)',
                    border: active ? '1px solid rgba(37,102,235,.2)' : '1px solid var(--border)',
                  }}
                >
                  {f.label}
                </button>
              );
            })}
          </div>

          {/* Column headers */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: GRID,
              padding: '10px 16px',
              borderBottom: '1px solid var(--border)',
              background: 'var(--bg)',
            }}
          >
            {['Giá trị', 'Loại', 'Nguồn'].map((h) => (
              <div key={h} style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--muted)' }}>
                {t(h)}
              </div>
            ))}
            <div />
          </div>

          {/* Loading / error / empty states */}
          {isLoading && <TableMessage>{t('Đang tải watchlist…')}</TableMessage>}
          {!isLoading && isError && (
            <TableMessage>
              <span style={{ color: 'var(--red)', fontWeight: 600 }}>
                {t('Không tải được watchlist.')}{' '}
              </span>
              <button
                type="button"
                onClick={() => refetch()}
                style={{ all: 'unset', color: 'var(--blue)', cursor: 'pointer', fontWeight: 600 }}
              >
                {t('Thử lại')}
              </button>
            </TableMessage>
          )}
          {!isLoading && !isError && visible.length === 0 && (
            <TableMessage>{t('Không có mục nào trong watchlist.')}</TableMessage>
          )}

          {/* Rows */}
          {!isLoading &&
            !isError &&
            visible.map((e, idx) => {
              const last = idx === visible.length - 1;
              return (
                <div
                  key={e.id}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: GRID,
                    padding: '13px 16px',
                    borderBottom: last ? 'none' : '1px solid var(--border)',
                    alignItems: 'center',
                  }}
                >
                  <div>
                    <div
                      style={{
                        fontFamily: "'JetBrains Mono', monospace",
                        fontSize: 13,
                        fontWeight: 500,
                        color: e.danger ? 'var(--red)' : 'var(--text)',
                      }}
                    >
                      {e.value}
                    </div>
                    <div style={{ fontSize: 11.5, color: 'var(--muted)' }}>{t(e.valueMuted)}</div>
                  </div>
                  <div style={{ fontSize: 12.5, color: 'var(--muted)' }}>{t(e.typeLabel)}</div>
                  <div style={{ fontSize: 12, color: 'var(--muted)' }}>{e.source}</div>
                  <button
                    type="button"
                    onClick={() => toast(t('Đã xóa {value}', { value: e.value }))}
                    style={{
                      all: 'unset',
                      fontSize: 12,
                      color: 'var(--red)',
                      cursor: 'pointer',
                    }}
                  >
                    {t('Xóa')}
                  </button>
                </div>
              );
            })}
        </div>

      <Drawer open={addOpen} onClose={() => setAddOpen(false)} title={t('Thêm vào watchlist')}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 20 }}>
          <Select label={t('Loại')} value={addType} onChange={(e) => setAddType(e.target.value)}>
            {ADD_TYPES.map((ty) => (
              <option key={ty} value={ty}>
                {ty}
              </option>
            ))}
          </Select>
          <Input
            label={t('Giá trị')}
            value={addValue}
            onChange={(e) => setAddValue(e.target.value)}
            placeholder={t('SĐT / domain / URL / email / IP')}
          />
          <Input
            label={t('Nguồn')}
            value={addSource}
            onChange={(e) => setAddSource(e.target.value)}
            placeholder={t('Thủ công')}
          />
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 8 }}>
            <Button variant="outline" onClick={() => setAddOpen(false)} disabled={addMut.isPending}>
              {t('Hủy')}
            </Button>
            <Button variant="primary" onClick={submitAdd} disabled={addMut.isPending}>
              {addMut.isPending ? t('Đang thêm…') : t('Thêm')}
            </Button>
          </div>
        </div>
      </Drawer>
    </div>
  );
}

const pageTitle: React.CSSProperties = {
  fontFamily: "'Space Grotesk', system-ui",
  fontSize: 22,
  fontWeight: 700,
  color: 'var(--text)',
  letterSpacing: '-0.02em',
  margin: 0,
};

function TableMessage({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        padding: '28px 16px',
        textAlign: 'center',
        fontSize: 13,
        color: 'var(--muted)',
      }}
    >
      {children}
    </div>
  );
}
