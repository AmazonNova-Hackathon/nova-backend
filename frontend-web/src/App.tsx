import React, { useState, useEffect, useRef } from 'react';
import './App.css';
import { api, session as sessionHelper } from './services/api';
import type { FamilyMember, Insight, Report, Observation, Session } from './services/api';
import { LandingPage } from './components/LandingPage';

// ─── Sub-components ───────────────────────────────────────────────────────────

const HealthTimeline = ({ observations }: { observations: Observation[] }) => {
  if (observations.length === 0) return null;

  // Dynamic scaling
  const maxVal = Math.max(...observations.map(o => o.value), 100);
  const minVal = Math.min(...observations.map(o => o.value), 0);
  const range = (maxVal - minVal) || 100;
  
  const getY = (val: number) => 130 - ((val - minVal) / range) * 110;

  const points = observations.length > 1 
    ? observations.map((o, i) => `${(i / (observations.length - 1)) * 1000} ${getY(o.value)}`).join(' ')
    : `0 ${getY(observations[0].value)} 1000 ${getY(observations[0].value)}`;

  return (
    <div style={{ position: 'relative', height: '180px', marginTop: '1.5rem' }}>
      <div style={{ position: 'absolute', top: '30px', left: 0, right: 0, height: '50px', background: 'hsla(150, 60%, 45%, 0.03)', borderRadius: '12px', border: '1px dashed hsla(150, 60%, 45%, 0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <span style={{ fontSize: '0.65rem', color: 'var(--healing-green)', opacity: 0.6, fontWeight: 700, letterSpacing: '0.15em' }}>NOMINAL RANGE</span>
      </div>
      <svg width="100%" height="150" viewBox="0 0 1000 150" preserveAspectRatio="none" style={{ overflow: 'visible' }}>
        <defs>
          <linearGradient id="line-grad" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="var(--chetana-teal)" stopOpacity="0.4" />
            <stop offset="100%" stopColor="var(--chetana-teal)" stopOpacity="1" />
          </linearGradient>
          <linearGradient id="area-grad" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="var(--chetana-teal)" stopOpacity="0.2" />
            <stop offset="100%" stopColor="var(--chetana-teal)" stopOpacity="0" />
          </linearGradient>
        </defs>
        {observations.length > 1 && (
          <path d={`M 0 150 L ${points} L 1000 150 Z`} fill="url(#area-grad)" />
        )}
        <path d={`M ${points}`} fill="none" stroke="url(#line-grad)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
        {observations.map((o, i) => (
          <circle 
            key={o.id} 
            cx={observations.length > 1 ? (i / (observations.length - 1)) * 1000 : 500} 
            cy={getY(o.value)} 
            r={i === observations.length - 1 ? 7 : 4} 
            fill="var(--chetana-teal)" 
            className={(i === observations.length - 1 && observations.length > 1) ? 'pulse' : ''} 
          />
        ))}
      </svg>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '1rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
        {observations.length > 1 ? observations.map((o, i) => (
          <span key={o.id} style={{ fontWeight: i === observations.length - 1 ? 700 : 400, color: i === observations.length - 1 ? 'var(--text-primary)' : 'var(--text-secondary)' }}>
            {o.date.split('T')[0].split('-').slice(1).join('/')}
          </span>
        )) : (
          <span style={{ width: '100%', textAlign: 'center' }}>Captured on {observations[0].date.split('T')[0]}</span>
        )}
      </div>
    </div>
  );
};

const Typewriter = ({ text, delay = 18 }: { text: string; delay?: number }) => {
  const [displayText, setDisplayText] = useState('');
  const [idx, setIdx] = useState(0);
  
  useEffect(() => { 
    setDisplayText(''); 
    setIdx(0); 
  }, [text]);

  useEffect(() => {
    if (!text) return;
    if (idx < text.length) {
      const t = setTimeout(() => { 
        setDisplayText(p => p + text[idx]); 
        setIdx(p => p + 1); 
      }, delay + Math.random() * 12);
      return () => clearTimeout(t);
    }
  }, [idx, delay, text]);

  if (!text) return null;
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
    <div style={{ marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid hsla(0,0%,100%,0.05)', display: 'flex', gap: '1rem' }}>
      {(insight.citedObservations?.length || 0) > 0 && (
        <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', fontWeight: 700 }}>
          {insight.citedObservations?.length} MARKERS ANALYZED
        </div>
      )}
      {(insight.citedReports?.length || 0) > 0 && (
        <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', fontWeight: 700 }}>
          {insight.citedReports?.length} CLINICAL SOURCES
        </div>
      )}
    </div>
  </div>
);


const ChetanaAssistant = ({ familyId, memberId, reportId, initialQuery, language, setLanguage, onClose }: { familyId: string; memberId: string; reportId?: string; initialQuery?: string; language: string; setLanguage: (l: string) => void; onClose: () => void }) => {
  const [message, setMessage] = useState('');
  const [chatLog, setChatLog] = useState<{ role: string; content: string }[]>([]);
  const [sessionId, setSessionId] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const lastProcessedQuery = useRef<string | null>(null);

  useEffect(() => {
    if (initialQuery && initialQuery !== lastProcessedQuery.current) {
      lastProcessedQuery.current = initialQuery;
      handleSend(initialQuery);
    }
  }, [initialQuery]);

  useEffect(() => {
     if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [chatLog, isTyping]);

  const handleSend = async (overriddenMsg?: string) => {
    const textToSend = overriddenMsg || message;
    if (!textToSend.trim() || isTyping) return;

    const userMsg = { role: 'user', content: textToSend };
    setChatLog(prev => [...prev, userMsg]);
    setMessage('');
    setIsTyping(true);

    try {
      const response = await api.chat(familyId, memberId, textToSend, sessionId, reportId, language);
      setSessionId(response.sessionId);
      setChatLog(prev => [...prev, { role: 'assistant', content: response.message }]);
    } catch (err) {
      setChatLog(prev => [...prev, { role: 'assistant', content: 'Forgive me, I encountered a connection issue. Please try again.' }]);
    } finally {
      setIsTyping(false);
    }
  };

  return (
    <div className="chat-panel-sidebar">
      <div className="chat-header">
         <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flex: 1 }}>
            <div style={{ width: '32px', height: '32px', borderRadius: '10px', background: 'var(--chetana-teal)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
               <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="black" strokeWidth="2.5"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            </div>
            <div>
               <div style={{ fontSize: '0.9375rem', fontWeight: 900 }}>Nova Assistant</div>
               <select 
                 value={language} 
                 onChange={(e) => setLanguage(e.target.value)}
                 style={{ background: 'none', border: 'none', color: 'var(--healing-green)', fontSize: '0.65rem', fontWeight: 800, padding: 0, cursor: 'pointer', appearance: 'none', textAlign: 'left', outline: 'none' }}
               >
                 <option value="English">ENGLISH • ONLINE</option>
                 <option value="Hindi">HINDI • हिंदी</option>
                 <option value="Marathi">MARATHI • मराठी</option>
                 <option value="Tamil">TAMIL • தமிழ்</option>
               </select>
            </div>
         </div>
         <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '8px' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M18 6L6 18M6 6l12 12"/></svg>
         </button>
      </div>
      
      <div className="chat-messages" ref={scrollRef}>
         {chatLog.length === 0 && (
           <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.875rem' }}>
              <p>Welcome to Nova. You can ask me anything about this report or your general health trends.</p>
           </div>
         )}
         {chatLog.map((m, i) => (
           <div key={i} className={`message-bubble ${m.role}`}>
              {m.content}
           </div>
         ))}
         {isTyping && (
           <div className="message-bubble assistant" style={{ display: 'flex', gap: '4px', padding: '1rem' }}>
              <div className="typing-dot" />
              <div className="typing-dot" />
              <div className="typing-dot" />
           </div>
         )}
      </div>

      <div className="chat-input-area">
         <div style={{ position: 'relative' }}>
            <input 
              type="text" 
              className="form-input" 
              placeholder="Type your question..." 
              value={message}
              onChange={e => setMessage(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSend()}
              disabled={isTyping}
              style={{ width: '100%', paddingRight: '48px' }}
            />
            <button 
              onClick={() => handleSend()}
              disabled={!message.trim() || isTyping}
              style={{ position: 'absolute', right: '8px', top: '50%', transform: 'translateY(-50%)', background: message.trim() ? 'var(--chetana-teal)' : 'none', border: 'none', width: '32px', height: '32px', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', transition: 'all 0.3s ease' }}
            >
               <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={message.trim() ? "black" : "currentColor"} strokeWidth="2.5"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/></svg>
            </button>
         </div>
      </div>
    </div>
  );
};

const ReportDetailView = ({ report, onBack, isChatOpen, setIsChatOpen, language, setLanguage }: { report: Report; onBack: () => void; isChatOpen: boolean; setIsChatOpen: (o: boolean) => void; language: string; setLanguage: (l: string) => void }) => {
  const [observations, setObservations] = useState<Observation[]>([]);
  const { appSession, selectedMember } = (window as any).appGlobals;
  const [initialChatQuery, setInitialChatQuery] = useState<string | undefined>();

  useEffect(() => {
    api.getObservations(appSession.familyId, selectedMember.id)
       .then(all => setObservations(all.filter(o => o.reportId === report.reportId)));
  }, [report.reportId]);

  const handleRowClick = (name: string) => {
    setInitialChatQuery(`Explain what my ${name} level means in this report.`);
    setIsChatOpen(true);
  };

  return (
    <div className="report-workspace">
      <div className="report-scroll-area">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
          <button className="back-btn" onClick={onBack} style={{ margin: 0 }}>
             <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
             Universal Dashboard
          </button>
          {!isChatOpen && (
            <button className="btn-primary" onClick={() => setIsChatOpen(true)} style={{ padding: '0.75rem 1.5rem', borderRadius: '12px' }}>
               <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="black" strokeWidth="2.5" style={{ marginRight: '0.5rem' }}><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
               Speak to Nova
            </button>
          )}
        </div>
        
        <div style={{ marginBottom: '3rem' }}>
           <h2 style={{ fontSize: '2.5rem', fontWeight: 900, marginBottom: '0.5rem' }}>{report.title}</h2>
           <p style={{ color: 'var(--text-secondary)', fontSize: '1.125rem' }}>Clinical Data Analyzed on {report.date}</p>
        </div>

        <LabResultsTable observations={observations} onRowClick={handleRowClick} />
      </div>
      
      {isChatOpen && (
        <ChetanaAssistant 
          key={initialChatQuery || 'default-chat'}
          familyId={appSession.familyId} 
          memberId={selectedMember.id} 
          reportId={report.reportId}
          initialQuery={initialChatQuery} 
          language={language}
          setLanguage={setLanguage}
          onClose={() => setIsChatOpen(false)} 
        />
      )}
    </div>
  );
};

const ExtractionOverlay = () => (
  <div className="extraction-overlay">
    <div className="extraction-pulse">
       <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5"><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/></svg>
    </div>
    <div style={{ textAlign: 'center' }}>
       <h3 style={{ fontSize: '1.5rem', fontWeight: 900, marginBottom: '0.5rem' }}>Syncing with Vault</h3>
       <p style={{ color: 'var(--text-secondary)' }}>Nova is extracting medical intelligence...</p>
    </div>
  </div>
);

const LabResultsTable = ({ observations, onRowClick }: { observations: Observation[], onRowClick?: (name: string) => void }) => {
  if (observations.length === 0) return (
     <div style={{ padding: '4rem', textAlign: 'center', opacity: 0.5 }}>
        No biological markers found in this specific record.
     </div>
  );

  // Group by LOINC or Name to show most recent result first
  const grouped = observations.reduce((acc: any, obs) => {
    const key = obs.loincCode || obs.name;
    if (!acc[key] || new Date(obs.date) > new Date(acc[key].date)) {
      acc[key] = obs;
    }
    return acc;
  }, {});

  const sortedTests = Object.values(grouped).sort((a: any, b: any) => {
    // Sort abnormal tests to top
    if (a.isAbnormal && !b.isAbnormal) return -1;
    if (!a.isAbnormal && b.isAbnormal) return 1;
    return a.name.localeCompare(b.name);
  }) as Observation[];

  return (
    <div className="glass-card results-card">
      <div className="card-title">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><line x1="10" y1="9" x2="8" y2="9"/></svg>
        Latest Lab Results
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'separate', borderSpacing: '0 0.75rem' }}>
          <thead>
            <tr style={{ textAlign: 'left', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              <th style={{ padding: '0 1rem' }}>Biological Marker</th>
              <th style={{ padding: '0 1rem' }}>Result</th>
              <th style={{ padding: '0 1rem' }}>Status</th>
              <th style={{ padding: '0 1rem' }}>Reference Range</th>
              <th style={{ padding: '0 1rem' }}>Collected</th>
            </tr>
          </thead>
          <tbody>
            {sortedTests.map(obs => (
              <tr 
                key={obs.id} 
                className="table-row-hover" 
                style={{ background: 'hsla(0,0%,100%,0.02)', borderRadius: '12px', cursor: 'pointer' }}
                onClick={() => onRowClick && onRowClick(obs.name)}
              >
                <td style={{ padding: '1rem', borderRadius: '12px 0 0 12px' }}>
                  <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{obs.name}</div>
                  <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', marginTop: '0.1rem' }}>{obs.loincCode}</div>
                </td>
                <td style={{ padding: '1rem' }}>
                  <div style={{ fontSize: '1.125rem', fontWeight: 800, color: obs.isAbnormal ? 'var(--rose-alert)' : 'var(--text-primary)' }}>
                    {obs.value} <span style={{ fontSize: '0.75rem', fontWeight: 500, opacity: 0.6 }}>{obs.unit}</span>
                  </div>
                </td>
                <td style={{ padding: '1rem' }}>
                   <div style={{ 
                     display: 'inline-flex', 
                     padding: '0.25rem 0.625rem', 
                     borderRadius: '6px', 
                     fontSize: '0.7rem', 
                     fontWeight: 800,
                     background: obs.isAbnormal ? 'hsla(0,100%,70%,0.1)' : 'hsla(150,60%,45%,0.1)',
                     color: obs.isAbnormal ? 'var(--rose-alert)' : 'var(--healing-green)'
                   }}>
                     {obs.isAbnormal ? (obs.interpretation || 'ABNORMAL') : 'NORMAL'}
                   </div>
                </td>
                <td style={{ padding: '1rem', color: 'var(--text-secondary)', fontSize: '0.8125rem' }}>
                  {(obs.normalLow !== undefined && obs.normalHigh !== undefined) ? (
                    `${obs.normalLow} – ${obs.normalHigh}`
                  ) : 'Standard'}
                </td>
                <td style={{ padding: '1rem', borderRadius: '0 12px 12px 0', fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
                  {obs.date}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const ReportCard = ({ report, onRefresh, onSelect }: { report: Report; onRefresh: () => void; onSelect: () => void }) => {
  const { appSession, selectedMember, setError } = (window as any).appGlobals;
  
  const handleViewOriginal = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      const url = await api.getReportDownloadUrl(appSession.familyId, selectedMember.id, report.reportId);
      window.open(url, '_blank');
    } catch { setError('Could not view report.'); }
  };

  const handleDelete = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this report? This will also remove its extracted observations.')) return;
    try {
      await api.deleteReport(appSession.familyId, selectedMember.id, report.reportId);
      onRefresh();
    } catch { setError('Could not delete report.'); }
  };

  return (
    <div className="glass-card" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1.25rem', cursor: 'pointer', border: '1px solid hsla(0,0%,100%,0.05)', transition: 'all 0.4s ease' }} onClick={onSelect}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
         <div style={{ width: '48px', height: '48px', borderRadius: '14px', background: 'hsla(175,100%,45%,0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--chetana-teal)" strokeWidth="2.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
         </div>
         <div style={{ display: 'flex', gap: '0.5rem' }}>
            {report.status === 'Analyzed' && (
              <button onClick={handleViewOriginal} className="icon-btn" title="View PDF">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /><polyline points="15 3 21 3 21 9" /><line x1="10" y1="14" x2="21" y2="3" /></svg>
              </button>
            )}
            <button onClick={handleDelete} className="icon-btn delete" title="Delete">
               <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /></svg>
            </button>
         </div>
      </div>
      
      <div style={{ flex: 1 }}>
         <h3 style={{ fontSize: '1.25rem', fontWeight: 900, marginBottom: '0.25rem' }}>{report.title}</h3>
         <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>{report.date || 'Analyzing Document...'}</p>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', paddingTop: '1rem', borderTop: '1px solid hsla(0,0%,100%,0.05)' }}>
         {report.status === 'Analyzed' ? (
           <div style={{ display: 'flex', gap: '1rem', flex: 1 }}>
              <div style={{ fontSize: '0.75rem' }}><span style={{ color: 'var(--text-muted)' }}>TESTS:</span> <strong style={{ color: 'var(--chetana-teal)' }}>{report.totalTests}</strong></div>
              {report.abnormalCount! > 0 && (
                 <div style={{ fontSize: '0.75rem' }}><span style={{ color: 'var(--rose-alert)' }}>ABNORMAL:</span> <strong>{report.abnormalCount}</strong></div>
              )}
           </div>
         ) : (
           <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flex: 1 }}>
              <div className="status-spinner" style={{ width: '12px', height: '12px' }} />
              <span style={{ fontSize: '0.75rem', fontWeight: 700, letterSpacing: '0.05em', color: 'var(--text-muted)' }}>SYNCING WITH VAULT</span>
           </div>
         )}
         <div className={`status-badge ${report.status.toLowerCase()}`} style={{ fontSize: '0.65rem' }}>{report.status.toUpperCase()}</div>
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
      setChatLog(prev => [...prev, { role: 'assistant', content: response.message || "I'm processing your request." }]);
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
          {chatLog.map((log, i) => {
            const parts = log.content.split('Disclaimer:');
            return (
              <div key={i} style={{ 
                alignSelf: log.role === 'user' ? 'flex-end' : 'flex-start', 
                maxWidth: '85%', 
                padding: '0.875rem 1.25rem', 
                borderRadius: '16px', 
                background: log.role === 'user' ? 'var(--chetana-teal)' : 'hsla(0,0%,100%,0.05)', 
                color: log.role === 'user' ? 'hsl(220,20%,5%)' : 'inherit', 
                fontSize: '0.9375rem', 
                lineHeight: 1.5,
                boxShadow: '0 4px 12px hsla(0,0%,0%,0.1)'
              }}>
                <div>{parts[0]}</div>
                {parts.length > 1 && (
                  <div style={{ 
                    marginTop: '0.75rem', 
                    paddingTop: '0.75rem', 
                    borderTop: '1px solid hsla(0,0%,100%,0.06)',
                    fontSize: '0.75rem', 
                    color: log.role === 'user' ? 'hsla(220,20%,5%,0.6)' : 'var(--text-muted)',
                    lineHeight: 1.4,
                    fontStyle: 'italic'
                  }}>
                    <strong>Disclaimer:</strong> {parts[1]}
                  </div>
                )}
              </div>
            );
          })}
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
  const [reports, setReports]         = useState<Report[]>([]);
  const [insights, setInsights]       = useState<Insight[]>([]);
  const [isVoiceOpen, setIsVoiceOpen] = useState(false);
  const [isChatOpen, setIsChatOpen]   = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError]             = useState<string | null>(null);

  const [currentView, setCurrentView] = useState<'dashboard' | 'report-detail'>('dashboard');
  const [activeReport, setActiveReport] = useState<Report | null>(null);
  const [selectedTrendName, setSelectedTrendName] = useState<string>('');
  const [chatLanguage, setChatLanguage] = useState<string>('English');

  useEffect(() => {
    if (observations.length > 0 && !selectedTrendName) {
      setSelectedTrendName(observations.find(o => o.isAbnormal)?.name || observations[0].name);
    }
  }, [observations]);

  const refreshDashboard = () => {
    if (appSession && selectedMember) {
      api.getInsights(appSession.familyId, selectedMember.id).then(setInsights);
      api.getReports(appSession.familyId, selectedMember.id).then(setReports);
      api.getObservations(appSession.familyId, selectedMember.id).then(setObservations);
    }
  };

  useEffect(() => {
    refreshDashboard();
  }, [appSession, selectedMember]);

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
        refreshDashboard();
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
      {isUploading && <ExtractionOverlay />}
      <aside className={`sidebar ${(currentView === 'report-detail' && isChatOpen) ? 'collapsed' : ''}`}>
        <div className="logo-section" style={{ gap: '0.875rem' }}>
          <div className="logo-icon" style={{ flexShrink: 0 }}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
            </svg>
          </div>
          <div className="logo-text">
            <div style={{ fontSize: '1.25rem', fontWeight: 900, letterSpacing: '-0.02em', color: 'var(--text-primary)', lineHeight: 1 }}>CHETANA</div>
            <div style={{ fontSize: '0.625rem', color: 'var(--chetana-teal)', fontWeight: 700, letterSpacing: '0.05em', marginTop: '0.35rem', opacity: 0.9, lineHeight: 1 }}>Awaken to your health</div>
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
            <span className="theme-label" style={{ color: 'var(--text-muted)' }}>{theme === 'dark' ? 'Dark' : 'Light'}</span>
          </div>

          {/* Family info */}
          <div style={{ padding: '0.75rem 1rem', background: 'hsla(175,100%,45%,0.05)', borderRadius: '12px', border: '1px solid hsla(175,100%,45%,0.1)' }}>
            <button onClick={handleLogout} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '0.75rem', padding: 0, display: 'flex', alignItems: 'center', gap: '0.375rem', fontWeight: 600 }}>
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

        <header className="header" style={{ marginBottom: '3.5rem' }}>
          {currentView === 'dashboard' && (
            <>
              <div className="header-top">
                <div>
                  <h1 style={{ fontSize: '2.5rem', marginBottom: '0.4rem', fontWeight: 900 }}>Welcome back, {appSession.familyName}</h1>
                  <p style={{ color: 'var(--text-secondary)', fontSize: '1.125rem' }}>Your family sanctuary is synced and secure.</p>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: '0.75rem', fontWeight: 800, color: 'var(--chetana-teal)', letterSpacing: '0.12em', textTransform: 'uppercase', marginBottom: '0.25rem' }}>Active Sanctuary</div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 900, color: 'var(--text-primary)' }}>{appSession.familyName} Residence</div>
                </div>
              </div>
              
              <div className="family-hub" style={{ marginTop: '2.5rem', display: 'flex', gap: '1.25rem', overflowX: 'auto', padding: '1.5rem 2rem' }}>
                {members.map(m => (
                  <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.625rem', cursor: 'pointer' }} onClick={() => setSelectedMember(m)}>
                    <div 
                      className={`member-avatar ${selectedMember?.id === m.id ? 'active' : ''}`}
                      style={{ width: '60px', height: '60px', borderRadius: '20px', background: selectedMember?.id === m.id ? 'var(--chetana-teal)' : 'var(--glass-bg)', display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'all 0.3s ease', border: '1px solid var(--glass-border)' }}
                    >
                      <span style={{ fontSize: '1.5rem', filter: selectedMember?.id === m.id ? 'brightness(0)' : 'none' }}>{m.icon || '👤'}</span>
                    </div>
                    <div style={{ fontSize: '0.8125rem', fontWeight: selectedMember?.id === m.id ? 700 : 500, color: selectedMember?.id === m.id ? 'var(--text-primary)' : 'var(--text-secondary)' }}>{m.name}</div>
                  </div>
                ))}
                <div 
                  style={{ width: '60px', height: '60px', borderRadius: '20px', border: '2px dashed var(--glass-border)', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: 'var(--text-muted)', flexShrink: 0 }}
                  onClick={() => {
                    const newName = window.prompt('Enter new family member name:');
                    if (newName && appSession) {
                       api.addMember(appSession.familyId, { name: newName.trim(), relationship: 'Dependent' }).then(m => {
                          const updated = [...members, m];
                          setMembers(updated);
                          setSelectedMember(m);
                          sessionHelper.save({ ...appSession, members: updated });
                       });
                    }
                  }}
                >
                  <span style={{ fontSize: '1.5rem' }}>+</span>
                </div>
              </div>
            </>
          )}
        </header>

        <div className="dashboard-grid">
          {currentView === 'report-detail' && activeReport ? (
            <div style={{ gridColumn: 'span 12' }}>
               <ReportDetailView 
                 report={activeReport!} 
                 onBack={() => { setIsChatOpen(false); setCurrentView('dashboard'); }} 
                 isChatOpen={isChatOpen}
                 setIsChatOpen={setIsChatOpen}
                 language={chatLanguage}
                 setLanguage={setChatLanguage}
               />
            </div>
          ) : (
            <>
              {/* Health Trends - Specific Logic */}
              <section className="glass-card timeline-card">
                <div className="card-title" style={{ justifyContent: 'space-between' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12" /></svg>
                    Health Trends
                  </div>
                  {/* Filter logic */}
                  {Array.from(new Set(observations.map(o => o.name))).length > 0 && (
                    <select 
                      className="form-input" 
                      style={{ width: 'auto', padding: '0.25rem 0.5rem', fontSize: '0.75rem', height: 'auto', borderRadius: '8px' }}
                      value={selectedTrendName}
                      onChange={(e) => setSelectedTrendName(e.target.value)}
                    >
                      {Array.from(new Set(observations.map(o => o.name)))
                        .sort((a, b) => {
                           const aObs = observations.find(o => o.name === a);
                           const bObs = observations.find(o => o.name === b);
                           if (aObs?.isAbnormal && !bObs?.isAbnormal) return -1;
                           return 0;
                        })
                        .map(name => <option key={name} value={name}>{name}</option>)}
                    </select>
                  )}
                </div>
                {(() => {
                  const currentName = selectedTrendName || observations.find(o => o.isAbnormal)?.name || observations[0]?.name;
                  const filtered = observations.filter(o => o.name === currentName);
                  
                  if (filtered.length >= 3) {
                    return (
                      <div key={currentName}>
                        <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.5rem', marginBottom: '0.75rem' }}>
                          <span style={{ fontSize: '2.5rem', fontWeight: 900 }}>{filtered[filtered.length - 1].value}</span>
                          <span style={{ color: 'var(--text-secondary)', fontSize: '1rem' }}>{filtered[filtered.length - 1].unit}</span>
                          <span style={{ color: 'var(--text-muted)', fontSize: '0.8125rem', marginLeft: '0.5rem' }}>{filtered[filtered.length - 1].name}</span>
                        </div>
                        <HealthTimeline observations={filtered} />
                      </div>
                    );
                  }
                  return (
                    <div key="no-data" style={{ padding: '2rem 0', textAlign: 'center' }}>
                      <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', fontWeight: 800 }}>INSUFFICIENT HISTORICAL DATA</p>
                      <p style={{ marginTop: '0.75rem', fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>Trend analysis requires 3 records for "{currentName || 'this test'}".</p>
                    </div>
                  );
                })()}
              </section>

              <section className="glass-card insights-card">
                <div className="card-title" style={{ justifyContent: 'space-between' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>
                    Nova AI Agent
                  </div>
                  <button 
                    className="icon-btn" 
                    title="Regenerate Insights" 
                    onClick={async () => {
                      try {
                        await api.generateInsights(appSession.familyId, selectedMember!.id);
                        refreshDashboard();
                      } catch (e) { setError('Failed to refresh insights.'); }
                    }}
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                  </button>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
                  {insights.slice(0, 2).map(i => <InsightCard key={i.insightId} insight={i} />)}
                  {insights.length === 0 && <div style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', fontStyle: 'italic', padding: '1rem 0' }}>Nova is waiting for your reports to begin analysis.</div>}
                </div>
              </section>

              {/* Reports Grid - Direct Access */}
              <section className="glass-card results-card" style={{ padding: 0 }}>
                 <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2.5rem' }}>
                  <div className="card-title" style={{ marginBottom: 0 }}>
                     <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/></svg>
                     Health Records Vault
                  </div>
                  <label className="btn-primary" style={{ cursor: 'pointer' }}>
                    + Upload to Vault
                    <input type="file" style={{ display: 'none' }} onChange={handleFileUpload} disabled={isUploading} accept=".pdf,image/*" />
                  </label>
                </div>
                <div className="reports-grid">
                  {reports.map(r => (
                    <ReportCard 
                      key={r.reportId} 
                      report={r} 
                      onRefresh={refreshDashboard} 
                      onSelect={() => { setActiveReport(r); setCurrentView('report-detail'); }}
                    />
                  ))}
                  {reports.length === 0 && <div style={{ gridColumn: '1 / -1', padding: '4rem', textAlign: 'center', color: 'var(--text-muted)' }}>No medical documents found in your sanctuary.</div>}
                </div>
              </section>
            </>
          )}
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
