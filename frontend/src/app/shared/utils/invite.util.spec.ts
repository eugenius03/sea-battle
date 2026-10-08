import { buildInviteUrl, extractInviteCode } from './invite.util';

describe('invite.util', () => {
  describe('extractInviteCode', () => {
    it('should return empty string for empty input', () => {
      expect(extractInviteCode('')).toBe('');
      expect(extractInviteCode('   ')).toBe('');
    });

    it('should return raw code when a plain code is passed', () => {
      expect(extractInviteCode('ROOM123')).toBe('ROOM123');
      expect(extractInviteCode('  ROOM123  ')).toBe('ROOM123');
      expect(extractInviteCode('"ROOM123"')).toBe('ROOM123');
    });

    it('should extract invite code from /game/:token URL', () => {
      expect(extractInviteCode('http://localhost:4200/game/abc-123_XYZ')).toBe('abc-123_XYZ');
      expect(extractInviteCode('https://battleship.app/game/ROOM456/')).toBe('ROOM456');
      expect(extractInviteCode('/game/ROOM789')).toBe('ROOM789');
    });

    it('should extract invite code from /match/:token URL', () => {
      expect(extractInviteCode('https://battleship.app/match/MATCH123')).toBe('MATCH123');
    });

    it('should extract invite code from query parameters ?join=, ?code=, or ?token=', () => {
      expect(extractInviteCode('https://battleship.app/lobby?join=JOIN99')).toBe('JOIN99');
      expect(extractInviteCode('https://battleship.app?code=CODE88')).toBe('CODE88');
      expect(extractInviteCode('http://localhost:4200/?token=TOK77')).toBe('TOK77');
      expect(extractInviteCode('https://battleship.app/invite?other=1&join=JOIN42&more=2')).toBe(
        'JOIN42',
      );
    });

    it('should extract last segment from generic paths if formatted as token', () => {
      expect(extractInviteCode('https://example.com/room/TESTCODE')).toBe('TESTCODE');
    });
  });

  describe('buildInviteUrl', () => {
    it('should return empty string for empty token', () => {
      expect(buildInviteUrl('')).toBe('');
    });

    it('should return a full URL with /game/:token', () => {
      const url = buildInviteUrl('TEST123');
      expect(url).toContain('/game/TEST123');
    });
  });
});
