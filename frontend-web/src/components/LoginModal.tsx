import React, { useState } from 'react';
import { session as sessionHelper } from '../services/api';

interface LoginModalProps {
  onClose: () => void;
  onSuccess: () => void;
  onSignUp: () => void;
}

// ─── Demo credentials ─────────────────────────────────────────────────────────
const DEMO_EMAIL    = 'demo@chetana.app';
const DEMO_PASSWORD = 'chetana123';

// Pre-seeded Sharma Family — created in production DynamoDB
const DEMO_SESSION = {
  familyId:   'cf7eb826-c613-4850-9cc0-b960ebfd7f5b',
  familyName: 'Sharma Family',
  members:    [], // fetched live from API after login
};

export const LoginModal: React.FC<LoginModalProps> = ({ onClose, onSuccess, onSignUp }) => {
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState('');

  const handleLogin = () => {
    if (!email.trim() || !password.trim()) {
      setError('Please enter your email and password.');
      return;
    }
    setLoading(true);
    setError('');

    setTimeout(() => {
      const emailNorm = email.trim().toLowerCase();

      // ── Demo account: always works, seeds Sharma Family ──────────────────
      if (emailNorm === DEMO_EMAIL && password === DEMO_PASSWORD) {
        sessionHelper.save(DEMO_SESSION);
        setLoading(false);
        onSuccess();
        return;
      }

      // ── Returning user: check if session already exists on this device ───
      const stored = sessionHelper.get();
      if (stored) {
        setLoading(false);
        onSuccess();
        return;
      }

      // ── No session and wrong credentials ─────────────────────────────────
      setLoading(false);
      setError(
        'No account found. Try the demo: demo@chetana.app / chetana123, or sign up.'
      );
    }, 700);
  };

  return (
    <div className="modal-overlay" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal-box">
        <div className="modal-header">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
            <div>
              <div className="modal-title">Welcome Back</div>
              <div className="modal-subtitle">Sign in to your family sanctuary.</div>
            </div>
            <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '1.25rem', padding: '0.25rem' }}>✕</button>
          </div>
        </div>

        <div className="modal-body">
          {error && <div className="error-banner">{error}</div>}

          {/* Demo credentials hint */}
          <div style={{ padding: '0.75rem 1rem', background: 'hsla(175,100%,45%,0.06)', border: '1px solid hsla(175,100%,45%,0.15)', borderRadius: '12px', marginBottom: '1.25rem', fontSize: '0.8rem' }}>
            <div style={{ color: 'var(--chetana-teal)', fontWeight: 700, marginBottom: '0.25rem' }}>🧪 Demo Account</div>
            <div style={{ color: 'var(--text-secondary)' }}>
              Email: <strong style={{ color: 'var(--text-primary)' }}>{DEMO_EMAIL}</strong><br />
              Password: <strong style={{ color: 'var(--text-primary)' }}>{DEMO_PASSWORD}</strong>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input
              className="form-input"
              type="email"
              placeholder={DEMO_EMAIL}
              value={email}
              onChange={e => setEmail(e.target.value)}
              autoFocus
            />
          </div>

          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              className="form-input"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={e => setPassword(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleLogin()}
            />
          </div>

          <p style={{ fontSize: '0.775rem', color: 'var(--text-muted)', marginBottom: '1.5rem' }}>
            🔒 Sessions are stored locally on this device. No passwords are transmitted.
          </p>

          <button
            className="btn-primary"
            style={{ width: '100%', justifyContent: 'center', marginBottom: '1rem' }}
            onClick={handleLogin}
            disabled={loading}
          >
            {loading ? 'Signing in...' : 'Sign In →'}
          </button>

          <div style={{ textAlign: 'center', fontSize: '0.875rem', color: 'var(--text-muted)' }}>
            Don't have a sanctuary?{' '}
            <button
              onClick={onSignUp}
              style={{ background: 'none', border: 'none', color: 'var(--chetana-teal)', cursor: 'pointer', fontWeight: 600, fontSize: 'inherit' }}
            >
              Sign up free →
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
