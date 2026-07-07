import { Activity, BookOpen, ChevronDown, ChevronRight, CircleAlert, PanelRightClose, PanelRightOpen, RefreshCw } from 'lucide-react';
import { createClient } from '@supabase/supabase-js';
import { Component, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import DebugPage from './DebugPage.jsx';
import { useScreenDetections } from './screenDetections/useScreenDetections.js';

const runtimeBackendUrls = resolveRuntimeBackendUrls();
const websocketUrl = import.meta.env.VITE_BACKEND_WS_URL ?? runtimeBackendUrls.websocketUrl;
const backendApiUrl = import.meta.env.VITE_BACKEND_API_URL ?? runtimeBackendUrls.backendApiUrl;
const supabaseConfig = {
  url: import.meta.env.VITE_SUPABASE_URL,
  anonKey: import.meta.env.VITE_SUPABASE_ANON_KEY,
  channelId: import.meta.env.VITE_SUPABASE_CHANNEL_ID ?? 'xerioc2'
};
const shouldUseSupabaseRelay = Boolean(supabaseConfig.url && supabaseConfig.anonKey);
const enableScreenDetections = import.meta.env.VITE_ENABLE_SCREEN_DETECTIONS === 'true';
// manapool.com must be declared as an allowed external domain in Twitch extension capabilities.
const MANA_POOL_REF = 'mtgcontent';
const emptyGameState = {
  hand: [],
  battlefield: [],
  graveyard: [],
  exile: [],
  handCards: [],
  battlefieldCards: [],
  graveyardCards: [],
  exileCards: [],
  opponentBattlefieldCards: [],
  opponentGraveyardCards: [],
  opponentExileCards: [],
  deckCatalogIds: [],
  deckCards: [],
  detectionRegions: [],
  gameId: null,
  updatedAt: null
};

const mockDeckGameState = import.meta.env.DEV ? {
  ...emptyGameState,
  gameId: 999999,
  deckCards: [
    { catalogId: 34460, quantity: 4, inSideboard: false },
    { catalogId: 91658, quantity: 4, inSideboard: false },
    { catalogId: 90457, quantity: 4, inSideboard: false },
    { catalogId: 83103, quantity: 4, inSideboard: false },
    { catalogId: 78520, quantity: 4, inSideboard: false },
    { catalogId: 54194, quantity: 4, inSideboard: false },
    { catalogId: 79608, quantity: 3, inSideboard: false },
    { catalogId: 62689, quantity: 3, inSideboard: false },
    { catalogId: 37388, quantity: 3, inSideboard: false },
    { catalogId: 149595, quantity: 2, inSideboard: false },
    { catalogId: 122032, quantity: 2, inSideboard: false },
    { catalogId: 104606, quantity: 1, inSideboard: false },
    { catalogId: 126509, quantity: 1, inSideboard: false },
    { catalogId: 97492, quantity: 1, inSideboard: false },
    { catalogId: 104610, quantity: 1, inSideboard: false },
    { catalogId: 22044, quantity: 1, inSideboard: false },
    { catalogId: 28707, quantity: 1, inSideboard: false },
    { catalogId: 41423, quantity: 1, inSideboard: false },
    { catalogId: 40024, quantity: 4, inSideboard: false },
    { catalogId: 104604, quantity: 1, inSideboard: false },
    { catalogId: 104586, quantity: 1, inSideboard: false },
    { catalogId: 23586, quantity: 2, inSideboard: false },
    { catalogId: 34812, quantity: 4, inSideboard: false },
    { catalogId: 54196, quantity: 4, inSideboard: false },
    { catalogId: 48404, quantity: 2, inSideboard: true },
    { catalogId: 93186, quantity: 1, inSideboard: true },
    { catalogId: 48406, quantity: 2, inSideboard: true },
    { catalogId: 62687, quantity: 3, inSideboard: true },
    { catalogId: 126449, quantity: 4, inSideboard: true },
    { catalogId: 97494, quantity: 3, inSideboard: true }
  ]
} : null;

const zones = [
  { key: 'hand', cardsKey: 'handCards', label: 'HAND' },
  { key: 'battlefield', cardsKey: 'battlefieldCards', label: 'BATTLEFIELD' },
  { key: 'graveyard', cardsKey: 'graveyardCards', label: 'GRAVEYARD' },
  { key: 'exile', cardsKey: 'exileCards', label: 'EXILE' }
];

const opponentZones = [
  { key: 'opponentBattlefieldCards', label: 'OPP BATTLEFIELD' },
  { key: 'opponentGraveyardCards', label: 'OPP GRAVEYARD' },
  { key: 'opponentExileCards', label: 'OPP EXILE' }
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

function toManaPoolSlug(name) {
  return name
    .toLowerCase()
    .replace(/['']/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function manaPoolUrl(cardName) {
  if (!cardName || cardName.startsWith('Catalog ID') || cardName.startsWith('CatalogID')) {
    return null;
  }

  return `https://manapool.com/card/${toManaPoolSlug(cardName)}?ref=${MANA_POOL_REF}`;
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
  const [connectionState, setConnectionState] = useState(() =>
    mockDeckGameState && new URLSearchParams(window.location.search).get('demo') === 'deck'
      ? 'connected'
      : 'connecting'
  );
  const [gameState, setGameState] = useState(() => {
    if (mockDeckGameState && new URLSearchParams(window.location.search).get('demo') === 'deck') {
      return mockDeckGameState;
    }

    return emptyGameState;
  });
  const [lastError, setLastError] = useState('');
  const [rescanStatus, setRescanStatus] = useState('');
  const [isRescanning, setIsRescanning] = useState(false);
  const [isBroadcaster, setIsBroadcaster] = useState(false);
  const [relayChannelId, setRelayChannelId] = useState(() => (
    window.Twitch?.ext?.onAuthorized ? '' : supabaseConfig.channelId
  ));
  const [showDecklist, setShowDecklist] = useState(false);
  const [panelOpen, setPanelOpen] = useState(
    () => window.localStorage.getItem('mtgtwitch.panelOpen') === 'true'
  );
  const [collapsedZones, setCollapsedZones] = useState({});
  const [hoveredCard, setHoveredCard] = useState(null);
  const [panelCard, setPanelCard] = useState(null);
  const [lockedPanelCardKey, setLockedPanelCardKey] = useState(null);
  const [pinPanel, setPinPanel] = useState(() => {
    if (!enableScreenDetections) {
      return false;
    }

    return window.localStorage.getItem('mtgtwitch.pinPanel') === 'true';
  });
  const [cardDetailsByCatalogId, setCardDetailsByCatalogId] = useState({});
  const [failedCatalogIds, setFailedCatalogIds] = useState({});
  const [manaPoolPriceByName, setManaPoolPriceByName] = useState({});
  const fetchedManaPoolKeys = useRef(new Set());
  const activeSupabaseChannelId = shouldUseSupabaseRelay ? relayChannelId : null;

  const fetchManaPoolPrice = useCallback(async (cardName) => {
    if (!manaPoolUrl(cardName)) {
      return;
    }

    const cacheKey = cardName.toLowerCase();
    if (fetchedManaPoolKeys.current.has(cacheKey)) {
      return;
    }
    fetchedManaPoolKeys.current.add(cacheKey);

    try {
      const response = await fetch('https://manapool.com/api/v1/card_info', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          card_names: [cardName]
        })
      });

      if (!response.ok) {
        return;
      }

      const result = await response.json();
      const card = result.cards?.[0];

      setManaPoolPriceByName((current) => ({
        ...current,
        [cacheKey]: card
          ? {
              fromPriceCents: card.from_price_cents ?? null,
              quantityAvailable: card.quantity_available ?? 0
            }
          : null
      }));
    } catch {
      // ManaPool prices are best-effort; keep card previews usable if the request fails.
    }
  }, []);

  const fetchManaPoolPricesBatch = useCallback(async (cardNames) => {
    const toFetch = cardNames.filter((name) => {
      if (!manaPoolUrl(name)) {
        return false;
      }

      const cacheKey = name.toLowerCase();
      if (fetchedManaPoolKeys.current.has(cacheKey)) {
        return false;
      }

      fetchedManaPoolKeys.current.add(cacheKey);
      return true;
    });

    if (toFetch.length === 0) {
      return;
    }

    const batchSize = 100;
    for (let index = 0; index < toFetch.length; index += batchSize) {
      const batch = toFetch.slice(index, index + batchSize);

      try {
        const response = await fetch('https://manapool.com/api/v1/card_info', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            card_names: batch
          })
        });

        if (!response.ok) {
          continue;
        }

        const result = await response.json();
        const priceMap = {};

        for (const card of result.cards ?? []) {
          priceMap[card.name.toLowerCase()] = {
            fromPriceCents: card.from_price_cents ?? null,
            quantityAvailable: card.quantity_available ?? 0
          };
        }

        for (const name of batch) {
          const cacheKey = name.toLowerCase();
          if (!(cacheKey in priceMap)) {
            priceMap[cacheKey] = null;
          }
        }

        setManaPoolPriceByName((current) => ({
          ...current,
          ...priceMap
        }));
      } catch {
        // Batch price fetch is best-effort; individual hover fallback still works.
      }
    }
  }, []);

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
      if (!activeSupabaseChannelId) {
        return undefined;
      }

      const supabase = createClient(supabaseConfig.url, supabaseConfig.anonKey);
      const channel = supabase.channel(`game-state:${activeSupabaseChannelId}`);

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
  }, [activeSupabaseChannelId]);

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

    for (const zone of opponentZones) {
      const cards = (gameState[zone.key] ?? []).map((card) => normalizeCard(card));

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

    for (const zone of opponentZones) {
      for (const card of gameState[zone.key] ?? []) {
        if (card.catalogId) {
          catalogIds.add(card.catalogId);
        }
      }
    }

    for (const card of gameState.deckCards ?? []) {
      if (card.catalogId) {
        catalogIds.add(card.catalogId);
      }
    }

    return Array.from(catalogIds);
  }, [gameState]);

  const decklistData = useMemo(() => {
    if (!gameState.deckCards?.length) {
      return null;
    }

    const typeOrder = ['Creature', 'Planeswalker', 'Instant', 'Sorcery', 'Artifact', 'Enchantment', 'Land', 'Other'];

    function getType(typeLine) {
      if (!typeLine) {
        return 'Other';
      }

      for (const type of typeOrder.slice(0, -1)) {
        if (typeLine.includes(type)) {
          return type;
        }
      }

      return 'Other';
    }

    function groupByType(cards) {
      const groups = {};

      for (const card of cards) {
        (groups[card.type] ??= []).push(card);
      }

      return typeOrder
        .filter((type) => groups[type])
        .map((type) => ({
          type,
          cards: groups[type].sort((a, b) => a.name.localeCompare(b.name))
        }));
    }

    const main = [];
    const side = [];

    for (const deckCard of gameState.deckCards) {
      const details = cardDetailsByCatalogId[deckCard.catalogId];
      const entry = {
        ...deckCard,
        name: details?.name || `Catalog ID ${deckCard.catalogId}`,
        typeLine: details?.typeLine || '',
        manaCost: details?.manaCost || '',
        imageUri: details?.imageUrl || details?.normalImageUri || '',
        type: getType(details?.typeLine || '')
      };

      (deckCard.inSideboard ? side : main).push(entry);
    }

    return {
      main: groupByType(main),
      mainCount: main.reduce((sum, card) => sum + card.quantity, 0),
      side: groupByType(side),
      sideCount: side.reduce((sum, card) => sum + card.quantity, 0)
    };
  }, [gameState.deckCards, cardDetailsByCatalogId]);
  const hasOpponentZoneCards = opponentZones.some((zone) => (zoneCards[zone.key] ?? []).length > 0);
  const screenDetectionRegions = useScreenDetections({
    enabled: isOverlay && enableScreenDetections,
    gameState
  });

  useEffect(() => {
    if (window.Twitch?.ext?.onAuthorized) {
      window.Twitch.ext.onAuthorized((auth) => {
        setIsBroadcaster(auth.role === 'broadcaster');
        if (auth.channelId) {
          setRelayChannelId(String(auth.channelId));
        }
      });
    } else {
      // Local dev outside Twitch sandbox: keep broadcaster tools reachable.
      setIsBroadcaster(true);
      setRelayChannelId(supabaseConfig.channelId);
    }
  }, []);

  useEffect(() => {
    let isCancelled = false;

    async function resolveCards() {
      const unresolvedCatalogIds = catalogIdsToResolve.filter((catalogId) => (
        !cardDetailsByCatalogId[catalogId] && !failedCatalogIds[catalogId]
      ));
      const resolvedNames = [];

      for (const catalogId of unresolvedCatalogIds) {
        if (isCancelled) {
          return;
        }

        const details = await fetchCardDetails(catalogId, { cacheFailures: true });
        if (details?.name) {
          resolvedNames.push(details.name);
        }
        await sleep(60);
      }

      if (!isCancelled) {
        fetchManaPoolPricesBatch(resolvedNames);
      }
    }

    resolveCards();

    return () => {
      isCancelled = true;
    };
  }, [cardDetailsByCatalogId, catalogIdsToResolve, failedCatalogIds, fetchCardDetails, fetchManaPoolPricesBatch]);

  const isConnected = connectionState === 'connected';
  const isPinnedPanelEnabled = enableScreenDetections && pinPanel;
  const activePreviewCard = isPinnedPanelEnabled ? panelCard : hoveredCard;

  useEffect(() => {
    if (!isOverlay) {
      return;
    }

    window.localStorage.setItem('mtgtwitch.panelOpen', String(panelOpen));
  }, [isOverlay, panelOpen]);

  useEffect(() => {
    if (!enableScreenDetections) {
      return;
    }

    window.localStorage.setItem('mtgtwitch.pinPanel', String(pinPanel));
  }, [pinPanel]);

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

  function handlePinPanelChange(event) {
    const nextPinPanel = event.target.checked;
    setPinPanel(nextPinPanel);
    setLockedPanelCardKey(null);

    if (nextPinPanel) {
      setPanelCard(hoveredCard);
      setHoveredCard(null);
    } else {
      setPanelCard(null);
    }
  }

  function handlePreviewMouseLeave() {
    if (!isPinnedPanelEnabled) {
      setHoveredCard(null);
    }
  }

  function getPreviewKey(card) {
    return `${card.catalogId ?? 'vision'}-${card.id ?? card.name}`;
  }

  function setPreviewCard(card) {
    const previewKey = getPreviewKey(card);

    if (isPinnedPanelEnabled) {
      if (lockedPanelCardKey && lockedPanelCardKey !== previewKey) {
        return false;
      }

      setPanelCard(card);
      setHoveredCard(null);
      return true;
    }

    setHoveredCard(card);
    return true;
  }

  function updatePreviewCard(catalogId, getNextCard) {
    if (isPinnedPanelEnabled) {
      setPanelCard((current) => current?.catalogId === catalogId ? getNextCard(current) : current);
      return;
    }

    setHoveredCard((current) => current?.catalogId === catalogId ? getNextCard(current) : current);
  }

  function handlePreviewClick(card) {
    if (!isPinnedPanelEnabled) {
      return;
    }

    const previewKey = getPreviewKey(card);

    if (lockedPanelCardKey === previewKey) {
      setLockedPanelCardKey(null);
      return;
    }

    if (lockedPanelCardKey) {
      return;
    }

    setPanelCard(panelCard && getPreviewKey(panelCard) === previewKey ? panelCard : card);
    setLockedPanelCardKey(previewKey);
  }

  function handleUnlockPanel() {
    setLockedPanelCardKey(null);
  }

  function handleClosePanel() {
    setLockedPanelCardKey(null);
    setPanelCard(null);
  }

  function buildDetectionRegionCard(region, catalogId, hasCatalogId) {
    return {
      id: hasCatalogId ? region.cardId ?? region.id : region.id ?? region.cardName,
      catalogId: hasCatalogId ? catalogId : null,
      name: region.cardName,
      manaCost: '',
      typeLine: region.zone,
      oracleText: '',
      imageUri: region.imageUrl ?? '',
      imageUrl: region.imageUrl ?? '',
      quantity: 1
    };
  }

  async function handleCardMouseEnter(card, event) {
    const rect = event.currentTarget.getBoundingClientRect();
    const previewWidth = 226;
    const previewLeft = isOverlay
      ? Math.max(8, rect.left - previewWidth - 8)
      : Math.min(rect.right + 8, window.innerWidth - previewWidth - 10);

    const didSetPreview = setPreviewCard({
      ...card,
      top: Math.min(rect.top, window.innerHeight - 260),
      left: previewLeft,
      loading: !cardDetailsByCatalogId[card.catalogId]
    });

    if (!didSetPreview) {
      return;
    }

    const resolvedName = cardDetailsByCatalogId[card.catalogId]?.name ?? card.name;
    fetchManaPoolPrice(resolvedName);

    if (!card.catalogId || cardDetailsByCatalogId[card.catalogId]) {
      return;
    }

    const details = await fetchCardDetails(card.catalogId, { cacheFailures: false });
    if (details) {
      updatePreviewCard(card.catalogId, (current) => current?.catalogId === card.catalogId
        ? { ...current, ...details, loading: false }
        : current);
    } else {
      updatePreviewCard(card.catalogId, (current) => current?.catalogId === card.catalogId
        ? { ...current, ...buildPlaceholderCard(card), loading: false }
        : current);
    }
  }

  async function handleDetectionRegionMouseEnter(region) {
    const previewWidth = 226;
    const catalogId = Number(region.catalogId);
    const hasCatalogId = Number.isInteger(catalogId) && catalogId > 0;

    const card = buildDetectionRegionCard(region, catalogId, hasCatalogId);
    fetchManaPoolPrice(region.cardName);
    const regionLeft = region.bbox.x * window.innerWidth;
    const regionTop = region.bbox.y * window.innerHeight;
    const previewLeft = Math.min(
      window.innerWidth - previewWidth - 10,
      Math.max(8, regionLeft + (region.bbox.w * window.innerWidth) + 8)
    );

    const didSetPreview = setPreviewCard({
      ...card,
      top: Math.min(regionTop, window.innerHeight - 260),
      left: previewLeft,
      loading: hasCatalogId && !cardDetailsByCatalogId[card.catalogId]
    });

    if (!didSetPreview) {
      return;
    }

    if (!hasCatalogId) {
      return;
    }

    if (cardDetailsByCatalogId[card.catalogId]) {
      return;
    }

    const details = await fetchCardDetails(card.catalogId, { cacheFailures: false });
    updatePreviewCard(card.catalogId, (current) => current?.catalogId === card.catalogId
      ? { ...current, ...(details ?? buildPlaceholderCard(card)), loading: false }
      : current);
  }

  return (
    <main className={[
      'extension-shell',
      isOverlay && 'extension-overlay-shell',
      isOverlay && panelOpen && 'extension-overlay-shell--open'
    ].filter(Boolean).join(' ')}
    >
      <section className="extension-panel" aria-labelledby="extension-title">
        <header className="extension-header">
          <div>
            <h1 id="extension-title">MTGO Zones</h1>
            <p>Game {gameState.gameId ?? 'none'}</p>
          </div>

          <div className="extension-actions">
            {enableScreenDetections && (
              <label className="extension-pin-toggle">
                <input
                  type="checkbox"
                  checked={pinPanel}
                  onChange={handlePinPanelChange}
                />
                <span>Pin card</span>
              </label>
            )}

            {gameState.deckCards?.length > 0 && (
              <button className="extension-icon-button" type="button" onClick={() => setShowDecklist((current) => !current)}>
                <BookOpen aria-hidden="true" size={14} />
                <span>{showDecklist ? 'Zones' : 'Decklist'}</span>
              </button>
            )}

            {isOverlay && (
              <button
                className="extension-icon-button"
                type="button"
                onClick={() => setPanelOpen((open) => !open)}
                aria-label={panelOpen ? 'Collapse panel' : 'Keep panel open'}
              >
                {panelOpen
                  ? <PanelRightClose aria-hidden="true" size={14} />
                  : <PanelRightOpen aria-hidden="true" size={14} />}
              </button>
            )}

            {isBroadcaster && (
              <button className="extension-icon-button" type="button" onClick={handleReconnect} disabled={isRescanning}>
                <RefreshCw aria-hidden="true" size={14} />
                <span>{isRescanning ? '...' : 'Reconnect'}</span>
              </button>
            )}

            <span className={`extension-status ${connectionState}`}>
              {isConnected ? <Activity aria-hidden="true" size={13} /> : <CircleAlert aria-hidden="true" size={13} />}
              {connectionState}
            </span>
          </div>
        </header>

        {(lastError || (isBroadcaster && rescanStatus)) && (
          <div className="extension-message">
            {lastError || rescanStatus}
          </div>
        )}

        {showDecklist ? (
          <DecklistView
            decklistData={decklistData}
            onCardHover={handleCardMouseEnter}
            onCardLeave={handlePreviewMouseLeave}
            onCardClick={handlePreviewClick}
          />
        ) : (
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
                            onMouseLeave={handlePreviewMouseLeave}
                            onClick={() => handlePreviewClick(card)}
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

            {hasOpponentZoneCards && (
              <>
                <div className="extension-zone-separator">Opponent</div>
                {opponentZones.map((zone) => {
                  const cards = zoneCards[zone.key] ?? [];
                  const isCollapsed = collapsedZones[zone.key];

                  if (cards.length === 0) {
                    return null;
                  }

                  return (
                    <section className="extension-zone" key={zone.key}>
                      <button className="extension-zone-header" type="button" onClick={() => toggleZone(zone.key)}>
                        {isCollapsed ? <ChevronRight aria-hidden="true" size={15} /> : <ChevronDown aria-hidden="true" size={15} />}
                        <span>{zone.label}</span>
                        <span className="extension-count">{countCards(cards)}</span>
                      </button>

                      {!isCollapsed && (
                        <div className="extension-card-list">
                          {cards.map((card) => (
                            <button
                              className="extension-card-row"
                              type="button"
                              key={`${zone.key}-${card.catalogId}-${card.name}`}
                              onMouseEnter={(event) => handleCardMouseEnter(card, event)}
                              onMouseLeave={handlePreviewMouseLeave}
                              onClick={() => handlePreviewClick(card)}
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
                          ))}
                        </div>
                      )}
                    </section>
                  );
                })}
              </>
            )}
          </div>
        )}
      </section>

      {screenDetectionRegions.length > 0 && (
        <div className="extension-detection-layer" aria-hidden="true">
          {screenDetectionRegions.map((region) => (
            <button
              className="extension-detection-region"
              type="button"
              key={region.id}
              style={{
                left: `${region.hitbox.x * 100}%`,
                top: `${region.hitbox.y * 100}%`,
                width: `${region.hitbox.w * 100}%`,
                height: `${region.hitbox.h * 100}%`
              }}
              onMouseEnter={() => handleDetectionRegionMouseEnter(region)}
              onMouseLeave={handlePreviewMouseLeave}
              onClick={() => {
                const clickCatalogId = Number(region.catalogId);
                const hasClickCatalogId = Number.isInteger(clickCatalogId) && clickCatalogId > 0;
                handlePreviewClick(buildDetectionRegionCard(region, clickCatalogId, hasClickCatalogId));
              }}
              tabIndex={-1}
            />
          ))}
        </div>
      )}

      {activePreviewCard && (
        <CardPreview
          card={activePreviewCard}
          cachedDetails={activePreviewCard.catalogId ? cardDetailsByCatalogId[activePreviewCard.catalogId] : undefined}
          manaPoolPrice={getManaPoolPrice(activePreviewCard, cardDetailsByCatalogId, manaPoolPriceByName)}
          docked={isPinnedPanelEnabled}
          locked={Boolean(lockedPanelCardKey)}
          onUnlock={handleUnlockPanel}
          onClose={handleClosePanel}
        />
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

function CardPreview({ card, cachedDetails, manaPoolPrice, docked = false, locked = false, onUnlock, onClose }) {
  const details = { ...card, ...cachedDetails };
  const imageUri = details.imageUrl || details.normalImageUri || details.imageUri;
  const buyUrl = manaPoolUrl(details.name);
  let priceLabel = null;

  if (buyUrl) {
    if (manaPoolPrice === null || manaPoolPrice?.quantityAvailable === 0 || manaPoolPrice?.fromPriceCents === null) {
      priceLabel = 'Out of stock';
    } else if (manaPoolPrice?.fromPriceCents !== undefined) {
      priceLabel = `From $${(manaPoolPrice.fromPriceCents / 100).toFixed(2)}`;
    }
  }

  const className = docked
    ? 'extension-card-preview extension-card-preview--docked'
    : 'extension-card-preview';
  const style = docked ? undefined : { top: card.top, left: card.left };

  return (
    <aside className={className} style={style}>
      {docked && (
        <div className="extension-card-preview-toolbar">
          <span>{locked ? 'Locked' : 'Pinned'}</span>
          <button type="button" onClick={locked ? onUnlock : onClose}>
            {locked ? 'Unlock' : 'Close'}
          </button>
        </div>
      )}
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
        {buyUrl && (
          <a
            className="extension-buy-link"
            href={buyUrl}
            target="_blank"
            rel="noreferrer"
          >
            <span>Buy on ManaPool</span>
            {priceLabel && <span className="extension-buy-price">{priceLabel}</span>}
          </a>
        )}
      </div>
    </aside>
  );
}

function DecklistView({ decklistData, onCardHover, onCardLeave, onCardClick }) {
  if (!decklistData) {
    return (
      <div className="extension-decklist">
        <div className="extension-empty">No deck loaded yet.</div>
      </div>
    );
  }

  return (
    <div className="extension-decklist" aria-live="polite">
      <DecklistSection
        label="MAIN DECK"
        count={decklistData.mainCount}
        groups={decklistData.main}
        onCardHover={onCardHover}
        onCardLeave={onCardLeave}
        onCardClick={onCardClick}
      />
      <DecklistSection
        label="SIDEBOARD"
        count={decklistData.sideCount}
        groups={decklistData.side}
        onCardHover={onCardHover}
        onCardLeave={onCardLeave}
        onCardClick={onCardClick}
      />
    </div>
  );
}

function DecklistSection({ label, count, groups, onCardHover, onCardLeave, onCardClick }) {
  return (
    <section>
      <div className="extension-decklist-section-label">
        <span>{label}</span>
        <span>{count}</span>
      </div>

      {groups.length === 0 ? (
        <div className="extension-empty">No cards</div>
      ) : (
        groups.map((group) => (
          <div key={group.type}>
            <div className="extension-decklist-type-label">{group.type}</div>
            <div className="extension-card-list">
              {group.cards.map((deckCard) => {
                const card = normalizeDecklistCard(deckCard);

                return (
                  <button
                    className="extension-card-row"
                    type="button"
                    key={`${label}-${deckCard.catalogId}-${deckCard.name}`}
                    onMouseEnter={(event) => onCardHover(card, event)}
                    onMouseLeave={onCardLeave}
                    onClick={() => onCardClick(card)}
                  >
                    <span className="extension-quantity">{deckCard.quantity}</span>
                    <span className="extension-card-main">
                      <span className="extension-card-title">
                        <span>{deckCard.name}</span>
                        <ManaCost manaCost={deckCard.manaCost} />
                      </span>
                      <span className="extension-card-subtitle">{deckCard.typeLine || `Catalog ID ${deckCard.catalogId}`}</span>
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        ))
      )}
    </section>
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

function normalizeDecklistCard(deckCard) {
  return {
    id: deckCard.catalogId,
    catalogId: deckCard.catalogId,
    name: deckCard.name,
    manaCost: deckCard.manaCost,
    typeLine: deckCard.typeLine,
    imageUri: deckCard.imageUri,
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

function getManaPoolPrice(card, cardDetailsByCatalogId, manaPoolPriceByName) {
  const cardName = card.catalogId
    ? cardDetailsByCatalogId[card.catalogId]?.name ?? card.name
    : card.name;

  return cardName ? manaPoolPriceByName[cardName.toLowerCase()] : undefined;
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
