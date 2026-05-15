import { Activity, CircleAlert, RadioTower, RefreshCw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

const websocketUrl = import.meta.env.VITE_BACKEND_WS_URL ?? 'ws://localhost:8080/ws/game-state';
const backendApiUrl = import.meta.env.VITE_BACKEND_API_URL
  ?? websocketUrl.replace(/^ws/, 'http').replace('/ws/game-state', '');
const emptyGameState = {
  hand: [],
  battlefield: [],
  graveyard: [],
  exile: [],
  updatedAt: null
};

const zones = [
  { key: 'hand', label: 'Hand' },
  { key: 'battlefield', label: 'Battlefield' },
  { key: 'graveyard', label: 'Graveyard' },
  { key: 'exile', label: 'Exile' }
];

export default function App() {
  const [connectionState, setConnectionState] = useState('connecting');
  const [gameState, setGameState] = useState(emptyGameState);
  const [lastError, setLastError] = useState('');
  const [resolvedLogPath, setResolvedLogPath] = useState('');
  const [rescanStatus, setRescanStatus] = useState('');
  const [isRescanning, setIsRescanning] = useState(false);
  const zoneGridRef = useRef(null);

  useEffect(() => {
    const socket = new WebSocket(websocketUrl);

    socket.addEventListener('open', () => {
      setConnectionState('connected');
    });

    socket.addEventListener('message', (event) => {
      try {
        const nextGameState = JSON.parse(event.data);
        setGameState({
          hand: nextGameState.hand ?? [],
          battlefield: nextGameState.battlefield ?? [],
          graveyard: nextGameState.graveyard ?? [],
          exile: nextGameState.exile ?? [],
          updatedAt: nextGameState.updatedAt ?? null
        });
        setLastError('');
      } catch {
        setLastError('Received a WebSocket message that was not valid game-state JSON.');
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

  useEffect(() => {
    const zoneGrid = zoneGridRef.current;
    if (zoneGrid) {
      zoneGrid.scrollTop = zoneGrid.scrollHeight;
    }
  }, [gameState]);

  const isConnected = connectionState === 'connected';
  const updatedAt = gameState.updatedAt
    ? new Date(gameState.updatedAt).toLocaleTimeString()
    : 'No parsed events yet';

  async function handleReconnect() {
    setIsRescanning(true);
    setRescanStatus('');

    try {
      const response = await fetch(`${backendApiUrl}/api/rescan-log`, {
        method: 'POST'
      });
      const result = await response.json();

      if (!response.ok || !result.watching) {
        setResolvedLogPath(result.path ?? '');
        setRescanStatus(result.message ?? 'No MTGO log file was found.');
        return;
      }

      setResolvedLogPath(result.path);
      setRescanStatus(result.message ?? 'Reconnected to MTGO log.');
    } catch {
      setRescanStatus('Unable to contact the backend rescan endpoint.');
    } finally {
      setIsRescanning(false);
    }
  }

  return (
    <main className="extension-shell">
      <section className="state-viewer" aria-labelledby="extension-title">
        <header className="viewer-header">
          <div className="title-lockup">
            <RadioTower aria-hidden="true" size={24} />
            <div>
              <h1 id="extension-title">MTGO Game State</h1>
              <p>{websocketUrl}</p>
            </div>
          </div>

          <div className="header-actions">
            <button className="icon-button" type="button" onClick={handleReconnect} disabled={isRescanning}>
              <RefreshCw aria-hidden="true" size={16} />
              <span>{isRescanning ? 'Reconnecting' : 'Reconnect'}</span>
            </button>

            <div className={`connection-badge ${connectionState}`}>
              {isConnected ? (
                <Activity aria-hidden="true" size={16} />
              ) : (
                <CircleAlert aria-hidden="true" size={16} />
              )}
              <span>{connectionState}</span>
            </div>
          </div>
        </header>

        <div className="state-meta">
          <div>
            <span>Updated: {updatedAt}</span>
            {resolvedLogPath && <span className="log-path">Log: {resolvedLogPath}</span>}
          </div>
          {rescanStatus && <span>{rescanStatus}</span>}
          {lastError && <span className="state-error">{lastError}</span>}
        </div>

        <div className="zone-grid" ref={zoneGridRef} aria-live="polite">
          {zones.map((zone) => (
            <section className="zone-panel" key={zone.key} aria-labelledby={`${zone.key}-title`}>
              <header className="zone-header">
                <h2 id={`${zone.key}-title`}>{zone.label}</h2>
                <span>{gameState[zone.key].length}</span>
              </header>

              {gameState[zone.key].length === 0 ? (
                <p className="empty-state">No cards detected.</p>
              ) : (
                <ul className="card-list">
                  {gameState[zone.key].map((cardName, index) => (
                    <li key={`${zone.key}-${cardName}-${index}`}>{cardName}</li>
                  ))}
                </ul>
              )}
            </section>
          ))}
        </div>
      </section>
    </main>
  );
}
