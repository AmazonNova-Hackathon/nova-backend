import React, { useState, useEffect } from 'react';
import './App.css';
import { api } from './services/api';
import type { FamilyMember, Insight, Report, Observation } from './services/api';

const FamilyMemberCard = ({ member, active = false, onClick }: { member: FamilyMember; active?: boolean; onClick: () => void }) => (
  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', cursor: 'pointer' }} onClick={onClick}>
    <div className={`member-avatar ${active ? 'active' : ''}`}>
      <span style={{ fontSize: '1.5rem', fontWeight: 'bold', color: active ? 'var(--chetana-teal)' : 'var(--text-secondary)' }}>
        {member.name[0]}
      </span>
    </div>
    <span className="member-name">{member.name}</span>
  </div>
);

const HealthTimeline = ({ observations }: { observations: Observation[] }) => {
  // Simple path generation based on points
  const points = observations.length > 0
    ? observations.map((o, i) => `${(i / (observations.length - 1)) * 1000} ${150 - (o.value / 150) * 150}`).join(' ')
    : "0 130 1000 130";

  return (
    <div style={{ position: 'relative', height: '180px', marginTop: '1.5rem' }}>
      <div style={{ position: 'absolute', top: '30px', left: 0, right: 0, height: '50px', background: 'hsla(150, 60%, 45%, 0.03)', borderRadius: '12px', border: '1px dashed hsla(150, 60%, 45%, 0.15)' }}>
        <span style={{ position: 'absolute', right: '15px', top: '15px', fontSize: '0.65rem', color: 'var(--healing-green)', opacity: 0.6, fontWeight: '700', letterSpacing: '0.1em' }}>NOMINAL RANGE</span>
      </div>
      <svg width="100%" height="150" viewBox="0 0 1000 150" preserveAspectRatio="none">
        <defs>
          <linearGradient id="line-grad" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="var(--chetana-teal)" stopOpacity="0.4" />
            <stop offset="100%" stopColor="var(--chetana-teal)" stopOpacity="1" />
          </linearGradient>
        </defs>
        <path
          d={`M ${points}`}
          fill="none"
          stroke="url(#line-grad)"
          strokeWidth="4"
          strokeLinecap="round"
        />
        {observations.map((o, i) => (
          <circle
            key={o.id}
            cx={(i / (observations.length - 1)) * 1000}
            cy={150 - (o.value / 150) * 150}
            r={i === observations.length - 1 ? 6 : 4}
            fill="var(--chetana-teal)"
            className={i === observations.length - 1 ? "pulse" : ""}
          />
        ))}
      </svg>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '1.25rem', fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: '500' }}>
        {observations.map((o, i) => (
          <span key={o.id} style={{ color: i === observations.length - 1 ? 'var(--text-primary)' : 'var(--text-secondary)', fontWeight: i === observations.length - 1 ? '700' : '500' }}>
            {o.date.split('-').slice(1).join('/')}
          </span>
        ))}
      </div>
    </div>
  );
};

const Typewriter = ({ text, delay = 20 }: { text: string; delay?: number }) => {
  const [displayText, setDisplayText] = useState('');
  const [currentIndex, setCurrentIndex] = useState(0);

  useEffect(() => {
    setDisplayText('');
    setCurrentIndex(0);
  }, [text]);

  useEffect(() => {
    if (currentIndex < text.length) {
      const timeout = setTimeout(() => {
        setDisplayText(prev => prev + text[currentIndex]);
        setCurrentIndex(prev => prev + 1);
      }, delay + Math.random() * 15);
      return () => clearTimeout(timeout);
    }
  }, [currentIndex, delay, text]);

  return <span>{displayText}</span>;
};

const InsightCard = ({ insight }: { insight: Insight }) => (
  <div className="shimmer-container" style={{ padding: '1.5rem', borderRadius: '20px', background: 'hsla(0,0%,100%,0.02)', border: '1px solid var(--glass-border)' }}>
    <div className="shimmer-overlay"></div>
    <div className="card-title" style={{ color: insight.severity === 'urgent' ? 'var(--solar-amber)' : 'var(--chetana-teal)', marginBottom: '0.75rem', fontSize: '1rem' }}>
      {insight.severity === 'urgent' ? '🚨' : '✨'} {insight.title}
    </div>
    <div style={{ fontSize: '0.9375rem', color: 'var(--text-primary)', lineHeight: '1.6', opacity: 0.9 }}>
      <Typewriter text={insight.content} />
    </div>
  </div>
);

const ReportCard = ({ report }: { report: Report }) => (
  <div className="glass-card" style={{ padding: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
      <div style={{ width: '48px', height: '48px', borderRadius: '12px', background: 'hsla(210, 100%, 100%, 0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>
      </div>
      <div>
        <div style={{ fontWeight: '600', marginBottom: '0.25rem' }}>{report.title}</div>
        <div style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>{report.date}</div>
      </div>
    </div>
    <div style={{
      padding: '0.5rem 1rem',
      borderRadius: '12px',
      fontSize: '0.75rem',
      background: 'hsla(175, 100%, 45%, 0.08)',
      color: 'var(--chetana-teal)',
      fontWeight: '600',
      letterSpacing: '0.05em'
    }}>
      {report.status.toUpperCase()}
    </div>
  </div>
);

const ChetanaLogo = () => (
  <div className="logo-icon">
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
    </svg>
  </div>
);

const NavItem = ({ icon, label, active = false, onClick }: { icon: React.ReactNode, label: string, active?: boolean, onClick?: () => void }) => (
  <a href="#" className={`nav-item ${active ? 'active' : ''}`} onClick={(e) => { e.preventDefault(); onClick?.(); }}>
    {icon}
    <span>{label}</span>
  </a>
);

const VoiceModal = ({ isOpen, onClose, patientId }: { isOpen: boolean; onClose: () => void; patientId: string }) => {
  const [message, setMessage] = useState('');
  const [chatLog, setChatLog] = useState<{ role: string; content: string }[]>([]);
  const [isTyping, setIsTyping] = useState(false);

  const handleSend = async () => {
    if (!message.trim()) return;
    const userMsg = { role: 'user', content: message };
    setChatLog(prev => [...prev, userMsg]);
    setMessage('');
    setIsTyping(true);

    try {
      const response = await api.chat(patientId, message, chatLog);
      setChatLog(prev => [...prev, { role: 'assistant', content: response.message || "I'm processing your request." }]);
    } catch (err) {
      setChatLog(prev => [...prev, { role: 'assistant', content: "Sorry, I'm having trouble connecting to the sanctuary right now." }]);
    } finally {
      setIsTyping(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'hsla(220, 20%, 3%, 0.8)', backdropFilter: 'blur(20px)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '2rem' }}>
      <div className="glass-card" style={{ width: '100%', maxWidth: '600px', height: '80vh', display: 'flex', flexDirection: 'column', padding: '1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: '800' }}>Voice Sanctuary</h2>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>✕</button>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', marginBottom: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {chatLog.map((log, i) => (
            <div key={i} style={{ alignSelf: log.role === 'user' ? 'flex-end' : 'flex-start', maxWidth: '80%', padding: '1rem', borderRadius: '16px', background: log.role === 'user' ? 'var(--chetana-teal)' : 'hsla(0,0%,100%,0.05)', color: log.role === 'user' ? 'var(--obsidian-abyss)' : 'inherit' }}>
              {log.content}
            </div>
          ))}
          {isTyping && <div style={{ opacity: 0.5, fontSize: '0.8rem' }}>Nova is reflecting...</div>}
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <input
            type="text"
            value={message}
            onChange={e => setMessage(e.target.value)}
            onKeyPress={e => e.key === 'Enter' && handleSend()}
            placeholder="Whisper your health concerns..."
            style={{ flex: 1, padding: '1rem', borderRadius: '12px', border: '1px solid var(--glass-border)', background: 'hsla(0,0%,100%,0.02)', color: '#fff' }}
          />
          <button onClick={handleSend} style={{ background: 'var(--chetana-teal)', border: 'none', padding: '0 1.5rem', borderRadius: '12px', fontWeight: '700', cursor: 'pointer' }}>SEND</button>
        </div>
      </div>
    </div>
  );
};

function App() {
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [selectedMember, setSelectedMember] = useState<FamilyMember | null>(null);
  const [observations, setObservations] = useState<Observation[]>([]);
  const [insights, setInsights] = useState<Insight[]>([]);
  const [reports, setReports] = useState<Report[]>([]);
  const [isVoiceOpen, setIsVoiceOpen] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchMemberData = (memberId: string) => {
    api.getObservations(memberId).then(setObservations).catch(e => setError("Failed to sync observations."));
    api.getInsights(memberId).then(setInsights).catch(e => setError("Failed to sync insights."));
    api.getReports(memberId).then(setReports).catch(e => setError("Failed to sync reports."));
  };

  useEffect(() => {
    api.getMembers().then(data => {
      setMembers(data);
      if (data.length > 0) {
        setSelectedMember(data[0]);
        fetchMemberData(data[0].id);
      }
    }).catch(e => setError("Sanctuary connection lost. Check API Key."));
  }, []);

  useEffect(() => {
    if (selectedMember) {
      fetchMemberData(selectedMember.id);
    }
  }, [selectedMember]);

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !selectedMember) return;

    setIsUploading(true);
    try {
      await api.uploadReport(selectedMember.id, file);
      fetchMemberData(selectedMember.id);
    } catch (err) {
      setError("Report sanctuary upload failed.");
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="app-container">
      <aside className="sidebar">
        <div className="logo-section">
          <ChetanaLogo />
          <div>
            <h2 style={{ fontSize: '1.5rem', fontWeight: '800', letterSpacing: '-0.03em' }}>CHETANA</h2>
            <div style={{ fontSize: '0.65rem', color: 'var(--chetana-teal)', fontWeight: 'bold' }}>PATIENT PORTAL</div>
          </div>
        </div>

        <nav className="nav-links">
          <NavItem
            label="Dashboard"
            active
            icon={<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect></svg>}
          />
          <NavItem
            label="Family Hub"
            onClick={() => {
              const el = document.querySelector('.family-hub');
              if (el) el.scrollIntoView({ behavior: 'smooth' });
            }}
            icon={<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M23 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path></svg>}
          />
          <NavItem
            label="Insights"
            onClick={() => {
              const el = document.querySelector('.insights-card');
              if (el) el.scrollIntoView({ behavior: 'smooth' });
            }}
            icon={<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></svg>}
          />
          <NavItem
            label="Voice sanctuary"
            onClick={() => setIsVoiceOpen(true)}
            icon={<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"></path><path d="M19 10v2a7 7 0 0 1-14 0v-2"></path><line x1="12" y1="19" x2="12" y2="23"></line><line x1="8" y1="23" x2="16" y2="23"></line></svg>}
          />
        </nav>
      </aside>

      <main className="main-content">
        {error && (
          <div style={{ padding: '1rem', background: 'hsla(0, 100%, 50%, 0.1)', border: '1px solid hsla(0, 100%, 50%, 0.3)', borderRadius: '12px', marginBottom: '2rem', color: '#ff6b6b', fontSize: '0.9rem', display: 'flex', justifyContent: 'space-between' }}>
            <span>{error}</span>
            <button onClick={() => setError(null)} style={{ background: 'none', border: 'none', color: '#ff6b6b', cursor: 'pointer' }}>✕</button>
          </div>
        )}
        <header className="header">
          <h1>Family Health Sanctuary</h1>
          <p>Strictly managing your family's records through Nova-powered AI reasoning.</p>
        </header>

        <div className="dashboard-grid">
          <div className="family-hub">
            {members.map(member => (
              <FamilyMemberCard
                key={member.id}
                member={member}
                active={selectedMember?.id === member.id}
                onClick={() => setSelectedMember(member)}
              />
            ))}
            <div style={{ width: '64px', height: '64px', borderRadius: '20px', border: '2px dashed var(--glass-border)', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: 'var(--text-secondary)', transition: 'all 0.3s ease' }}
              onMouseEnter={(e) => { e.currentTarget.style.borderColor = 'var(--chetana-teal)'; e.currentTarget.style.color = 'var(--chetana-teal)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'var(--glass-border)'; e.currentTarget.style.color = 'var(--text-secondary)'; }}
              onClick={() => {
                const newName = window.prompt('Enter new family member name:');
                if (newName) {
                  const newMember = { id: 'm' + (members.length + 1), name: newName, age: 30, relationship: 'Dependent' };
                  setMembers([...members, newMember]);
                  setSelectedMember(newMember);
                }
              }}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
            </div>
          </div>

          <section className="glass-card timeline-card">
            <div className="card-title">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline></svg>
              Health Trends: Fasting Glucose
            </div>
            {observations.length > 0 ? (
              <>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.5rem', marginBottom: '1rem' }}>
                  <span style={{ fontSize: '2.5rem', fontWeight: 'bold' }}>{observations[observations.length - 1].value}</span>
                  <span style={{ color: 'var(--text-secondary)' }}>{observations[observations.length - 1].unit}</span>
                  {observations.length > 1 && (
                    <span style={{ color: observations[observations.length - 1].value > observations[observations.length - 2].value ? 'var(--solar-amber)' : 'var(--healing-green)', fontSize: '0.875rem', fontWeight: '600', marginLeft: '1rem' }}>
                      {observations[observations.length - 1].value > observations[observations.length - 2].value ? '↑' : '↓'}
                      {Math.abs(Math.round(((observations[observations.length - 1].value - observations[observations.length - 2].value) / observations[observations.length - 2].value) * 100))}%
                      from last month
                    </span>
                  )}
                </div>
                <HealthTimeline observations={observations} />
              </>
            ) : (
              <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>No observation data available.</div>
            )}
          </section>

          <section className="glass-card insights-card">
            <div className="card-title">Proactive Insights</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
              {insights.map(insight => (
                <InsightCard key={insight.insightId} insight={insight} />
              ))}
              {insights.length === 0 && <div style={{ color: 'var(--text-secondary)' }}>No insights available.</div>}
            </div>
          </section>

          <section className="glass-card reports-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2.5rem' }}>
              <div className="card-title" style={{ marginBottom: 0 }}>Recent Health Records</div>
              <label style={{
                background: 'var(--chetana-teal)',
                color: 'var(--obsidian-abyss)',
                border: 'none',
                padding: '0.75rem 1.5rem',
                borderRadius: '16px',
                fontWeight: '700',
                cursor: 'pointer',
                boxShadow: '0 8px 20px hsla(175, 100%, 45%, 0.2)',
                opacity: isUploading ? 0.5 : 1
              }}>
                {isUploading ? 'SYNCING...' : '+ UPLOAD REPORT'}
                <input type="file" style={{ display: 'none' }} onChange={handleFileUpload} disabled={isUploading} />
              </label>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(380px, 1fr))', gap: '1.5rem' }}>
              {reports.map(report => (
                <ReportCard key={report.reportId} report={report} />
              ))}
              {reports.length === 0 && <div style={{ color: 'var(--text-secondary)', gridColumn: 'span 3', textAlign: 'center' }}>No reports uploaded securely yet.</div>}
            </div>
          </section>
        </div>
      </main>
      <VoiceModal isOpen={isVoiceOpen} onClose={() => setIsVoiceOpen(false)} patientId={selectedMember?.id || ''} />
    </div>
  );
}

export default App;
