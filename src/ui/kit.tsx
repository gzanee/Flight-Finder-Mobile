/** Small building blocks shared by the screens. */
import DateTimePicker, { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useState } from 'react';
import {
  ActivityIndicator,
  Platform,
  Pressable,
  type StyleProp,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  type TextInputProps,
  View,
  type ViewStyle,
} from 'react-native';
import { longDay } from '../app-core/format';
import { type Colors, radius, space, useColors } from './theme';

export function tap() {
  Haptics.selectionAsync().catch(() => undefined);
}

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const c = useColors();
  return <View style={[{ backgroundColor: c.card, borderRadius: radius.l, padding: space(4), borderWidth: StyleSheet.hairlineWidth, borderColor: c.border }, style]}>{children}</View>;
}

export function Label({ children }: { children: ReactNode }) {
  const c = useColors();
  return <Text style={{ color: c.muted, fontSize: 13, fontWeight: '600', marginBottom: space(1.5) }}>{children}</Text>;
}

export function Hint({ children, tone = 'muted' }: { children: ReactNode; tone?: 'muted' | 'warn' | 'bad' | 'good' }) {
  const c = useColors();
  const color = { muted: c.muted, warn: c.warn, bad: c.bad, good: c.good }[tone];
  return <Text style={{ color, fontSize: 13, lineHeight: 18 }}>{children}</Text>;
}

export function Notice({ children, tone = 'warn' }: { children: ReactNode; tone?: 'warn' | 'bad' | 'good' | 'info' }) {
  const c = useColors();
  const [bg, fg] = {
    warn: [c.warnSoft, c.warn],
    bad: [c.badSoft, c.bad],
    good: [c.goodSoft, c.good],
    info: [c.primarySoft, c.primary],
  }[tone];
  return (
    <View style={{ backgroundColor: bg, borderRadius: radius.m, padding: space(3) }}>
      <Text style={{ color: fg, fontSize: 14, lineHeight: 20 }}>{children}</Text>
    </View>
  );
}

export function Button({
  title,
  onPress,
  kind = 'primary',
  disabled,
  loading,
  small,
  style,
}: {
  title: string;
  onPress: () => void;
  kind?: 'primary' | 'secondary' | 'ghost' | 'danger';
  disabled?: boolean;
  loading?: boolean;
  small?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const c = useColors();
  const bg = { primary: c.primary, secondary: c.primarySoft, ghost: 'transparent', danger: c.badSoft }[kind];
  const fg = { primary: c.primaryText, secondary: c.primary, ghost: c.primary, danger: c.bad }[kind];
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled || loading}
      onPress={() => {
        tap();
        onPress();
      }}
      style={({ pressed }) => [
        {
          backgroundColor: bg,
          borderRadius: radius.m,
          paddingVertical: small ? space(2) : space(3.5),
          paddingHorizontal: small ? space(3) : space(4),
          alignItems: 'center',
          justifyContent: 'center',
          flexDirection: 'row',
          gap: space(2),
          opacity: disabled ? 0.45 : pressed ? 0.75 : 1,
        },
        style,
      ]}
    >
      {loading ? <ActivityIndicator color={fg} /> : null}
      <Text style={{ color: fg, fontSize: small ? 14 : 16, fontWeight: '700' }}>{title}</Text>
    </Pressable>
  );
}

export function Segmented<T extends string | number>({
  options,
  value,
  onChange,
}: {
  options: [T, string][];
  value: T;
  onChange: (v: T) => void;
}) {
  const c = useColors();
  return (
    <View style={{ flexDirection: 'row', backgroundColor: c.chip, borderRadius: radius.m, padding: 3 }}>
      {options.map(([v, label]) => {
        const on = v === value;
        return (
          <Pressable
            key={String(v)}
            accessibilityRole="button"
            accessibilityState={{ selected: on }}
            onPress={() => {
              tap();
              onChange(v);
            }}
            style={{
              flex: 1,
              paddingVertical: space(2),
              borderRadius: radius.s + 2,
              backgroundColor: on ? c.card : 'transparent',
              alignItems: 'center',
            }}
          >
            <Text style={{ color: on ? c.text : c.muted, fontWeight: on ? '700' : '500', fontSize: 14 }} numberOfLines={1}>
              {label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function Chip({
  label,
  detail,
  onPress,
  onRemove,
  selected,
}: {
  label: string;
  detail?: string;
  onPress?: () => void;
  onRemove?: () => void;
  selected?: boolean;
}) {
  const c = useColors();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={onRemove ? `Rimuovi ${label}` : label}
      onPress={() => {
        tap();
        (onRemove ?? onPress)?.();
      }}
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: space(1.5),
        backgroundColor: selected ? c.primary : c.chip,
        borderRadius: 999,
        paddingVertical: space(1.5),
        paddingHorizontal: space(3),
      }}
    >
      <Text style={{ color: selected ? c.primaryText : c.text, fontWeight: '600', fontSize: 14 }}>{label}</Text>
      {detail ? <Text style={{ color: selected ? c.primaryText : c.muted, fontSize: 12, fontWeight: '700' }}>{detail}</Text> : null}
      {onRemove ? <Text style={{ color: c.muted, fontSize: 16, marginLeft: 2 }}>×</Text> : null}
    </Pressable>
  );
}

export function Stepper({
  label,
  hint,
  value,
  min,
  max,
  onChange,
}: {
  label: string;
  hint?: string;
  value: number;
  min: number;
  max: number;
  onChange: (n: number) => void;
}) {
  const c = useColors();
  const btn = (delta: number, text: string) => (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${delta > 0 ? 'Più' : 'Meno'} ${label.toLowerCase()}`}
      disabled={value + delta < min || value + delta > max}
      onPress={() => {
        tap();
        onChange(value + delta);
      }}
      style={({ pressed }) => ({
        width: 36,
        height: 36,
        borderRadius: 18,
        borderWidth: 1,
        borderColor: c.border,
        alignItems: 'center',
        justifyContent: 'center',
        opacity: value + delta < min || value + delta > max ? 0.35 : pressed ? 0.6 : 1,
      })}
    >
      <Text style={{ color: c.primary, fontSize: 20, fontWeight: '600' }}>{text}</Text>
    </Pressable>
  );
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: space(2) }}>
      <View style={{ flex: 1 }}>
        <Text style={{ color: c.text, fontSize: 16 }}>{label}</Text>
        {hint ? <Text style={{ color: c.muted, fontSize: 12 }}>{hint}</Text> : null}
      </View>
      {btn(-1, '−')}
      <Text style={{ color: c.text, fontSize: 17, fontWeight: '700', width: 36, textAlign: 'center' }}>{value}</Text>
      {btn(1, '+')}
    </View>
  );
}

export function ToggleRow({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  const c = useColors();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: space(2), gap: space(3) }}>
      <Text style={{ color: c.text, fontSize: 15, flex: 1 }}>{label}</Text>
      <Switch value={value} onValueChange={onChange} trackColor={{ true: c.primary, false: c.border }} thumbColor="#ffffff" />
    </View>
  );
}

export function Field(props: TextInputProps & { label?: string; suffix?: string }) {
  const c = useColors();
  const { label, suffix, style, ...rest } = props;
  return (
    <View style={{ flex: 1 }}>
      {label ? <Label>{label}</Label> : null}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          borderWidth: 1,
          borderColor: c.border,
          borderRadius: radius.m,
          paddingHorizontal: space(3),
          backgroundColor: c.card,
        }}
      >
        <TextInput
          placeholderTextColor={c.faint}
          style={[{ flex: 1, color: c.text, fontSize: 16, paddingVertical: space(3) }, style]}
          {...rest}
        />
        {suffix ? <Text style={{ color: c.muted, fontSize: 15 }}>{suffix}</Text> : null}
      </View>
    </View>
  );
}

const pad = (n: number) => String(n).padStart(2, '0');

function toDate(value: string, mode: 'date' | 'time'): Date {
  if (mode === 'date') return new Date(Number(value.slice(0, 4)), Number(value.slice(5, 7)) - 1, Number(value.slice(8, 10)), 12);
  const d = new Date();
  d.setHours(Number(value.slice(0, 2) || 0), Number(value.slice(3, 5) || 0), 0, 0);
  return d;
}

function fromDate(d: Date, mode: 'date' | 'time'): string {
  return mode === 'date' ? `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` : `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** A date (YYYY-MM-DD) or time (HH:MM) chosen with the system picker. */
export function PickerField({
  label,
  value,
  mode,
  onChange,
  minimum,
  placeholder,
  onClear,
}: {
  label?: string;
  value: string;
  mode: 'date' | 'time';
  onChange: (v: string) => void;
  minimum?: string;
  placeholder?: string;
  onClear?: () => void;
}) {
  const c = useColors();
  const [open, setOpen] = useState(false);
  const current = toDate(value || (mode === 'time' ? '12:00' : fromDate(new Date(), 'date')), mode);
  const minimumDate = minimum ? toDate(minimum, 'date') : undefined;
  const show = () => {
    tap();
    if (Platform.OS === 'android') {
      DateTimePickerAndroid.open({
        value: current,
        mode,
        is24Hour: true,
        minimumDate,
        onValueChange: (_e, d) => onChange(fromDate(d, mode)),
      });
    } else {
      setOpen((o) => !o);
    }
  };
  return (
    <View style={{ flex: 1 }}>
      {label ? <Label>{label}</Label> : null}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={label}
        onPress={show}
        style={{
          borderWidth: 1,
          borderColor: open ? c.primary : c.border,
          borderRadius: radius.m,
          paddingHorizontal: space(3),
          paddingVertical: space(3),
          backgroundColor: c.card,
          flexDirection: 'row',
          alignItems: 'center',
        }}
      >
        <Text style={{ color: value ? c.text : c.faint, fontSize: 16, flex: 1 }} numberOfLines={1}>
          {value ? (mode === 'date' ? longDay(value) : value) : (placeholder ?? '—')}
        </Text>
        {onClear && value ? (
          <Pressable accessibilityLabel="Cancella" onPress={onClear} hitSlop={10}>
            <Text style={{ color: c.muted, fontSize: 16 }}>×</Text>
          </Pressable>
        ) : null}
      </Pressable>
      {open && Platform.OS === 'ios' ? (
        <DateTimePicker
          value={current}
          mode={mode}
          display={mode === 'date' ? 'inline' : 'spinner'}
          locale="it-IT"
          minimumDate={minimumDate}
          onValueChange={(_e, d) => {
            onChange(fromDate(d, mode));
            if (mode === 'date') setOpen(false);
          }}
        />
      ) : null}
    </View>
  );
}

export function DaysPicker({ value, onChange, labels }: { value: number[]; onChange: (v: number[]) => void; labels: string[] }) {
  const c = useColors();
  return (
    <View style={{ flexDirection: 'row', gap: space(1.5) }}>
      {labels.map((l, i) => {
        const on = value.includes(i);
        return (
          <Pressable
            key={l}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: on }}
            accessibilityLabel={l}
            onPress={() => {
              tap();
              onChange(on ? value.filter((d) => d !== i) : [...value, i].sort());
            }}
            style={{
              flex: 1,
              paddingVertical: space(2),
              borderRadius: radius.s,
              backgroundColor: on ? c.primary : c.chip,
              alignItems: 'center',
            }}
          >
            <Text style={{ color: on ? c.primaryText : c.muted, fontWeight: '700', fontSize: 13 }}>{l}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function Collapsible({ title, children, initiallyOpen = false, right }: { title: string; children: ReactNode; initiallyOpen?: boolean; right?: string }) {
  const c = useColors();
  const [open, setOpen] = useState(initiallyOpen);
  return (
    <View>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        onPress={() => {
          tap();
          setOpen((o) => !o);
        }}
        style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: space(2) }}
      >
        <Text style={{ color: c.text, fontSize: 16, fontWeight: '700', flex: 1 }}>{title}</Text>
        {right ? <Text style={{ color: c.muted, marginRight: space(2) }}>{right}</Text> : null}
        <Text style={{ color: c.muted, fontSize: 16 }}>{open ? '▴' : '▾'}</Text>
      </Pressable>
      {open ? <View style={{ gap: space(3), paddingTop: space(2) }}>{children}</View> : null}
    </View>
  );
}

export function OptionList<T extends string | number>({
  options,
  value,
  onChange,
}: {
  options: [T, string][];
  value: T;
  onChange: (v: T) => void;
}) {
  const c = useColors();
  return (
    <View style={{ borderRadius: radius.m, borderWidth: 1, borderColor: c.border, overflow: 'hidden' }}>
      {options.map(([v, label], i) => (
        <Pressable
          key={String(v)}
          accessibilityRole="radio"
          accessibilityState={{ checked: v === value }}
          onPress={() => {
            tap();
            onChange(v);
          }}
          style={{
            flexDirection: 'row',
            paddingVertical: space(3),
            paddingHorizontal: space(3),
            borderTopWidth: i ? StyleSheet.hairlineWidth : 0,
            borderColor: c.border,
            backgroundColor: v === value ? c.primarySoft : c.card,
          }}
        >
          <Text style={{ color: c.text, flex: 1, fontSize: 15 }}>{label}</Text>
          {v === value ? <Text style={{ color: c.primary, fontWeight: '800' }}>✓</Text> : null}
        </Pressable>
      ))}
    </View>
  );
}

export const styles = (c: Colors) =>
  StyleSheet.create({
    screen: { flex: 1, backgroundColor: c.bg },
    row: { flexDirection: 'row', gap: space(3) },
    title: { color: c.text, fontSize: 22, fontWeight: '800' },
    h2: { color: c.text, fontSize: 18, fontWeight: '800' },
    body: { color: c.text, fontSize: 15 },
    muted: { color: c.muted, fontSize: 13 },
  });
