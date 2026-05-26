import { ChevronDown, ChevronRight, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

const runtimeBackendUrls = resolveRuntimeBackendUrls();
const websocketUrl = import.meta.env.VITE_BACKEND_WS_URL ?? runtimeBackendUrls.websocketUrl;
const backendApiUrl = import.meta.env.VITE_BACKEND_API_URL ?? runtimeBackendUrls.backendApiUrl;
const emptyGameState = {
  hand: [],
  battlefield: [],
  graveyard: [],
  exile: [],
  handCards: [],
  battlefieldCards: [],
  graveyardCards: [],
  exileCards: [],
  deckCatalogIds: [],
  gameId: null
};

const zones = [
  { key: 'hand', cardsKey: 'handCards', label: 'HAND' },
  { key: 'battlefield', cardsKey: 'battlefieldCards', label: 'BATTLEFIELD' },
  { key: 'graveyard', cardsKey: 'graveyardCards', label: 'GRAVEYARD' },
  { key: 'exile', cardsKey: 'exileCards', label: 'EXILE' }
];

const pipColors = {
  W: '#f7edc5',
  U: '#9cc9f5',
  B: '#c5bdaf',
  R: '#f08a5d',
  G: '#74c383',
  C: '#c7ced1'
};

function resolveRuntimeBackendUrls() {
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';

  return {
    websocketUrl: `${wsProtocol}//${window.location.host}/ws/game-state`,
    backendApiUrl: `${window.location.protocol}//${window.location.host}`
  };
}

export default function DebugPage() {
  const [connectionState, setConnectionState] = useState('connecting');
  const [gameState, setGameState] = useState(emptyGameState);
  const [rescanStatus, setRescanStatus] = useState('');
  const [isRescanning, setIsRescanning] = useState(false);
  const [collapsedZones, setCollapsedZones] = useState({});
  const [hoveredCard, setHoveredCard] = useState(null);
  const [cardDetailsByCatalogId, setCardDetailsByCatalogId] = useState({});
  const [failedCatalogIds, setFailedCatalogIds] = useState({});

  const fetchCardDetails = useCallback(async (catalogId, { cacheFailures }) => {
    try {
      const response = await fetch(`${backendApiUrl}/api/cards/${catalogId}`);
      if (!response.ok) {
        throw new Error(`Card ${catalogId} was not found.`);
      }

      const details = await response.json();
      setCardDetailsByCatalogId((current) => ({
        ...current,
        [catalogId]: details
      }));
      return details;
    } catch {
      if (cacheFailures) {
        setFailedCatalogIds((current) => ({
          ...current,
          [catalogId]: true
        }));
      }
      return null;
    }
  }, []);

  useEffect(() => {
    const socket = new WebSocket(websocketUrl);

    socket.addEventListener('open', () => {
      setConnectionState('connected');
    });

    socket.addEventListener('message', (event) => {
      try {
        setGameState({ ...emptyGameState, ...JSON.parse(event.data) });
      } catch {
        setConnectionState('error');
      }
    });

    socket.addEventListener('close', () => {
      setConnectionState('disconnected');
    });

    socket.addEventListener('error', () => {
      setConnectionState('error');
    });

    return () => {
      socket.close();
    };
  }, []);

  const zoneCards = useMemo(() => {
    const nextZoneCards = {};

    for (const zone of zones) {
      const structuredCards = gameState[zone.cardsKey] ?? [];
      const fallbackCards = gameState[zone.key] ?? [];
      const cards = structuredCards.length > 0
        ? structuredCards.map((card) => normalizeCard(card))
        : fallbackCards.map((cardName, index) => normalizeFallbackCard(cardName, index));

      nextZoneCards[zone.key] = groupDuplicates(cards, cardDetailsByCatalogId);
    }

    return nextZoneCards;
  }, [cardDetailsByCatalogId, gameState]);

  const catalogIdsToResolve = useMemo(() => {
    const catalogIds = new Set();

    for (const zone of zones) {
      for (const card of gameState[zone.cardsKey] ?? []) {
        if (card.catalogId) {
          catalogIds.add(card.catalogId);
        }
      }

      for (const cardName of gameState[zone.key] ?? []) {
        const fallbackCard = normalizeFallbackCard(cardName, 0);
        if (fallbackCard.catalogId) {
          catalogIds.add(fallbackCard.catalogId);
        }
      }
    }

    for (const catalogId of gameState.deckCatalogIds ?? []) {
      if (catalogId) {
        catalogIds.add(catalogId);
      }
    }

    return Array.from(catalogIds);
  }, [gameState]);

  useEffect(() => {
    let isCancelled = false;

    async function resolveCards() {
      const unresolvedCatalogIds = catalogIdsToResolve.filter((catalogId) => (
        !cardDetailsByCatalogId[catalogId] && !failedCatalogIds[catalogId]
      ));

      for (const catalogId of unresolvedCatalogIds) {
        if (isCancelled) {
          return;
        }

        await fetchCardDetails(catalogId, { cacheFailures: true });
        await sleep(60);
      }
    }

    resolveCards();

    return () => {
      isCancelled = true;
    };
  }, [cardDetailsByCatalogId, catalogIdsToResolve, failedCatalogIds, fetchCardDetails]);

  async function handleReconnect() {
    setIsRescanning(true);
    setRescanStatus('');

    try {
      const response = await fetch(`${backendApiUrl}/api/rescan-log`, {
        method: 'POST'
      });
      const result = await response.json();
      setRescanStatus(result.path
        ? `${result.message ?? 'Rescan complete'}: ${result.path}`
        : result.message ?? 'No MTGO log file was found.');
    } catch {
      setRescanStatus('Unable to contact the backend rescan endpoint.');
    } finally {
      setIsRescanning(false);
    }
  }

  function toggleZone(zoneKey) {
    setCollapsedZones((current) => ({
      ...current,
      [zoneKey]: !current[zoneKey]
    }));
  }

  async function handleCardMouseEnter(card, event) {
    const rect = event.currentTarget.getBoundingClientRect();
    setHoveredCard({
      ...card,
      top: rect.top,
      left: rect.right + 12,
      loading: !cardDetailsByCatalogId[card.catalogId]
    });

    if (!card.catalogId || cardDetailsByCatalogId[card.catalogId]) {
      return;
    }

    const details = await fetchCardDetails(card.catalogId, { cacheFailures: false });
    if (details) {
      setHoveredCard((current) => current?.catalogId === card.catalogId
        ? { ...current, ...details, loading: false }
        : current);
    } else {
      setHoveredCard((current) => current?.catalogId === card.catalogId
        ? { ...current, ...buildPlaceholderCard(card), loading: false }
        : current);
    }
  }

  function handleCardMouseLeave() {
    setHoveredCard(null);
  }

  return (
    <main className="debug-shell">
      <section className="debug-panel" aria-labelledby="debug-title">
        <header className="debug-header">
          <div>
            <h1 id="debug-title">MTGO Debug Decklist</h1>
            <p>Game {gameState.gameId ?? 'none'} · Deck IDs {gameState.deckCatalogIds?.length ?? 0}</p>
          </div>

          <div className="debug-actions">
            <span className={`debug-status ${connectionState}`}>{connectionState}</span>
            <button className="debug-reconnect" type="button" onClick={handleReconnect} disabled={isRescanning}>
              <RefreshCw aria-hidden="true" size={15} />
              <span>{isRescanning ? 'Reconnecting' : 'Reconnect'}</span>
            </button>
          </div>
        </header>

        {rescanStatus && <div className="debug-rescan">{rescanStatus}</div>}

        <div className="debug-zone-list">
          {zones.map((zone) => {
            const cards = zoneCards[zone.key] ?? [];
            const isCollapsed = collapsedZones[zone.key];

            return (
              <section className="debug-zone" key={zone.key}>
                <button className="debug-zone-header" type="button" onClick={() => toggleZone(zone.key)}>
                  {isCollapsed ? <ChevronRight aria-hidden="true" size={16} /> : <ChevronDown aria-hidden="true" size={16} />}
                  <span>{zone.label}</span>
                  <span className="debug-count">{countCards(cards)}</span>
                </button>

                {!isCollapsed && (
                  <div className="debug-card-list">
                    {cards.length === 0 ? (
                      <div className="debug-empty">No cards detected</div>
                    ) : (
                      cards.map((card) => (
                        <button
                          className="debug-card-row"
                          type="button"
                          key={`${zone.key}-${card.catalogId}-${card.name}`}
                          onMouseEnter={(event) => handleCardMouseEnter(card, event)}
                          onMouseLeave={handleCardMouseLeave}
                        >
                          <span className="debug-quantity">{card.quantity}</span>
                          <span className="debug-card-main">
                            <span className="debug-card-title">
                              <span>{card.name}</span>
                              <ManaCost manaCost={card.manaCost} />
                            </span>
                            <span className="debug-card-subtitle">
                              <span className="debug-rarity-dot" />
                              {card.typeLine || `Catalog ID ${card.catalogId}`}
                            </span>
                          </span>
                        </button>
                      ))
                    )}
                  </div>
                )}
              </section>
            );
          })}
        </div>
      </section>

      {hoveredCard && (
        <CardPreview card={hoveredCard} cachedDetails={cardDetailsByCatalogId[hoveredCard.catalogId]} />
      )}
    </main>
  );
}

function ManaCost({ manaCost }) {
  if (!manaCost) {
    return null;
  }

  const symbols = manaCost.match(/\{[^}]+}/g);
  if (!symbols) {
    return <span className="debug-mana-text">{manaCost}</span>;
  }

  return (
    <span className="debug-mana" aria-label={manaCost}>
      {symbols.map((symbol, index) => {
        const value = symbol.replace(/[{}]/g, '');
        const color = pipColors[value] ?? '#e4ded4';

        return (
          <span className="debug-pip" style={{ backgroundColor: color }} key={`${symbol}-${index}`}>
            {value}
          </span>
        );
      })}
    </span>
  );
}

function CardPreview({ card, cachedDetails }) {
  const details = { ...card, ...cachedDetails };
  const imageUri = details.imageUrl || details.normalImageUri || details.imageUri;

  return (
    <aside className="debug-card-preview" style={{ top: card.top, left: card.left }}>
      {imageUri ? (
        <img src={imageUri} alt={details.name} />
      ) : (
        <div className="debug-card-image-placeholder">Image pending</div>
      )}
      <div className="debug-card-preview-body">
        <div className="debug-card-preview-title">
          <strong>{details.name}</strong>
          <ManaCost manaCost={details.manaCost} />
        </div>
        <p>{details.typeLine || `Catalog ID ${details.catalogId}`}</p>
        <p>{details.oracleText || (details.loading ? 'Loading Scryfall card data...' : 'TODO: GET /api/cards/{catalogId}')}</p>
      </div>
    </aside>
  );
}

function normalizeCard(card) {
  return {
    id: card.id,
    catalogId: card.catalogId,
    name: card.name || card.displayName || `Catalog ID ${card.catalogId}`,
    manaCost: card.manaCost || '',
    typeLine: card.typeLine || '',
    oracleText: card.oracleText || '',
    imageUri: card.imageUrl || card.normalImageUri || card.imageUri || '',
    quantity: 1
  };
}

function normalizeFallbackCard(cardName, index) {
  const catalogIdMatch = String(cardName).match(/CatalogID\s+(\d+)/i);

  return {
    id: index,
    catalogId: catalogIdMatch ? Number(catalogIdMatch[1]) : index,
    name: String(cardName),
    manaCost: '',
    typeLine: '',
    oracleText: '',
    imageUri: '',
    quantity: 1
  };
}

function groupDuplicates(cards, cardDetailsByCatalogId) {
  const groupedCards = new Map();

  for (const card of cards) {
    const cachedDetails = cardDetailsByCatalogId[card.catalogId] ?? {};
    const displayCard = {
      ...card,
      ...cachedDetails,
      name: cachedDetails.name || card.name,
      imageUri: cachedDetails.imageUrl || cachedDetails.normalImageUri || card.imageUri
    };
    const key = `${displayCard.catalogId}-${displayCard.name}`;
    const existingCard = groupedCards.get(key);

    if (existingCard) {
      groupedCards.set(key, {
        ...existingCard,
        quantity: existingCard.quantity + 1
      });
    } else {
      groupedCards.set(key, displayCard);
    }
  }

  return Array.from(groupedCards.values());
}

function countCards(cards) {
  return cards.reduce((total, card) => total + card.quantity, 0);
}

function buildPlaceholderCard(card) {
  return {
    name: card.name,
    normalImageUri: '',
    imageUri: '',
    oracleText: 'TODO: GET /api/cards/{catalogId}',
    manaCost: card.manaCost || '',
    typeLine: card.typeLine || `Catalog ID ${card.catalogId}`
  };
}

function sleep(milliseconds) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });
}
