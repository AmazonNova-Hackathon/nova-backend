// ─── Types ───────────────────────────────────────────────────────────────────

export interface FamilyMember {
  id: string;
  name: string;
  age?: number;
  gender?: string;
  relationship?: string;
  icon?: string;
}

export interface Observation {
  id: string;
  loincCode: string;
  name: string;
  value: number;
  unit: string;
  date: string;
  isAbnormal: boolean;
  interpretation?: 'N' | 'H' | 'L' | 'HH' | 'LL' | 'U';
  reportId?: string;
  normalLow?: number;
  normalHigh?: number;
}

export interface Insight {
  insightId: string;
  severity: 'urgent' | 'attention' | 'informational';
  title: string;
  summary: string;
  content: string;
  citedObservations?: string[];
  citedReports?: string[];
  generatedAt?: string;
}

export interface Followup {
  id: string;
  testName: string;
  loincCode: string;
  reason: string;
  suggestedDate: string;
  status: 'pending' | 'completed' | 'dismissed';
}

export interface Report {
  reportId: string;
  title: string;
  date: string;
  status: 'Analyzed' | 'Processing' | 'Failed';
  totalTests?: number;
  abnormalCount?: number;
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
      `${BASE_URL}/families/${familyId}/members/${memberId}/observations`,
      { headers }
    );
    if (!res.ok) throw new Error(`GET /observations failed: ${res.status}`);
    const data = await res.json();
    return (data.observations || []).map((o: any) => ({
      id: o.id,
      loincCode: o.loincCode || '',
      name: o.name || 'Observation',
      value: o.value ?? 0,
      unit: o.unit || '',
      date: o.date || '',
      isAbnormal: o.isAbnormal || false,
      interpretation: o.interpretation,
      reportId: o.reportId,
      normalLow: o.normalLow,
      normalHigh: o.normalHigh
    }));
  },

  getInsights: async (familyId: string, memberId: string): Promise<Insight[]> => {
    const res = await fetch(
      `${BASE_URL}/families/${familyId}/members/${memberId}/insights`,
      { headers }
    );
    if (!res.ok) throw new Error(`GET /insights failed: ${res.status}`);
    const data = await res.json();
    return data.insights || [];
  },

  getFollowups: async (familyId: string, memberId: string): Promise<Followup[]> => {
    const res = await fetch(
      `${BASE_URL}/families/${familyId}/members/${memberId}/followups?status=all`,
      { headers }
    );
    if (!res.ok) throw new Error(`GET /followups failed: ${res.status}`);
    const data = await res.json();
    return data.followups || [];
  },

  updateFollowup: async (familyId: string, memberId: string, followUpId: string, updates: Partial<Followup>) => {
    const res = await fetch(
      `${BASE_URL}/families/${familyId}/members/${memberId}/followups/${followUpId}`,
      { method: 'PATCH', headers, body: JSON.stringify(updates) }
    );
    if (!res.ok) throw new Error(`PATCH /followups failed: ${res.status}`);
    return res.json();
  },

  generateInsights: async (familyId: string, memberId: string): Promise<any> => {
    const res = await fetch(
      `${BASE_URL}/families/${familyId}/members/${memberId}/insights/generate`,
      { method: 'POST', headers }
    );
    if (!res.ok) throw new Error(`POST /insights/generate failed: ${res.status}`);
    return res.json();
  },

  getReports: async (familyId: string, memberId: string): Promise<Report[]> => {
    const res = await fetch(
      `${BASE_URL}/families/${familyId}/members/${memberId}/reports`,
      { headers }
    );
    if (!res.ok) throw new Error(`GET /reports failed: ${res.status}`);
    const data = await res.json();
    return (data.reports || []).map((r: any) => ({
      reportId: r.reportId,
      title: r.labName || 'Medical Report',
      date: r.date || '',
      status: r.status === 'completed' ? 'Analyzed' : (r.status === 'processing' || r.status === 'uploading') ? 'Processing' : 'Failed',
      totalTests: r.totalObservations,
      abnormalCount: r.abnormalCount,
    }));
  },

  uploadReport: async (familyId: string, memberId: string, file: File) => {
    const urlRes = await fetch(
      `${BASE_URL}/families/${familyId}/members/${memberId}/reports/upload-url?contentType=${encodeURIComponent(file.type)}`,
      { headers }
    );
    if (!urlRes.ok) throw new Error('Failed to get upload URL');
    const { url, s3Key, reportId } = await urlRes.json();
    
    // Upload directly to S3
    const uploadRes = await fetch(url, { 
      method: 'PUT', 
      body: file, 
      headers: { 'Content-Type': file.type } 
    });
    
    if (!uploadRes.ok) throw new Error(`S3 upload failed: ${uploadRes.status}`);

    // Trigger processing
    await fetch(`${BASE_URL}/families/${familyId}/members/${memberId}/reports/upload`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ s3Key, reportType: 'lab_report' }),
    });

    return { reportId, s3Key };
  },

  getReportDownloadUrl: async (familyId: string, memberId: string, reportId: string) => {
    const res = await fetch(`${BASE_URL}/families/${familyId}/members/${memberId}/reports/${reportId}/download`, { headers });
    if (!res.ok) throw new Error('Failed to get download URL');
    const data = await res.json();
    return data.url;
  },

  deleteReport: async (familyId: string, memberId: string, reportId: string) => {
    const res = await fetch(`${BASE_URL}/families/${familyId}/members/${memberId}/reports/${reportId}`, {
      method: 'DELETE',
      headers
    });
    if (!res.ok) throw new Error('Failed to delete report');
    return true;
  },

  chat: async (familyId: string, memberId: string, message: string, sessionId: string = '', reportId: string = '', language: string = 'English', responseFormat: 'text' | 'audio' | 'both' = 'text'): Promise<{ message: string; sessionId: string; audioBase64?: string }> => {
    const res = await fetch(`${BASE_URL}/families/${familyId}/members/${memberId}/chat`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ message, sessionId, reportId, language, responseFormat }),
    });
    if (!res.ok) throw new Error(`POST /chat failed: ${res.status}`);
    const data = await res.json();
    return { message: data.reply, sessionId: data.sessionId, audioBase64: data.audioBase64 };
  },
};
