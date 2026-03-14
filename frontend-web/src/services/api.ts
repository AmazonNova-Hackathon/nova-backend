// ─── Types ───────────────────────────────────────────────────────────────────

export interface FamilyMember {
  id: string;
  name: string;
  age?: number;
  gender?: string;
  relationship?: string;
}

export interface Observation {
  id: string;
  loincCode: string;
  name: string;
  value: number;
  unit: string;
  date: string;
  isAbnormal: boolean;
}

export interface Insight {
  insightId: string;
  severity: 'urgent' | 'attention' | 'informational';
  title: string;
  summary: string;
  content: string;
}

export interface Report {
  reportId: string;
  title: string;
  date: string;
  status: 'Analyzed' | 'Processing' | 'Archived';
}

export interface Session {
  familyId: string;
  familyName: string;
  members: FamilyMember[];
}

// ─── Config ──────────────────────────────────────────────────────────────────

const BASE_URL = import.meta.env.VITE_API_URL;
const API_KEY  = import.meta.env.VITE_API_KEY;

const SESSION_KEY = 'chetana_session';

const headers = {
  'Content-Type': 'application/json',
  'x-api-key': API_KEY,
};

// ─── Session helpers ──────────────────────────────────────────────────────────

export const session = {
  get: (): Session | null => {
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  },
  save: (s: Session) => {
    localStorage.setItem(SESSION_KEY, JSON.stringify(s));
  },
  clear: () => {
    localStorage.removeItem(SESSION_KEY);
  },
};

// ─── API ─────────────────────────────────────────────────────────────────────

export const api = {
  // Create a new family — POST /families
  createFamily: async (name: string): Promise<{ id: string; name: string }> => {
    const res = await fetch(`${BASE_URL}/families`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ name }),
    });
    if (!res.ok) throw new Error(`POST /families failed: ${res.status}`);
    return res.json();
  },

  // Add a member — POST /families/{familyId}/members
  addMember: async (
    familyId: string,
    memberData: { name: string; age?: number; gender?: string; relationship?: string }
  ): Promise<FamilyMember> => {
    const res = await fetch(`${BASE_URL}/families/${familyId}/members`, {
      method: 'POST',
      headers,
      body: JSON.stringify(memberData),
    });
    if (!res.ok) throw new Error(`POST /families/${familyId}/members failed: ${res.status}`);
    return res.json();
  },

  // Get family members — GET /families/{familyId}/members
  getMembers: async (familyId: string): Promise<FamilyMember[]> => {
    const res = await fetch(`${BASE_URL}/families/${familyId}/members`, { headers });
    if (!res.ok) throw new Error(`GET /families/${familyId}/members failed: ${res.status}`);
    const data = await res.json();
    return data.members || [];
  },

  getObservations: async (familyId: string, memberId: string): Promise<Observation[]> => {
    const res = await fetch(
      `${BASE_URL}/observations?familyId=${familyId}&memberId=${memberId}`,
      { headers }
    );
    if (!res.ok) throw new Error(`GET /observations failed: ${res.status}`);
    const data = await res.json();
    return (data.observations || []).map((o: any) => ({
      id: o.id,
      loincCode: o.code?.coding?.[0]?.code || '',
      name: o.code?.coding?.[0]?.display || o.code?.text || 'Observation',
      value: o.valueQuantity?.value ?? 0,
      unit: o.valueQuantity?.unit || '',
      date: o.effectiveDateTime || o.issued || '',
      isAbnormal: o.interpretation?.[0]?.coding?.[0]?.code === 'A',
    }));
  },

  getInsights: async (familyId: string, memberId: string): Promise<Insight[]> => {
    const res = await fetch(
      `${BASE_URL}/insights?familyId=${familyId}&memberId=${memberId}`,
      { headers }
    );
    if (!res.ok) throw new Error(`GET /insights failed: ${res.status}`);
    const data = await res.json();
    return data.insights || [];
  },

  getReports: async (familyId: string, memberId: string): Promise<Report[]> => {
    const res = await fetch(
      `${BASE_URL}/reports?familyId=${familyId}&memberId=${memberId}`,
      { headers }
    );
    if (!res.ok) throw new Error(`GET /reports failed: ${res.status}`);
    const data = await res.json();
    return (data.reports || []).map((r: any) => ({
      reportId: r.id,
      title: r.code?.coding?.[0]?.display || r.code?.text || 'Medical Report',
      date: r.issued || r.effectiveDateTime || '',
      status: r.status === 'final' ? 'Analyzed' : r.status === 'preliminary' ? 'Processing' : 'Archived',
    }));
  },

  uploadReport: async (familyId: string, memberId: string, file: File) => {
    const urlRes = await fetch(
      `${BASE_URL}/reports/upload-url?familyId=${familyId}&memberId=${memberId}`,
      { headers }
    );
    const { url, s3Key } = await urlRes.json();
    await fetch(url, { method: 'PUT', body: file, headers: { 'Content-Type': file.type } });
    await fetch(`${BASE_URL}/reports/upload`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ patientId: memberId, s3Key, reportType: 'lab_report' }),
    });
  },

  chat: async (memberId: string, message: string, history: { role: string; content: string }[] = []) => {
    const res = await fetch(`${BASE_URL}/chat`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ patientId: memberId, message, conversationHistory: history }),
    });
    if (!res.ok) throw new Error(`POST /chat failed: ${res.status}`);
    return res.json();
  },
};
