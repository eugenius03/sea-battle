export const state = {
    currentInviteToken: null,
    myMatchPlayerId: null,
    finalWinState: false,
    currentOrientation: 'HORIZONTAL',
    isReady: false,
    placedShips: new Set(),
    selectedShip: null,
    firedCells: new Set(),
    shipRegistry: new Map(),
    draggedDroneType: null,
    surveillanceDroneUsesLeft: 0,
    attackDroneUsesLeft: 0,
    stompClient: null,
    subs: []
};

export const shipTypes = [
    {type: 'QUADRO_DECK', size: 4, count: 1, label: 'Battleship'},
    {type: 'TRIPLE_DECK', size: 3, count: 2, label: 'Cruiser'},
    {type: 'DOUBLE_DECK', size: 2, count: 3, label: 'Destroyer'},
    {type: 'SINGLE_DECK', size: 1, count: 4, label: 'Submarine'}
];

export function setState(patch) {
    Object.assign(state, patch);
}

export const shipSizeMap = Object.fromEntries(shipTypes.map(t => [t.type, t.size]));
export const unplacedShipOrientations = new Map();
export const SHIP_SIZES = {SINGLE_DECK: 1, DOUBLE_DECK: 2, TRIPLE_DECK: 3, QUADRO_DECK: 4};
