import React, { useState, useEffect } from 'react';
import './App.css';
import { api, session as sessionHelper } from './services/api';
import type { FamilyMember, Insight, Report, Observation, Session } from './services/api';
import { LandingPage } from './components/LandingPage';

// ─── Sub-components ───────────────────────────────────────────────────────────

const FamilyMemberCard = ({ member, active = false, onClick }: { member: FamilyMember; active?: boolean; onClick: () => void }) => (
  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', cursor: 'pointer', gap: '0.5rem' }} onClick={onClick}>
    <div className={`member-avatar ${active ? 'active' : ''}`}>
      <span style={{ fontSize: '1.375rem', fontWeight: 700, color: active ? 'var(--chetana-teal)' : 'var(--text-secondary)' }}>
        {member.name[0].toUpperCase()}
      </span>
    </div>
    <span className="member-name">{member.name.split(' ')[0]}</span>
  </div>
);

const HealthTimeline = ({ observations }: { observations: Observation[] }) => {
  if (observations.length < 2) return null;
  const points = observations.map((o, i) =>
    `${(i / (observations.length - 1)) * 1000} ${150 - Math.min((o.value / 200) * 150, 145)}`
  ).join(' ');

  return (
    <div style={{ position: 'relative', height: '180px', marginTop: '1.5rem' }}>
      <div style={{ position: 'absolute', top: '30px', left: 0, right: 0, height: '50px', background: 'hsla(150, 60%, 45%, 0.03)', borderRadius: '12px', border: '1px dashed hsla(150, 60%, 45%, 0.15)' }}>
        <span style={{ position: 'absolute', right: '15px', top: '15px', fontSize: '0.65rem', color: 'var(--healing-green)', opacity: 0.6, fontWeight: 700, letterSpacing: '0.1em' }}>NOMINAL RANGE</span>
      </div>
      <svg width="100%" height="150" viewBox="0 0 1000 150" preserveAspectRatio="none">
        <defs>
          <linearGradient id="line-grad" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="var(--chetana-teal)" stopOpacity="0.4" />
            <stop offset="100%" stopColor="var(--chetana-teal)" stopOpacity="1" />
          </linearGradient>
        </defs>
        <path d={`M ${points}`} fill="none" stroke="url(#line-grad)" strokeWidth="4" strokeLinecap="round" />
        {observations.map((o, i) => (
          <circle key={o.id} cx={(i / (observations.length - 1)) * 1000} cy={150 - Math.min((o.value / 200) * 150, 145)} r={i === observations.length - 1 ? 7 : 4} fill="var(--chetana-teal)" className={i === observations.length - 1 ? 'pulse' : ''} />
        ))}
      </svg>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '1rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
        {observations.map((o, i) => (
          <span key={o.id} style={{ fontWeight: i === observations.length - 1 ? 700 : 400, color: i === observations.length - 1 ? 'var(--text-primary)' : 'var(--text-secondary)' }}>
            {o.date.split('T')[0].split('-').slice(1).join('/')}
          </span>
        ))}
      </div>
    </div>
  );
};

const Typewriter = ({ text, delay = 18 }: { text: string; delay?: number }) => {
  const [displayText, setDisplayText] = useState('');
  const [idx, setIdx] = useState(0);
  useEffect(() => { setDisplayText(''); setIdx(0); }, [text]);
  useEffect(() => {
    if (idx < text.length) {
      const t = setTimeout(() => { setDisplayText(p => p + text[idx]); setIdx(p => p + 1); }, delay + Math.random() * 12);
      return () => clearTimeout(t);
    }
  }, [idx, delay, text]);
  return <span>{displayText}</span>;
};

const InsightCard = ({ insight }: { insight: Insight }) => (
  <div className="shimmer-container" style={{ padding: '1.5rem', borderRadius: '20px', background: 'hsla(0,0%,100%,0.02)', border: '1px solid var(--glass-border)' }}>
    <div className="shimmer-overlay" />
    <div className="card-title" style={{ color: insight.severity === 'urgent' ? 'var(--solar-amber)' : 'var(--chetana-teal)', marginBottom: '0.75rem', fontSize: '1rem' }}>
      {insight.severity === 'urgent' ? '🚨' : insight.severity === 'attention' ? '⚠️' : '✨'} {insight.title}
    </div>
    <div style={{ fontSize: '0.9375rem', color: 'var(--text-primary)', lineHeight: 1.6, opacity: 0.9 }}>
      <Typewriter text={insight.content} />
    </div>
  </div>
);

const ReportCard = ({ report, onRefresh }: { report: Report; onRefresh: () => void }) => {
  const { appSession, selectedMember, setError } = (window as any).appGlobals;
  
  const handleView = async () => {
    try {
      const url = await api.getReportDownloadUrl(appSession.familyId, selectedMember.id, report.reportId);
      window.open(url, '_blank');
    } catch { setError('Could not view report.'); }
  };

  const handleDelete = async () => {
    if (!window.confirm('Are you sure you want to delete this report? This will also remove its extracted observations.')) return;
    try {
      await api.deleteReport(appSession.familyId, selectedMember.id, report.reportId);
      onRefresh();
    } catch { setError('Could not delete report.'); }
  };

  return (
    <div className="glass-card" style={{ padding: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
        <div style={{ width: '44px', height: '44px', borderRadius: '12px', background: 'hsla(210, 100%, 100%, 0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
          {report.status === 'Processing' ? (
            <div className="status-spinner" />
          ) : (
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
            </svg>
          )}
        </div>
        <div>
          <div style={{ fontWeight: 600, marginBottom: '0.2rem' }}>{report.title}</div>
          <div style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
            <span>{report.date || 'Pending Processing'}</span>
            {report.status === 'Analyzed' && (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.375rem' }}>
                <span style={{ width: '3px', height: '3px', borderRadius: '50%', background: 'var(--text-muted)' }} />
                <span>{report.totalTests} Tests</span>
                {report.abnormalCount! > 0 && (
                  <span style={{ color: 'var(--rose-alert)', fontWeight: 600 }}>· {report.abnormalCount} Abnormal</span>
                )}
              </span>
            )}
          </div>
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <div className={`status-badge ${report.status.toLowerCase()}`}>
          {report.status.toUpperCase()}
        </div>
        {report.status === 'Analyzed' && (
          <button onClick={handleView} className="icon-btn" title="View Original">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /><polyline points="15 3 21 3 21 9" /><line x1="10" y1="14" x2="21" y2="3" /></svg>
          </button>
        )}
        <button onClick={handleDelete} className="icon-btn delete" title="Delete">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /></svg>
        </button>
      </div>
    </div>
  );
};

const VoiceModal = ({ isOpen, onClose, familyId, memberId }: { isOpen: boolean; onClose: () => void; familyId: string; memberId: string }) => {
  const [message, setMessage] = useState('');
  const [chatLog, setChatLog] = useState<{ role: string; content: string }[]>([]);
  const [sessionId, setSessionId] = useState('');
  const [isTyping, setIsTyping] = useState(false);

  const handleSend = async () => {
    if (!message.trim()) return;
    const userMsg = { role: 'user', content: message };
    setChatLog(prev => [...prev, userMsg]);
    setMessage('');
    setIsTyping(true);
    try {
      const response = await api.chat(familyId, memberId, message, sessionId);
      setSessionId(response.sessionId);
      setChatLog(prev => [...prev, { role: 'assistant', content: response.reply || "I'm processing your request." }]);
    } catch {
      setChatLog(prev => [...prev, { role: 'assistant', content: "Sorry, I'm having trouble connecting right now." }]);
    } finally {
      setIsTyping(false);
    }
  };

  if (!isOpen) return null;
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'hsla(220, 20%, 3%, 0.8)', backdropFilter: 'blur(20px)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '2rem' }}>
      <div className="glass-card" style={{ width: '100%', maxWidth: '600px', height: '80vh', display: 'flex', flexDirection: 'column', padding: '1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 800 }}>Voice Sanctuary</h2>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', fontSize: '1.25rem' }}>✕</button>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', marginBottom: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {chatLog.length === 0 && (
            <div style={{ color: 'var(--text-muted)', textAlign: 'center', marginTop: '3rem', fontSize: '0.9rem' }}>
              Ask Nova anything about your family's health records.
            </div>
          )}
          {chatLog.map((log, i) => (
            <div key={i} style={{ alignSelf: log.role === 'user' ? 'flex-end' : 'flex-start', maxWidth: '80%', padding: '0.875rem 1.25rem', borderRadius: '16px', background: log.role === 'user' ? 'var(--chetana-teal)' : 'hsla(0,0%,100%,0.05)', color: log.role === 'user' ? 'hsl(220,20%,5%)' : 'inherit', fontSize: '0.9375rem', lineHeight: 1.5 }}>
              {log.content}
            </div>
          ))}
          {isTyping && <div style={{ opacity: 0.5, fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Nova is thinking…</div>}
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <input type="text" className="form-input" value={message} onChange={e => setMessage(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleSend()} placeholder="Ask about your health records…" style={{ flex: 1 }} />
          <button className="btn-primary" onClick={handleSend} style={{ padding: '0 1.5rem', borderRadius: '12px' }}>Send</button>
        </div>
      </div>
    </div>
  );
};

// ─── Theme utilities ──────────────────────────────────────────────────────────

const getStoredTheme = (): 'dark' | 'light' => {
  return (localStorage.getItem('chetana_theme') as 'dark' | 'light') || 'dark';
};

const applyTheme = (theme: 'dark' | 'light') => {
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem('chetana_theme', theme);
};

// ─── Main App ─────────────────────────────────────────────────────────────────

function App() {
  const [appSession, setAppSession] = useState<Session | null>(null);
  const [sessionLoaded, setSessionLoaded] = useState(false);
  const [theme, setTheme] = useState<'dark' | 'light'>(getStoredTheme);

  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [selectedMember, setSelectedMember] = useState<FamilyMember | null>(null);
  const [observations, setObservations] = useState<Observation[]>([]);
  const [insights, setInsights]       = useState<Insight[]>([]);
  const [reports, setReports]         = useState<Report[]>([]);
  const [isVoiceOpen, setIsVoiceOpen] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError]             = useState<string | null>(null);

  // Expose globals for sub-components without heavy prop drilling
  (window as any).appGlobals = { appSession, selectedMember, setError };

  // Apply theme on mount + changes
  useEffect(() => { applyTheme(theme); }, [theme]);

  // Load session on mount, then fetch real members from API
  useEffect(() => {
    const s = sessionHelper.get();
    setAppSession(s);
    setSessionLoaded(true);
    if (s) {
      api.getMembers(s.familyId).then(fetched => {
        if (fetched.length > 0) {
          setMembers(fetched);
          setSelectedMember(fetched[0]);
          // Keep session members up-to-date
          sessionHelper.save({ ...s, members: fetched });
        }
      }).catch(() => setError('Could not load family members.'));
    }
  }, []);

  // Fetch data whenever selected member changes + setup polling
  useEffect(() => {
    if (!appSession || !selectedMember) return;
    const { familyId } = appSession;
    
    // Initial fetch
    api.getReports(familyId, selectedMember.id).then(setReports).catch(() => setError('Reports sync failed.'));
    api.getObservations(familyId, selectedMember.id).then(setObservations).catch(() => setError('Observations sync failed.'));
    api.getInsights(familyId, selectedMember.id).then(setInsights).catch(() => setError('Insights sync failed.'));

    // Poll if there's a processing report
    const hasProcessingReports = reports.some(r => r.status === 'Processing');
    let interval: any;

    if (hasProcessingReports) {
      interval = setInterval(() => {
        api.getReports(familyId, selectedMember.id).then(setReports).catch(() => setError('Reports sync failed.'));
        api.getInsights(familyId, selectedMember.id).then(setInsights).catch(() => setError('Insights sync failed.'));
      }, 5000);
    }

    return () => {
      if (interval) clearInterval(interval);
    };
  }, [appSession, selectedMember, reports.some(r => r.status === 'Processing')]);

  const handleEnterApp = () => {
    const s = sessionHelper.get();
    setAppSession(s);
    if (s) {
      api.getMembers(s.familyId).then(fetched => {
        if (fetched.length > 0) {
          setMembers(fetched);
          setSelectedMember(fetched[0]);
          sessionHelper.save({ ...s, members: fetched });
        }
      }).catch(() => setError('Could not load family members.'));
    }
  };

  const handleLogout = () => {
    sessionHelper.clear();
    setAppSession(null);
    setMembers([]);
    setSelectedMember(null);
    setObservations([]);
    setInsights([]);
    setReports([]);
  };

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !selectedMember || !appSession) return;
    setIsUploading(true);
    try {
      await api.uploadReport(appSession.familyId, selectedMember.id, file);
      // After upload, immediately fetch reports to show the new one, likely in 'Processing' status
      api.getReports(appSession.familyId, selectedMember.id).then(setReports);
    } catch { setError('Report upload failed.'); }
    finally { setIsUploading(false); }
  };

  const toggleTheme = () => setTheme(t => t === 'dark' ? 'light' : 'dark');

  // Don't render until we've checked localStorage
  if (!sessionLoaded) return null;

  // ── Landing page (no session) ─────────────────────────────────────────────
  if (!appSession) {
    return <LandingPage onEnterApp={handleEnterApp} theme={theme} onToggleTheme={toggleTheme} />;
  }

  // ── Dashboard ─────────────────────────────────────────────────────────────
  const NavItem = ({ icon, label, active = false, onClick }: { icon: React.ReactNode; label: string; active?: boolean; onClick?: () => void }) => (
    <a href="#" className={`nav-item ${active ? 'active' : ''}`} onClick={e => { e.preventDefault(); onClick?.(); }}>
      {icon} <span>{label}</span>
    </a>
  );

  return (
    <div className="app-container">
      <aside className="sidebar">
        <div className="logo-section">
          <div className="logo-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
            </svg>
          </div>
          <div>
            <div style={{ fontSize: '1.125rem', fontWeight: 800, letterSpacing: '-0.03em', fontFamily: 'var(--font-header)' }}><span>CHETANA</span></div>
            <div style={{ fontSize: '0.6rem', color: 'var(--chetana-teal)', fontWeight: 700, letterSpacing: '0.1em' }}><span>PATIENT PORTAL</span></div>
          </div>
        </div>

        <nav className="nav-links">
          <NavItem label="Dashboard" active icon={<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="14" y="14" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /></svg>} />
          <NavItem label="Family Hub" onClick={() => document.querySelector('.family-hub')?.scrollIntoView({ behavior: 'smooth' })} icon={<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></svg>} />
          <NavItem label="Insights" onClick={() => document.querySelector('.insights-card')?.scrollIntoView({ behavior: 'smooth' })} icon={<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" /></svg>} />
          <NavItem label="Voice Sanctuary" onClick={() => setIsVoiceOpen(true)} icon={<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" /><path d="M19 10v2a7 7 0 0 1-14 0v-2" /><line x1="12" y1="19" x2="12" y2="23" /><line x1="8" y1="23" x2="16" y2="23" /></svg>} />
        </nav>

        {/* Bottom controls */}
        <div className="nav-bottom">
          {/* Theme toggle */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.5rem 0' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{theme === 'dark' ? '🌙' : '☀️'}</span>
            <button className="theme-toggle" data-active={theme === 'dark' ? 'true' : 'false'} onClick={toggleTheme} aria-label="Toggle theme" />
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{theme === 'dark' ? 'Dark' : 'Light'}</span>
          </div>

          {/* Family info */}
          <div style={{ padding: '0.75rem 1rem', background: 'hsla(175,100%,45%,0.05)', borderRadius: '12px', border: '1px solid hsla(175,100%,45%,0.1)' }}>
            <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', fontWeight: 600, letterSpacing: '0.06em', marginBottom: '0.25rem' }}>FAMILY</div>
                <div style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.5rem' }}><span>{appSession.familyName}</span></div>
            <button onClick={handleLogout} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '0.75rem', padding: 0, display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" /></svg>
              <span>Sign out</span>
            </button>
          </div>
        </div>
      </aside>

      <main className="main-content">
        {error && (
          <div style={{ padding: '1rem', background: 'hsla(0,100%,50%,0.08)', border: '1px solid hsla(0,100%,50%,0.2)', borderRadius: '12px', marginBottom: '1.5rem', color: 'var(--rose-alert)', fontSize: '0.875rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>{error}</span>
            <button onClick={() => setError(null)} style={{ background: 'none', border: 'none', color: 'var(--rose-alert)', cursor: 'pointer' }}>✕</button>
          </div>
        )}

        <header className="header">
          <h1>Family Health Sanctuary</h1>
          <p>Powered by Amazon Nova · FHIR HL7 R4 · AES-256 Encrypted</p>
        </header>

        <div className="dashboard-grid">
          {/* Family Hub */}
          <div className="family-hub">
            {members.map(member => (
              <FamilyMemberCard key={member.id} member={member} active={selectedMember?.id === member.id} onClick={() => setSelectedMember(member)} />
            ))}
            <div
              title="Add family member"
              style={{ width: '56px', height: '56px', borderRadius: '18px', border: '2px dashed var(--glass-border)', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: 'var(--text-secondary)', transition: 'all 0.3s ease', flexShrink: 0 }}
              onMouseEnter={e => { (e.currentTarget as HTMLElement).style.borderColor = 'var(--chetana-teal)'; (e.currentTarget as HTMLElement).style.color = 'var(--chetana-teal)'; }}
              onMouseLeave={e => { (e.currentTarget as HTMLElement).style.borderColor = 'var(--glass-border)'; (e.currentTarget as HTMLElement).style.color = 'var(--text-secondary)'; }}
              onClick={async () => {
                const newName = window.prompt('Enter new family member name:');
                if (!newName || !appSession) return;
                try {
                  const m = await api.addMember(appSession.familyId, { name: newName.trim(), relationship: 'Dependent' });
                  const updated = [...members, m];
                  setMembers(updated);
                  setSelectedMember(m);
                  sessionHelper.save({ ...appSession, members: updated });
                } catch { setError('Could not add member.'); }
              }}
            >
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
            </div>
          </div>

          {/* Health Timeline */}
          <section className="glass-card timeline-card">
            <div className="card-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12" /></svg>
              Health Trends · {selectedMember?.name}
            </div>
            {observations.length > 0 ? (
              <>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.5rem', marginBottom: '0.75rem' }}>
                  <span style={{ fontSize: '2.5rem', fontWeight: 800 }}>{observations[observations.length - 1].value}</span>
                  <span style={{ color: 'var(--text-secondary)', fontSize: '1rem' }}>{observations[observations.length - 1].unit}</span>
                  {observations.length > 1 && (
                    <span style={{ color: observations[observations.length - 1].value > observations[observations.length - 2].value ? 'var(--solar-amber)' : 'var(--healing-green)', fontSize: '0.875rem', fontWeight: 600, marginLeft: '0.75rem' }}>
                      {observations[observations.length - 1].value > observations[observations.length - 2].value ? '↑' : '↓'}
                      {Math.abs(Math.round(((observations[observations.length - 1].value - observations[observations.length - 2].value) / observations[observations.length - 2].value) * 100))}% from last
                    </span>
                  )}
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>{observations[observations.length - 1].name}</div>
                <HealthTimeline observations={observations} />
              </>
            ) : (
              <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>No observations yet. Upload a lab report to get started.</div>
            )}
          </section>

          {/* Insights */}
          <section className="glass-card insights-card">
            <div className="card-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></svg>
              Nova Insights
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              {insights.map(i => <InsightCard key={i.insightId} insight={i} />)}
              {insights.length === 0 && <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>No insights yet. Nova will analyze your data after report upload.</div>}
            </div>
          </section>

          {/* Reports */}
          <section className="glass-card reports-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
              <div className="card-title" style={{ marginBottom: 0 }}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /></svg>
                Health Records
              </div>
              <label className="btn-primary" style={{ cursor: 'pointer', opacity: isUploading ? 0.6 : 1, fontSize: '0.8125rem', padding: '0.625rem 1.25rem' }}>
                {isUploading ? 'Syncing…' : '+ Upload Report'}
                <input type="file" style={{ display: 'none' }} onChange={handleFileUpload} disabled={isUploading} accept=".pdf,image/*" />
              </label>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem' }}>
              {reports.map(r => <ReportCard key={r.reportId} report={r} onRefresh={() => api.getReports(appSession.familyId, selectedMember!.id).then(setReports)} />)}
              {reports.length === 0 && <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', gridColumn: '1 / -1' }}>No reports uploaded yet. Upload a PDF lab result to get started.</div>}
            </div>
          </section>
        </div>
      </main>

      <VoiceModal 
        isOpen={isVoiceOpen} 
        onClose={() => setIsVoiceOpen(false)} 
        familyId={appSession.familyId}
        memberId={selectedMember?.id || ''} 
      />
    </div>
  );
}

export default App;
