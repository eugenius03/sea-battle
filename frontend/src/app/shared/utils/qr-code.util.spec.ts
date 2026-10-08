import { createQrMatrix, generateQrCodeSvg } from './qr-code.util';

describe('qr-code.util', () => {
  describe('createQrMatrix', () => {
    it('should generate a 21x21 matrix for short text (version 1)', () => {
      const result = createQrMatrix('TEST');
      expect(result.size).toBe(21);
      expect(result.grid.length).toBe(21);
      expect(result.grid[0].length).toBe(21);
    });

    it('should correctly position finder patterns', () => {
      const { grid, size } = createQrMatrix('http://localhost:4200/game/XYZ');

      // Top-left finder pattern 7x7 outer border should have black modules
      expect(grid[0][0]).toBe(1);
      expect(grid[0][6]).toBe(1);
      expect(grid[6][0]).toBe(1);
      expect(grid[6][6]).toBe(1);

      // Top-right finder pattern
      expect(grid[0][size - 7]).toBe(1);
      expect(grid[0][size - 1]).toBe(1);
      expect(grid[6][size - 7]).toBe(1);
      expect(grid[6][size - 1]).toBe(1);

      // Bottom-left finder pattern
      expect(grid[size - 7][0]).toBe(1);
      expect(grid[size - 7][6]).toBe(1);
      expect(grid[size - 1][0]).toBe(1);
      expect(grid[size - 1][6]).toBe(1);
    });

    it('should handle typical invite URL lengths without throwing', () => {
      const inviteUrl = 'https://sea-battle.example.com/game/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d';
      const result = createQrMatrix(inviteUrl);
      expect(result.size).toBeGreaterThan(21);
      expect(result.grid.length).toBe(result.size);
    });

    it('should throw an error for payload exceeding supported versions', () => {
      const hugeString = 'A'.repeat(500);
      expect(() => createQrMatrix(hugeString)).toThrowError(/exceeds supported QR capacity/);
    });
  });

  describe('generateQrCodeSvg', () => {
    it('should return a valid SVG string', () => {
      const svg = generateQrCodeSvg('https://example.com/game/ABC123');
      expect(svg).toContain('<svg');
      expect(svg).toContain('xmlns="http://www.w3.org/2000/svg"');
      expect(svg).toContain('viewBox=');
      expect(svg).toContain('shape-rendering="crispEdges"');
      expect(svg).toContain('<path');
      expect(svg).toContain('</svg>');
    });

    it('should include correct dimensions including margin', () => {
      const margin = 3;
      const { size } = createQrMatrix('TEST');
      const expectedTotal = size + margin * 2;
      const svg = generateQrCodeSvg('TEST', margin);

      expect(svg).toContain(`viewBox="0 0 ${expectedTotal} ${expectedTotal}"`);
    });
  });
});
