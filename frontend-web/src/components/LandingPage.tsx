import React, { useState } from 'react';
import { SignUpModal } from './SignUpModal';
import { LoginModal } from './LoginModal';

interface LandingPageProps {
  onEnterApp: () => void;
  theme: 'dark' | 'light';
  onToggleTheme: () => void;
}

const features = [
  {
    icon: '🔐',
    color: 'hsla(175, 100%, 45%, 0.1)',
    iconColor: 'var(--chetana-teal)',
    title: 'Your Data, Your Control',
    desc: 'AES-256 encryption at rest and TLS 1.3 in transit. Only your family can access your records — we can\'t see them either.',
  },
  {
    icon: '🏥',
    color: 'hsla(200, 85%, 55%, 0.1)',
    iconColor: 'hsl(200, 85%, 65%)',
    title: 'FHIR HL7 Standardization',
    desc: 'Every result is stored in FHIR R4 — the same international standard used by hospitals and insurers worldwide. Your data is truly portable.',
  },
  {
    icon: '🤖',
    color: 'hsla(45, 95%, 60%, 0.1)',
    iconColor: 'var(--solar-amber)',
    title: 'Powered by Amazon Nova',
    desc: 'Don\'t just see "H" or "L" next to a lab value. Nova explains what it means, tracks how it\'s changing, and flags what needs your attention.',
  },
  {
    icon: '👨‍👩‍👧‍👦',
    color: 'hsla(150, 60%, 45%, 0.1)',
    iconColor: 'var(--healing-green)',
    title: 'One Family, One Dashboard',
    desc: 'Track reports, trends, and follow-ups for everyone — yourself, parents, spouse, kids. Health is a family affair.',
  },
  {
    icon: '📋',
    color: 'hsla(270, 70%, 65%, 0.1)',
    iconColor: 'hsl(270, 70%, 75%)',
    title: 'Upload. Done. Understood.',
    desc: 'Photograph a lab report. Nova extracts every test result, maps it to medical codes, and files it — in seconds.',
  },
  {
    icon: '🎙️',
    color: 'hsla(0, 80%, 65%, 0.1)',
    iconColor: 'var(--rose-alert)',
    title: 'Ask in Your Language',
    desc: 'Ask Nova about your health in Hindi, Marathi, Tamil, or English. It answers in the language you think in.',
  },
];

const steps = [
  {
    num: '1',
    title: 'Create Your Family\'s Health Profile',
    desc: 'One account for everyone. Mom\'s thyroid, Dad\'s sugar, your annual checkup — all in one place.',
  },
  {
    num: '2',
    title: 'Upload Any Lab Report',
    desc: 'PDF or photo — it doesn\'t matter. Nova AI reads it in seconds and structures every result.',
  },
  {
    num: '3',
    title: 'Get Insights That Matter',
    desc: 'No more googling "is 110 glucose bad?" Nova spots trends, flags concerns, and reminds you when to retest.',
  },
];

export const LandingPage: React.FC<LandingPageProps> = ({ onEnterApp, theme, onToggleTheme }) => {
  const [showSignUp, setShowSignUp] = useState(false);
  const [showLogin, setShowLogin] = useState(false);

  return (
    <div className="landing-page">
      {/* ── Nav ── */}
      <nav className="landing-nav">
        <div className="landing-nav-logo">
          <div className="logo-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
            </svg>
          </div>
          <div>
            <div style={{ fontSize: '1.125rem', fontWeight: 800, letterSpacing: '-0.03em', fontFamily: 'var(--font-header)' }}>CHETANA</div>
            <div style={{ fontSize: '0.6rem', color: 'var(--chetana-teal)', fontWeight: 600, letterSpacing: '0.04em' }}>Awaken to your health</div>
          </div>
        </div>

        <div className="landing-nav-links">
          {/* Theme toggle */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            <span>☀️</span>
            <button
              className="theme-toggle"
              data-active={theme === 'dark' ? 'true' : 'false'}
              onClick={onToggleTheme}
              aria-label="Toggle theme"
            />
            <span>🌙</span>
          </div>

          <button className="btn-ghost" onClick={() => setShowLogin(true)} style={{ padding: '0.625rem 1.25rem', fontSize: '0.875rem' }}>
            Sign In
          </button>
          <button className="btn-primary" onClick={() => setShowSignUp(true)} style={{ padding: '0.625rem 1.25rem', fontSize: '0.875rem' }}>
            Get Started Free
          </button>
        </div>
      </nav>

      {/* ── Hero ── */}
      <section className="landing-hero">
        <div className="hero-glow" />
        <div style={{ position: 'relative', zIndex: 1, maxWidth: 700 }}>
          <div className="hero-badge">
            <span>⚡</span> Powered by Amazon Nova + FHIR HL7 R4
          </div>
          <h1 className="hero-headline">
            Every Lab Report.<br />
            Every Family Member.<br />
            <span className="gradient-text">One AI Companion.</span>
          </h1>
          <p className="hero-sub">
            Most families have a drawer full of lab reports no one understands. Chetana turns them into structured health records with AI-powered insights you can actually act on.
          </p>
          <div className="hero-cta-group">
            <button className="btn-primary" style={{ fontSize: '1rem', padding: '1rem 2.25rem' }} onClick={() => setShowSignUp(true)}>
              Create Your Sanctuary →
            </button>
            <button className="btn-ghost" style={{ fontSize: '1rem' }} onClick={() => setShowLogin(true)}>
              Sign In
            </button>
          </div>

          <div className="hero-stats">
            {[
              { num: 'FHIR R4', label: 'International Standard' },
              { num: 'AES-256', label: 'Encryption at Rest' },
              { num: 'AWS', label: 'Bedrock + DynamoDB' },
              { num: '100%', label: 'Serverless & Scalable' },
            ].map(s => (
              <div className="stat-item" key={s.label}>
                <div className="stat-number">{s.num}</div>
                <div className="stat-label">{s.label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Features ── */}
      <section className="landing-features">
        <div className="section-eyebrow">Why Chetana</div>
        <h2 className="section-title">
          Healthcare intelligence,<br />not just record-keeping
        </h2>
        <p className="section-sub">
          Go beyond storing PDFs. Chetana gives your family structured data, trend tracking, and proactive AI insights.
        </p>
        <div className="features-grid">
          {features.map(f => (
            <div className="feature-card" key={f.title}>
              <div className="feature-icon" style={{ background: f.color, color: f.iconColor }}>
                {f.icon}
              </div>
              <div className="feature-title">{f.title}</div>
              <div className="feature-desc">{f.desc}</div>
            </div>
          ))}
        </div>
      </section>

      {/* ── How it works ── */}
      <section className="landing-how">
        <div className="landing-how-inner">
          <div className="section-eyebrow">Getting Started</div>
          <h2 className="section-title">Up and running in 60 seconds</h2>
          <p className="section-sub" style={{ margin: '0 auto 0' }}>
            No app download. No insurance forms. Just your family's health data, structured and secured.
          </p>
          <div className="steps-grid">
            {steps.map(s => (
              <div className="step-item" key={s.num}>
                <div className="step-num">{s.num}</div>
                <div className="step-title">{s.title}</div>
                <div className="step-desc">{s.desc}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA Footer ── */}
      <section className="landing-cta-section">
        <div style={{ position: 'relative', zIndex: 1 }}>
          <div className="section-eyebrow">Start Free Today</div>
          <h2 className="section-title" style={{ marginBottom: '1rem' }}>
            Stop losing lab reports.<br />
            <span style={{ color: 'var(--chetana-teal)' }}>Start understanding them.</span>
          </h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '2.5rem', fontSize: '1rem' }}>
            Upload your first report and see Chetana in action.
          </p>
          <button
            className="btn-primary"
            style={{ fontSize: '1rem', padding: '1rem 2.5rem' }}
            onClick={() => setShowSignUp(true)}
          >
            Try It Now →
          </button>
        </div>
      </section>

      {/* ── Footer ── */}
      <footer className="landing-footer">
        <div>© 2025 Chetana · Awaken to your health · FHIR HL7 R4 Compliant</div>
        <div style={{ display: 'flex', gap: '1.5rem' }}>
          <span>Privacy Policy</span>
          <span>HIPAA Notice</span>
          <span>Contact</span>
        </div>
      </footer>

      {/* ── Modals ── */}
      {showSignUp && (
        <SignUpModal
          onClose={() => setShowSignUp(false)}
          onSuccess={() => { setShowSignUp(false); onEnterApp(); }}
        />
      )}
      {showLogin && (
        <LoginModal
          onClose={() => setShowLogin(false)}
          onSuccess={() => { setShowLogin(false); onEnterApp(); }}
          onSignUp={() => { setShowLogin(false); setShowSignUp(true); }}
        />
      )}
    </div>
  );
};
