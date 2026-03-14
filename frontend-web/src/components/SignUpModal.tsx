import React, { useState } from 'react';

interface SignUpModalProps {
  onClose: () => void;
  onSuccess: () => void;
}

const RELATIONSHIPS = ['Self', 'Spouse', 'Parent', 'Child', 'Sibling', 'Other'];

export const SignUpModal: React.FC<SignUpModalProps> = ({ onClose, onSuccess }) => {
  const [step, setStep] = useState(1); // 1 = family name, 2 = add yourself, 3 = success

  // Step 1
  const [familyName, setFamilyName] = useState('');
  const [createdFamilyId, setCreatedFamilyId] = useState('');

  // Step 2
  const [memberName, setMemberName] = useState('');
  const [age, setAge] = useState('');
  const [gender, setGender] = useState('');
  const [relationship, setRelationship] = useState('Self');

  // State
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const API_KEY = import.meta.env.VITE_API_KEY;
  const BASE_URL = import.meta.env.VITE_API_URL;

  const headers = { 'Content-Type': 'application/json', 'x-api-key': API_KEY };

  const handleCreateFamily = async () => {
    if (!familyName.trim()) { setError('Please enter a family name.'); return; }
    setLoading(true);
    setError('');
    try {
      const res = await fetch(`${BASE_URL}/families`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ name: familyName.trim() }),
      });
      if (!res.ok) throw new Error(`API error ${res.status}`);
      const data = await res.json();
      setCreatedFamilyId(data.id);
      setStep(2);
    } catch (e: any) {
      setError('Could not create family. Check your connection and try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleAddMember = async () => {
    if (!memberName.trim()) { setError('Please enter your name.'); return; }
    setLoading(true);
    setError('');
    try {
      const body: any = { name: memberName.trim(), relationship };
      if (age) body.age = parseInt(age, 10);
      if (gender) body.gender = gender;

      const res = await fetch(`${BASE_URL}/families/${createdFamilyId}/members`, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(`API error ${res.status}`);
      const member = await res.json();

      // Save session to localStorage
      const sessionData = {
        familyId: createdFamilyId,
        familyName: familyName.trim(),
        members: [{ id: member.id, name: member.name, relationship: member.relationship }],
      };
      localStorage.setItem('chetana_session', JSON.stringify(sessionData));
      setStep(3);
    } catch (e: any) {
      setError('Could not add member. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal-box">
        <div className="modal-header">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
            <div>
              <div className="modal-title">
                {step === 1 && 'Create Your Sanctuary'}
                {step === 2 && 'Add Yourself'}
                {step === 3 && 'Welcome Aboard!'}
              </div>
              <div className="modal-subtitle">
                {step === 1 && 'Your family health records, secured by FHIR HL7.'}
                {step === 2 && 'Add your profile to the family.'}
                {step === 3 && 'Your sanctuary is ready.'}
              </div>
            </div>
            <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '1.25rem', padding: '0.25rem' }}>✕</button>
          </div>

          {/* Step indicator */}
          {step < 3 && (
            <div className="step-indicator">
              <div className={`step-dot ${step >= 1 ? (step > 1 ? 'done' : 'active') : ''}`} />
              <div className={`step-dot ${step >= 2 ? 'active' : ''}`} />
            </div>
          )}
        </div>

        <div className="modal-body">
          {error && <div className="error-banner">{error}</div>}

          {/* Step 1: Family Name */}
          {step === 1 && (
            <div>
              <div className="form-group">
                <label className="form-label">Family Name</label>
                <input
                  className="form-input"
                  type="text"
                  placeholder="e.g. Sharma Family"
                  value={familyName}
                  onChange={e => setFamilyName(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && handleCreateFamily()}
                  autoFocus
                />
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '1.5rem' }}>
                Your health data is stored in FHIR HL7 format and encrypted at rest with AES-256.
              </p>
              <button className="btn-primary" style={{ width: '100%', justifyContent: 'center' }} onClick={handleCreateFamily} disabled={loading}>
                {loading ? 'Creating...' : 'Continue →'}
              </button>
            </div>
          )}

          {/* Step 2: Add yourself */}
          {step === 2 && (
            <div>
              <div className="form-group">
                <label className="form-label">Your Full Name</label>
                <input className="form-input" type="text" placeholder="e.g. Rahul Sharma" value={memberName} onChange={e => setMemberName(e.target.value)} autoFocus />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Age (optional)</label>
                  <input className="form-input" type="number" placeholder="35" min={1} max={120} value={age} onChange={e => setAge(e.target.value)} />
                </div>
                <div className="form-group">
                  <label className="form-label">Gender (optional)</label>
                  <select className="form-input" value={gender} onChange={e => setGender(e.target.value)}>
                    <option value="">Prefer not to say</option>
                    <option value="male">Male</option>
                    <option value="female">Female</option>
                    <option value="other">Other</option>
                  </select>
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Relationship</label>
                <select className="form-input" value={relationship} onChange={e => setRelationship(e.target.value)}>
                  {RELATIONSHIPS.map(r => <option key={r} value={r}>{r}</option>)}
                </select>
              </div>
              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
                <button className="btn-ghost" onClick={() => setStep(1)}>← Back</button>
                <button className="btn-primary" style={{ flex: 1, justifyContent: 'center' }} onClick={handleAddMember} disabled={loading}>
                  {loading ? 'Setting up...' : 'Enter Sanctuary →'}
                </button>
              </div>
            </div>
          )}

          {/* Step 3: Success */}
          {step === 3 && (
            <div style={{ textAlign: 'center' }}>
              <div className="success-icon">✓</div>
              <p style={{ color: 'var(--text-secondary)', marginBottom: '2rem', lineHeight: 1.7 }}>
                Your family health sanctuary has been created with a FHIR-compliant record for <strong style={{ color: 'var(--text-primary)' }}>{memberName}</strong>. Your data is encrypted and secure.
              </p>
              <button className="btn-primary" style={{ width: '100%', justifyContent: 'center' }} onClick={onSuccess}>
                Go to Dashboard →
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
