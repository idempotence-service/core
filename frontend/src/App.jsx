import { useEffect, useState } from 'react';

export default function App() {
  const [backendStatus, setBackendStatus] = useState('loading');

  useEffect(() => {
    fetch('/api/health')
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        return response.json();
      })
      .then((data) => setBackendStatus(data.status ?? 'unknown'))
      .catch(() => setBackendStatus('unreachable'));
  }, []);

  return (
    <main className="page">
      <section className="card">
        <p className="eyebrow">demo stack</p>
        <h1>Java backend + React frontend + Kafka + Postgres</h1>
        <p className="description">
          Этот фронт добавлен как минимальная заглушка, чтобы стек поднимался целиком и был готов к деплою с выбором ветки или тега.
        </p>

        <div className="grid">
          <article>
            <span>Frontend</span>
            <strong>running</strong>
          </article>
          <article>
            <span>Backend /health</span>
            <strong>{backendStatus}</strong>
          </article>
          <article>
            <span>Postgres</span>
            <strong>dockerized</strong>
          </article>
          <article>
            <span>Kafka</span>
            <strong>dockerized</strong>
          </article>
        </div>
      </section>
    </main>
  );
}
