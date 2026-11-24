import { useColorScheme } from 'react-native';

const light = {
  bg: '#f4f6fb',
  card: '#ffffff',
  text: '#121826',
  muted: '#5b6478',
  faint: '#8a93a6',
  border: '#e2e6ef',
  primary: '#1a5cff',
  primaryText: '#ffffff',
  primarySoft: '#e8efff',
  good: '#0f8a4f',
  goodSoft: '#e5f6ed',
  warn: '#a55a00',
  warnSoft: '#fff3e0',
  bad: '#c0262d',
  badSoft: '#fdecec',
  chip: '#eef1f7',
  hero: '#0b1f4d',
  heroText: '#ffffff',
};

const dark: typeof light = {
  bg: '#0c0f16',
  card: '#161b26',
  text: '#eef1f7',
  muted: '#a3abbd',
  faint: '#6f788c',
  border: '#272e3d',
  primary: '#5b8cff',
  primaryText: '#ffffff',
  primarySoft: '#1b2747',
  good: '#4cc58a',
  goodSoft: '#12291e',
  warn: '#f0a54a',
  warnSoft: '#2e2212',
  bad: '#ff6b6b',
  badSoft: '#2e1517',
  chip: '#202736',
  hero: '#0f1a36',
  heroText: '#ffffff',
};

export type Colors = typeof light;

export function useColors(): Colors {
  return useColorScheme() === 'dark' ? dark : light;
}

export const radius = { s: 8, m: 12, l: 18 };
export const space = (n: number) => n * 4;
