import namesData from '../data/guest-names.json';

export interface GuestNamesData {
  adjectives: string[];
  nouns: string[];
}

export interface GuestNicknameOptions {
  separator?: string;
}

const data = namesData as GuestNamesData;

export function getGuestAdjectives(): readonly string[] {
  return data.adjectives;
}

export function getGuestNouns(): readonly string[] {
  return data.nouns;
}

export function generateGuestNickname(options: GuestNicknameOptions = {}): string {
  const separator = options.separator ?? '_';
  const adjectives = data.adjectives;
  const nouns = data.nouns;

  if (!adjectives.length || !nouns.length) {
    return 'Guest_Admiral';
  }

  const adj = adjectives[Math.floor(Math.random() * adjectives.length)];
  const noun = nouns[Math.floor(Math.random() * nouns.length)];

  return `${adj}${separator}${noun}`;
}
