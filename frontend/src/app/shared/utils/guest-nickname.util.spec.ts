import { generateGuestNickname, getGuestAdjectives, getGuestNouns } from './guest-nickname.util';

describe('guest-nickname.util', () => {
  describe('data lists', () => {
    it('should have non-empty lists of adjectives and nouns', () => {
      const adjectives = getGuestAdjectives();
      const nouns = getGuestNouns();

      expect(adjectives.length).toBeGreaterThan(20);
      expect(nouns.length).toBeGreaterThan(20);
    });

    it('should contain NO numbers in any adjective or noun', () => {
      const adjectives = getGuestAdjectives();
      const nouns = getGuestNouns();

      for (const adj of adjectives) {
        expect(adj).not.toMatch(/\d/);
      }

      for (const noun of nouns) {
        expect(noun).not.toMatch(/\d/);
      }
    });
  });

  describe('generateGuestNickname', () => {
    it('should generate a valid nickname without any digits/numbers', () => {
      for (let i = 0; i < 50; i++) {
        const nickname = generateGuestNickname();
        expect(nickname).toBeTruthy();
        expect(nickname).not.toMatch(/\d/);
      }
    });

    it('should compose of an adjective and noun separated by default underscore', () => {
      const adjectives = new Set(getGuestAdjectives());
      const nouns = new Set(getGuestNouns());

      const nickname = generateGuestNickname();
      const parts = nickname.split('_');

      expect(parts.length).toBe(2);
      expect(adjectives.has(parts[0])).toBe(true);
      expect(nouns.has(parts[1])).toBe(true);
    });

    it('should support custom separator like space or hyphen', () => {
      const nickname = generateGuestNickname({ separator: '-' });
      expect(nickname).toContain('-');
      expect(nickname).not.toMatch(/\d/);

      const parts = nickname.split('-');
      expect(parts.length).toBe(2);
    });
  });
});
