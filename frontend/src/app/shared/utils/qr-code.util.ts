const EXP = new Uint8Array(512);
const LOG = new Uint8Array(256);
for (let i = 0, x = 1; i < 255; i++) {
  EXP[i] = x;
  EXP[i + 255] = x;
  LOG[x] = i;
  x = (x << 1) ^ (x >= 128 ? 0x11d : 0);
}

function gMul(x: number, y: number): number {
  if (x === 0 || y === 0) return 0;
  return EXP[LOG[x] + LOG[y]];
}

function rsGenPoly(degree: number): Uint8Array {
  let poly = new Uint8Array([1]);
  for (let i = 0; i < degree; i++) {
    const next = new Uint8Array(poly.length + 1);
    const root = EXP[i];
    for (let j = 0; j < poly.length; j++) {
      next[j] ^= poly[j];
      next[j + 1] ^= gMul(poly[j], root);
    }
    poly = next;
  }
  return poly;
}

function rsComputeRemainder(data: number[], degree: number): Uint8Array {
  const gen = rsGenPoly(degree);
  const rem = new Uint8Array(degree);
  for (const byte of data) {
    const factor = byte ^ rem[0];
    for (let i = 0; i < degree - 1; i++) {
      rem[i] = rem[i + 1] ^ gMul(gen[i + 1], factor);
    }
    rem[degree - 1] = gMul(gen[degree], factor);
  }
  return rem;
}

interface QrSpec {
  version: number;
  total: number;
  data: number;
  ecPerBlock: number;
  b1: number;
  d1: number;
  b2: number;
  d2: number;
}

const QR_SPECS_M: (QrSpec | null)[] = [
  null,
  { version: 1, total: 26, data: 16, ecPerBlock: 10, b1: 1, d1: 16, b2: 0, d2: 0 },
  { version: 2, total: 44, data: 28, ecPerBlock: 16, b1: 1, d1: 28, b2: 0, d2: 0 },
  { version: 3, total: 70, data: 44, ecPerBlock: 26, b1: 1, d1: 44, b2: 0, d2: 0 },
  { version: 4, total: 100, data: 64, ecPerBlock: 18, b1: 2, d1: 32, b2: 0, d2: 0 },
  { version: 5, total: 134, data: 86, ecPerBlock: 24, b1: 2, d1: 43, b2: 0, d2: 0 },
  { version: 6, total: 172, data: 108, ecPerBlock: 16, b1: 4, d1: 27, b2: 0, d2: 0 },
  { version: 7, total: 196, data: 124, ecPerBlock: 18, b1: 4, d1: 31, b2: 0, d2: 0 },
  { version: 8, total: 242, data: 154, ecPerBlock: 22, b1: 2, d1: 38, b2: 2, d2: 39 },
  { version: 9, total: 292, data: 182, ecPerBlock: 22, b1: 3, d1: 36, b2: 2, d2: 37 },
  { version: 10, total: 346, data: 216, ecPerBlock: 26, b1: 4, d1: 40, b2: 1, d2: 41 },
];

const ALIGNMENT_PATTERNS = [
  [],
  [],
  [6, 18],
  [6, 22],
  [6, 26],
  [6, 30],
  [6, 34],
  [6, 22, 38],
  [6, 24, 42],
  [6, 26, 46],
  [6, 28, 50],
];

function getFormatBits(mask: number): number {
  const data = (0 << 3) | mask;
  let rem = data;
  for (let i = 0; i < 10; i++) {
    rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
  }
  return ((data << 10) | rem) ^ 0x5412;
}

function chooseVersion(dataLen: number): number {
  for (let v = 1; v <= 10; v++) {
    const spec = QR_SPECS_M[v]!;
    const charCountBits = v < 10 ? 8 : 16;
    const headerBits = 4 + charCountBits;
    const availableDataBytes = spec.data;
    const requiredBits = headerBits + dataLen * 8;
    if (requiredBits <= availableDataBytes * 8) {
      return v;
    }
  }
  throw new Error(`Data length (${dataLen} bytes) exceeds supported QR capacity (max version 10).`);
}

function encodeData(text: string, version: number): number[] {
  const spec = QR_SPECS_M[version]!;
  const charCountBits = version < 10 ? 8 : 16;
  const utf8 = new TextEncoder().encode(text);
  const dataLen = utf8.length;

  const bits: number[] = [];
  function pushBits(val: number, count: number): void {
    for (let i = count - 1; i >= 0; i--) {
      bits.push((val >> i) & 1);
    }
  }

  pushBits(0b0100, 4);
  pushBits(dataLen, charCountBits);
  for (let i = 0; i < dataLen; i++) {
    pushBits(utf8[i], 8);
  }

  const totalBits = spec.data * 8;
  const termLen = Math.min(4, totalBits - bits.length);
  pushBits(0, termLen);

  while (bits.length % 8 !== 0) {
    bits.push(0);
  }

  const bytes: number[] = [];
  for (let i = 0; i < bits.length; i += 8) {
    let b = 0;
    for (let j = 0; j < 8; j++) {
      b = (b << 1) | bits[i + j];
    }
    bytes.push(b);
  }

  let pad = 0xec;
  while (bytes.length < spec.data) {
    bytes.push(pad);
    pad = pad === 0xec ? 0x11 : 0xec;
  }

  const blocks: number[][] = [];
  let byteIdx = 0;
  for (let b = 0; b < spec.b1; b++) {
    blocks.push(bytes.slice(byteIdx, byteIdx + spec.d1));
    byteIdx += spec.d1;
  }
  for (let b = 0; b < spec.b2; b++) {
    blocks.push(bytes.slice(byteIdx, byteIdx + spec.d2));
    byteIdx += spec.d2;
  }

  const ecBlocks: Uint8Array[] = [];
  for (const block of blocks) {
    ecBlocks.push(rsComputeRemainder(block, spec.ecPerBlock));
  }

  const finalCodewords: number[] = [];
  const maxDataBlockLen = Math.max(spec.d1, spec.d2 || 0);
  for (let i = 0; i < maxDataBlockLen; i++) {
    for (const block of blocks) {
      if (i < block.length) finalCodewords.push(block[i]);
    }
  }
  for (let i = 0; i < spec.ecPerBlock; i++) {
    for (const ecBlock of ecBlocks) {
      finalCodewords.push(ecBlock[i]);
    }
  }

  return finalCodewords;
}

export interface QrMatrixResult {
  size: number;
  grid: number[][];
  version: number;
  mask: number;
}

export function createQrMatrix(text: string): QrMatrixResult {
  const utf8 = new TextEncoder().encode(text);
  const version = chooseVersion(utf8.length);
  const codewords = encodeData(text, version);
  const size = version * 4 + 17;

  const matrix: number[][] = Array.from({ length: size }, () => Array(size).fill(0));
  const isFunction: boolean[][] = Array.from({ length: size }, () => Array(size).fill(false));

  function setFunc(x: number, y: number, val: boolean): void {
    matrix[y][x] = val ? 1 : 0;
    isFunction[y][x] = true;
  }

  function drawFinder(ox: number, oy: number): void {
    for (let dy = -1; dy <= 7; dy++) {
      for (let dx = -1; dx <= 7; dx++) {
        const x = ox + dx;
        const y = oy + dy;
        if (x < 0 || x >= size || y < 0 || y >= size) continue;
        if (dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6) {
          const isBlack =
            dx === 0 ||
            dx === 6 ||
            dy === 0 ||
            dy === 6 ||
            (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4);
          setFunc(x, y, isBlack);
        } else {
          setFunc(x, y, false);
        }
      }
    }
  }
  drawFinder(0, 0);
  drawFinder(size - 7, 0);
  drawFinder(0, size - 7);

  const alignCoords = ALIGNMENT_PATTERNS[version];
  const numAlign = alignCoords.length;
  for (let i = 0; i < numAlign; i++) {
    for (let j = 0; j < numAlign; j++) {
      if (
        (i === 0 && j === 0) ||
        (i === 0 && j === numAlign - 1) ||
        (i === numAlign - 1 && j === 0)
      )
        continue;
      const ax = alignCoords[i];
      const ay = alignCoords[j];
      for (let dy = -2; dy <= 2; dy++) {
        for (let dx = -2; dx <= 2; dx++) {
          const isBlack = Math.abs(dx) === 2 || Math.abs(dy) === 2 || (dx === 0 && dy === 0);
          setFunc(ax + dx, ay + dy, isBlack);
        }
      }
    }
  }

  for (let i = 0; i < size; i++) {
    if (!isFunction[6][i]) setFunc(i, 6, i % 2 === 0);
    if (!isFunction[i][6]) setFunc(6, i, i % 2 === 0);
  }

  setFunc(8, size - 8, true);

  for (let i = 0; i <= 5; i++) setFunc(8, i, false);
  setFunc(8, 7, false);
  setFunc(8, 8, false);
  setFunc(7, 8, false);
  for (let i = 9; i < 15; i++) setFunc(14 - i, 8, false);

  for (let i = 0; i < 8; i++) setFunc(size - 1 - i, 8, false);
  for (let i = 8; i < 15; i++) setFunc(8, size - 15 + i, false);

  const allBits: number[] = [];
  for (const cw of codewords) {
    for (let b = 7; b >= 0; b--) {
      allBits.push((cw >> b) & 1);
    }
  }

  let bitIdx = 0;
  let up = true;
  for (let right = size - 1; right > 0; right -= 2) {
    if (right === 6) right--;
    const left = right - 1;
    for (let step = 0; step < size; step++) {
      const y = up ? size - 1 - step : step;
      for (const x of [right, left]) {
        if (!isFunction[y][x]) {
          matrix[y][x] = bitIdx < allBits.length ? allBits[bitIdx++] : 0;
        }
      }
    }
    up = !up;
  }

  const maskFunctions = [
    (x: number, y: number) => (x + y) % 2 === 0,
    (_x: number, y: number) => y % 2 === 0,
    (x: number, _y: number) => x % 3 === 0,
    (x: number, y: number) => (x + y) % 3 === 0,
    (x: number, y: number) => (Math.floor(y / 2) + Math.floor(x / 3)) % 2 === 0,
    (x: number, y: number) => ((x * y) % 2) + ((x * y) % 3) === 0,
    (x: number, y: number) => (((x * y) % 2) + ((x * y) % 3)) % 2 === 0,
    (x: number, y: number) => (((x + y) % 2) + ((x * y) % 3)) % 2 === 0,
  ];

  function evaluatePenalty(grid: number[][]): number {
    let penalty = 0;
    for (let y = 0; y < size; y++) {
      let runColor = -1,
        runLen = 0;
      for (let x = 0; x < size; x++) {
        if (grid[y][x] === runColor) {
          runLen++;
        } else {
          if (runLen >= 5) penalty += 3 + (runLen - 5);
          runColor = grid[y][x];
          runLen = 1;
        }
      }
      if (runLen >= 5) penalty += 3 + (runLen - 5);
    }
    for (let x = 0; x < size; x++) {
      let runColor = -1,
        runLen = 0;
      for (let y = 0; y < size; y++) {
        if (grid[y][x] === runColor) {
          runLen++;
        } else {
          if (runLen >= 5) penalty += 3 + (runLen - 5);
          runColor = grid[y][x];
          runLen = 1;
        }
      }
      if (runLen >= 5) penalty += 3 + (runLen - 5);
    }
    for (let y = 0; y < size - 1; y++) {
      for (let x = 0; x < size - 1; x++) {
        const c = grid[y][x];
        if (c === grid[y + 1][x] && c === grid[y][x + 1] && c === grid[y + 1][x + 1]) {
          penalty += 3;
        }
      }
    }
    let dark = 0;
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        if (grid[y][x] === 1) dark++;
      }
    }
    const percent = (dark * 100) / (size * size);
    const step = Math.floor(Math.abs(percent - 50) / 5);
    penalty += step * 10;
    return penalty;
  }

  function writeFormatBits(grid: number[][], maskIdx: number): void {
    const bits = getFormatBits(maskIdx);
    function getBit(b: number, i: number): number {
      return (b >> i) & 1;
    }

    for (let i = 0; i <= 5; i++) grid[i][8] = getBit(bits, i);
    grid[7][8] = getBit(bits, 6);
    grid[8][8] = getBit(bits, 7);
    grid[8][7] = getBit(bits, 8);
    for (let i = 9; i < 15; i++) grid[8][14 - i] = getBit(bits, i);

    for (let i = 0; i < 8; i++) grid[8][size - 1 - i] = getBit(bits, i);
    for (let i = 8; i < 15; i++) grid[size - 15 + i][8] = getBit(bits, i);
  }

  let bestMask = 0;
  let minPenalty = Infinity;
  let bestGrid: number[][] = matrix;

  for (let maskIdx = 0; maskIdx < 8; maskIdx++) {
    const fn = maskFunctions[maskIdx];
    const candidate: number[][] = Array.from({ length: size }, () => Array(size).fill(0));
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        if (isFunction[y][x]) {
          candidate[y][x] = matrix[y][x];
        } else {
          candidate[y][x] = matrix[y][x] ^ (fn(x, y) ? 1 : 0);
        }
      }
    }
    writeFormatBits(candidate, maskIdx);

    const p = evaluatePenalty(candidate);
    if (p < minPenalty) {
      minPenalty = p;
      bestMask = maskIdx;
      bestGrid = candidate;
    }
  }

  return { size, grid: bestGrid, version, mask: bestMask };
}

export function generateQrCodeSvg(text: string, margin = 2): string {
  const { size, grid } = createQrMatrix(text);
  const totalSize = size + margin * 2;
  let path = '';
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (grid[y][x] === 1) {
        path += `M${x + margin},${y + margin}h1v1h-1z`;
      }
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${totalSize} ${totalSize}" shape-rendering="crispEdges" class="w-full h-full block"><rect width="${totalSize}" height="${totalSize}" fill="#ffffff"/><path d="${path}" fill="#000000"/></svg>`;
}
