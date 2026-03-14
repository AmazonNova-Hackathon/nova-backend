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
    title: 'End-to-End Security',
    desc: 'Your health data is encrypted with AES-256 at rest and TLS 1.3 in transit. Zero-knowledge architecture ensures only you and your family can access your records.',
  },
  {
    icon: '🏥',
    color: 'hsla(200, 85%, 55%, 0.1)',
    iconColor: 'hsl(200, 85%, 65%)',
    title: 'FHIR HL7 Standardization',
    desc: 'All health records follow the international FHIR R4 standard. Interoperable with hospitals, labs, and insurance providers across the globe.',
  },
  {
    icon: '🤖',
    color: 'hsla(45, 95%, 60%, 0.1)',
    iconColor: 'var(--solar-amber)',
    title: 'Amazon Nova AI Engine',
    desc: 'Powered by Amazon Nova — proactive health insights, trend analysis, and anomaly detection without you lifting a finger.',
  },
  {
    icon: '👨‍👩‍👧‍👦',
    color: 'hsla(150, 60%, 45%, 0.1)',
    iconColor: 'var(--healing-green)',
    title: 'Unified Family Hub',
    desc: 'One dashboard for your entire family. Track each member\'s health records, observations, and follow-ups — all in one place.',
  },
  {
    icon: '📋',
    color: 'hsla(270, 70%, 65%, 0.1)',
    iconColor: 'hsl(270, 70%, 75%)',
    title: 'Automated Report Parsing',
    desc: 'Upload a lab report PDF. Nova OCR extracts, structures, and FHIR-codes the results automatically. No manual entry needed.',
  },
  {
    icon: '🎙️',
    color: 'hsla(0, 80%, 65%, 0.1)',
    iconColor: 'var(--rose-alert)',
    title: 'Voice Sanctuary',
    desc: 'Ask your AI doctor anything — symptoms, medication interactions, lab results — via natural conversation powered by Amazon Bedrock Agents.',
  },
];

const steps = [
  {
    num: '1',
    title: 'Create Your Sanctuary',
    desc: 'Sign up with a family name. Your FHIR data record is created instantly on AWS.',
  },
  {
    num: '2',
    title: 'Add Family Members',
    desc: 'Add each family member with their profile. Profiles are stored securely in DynamoDB.',
  },
  {
    num: '3',
    title: 'Upload & Discover',
    desc: 'Upload lab reports. Nova AI parses, analyzes, and surfaces proactive health insights.',
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
            <div style={{ fontSize: '0.6rem', color: 'var(--chetana-teal)', fontWeight: 700, letterSpacing: '0.1em' }}>PATIENT PORTAL</div>
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
            Your Family's Health,<br />
            <span className="gradient-text">Intelligently Guarded</span>
          </h1>
          <p className="hero-sub">
            Chetana is a FHIR-native health platform that gives every family enterprise-grade medical records management, AI-powered insights, and end-to-end encryption — for free.
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
          Healthcare records that meet<br />global standards
        </h2>
        <p className="section-sub">
          Built on AWS with FHIR HL7 R4, military-grade encryption, and Amazon Nova AI — the same infrastructure that powers the world's leading health systems.
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
            Your family deserves<br />
            <span style={{ color: 'var(--chetana-teal)' }}>world-class healthcare records</span>
          </h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '2.5rem', fontSize: '1rem' }}>
            No credit card. No installation. Instant FHIR record creation.
          </p>
          <button
            className="btn-primary"
            style={{ fontSize: '1rem', padding: '1rem 2.5rem' }}
            onClick={() => setShowSignUp(true)}
          >
            Create Your Sanctuary →
          </button>
        </div>
      </section>

      {/* ── Footer ── */}
      <footer className="landing-footer">
        <div>© 2025 Chetana Patient Portal · FHIR HL7 R4 Compliant</div>
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
