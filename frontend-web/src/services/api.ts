export interface FamilyMember {
  id: string;
  name: string;
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

const BASE_URL = import.meta.env.VITE_API_URL;
const API_KEY = import.meta.env.VITE_API_KEY;

// Mock Family ID for now (matches patient-demo-001 context)
const DEFAULT_FAMILY_ID = 'family-demo-001';

const headers = {
  'Content-Type': 'application/json',
  'x-api-key': API_KEY,
};

console.log('Sanctuary initialized with key:', API_KEY ? 'Present' : 'Missing');

export const api = {
  getMembers: async (): Promise<FamilyMember[]> => {
    // API GET /families/ members is missing in target AWS stage, using hardcoded sanctuary list
    return [
      { id: 'm1', name: 'Rahul' },
      { id: 'm2', name: 'Sunita' },
      { id: 'm3', name: 'Amit' },
      { id: 'm4', name: 'Priya' },
    ];
  },

  getObservations: async (memberId: string): Promise<Observation[]> => {
    // memberId is used as familyId for demo purposes in this stage
    try {
      const response = await fetch(`${BASE_URL}/observations?familyId=${DEFAULT_FAMILY_ID}&memberId=${memberId}`, { headers });
      if (!response.ok) {
        throw new Error("Live failed");
      }
      const data = await response.json();
      return (data.observations || []).map((o: any) => ({
        id: o.id,
        loincCode: o.code?.coding?.[0]?.code || '2339-0',
        name: o.code?.coding?.[0]?.display || 'Glucose',
        value: o.valueQuantity?.value || 0,
        unit: o.valueQuantity?.unit || 'mg/dL',
        date: o.effectiveDateTime || new Date().toISOString(),
        isAbnormal: o.interpretation?.[0]?.coding?.[0]?.code === 'A',
      }));
    } catch (e) {
      console.warn('Live observations failed, falling back to pulse mock data');
      return [
        { id: '1', loincCode: '2339-0', name: 'Glucose', value: 92, unit: 'mg/dL', date: '2024-01-10', isAbnormal: false },
        { id: '2', loincCode: '2339-0', name: 'Glucose', value: 98, unit: 'mg/dL', date: '2024-02-15', isAbnormal: false },
        { id: '3', loincCode: '2339-0', name: 'Glucose', value: 108, unit: 'mg/dL', date: '2024-03-20', isAbnormal: true },
      ];
    }
  },

  getInsights: async (memberId: string): Promise<Insight[]> => {
    try {
      const response = await fetch(`${BASE_URL}/insights?familyId=${DEFAULT_FAMILY_ID}&memberId=${memberId}`, { headers });
      if (!response.ok) throw new Error("Live failed");
      const data = await response.json();
      return data.insights || [];
    } catch (e) {
      return [
        { insightId: 'i1', severity: 'urgent', title: 'Elevated Fasting Glucose', summary: 'Pre-diabetic trend', content: 'Your glucose levels have breached the 100mg/dL threshold for the first time across 3 trailing months. Suggest scheduling an HbA1c screening.' },
        { insightId: 'i2', severity: 'attention', title: 'Consistent BP Stability', summary: 'Stable trend', content: 'Rahul, your blood pressure has remained perfectly in the 120/80 range since the start of the year.' }
      ];
    }
  },

  getReports: async (memberId: string): Promise<Report[]> => {
    try {
      const response = await fetch(`${BASE_URL}/reports?familyId=${DEFAULT_FAMILY_ID}&memberId=${memberId}`, { headers });
      if (!response.ok) throw new Error("Live failed");
      const data = await response.json();
      return (data.reports || []).map((r: any) => ({
        reportId: r.id,
        title: r.code?.coding?.[0]?.display || 'Medical Report',
        date: r.issued || r.effectiveDateTime || 'Recent',
        status: r.status === 'final' ? 'Analyzed' : 'Processing',
      }));
    } catch (e) {
      return [
        { reportId: 'r1', title: 'Q1 Comprehensive Metabolic Panel', date: '2024-03-20', status: 'Analyzed' },
        { reportId: 'r2', title: 'Lipid Panel Screen', date: '2023-11-15', status: 'Archived' }
      ];
    }
  },

  uploadReport: async (memberId: string, file: File) => {
    const urlResponse = await fetch(`${BASE_URL}/reports/upload-url?familyId=${DEFAULT_FAMILY_ID}&memberId=${memberId}`, { headers });
    const { url, s3Key } = await urlResponse.json();

    await fetch(url, {
      method: 'PUT',
      body: file,
      headers: { 'Content-Type': file.type }
    });

    await fetch(`${BASE_URL}/reports/upload`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ patientId: memberId, s3Key, reportType: 'lab_report' })
    });
  },

  chat: async (memberId: string, message: string, history: any[] = []) => {
    try {
      const response = await fetch(`${BASE_URL}/chat`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ patientId: memberId, message, conversationHistory: history })
      });
      if (!response.ok) throw new Error("Live failed");
      return await response.json();
    } catch (e) {
      await new Promise(r => setTimeout(r, 1000));
      return {
        message: "Rahul, I have reviewed the last 3 months of observations you requested. Your fasting glucose remains stable, although the most recent report on March 20th showed a slight rise from 98 to 108 mg/dL. I suggest you keep monitoring it. Do you have any questions about this trend?"
      };
    }
  }
};
