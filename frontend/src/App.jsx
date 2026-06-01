import { Activity, ChevronDown, ChevronRight, CircleAlert, RefreshCw } from 'lucide-react';
import { createClient } from '@supabase/supabase-js';
import { Component, useCallback, useEffect, useMemo, useState } from 'react';
import DebugPage from './DebugPage.jsx';

const runtimeBackendUrls = resolveRuntimeBackendUrls();
const websocketUrl = import.meta.env.VITE_BACKEND_WS_URL ?? runtimeBackendUrls.websocketUrl;
const backendApiUrl = import.meta.env.VITE_BACKEND_API_URL ?? runtimeBackendUrls.backendApiUrl;
const supabaseConfig = {
  url: import.meta.env.VITE_SUPABASE_URL,
  anonKey: import.meta.env.VITE_SUPABASE_ANON_KEY,
  channelId: import.meta.env.VITE_SUPABASE_CHANNEL_ID ?? 'xerioc2'
};
const shouldUseSupabaseRelay = Boolean(supabaseConfig.url && supabaseConfig.anonKey);
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
  gameId: null,
  updatedAt: null
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

export default function App() {
  if (window.location.pathname === '/debug') {
    return <DebugPage />;
  }

  const isOverlay = window.location.pathname.endsWith('/overlay.html')
    || window.location.pathname === '/overlay';

  return (
    <ExtensionErrorBoundary>
      <ExtensionPanel isOverlay={isOverlay} />
    </ExtensionErrorBoundary>
  );
}

function ExtensionPanel({ isOverlay }) {
  const [connectionState, setConnectionState] = useState('connecting');
  const [gameState, setGameState] = useState(emptyGameState);
  const [lastError, setLastError] = useState('');
  const [rescanStatus, setRescanStatus] = useState('');
  const [isRescanning, setIsRescanning] = useState(false);
  const [collapsedZones, setCollapsedZones] = useState({});
  const [hoveredCard, setHoveredCard] = useState(null);
  const [cardDetailsByCatalogId, setCardDetailsByCatalogId] = useState({});
  const [failedCatalogIds, setFailedCatalogIds] = useState({});

  const fetchCardDetails = useCallback(async (catalogId, { cacheFailures }) => {
    try {
      const details = await fetchCardDetailsFromBackendOrScryfall(catalogId);
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
    if (shouldUseSupabaseRelay) {
      const supabase = createClient(supabaseConfig.url, supabaseConfig.anonKey);
      const channel = supabase.channel(`game-state:${supabaseConfig.channelId}`);

      channel
        .on('broadcast', { event: 'game-state' }, (message) => {
          const nextGameState = message.payload?.payload ?? message.payload;
          setGameState({ ...emptyGameState, ...nextGameState });
          setLastError('');
        })
        .subscribe((status) => {
          if (status === 'SUBSCRIBED') {
            setConnectionState('connected');
            setLastError('');
          } else if (status === 'CHANNEL_ERROR' || status === 'TIMED_OUT') {
            setConnectionState('error');
            setLastError(`Supabase relay ${status.toLowerCase().replace('_', ' ')}.`);
          } else if (status === 'CLOSED') {
            setConnectionState('disconnected');
          }
        });

      return () => {
        supabase.removeChannel(channel);
      };
    }

    let socket;

    try {
      socket = new WebSocket(websocketUrl);
    } catch (error) {
      window.setTimeout(() => {
        setConnectionState('error');
        setLastError(error instanceof Error ? error.message : 'WebSocket blocked.');
      }, 0);
      return undefined;
    }

    socket.addEventListener('open', () => {
      setConnectionState('connected');
    });

    socket.addEventListener('message', (event) => {
      try {
        setGameState({ ...emptyGameState, ...JSON.parse(event.data) });
        setLastError('');
      } catch {
        setLastError('Invalid game-state JSON.');
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

  const isConnected = connectionState === 'connected';

  async function handleReconnect() {
    setIsRescanning(true);
    setRescanStatus('');

    try {
      const response = await fetch(`${backendApiUrl}/api/rescan-log`, {
        method: 'POST'
      });
      const result = await response.json();
      setRescanStatus(result.watching ? 'Reconnected' : result.message ?? 'No log found');
    } catch {
      setRescanStatus('Backend unavailable');
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
    const previewWidth = 226;
    const previewLeft = isOverlay
      ? Math.max(8, rect.left - previewWidth - 8)
      : Math.min(rect.right + 8, window.innerWidth - previewWidth - 10);

    setHoveredCard({
      ...card,
      top: Math.min(rect.top, window.innerHeight - 260),
      left: previewLeft,
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

  return (
    <main className={isOverlay ? 'extension-shell extension-overlay-shell' : 'extension-shell'}>
      <section className="extension-panel" aria-labelledby="extension-title">
        <header className="extension-header">
          <div>
            <h1 id="extension-title">MTGO Zones</h1>
            <p>Game {gameState.gameId ?? 'none'}</p>
          </div>

          <div className="extension-actions">
            <button className="extension-icon-button" type="button" onClick={handleReconnect} disabled={isRescanning}>
              <RefreshCw aria-hidden="true" size={14} />
              <span>{isRescanning ? '...' : 'Reconnect'}</span>
            </button>

            <span className={`extension-status ${connectionState}`}>
              {isConnected ? <Activity aria-hidden="true" size={13} /> : <CircleAlert aria-hidden="true" size={13} />}
              {connectionState}
            </span>
          </div>
        </header>

        {(rescanStatus || lastError) && (
          <div className="extension-message">
            {lastError || rescanStatus}
          </div>
        )}

        <div className="extension-zone-list" aria-live="polite">
          {zones.map((zone) => {
            const cards = zoneCards[zone.key] ?? [];
            const isCollapsed = collapsedZones[zone.key];

            return (
              <section className="extension-zone" key={zone.key}>
                <button className="extension-zone-header" type="button" onClick={() => toggleZone(zone.key)}>
                  {isCollapsed ? <ChevronRight aria-hidden="true" size={15} /> : <ChevronDown aria-hidden="true" size={15} />}
                  <span>{zone.label}</span>
                  <span className="extension-count">{countCards(cards)}</span>
                </button>

                {!isCollapsed && (
                  <div className="extension-card-list">
                    {cards.length === 0 ? (
                      <div className="extension-empty">No cards</div>
                    ) : (
                      cards.map((card) => (
                        <button
                          className="extension-card-row"
                          type="button"
                          key={`${zone.key}-${card.catalogId}-${card.name}`}
                          onMouseEnter={(event) => handleCardMouseEnter(card, event)}
                          onMouseLeave={() => setHoveredCard(null)}
                        >
                          <span className="extension-quantity">{card.quantity}</span>
                          <span className="extension-card-main">
                            <span className="extension-card-title">
                              <span>{card.name}</span>
                              <ManaCost manaCost={card.manaCost} />
                            </span>
                            <span className="extension-card-subtitle">{card.typeLine || `Catalog ID ${card.catalogId}`}</span>
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

class ExtensionErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { errorMessage: '' };
  }

  static getDerivedStateFromError(error) {
    return {
      errorMessage: error instanceof Error ? error.message : 'Extension failed to render.'
    };
  }

  render() {
    if (this.state.errorMessage) {
      return (
        <main className="extension-shell extension-overlay-shell">
          <section className="extension-panel">
            <header className="extension-header">
              <div>
                <h1>MTGO Zones</h1>
                <p>Render error</p>
              </div>
              <span className="extension-status error">
                <CircleAlert aria-hidden="true" size={13} />
                error
              </span>
            </header>
            <div className="extension-message">{this.state.errorMessage}</div>
          </section>
        </main>
      );
    }

    return this.props.children;
  }
}

function ManaCost({ manaCost }) {
  if (!manaCost) {
    return null;
  }

  const symbols = manaCost.match(/\{[^}]+}/g);
  if (!symbols) {
    return <span className="extension-mana-text">{manaCost}</span>;
  }

  return (
    <span className="extension-mana" aria-label={manaCost}>
      {symbols.map((symbol, index) => {
        const value = symbol.replace(/[{}]/g, '');
        const color = pipColors[value] ?? '#e4ded4';

        return (
          <span className="extension-pip" style={{ backgroundColor: color }} key={`${symbol}-${index}`}>
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
    <aside className="extension-card-preview" style={{ top: card.top, left: card.left }}>
      {imageUri ? (
        <img src={imageUri} alt={details.name} />
      ) : (
        <div className="extension-card-image-placeholder">Image pending</div>
      )}
      <div className="extension-card-preview-body">
        <div className="extension-card-preview-title">
          <strong>{details.name}</strong>
          <ManaCost manaCost={details.manaCost} />
        </div>
        <p>{details.typeLine || `Catalog ID ${details.catalogId}`}</p>
        <p>{details.oracleText || (details.loading ? 'Loading Scryfall card data...' : 'Card details unavailable')}</p>
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
    oracleText: 'Card details unavailable',
    manaCost: card.manaCost || '',
    typeLine: card.typeLine || `Catalog ID ${card.catalogId}`
  };
}

async function fetchCardDetailsFromBackendOrScryfall(catalogId) {
  try {
    const response = await fetch(`${backendApiUrl}/api/cards/${catalogId}`);
    if (response.ok) {
      return response.json();
    }
  } catch {
    // Hosted Test may not have access to the local bridge API; fall back to Scryfall.
  }

  return fetchCardDetailsFromScryfall(catalogId);
}

async function fetchCardDetailsFromScryfall(catalogId) {
  const card = await fetchScryfallCard(`https://api.scryfall.com/cards/mtgo/${catalogId}`)
    ?? await fetchScryfallCard(`https://api.scryfall.com/cards/multiverse/${catalogId}`);

  if (!card) {
    throw new Error(`Card ${catalogId} was not found.`);
  }

  return {
    catalogId,
    name: card.name,
    typeLine: card.type_line,
    manaCost: card.mana_cost,
    oracleText: card.oracle_text,
    imageUrl: card.image_uris?.normal
  };
}

async function fetchScryfallCard(url) {
  const response = await fetch(url, {
    headers: {
      Accept: 'application/json'
    }
  });
  return response.ok ? response.json() : null;
}

function sleep(milliseconds) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });
}
