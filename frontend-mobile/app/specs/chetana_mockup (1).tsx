import { useState, useEffect, useRef } from "react";

const C = {
  brandDeep:"#1a1145", brandMid:"#2d1f6e", brandPurple:"#7F77DD",
  brandPurpleLight:"#EEEDFE", brandPurpleMid:"#534AB7",
  forest:"#0F6E56", forestLight:"#E1F5EE",
  normalBg:"#E1F5EE", normalText:"#0F6E56",
  highBg:"#FCEBEB", highText:"#A32D2D",
  lowBg:"#E6F1FB", lowText:"#185FA5",
  critHighBg:"#E24B4A", critHighText:"#FFFFFF",
  critLowBg:"#378ADD", critLowText:"#FFFFFF",
  urgentBorder:"#E24B4A", urgentBg:"#FCEBEB", urgentText:"#A32D2D",
  attnBorder:"#EF9F27", attnBg:"#FAEEDA", attnText:"#633806",
  infoBorder:"#1D9E75", infoBg:"#E1F5EE", infoText:"#085041",
  bg:"#F8F8F6", card:"#FFFFFF", border:"rgba(0,0,0,0.1)",
  t1:"#1C1C1A", t2:"#5F5E5A", t3:"#888780",
};

// ── SaMD string helpers (mirrors SaMDStringHelper.kt) ──
const SaMD = {
  interpLabel: code => ({
    N:"Within range", H:"Above reference range",
    L:"Below reference range", HH:"Critically elevated",
    LL:"Critically low"
  }[code] || "See report"),

  sevLabel: sev => ({
    urgent:"Requires prompt review",
    attention:"Worth monitoring",
    informational:"For your awareness"
  }[sev] || sev),

  disclaimer: {
    full: "Chetana does not provide medical advice. All information is for informational purposes only. Always consult a licensed healthcare provider.",
    short: "For informational purposes only. Consult your doctor.",
    inline: "ⓘ This is not medical advice."
  }
};

// ── Confidence badge logic ──
function confidenceStyle(c) {
  if (c >= 0.90) return { bg:"#E1F5EE", text:"#0F6E56", label:"High confidence" };
  if (c >= 0.70) return { bg:"#FAEEDA", text:"#633806", label:"Moderate confidence" };
  return { bg:"#FCEBEB", text:"#A32D2D", label:"Low confidence — verify manually" };
}

const MEMBERS = [
  {id:"m1",name:"Rahul Sharma",rel:"Self",initials:"RS",color:"#7F77DD",score:68,abnormal:3,lastReport:"Mar 15, 2024",severity:"urgent"},
  {id:"m2",name:"Sunita Sharma",rel:"Mother",initials:"SS",color:"#1D9E75",score:82,abnormal:1,lastReport:"Feb 28, 2024",severity:"attention"},
  {id:"m3",name:"Vikram Sharma",rel:"Father",initials:"VS",color:"#D85A30",score:91,abnormal:0,lastReport:"Jan 10, 2024",severity:"normal"},
];

// Each observation now carries the lab's own printed ref range (from API)
// plus the FHIR resourceType field — mirrors the Kotlin data class
const OBSERVATIONS = [
  {resourceType:"Observation", name:"Glucose (Fasting)", loinc:"2339-0", value:110, unit:"mg/dL", normalLow:70,  normalHigh:100, interp:"H"},
  {resourceType:"Observation", name:"HbA1c",             loinc:"4548-4", value:6.2, unit:"%",     normalLow:4.0, normalHigh:5.6, interp:"H"},
  {resourceType:"Observation", name:"Total Cholesterol", loinc:"2093-3", value:185, unit:"mg/dL", normalLow:0,   normalHigh:200, interp:"N"},
  {resourceType:"Observation", name:"LDL Cholesterol",   loinc:"18262-6",value:112, unit:"mg/dL", normalLow:0,   normalHigh:100, interp:"H"},
  {resourceType:"Observation", name:"HDL Cholesterol",   loinc:"2085-9", value:52,  unit:"mg/dL", normalLow:40,  normalHigh:60,  interp:"N"},
  {resourceType:"Observation", name:"Triglycerides",     loinc:"2571-8", value:145, unit:"mg/dL", normalLow:0,   normalHigh:150, interp:"N"},
  {resourceType:"Observation", name:"Vitamin D (25-OH)", loinc:"1989-3", value:28,  unit:"ng/mL", normalLow:30,  normalHigh:100, interp:"L"},
  {resourceType:"Observation", name:"TSH",               loinc:"3016-3", value:2.1, unit:"µIU/mL",normalLow:0.4, normalHigh:4.0, interp:"N"},
];

// Insights use SaMD language — no "you have", no "dangerous"
const INSIGHTS = [
  {id:"i1", severity:"urgent",
   title:"Glucose trending upward",
   summary:"Your fasting glucose has been rising across 3 consecutive reports (95 → 102 → 110 mg/dL). Combined with HbA1c at 6.2%, which is in the pre-diabetic range (5.7–6.4%), this pattern warrants a conversation with your doctor.",
   member:"Rahul Sharma", obs:[{name:"Glucose",loinc:"2339-0",val:"110 mg/dL"},{name:"HbA1c",loinc:"4548-4",val:"6.2%"}],
   action:"Schedule an appointment with your doctor", read:false, date:"2 hours ago"},
  {id:"i2", severity:"attention",
   title:"LDL above optimal range",
   summary:"Your LDL is 112 mg/dL, above the optimal level of 100. This pattern may improve with dietary changes — consult your doctor for personalised advice.",
   member:"Rahul Sharma", obs:[{name:"LDL",loinc:"18262-6",val:"112 mg/dL"}],
   action:"Recheck in 3 months after dietary review", read:false, date:"2 hours ago"},
  {id:"i3", severity:"attention",
   title:"Vitamin D below recommended level",
   summary:"Your Vitamin D is 28 ng/mL, just below the recommended threshold of 30. This is common in urban India. Your doctor can advise on supplementation.",
   member:"Rahul Sharma", obs:[{name:"Vitamin D",loinc:"1989-3",val:"28 ng/mL"}],
   action:"Discuss supplementation with your doctor", read:true, date:"Yesterday"},
  {id:"i4", severity:"informational",
   title:"Thyroid function within range",
   summary:"TSH is within the reference range — thyroid function appears normal. No action needed at this time.",
   member:"Sunita Sharma", obs:[{name:"TSH",loinc:"3016-3",val:"2.1 µIU/mL"}],
   action:"Routine check in 12 months", read:true, date:"3 days ago"},
];

const FOLLOWUPS = [
  {test:"HbA1c",  loinc:"4548-4",  reason:"Elevated glucose and HbA1c pattern — standard guidelines suggest retesting every 3 months", date:"Jun 15, 2024", status:"pending"},
  {test:"Vitamin D", loinc:"1989-3", reason:"Follow-up after 2 months of supplementation to check whether levels have normalised", date:"May 28, 2024", status:"pending"},
  {test:"Lipid Panel", loinc:"2093-3", reason:"LDL above optimal — recheck after dietary changes to monitor response", date:"Jun 1, 2024", status:"accepted"},
];

const CHAT_INIT = [
  {role:"assistant", text:"Hi Rahul! I can help you understand your lab reports and health trends. What would you like to know?", obs:[]},
];

const SUGGESTIONS = ["How is my glucose?","Summarize last report","What should I test next?","Is my HbA1c improving?"];

// SaMD-compliant response map — no diagnosis, no treatment advice
const CHAT_REPLIES = {
  "How is my glucose?":
    "Your fasting glucose has been rising over the past 3 reports: 95 mg/dL in January, 102 in February, and 110 in March. It is now above the reference range of 70–100 mg/dL. Please consult your doctor to discuss this pattern.\n\n" + SaMD.disclaimer.short,
  "Summarize last report":
    "Your March 15 report from City Diagnostics shows 8 tests. 3 values are outside the reference range: Glucose (110 mg/dL, above 70–100), HbA1c (6.2%, above 4.0–5.6%), and LDL (112 mg/dL, above 0–100). The remaining 5 values are within range. Please consult your doctor for medical advice.\n\n" + SaMD.disclaimer.short,
  "What should I test next?":
    "Based on the pattern in your records, your follow-up reminders include: HbA1c recheck in 3 months, a Lipid Panel after dietary changes, and a Vitamin D recheck. Your doctor can confirm the appropriate timing and tests for your situation.\n\n" + SaMD.disclaimer.short,
  "Is my HbA1c improving?":
    "Your HbA1c has been rising: 5.8% in January → 6.0% in February → 6.2% in March. It is currently in the pre-diabetic range (5.7–6.4%). This is a pattern worth discussing with your doctor.\n\n" + SaMD.disclaimer.short,
};

function interpStyle(i) {
  if (i==="HH") return {bg:C.critHighBg, text:C.critHighText, border:C.critHighBg};
  if (i==="LL") return {bg:C.critLowBg,  text:C.critLowText,  border:C.critLowBg};
  if (i==="H")  return {bg:C.highBg,     text:C.highText,     border:C.highText};
  if (i==="L")  return {bg:C.lowBg,      text:C.lowText,      border:C.lowText};
  return {bg:C.normalBg, text:C.normalText, border:C.normalText};
}
function sevStyle(s) {
  if(s==="urgent")      return {border:C.urgentBorder, bg:C.urgentBg, text:C.urgentText};
  if(s==="attention")   return {border:C.attnBorder,   bg:C.attnBg,   text:C.attnText};
  return {border:C.infoBorder, bg:C.infoBg, text:C.infoText};
}

function Phone({children, screen, nav}) {
  return (
    <div style={{maxWidth:390,margin:"0 auto",background:C.bg,minHeight:700,display:"flex",flexDirection:"column",borderRadius:32,overflow:"hidden",border:`1px solid ${C.border}`}}>
      <div style={{flex:1,overflowY:"auto",WebkitOverflowScrolling:"touch"}}>{children}</div>
      {nav && <BottomNav screen={screen} nav={nav}/>}
    </div>
  );
}

function BottomNav({screen,nav}) {
  const tabs=[{id:"family",label:"Family",icon:"⌂"},{id:"upload",label:"Upload",icon:"⊕"},{id:"chat",label:"Chat",icon:"◎"},{id:"insights",label:"Insights",icon:"✦"}];
  const unread = INSIGHTS.filter(i=>!i.read).length;
  return (
    <div style={{background:C.card,borderTop:`0.5px solid ${C.border}`,display:"flex",padding:"8px 0 12px"}}>
      {tabs.map(t=>(
        <button key={t.id} onClick={()=>nav(t.id)} style={{flex:1,background:"none",border:"none",cursor:"pointer",display:"flex",flexDirection:"column",alignItems:"center",gap:3,padding:"4px 0",position:"relative"}}>
          <span style={{fontSize:18,color:screen===t.id?C.brandPurple:C.t3}}>{t.icon}</span>
          <span style={{fontSize:10,color:screen===t.id?C.brandPurple:C.t3,fontWeight:screen===t.id?500:400}}>{t.label}</span>
          {t.id==="insights" && unread>0 && <span style={{position:"absolute",top:0,right:"18%",background:"#E24B4A",color:"#fff",borderRadius:8,fontSize:9,padding:"1px 5px",fontWeight:500}}>{unread}</span>}
        </button>
      ))}
    </div>
  );
}

function TopBar({title,back,onBack,right}) {
  return (
    <div style={{background:C.card,padding:"14px 16px 12px",display:"flex",alignItems:"center",gap:10,borderBottom:`0.5px solid ${C.border}`}}>
      {back&&<button onClick={onBack} style={{background:"none",border:"none",cursor:"pointer",fontSize:18,color:C.t2,padding:"0 4px 0 0"}}>←</button>}
      <div style={{flex:1,fontSize:17,fontWeight:500,color:C.t1}}>{title}</div>
      {right}
    </div>
  );
}

function Chip({label,bg,color,small}) {
  return <span style={{background:bg||C.brandPurpleLight,color:color||C.brandPurpleMid,borderRadius:100,padding:small?"2px 8px":"4px 10px",fontSize:small?10:12,fontWeight:500,whiteSpace:"nowrap"}}>{label}</span>;
}

// ── Reusable DisclaimerBar ──
function DisclaimerBar({variant="full"}) {
  const text = SaMD.disclaimer[variant];
  return (
    <div style={{borderTop:`0.5px solid ${C.border}`,background:C.bg,padding:"7px 14px",textAlign:"center"}}>
      <span style={{fontSize:11,color:C.t3,lineHeight:1.5}}>{text}</span>
    </div>
  );
}

// ── Confidence badge ──
function ConfidenceBadge({confidence}) {
  const cs = confidenceStyle(confidence);
  const pct = Math.round(confidence*100);
  return (
    <div style={{display:"inline-flex",alignItems:"center",gap:6,background:cs.bg,borderRadius:6,padding:"4px 10px"}}>
      <span style={{fontSize:11,color:cs.text}}>Extracted by Nova AI</span>
      <span style={{fontSize:11,color:cs.text,opacity:0.5}}>·</span>
      <span style={{fontSize:11,color:cs.text,fontWeight:500}}>{pct}% · {cs.label}</span>
    </div>
  );
}

// ── SPLASH ──
function SplashScreen({onNext}) {
  const [show,setShow]=useState(false);
  useEffect(()=>{setTimeout(()=>setShow(true),300);},[]);
  return (
    <div onClick={()=>show&&onNext()} style={{minHeight:700,background:`linear-gradient(160deg,${C.brandDeep} 0%,${C.brandMid} 55%,#0f3d2e 100%)`,display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"center",gap:16,cursor:"pointer",opacity:show?1:0,transition:"opacity .4s"}}>
      <div style={{width:88,height:88,borderRadius:24,background:"rgba(255,255,255,0.1)",border:"1.5px solid rgba(255,255,255,0.2)",display:"flex",alignItems:"center",justifyContent:"center",fontSize:40}}>🌿</div>
      <div style={{textAlign:"center"}}>
        <div style={{fontSize:36,fontWeight:500,color:"#fff",letterSpacing:1}}>चेतना</div>
        <div style={{fontSize:13,color:"rgba(255,255,255,0.5)",letterSpacing:3,marginTop:2}}>CHETANA</div>
      </div>
      <div style={{fontSize:16,color:"rgba(255,255,255,0.7)",fontStyle:"italic"}}>"Awaken to your health"</div>
      <div style={{marginTop:24,padding:"13px 44px",borderRadius:14,background:"rgba(255,255,255,0.13)",border:"1.5px solid rgba(255,255,255,0.28)",color:"#fff",fontSize:15,fontWeight:500}}>Get Started →</div>
      <div style={{fontSize:11,color:"rgba(255,255,255,0.3)",marginTop:8}}>Powered by Amazon Bedrock · Nova AI</div>
    </div>
  );
}

// ── LOGIN ──
function LoginScreen({onNext}) {
  const [email,setEmail]=useState("");
  const [pw,setPw]=useState("");
  const [err,setErr]=useState("");
  const [loading,setLoading]=useState(false);
  const [showPw,setShowPw]=useState(false);
  function go(){
    setErr("");
    if(!email||!pw){setErr("Please fill in all fields.");return;}
    setLoading(true);
    setTimeout(()=>{setLoading(false);if(email==="demo@chetana.health"&&pw==="demo1234")onNext(email);else setErr("Invalid credentials. Tap the hint to auto-fill.");},1200);
  }
  return (
    <div style={{background:C.bg,minHeight:700}}>
      <div style={{height:200,background:`linear-gradient(160deg,${C.brandDeep},${C.brandMid})`,borderRadius:"0 0 32px 32px",display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"flex-end",paddingBottom:24,gap:8}}>
        <div style={{width:60,height:60,borderRadius:18,background:"rgba(255,255,255,0.12)",border:"1.5px solid rgba(255,255,255,0.22)",display:"flex",alignItems:"center",justifyContent:"center",fontSize:28}}>🌿</div>
        <div style={{textAlign:"center"}}>
          <div style={{fontSize:22,fontWeight:500,color:"#fff"}}>चेतना · Chetana</div>
          <div style={{fontSize:12,color:"rgba(255,255,255,0.5)",fontStyle:"italic",marginTop:2}}>"Awaken to your health"</div>
        </div>
      </div>
      <div style={{padding:"24px 20px",display:"flex",flexDirection:"column",gap:14}}>
        <div><div style={{fontSize:20,fontWeight:500,color:C.t1}}>Sign in</div><div style={{fontSize:13,color:C.t2,marginTop:3}}>Your family's health companion</div></div>
        <div style={{display:"flex",flexDirection:"column",gap:5}}>
          <label style={{fontSize:12,color:C.t2,fontWeight:500}}>Email</label>
          <input type="email" placeholder="you@example.com" value={email} onChange={e=>{setEmail(e.target.value);setErr("");}} style={{padding:"13px 14px",borderRadius:12,fontSize:14,border:`1px solid ${C.border}`,background:C.card,color:C.t1,outline:"none",width:"100%",boxSizing:"border-box"}}/>
        </div>
        <div style={{display:"flex",flexDirection:"column",gap:5}}>
          <label style={{fontSize:12,color:C.t2,fontWeight:500}}>Password</label>
          <div style={{position:"relative"}}>
            <input type={showPw?"text":"password"} placeholder="••••••••" value={pw} onChange={e=>{setPw(e.target.value);setErr("");}} style={{padding:"13px 44px 13px 14px",borderRadius:12,fontSize:14,border:`1px solid ${C.border}`,background:C.card,color:C.t1,outline:"none",width:"100%",boxSizing:"border-box"}}/>
            <button onClick={()=>setShowPw(s=>!s)} style={{position:"absolute",right:12,top:"50%",transform:"translateY(-50%)",background:"none",border:"none",cursor:"pointer",color:C.t3,fontSize:12}}>{showPw?"Hide":"Show"}</button>
          </div>
        </div>
        {err&&<div style={{padding:"9px 13px",borderRadius:10,background:"#FCEBEB",color:C.urgentText,fontSize:13}}>{err}</div>}
        <div onClick={()=>{setEmail("demo@chetana.health");setPw("demo1234");setErr("");}} style={{padding:"10px 13px",borderRadius:10,background:"rgba(127,119,221,0.07)",border:"1px solid rgba(127,119,221,0.18)",fontSize:12,color:C.t2,cursor:"pointer",lineHeight:1.7}}>
          <span style={{fontWeight:500,color:C.brandPurple}}>Tap to fill demo credentials</span><br/>
          Email: <code>demo@chetana.health</code> · Pass: <code>demo1234</code>
        </div>
        <button onClick={go} disabled={loading} style={{padding:"14px 0",borderRadius:13,background:loading?"rgba(127,119,221,0.4)":`linear-gradient(135deg,${C.brandPurple},${C.brandPurpleMid})`,border:"none",color:"#fff",fontSize:15,fontWeight:500,cursor:loading?"not-allowed":"pointer"}}>{loading?"Signing in...":"Continue →"}</button>
        <div style={{display:"flex",alignItems:"center",gap:10}}><div style={{flex:1,height:1,background:C.border}}/><span style={{fontSize:12,color:C.t3}}>or</span><div style={{flex:1,height:1,background:C.border}}/></div>
        <div style={{display:"flex",gap:10}}>
          {["G  Google","📱 Phone OTP"].map(l=><button key={l} onClick={()=>setErr("Coming post-hackathon via Cognito")} style={{flex:1,padding:"11px 0",borderRadius:11,border:`1px solid ${C.border}`,background:C.card,color:C.t2,fontSize:13,cursor:"pointer"}}>{l}</button>)}
        </div>
        <div style={{textAlign:"center",fontSize:13,color:C.t2}}>New to Chetana? <span style={{color:C.brandPurple,cursor:"pointer",fontWeight:500}}>Create account</span></div>
      </div>
    </div>
  );
}

// ── OTP ──
function OtpScreen({email,onNext}) {
  const [otp,setOtp]=useState(["","","","","",""]);
  const [loading,setLoading]=useState(false);
  function change(i,v){if(!/^\d?$/.test(v))return;const n=[...otp];n[i]=v;setOtp(n);if(v&&i<5)document.getElementById(`oc${i+1}`)?.focus();}
  function verify(){setLoading(true);setTimeout(()=>{setLoading(false);onNext();},1000);}
  return (
    <div style={{background:C.bg,minHeight:700}}>
      <div style={{height:170,background:`linear-gradient(160deg,${C.brandDeep},${C.brandMid})`,borderRadius:"0 0 28px 28px",display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"flex-end",paddingBottom:22,gap:7}}>
        <div style={{fontSize:26}}>🌿</div>
        <div style={{fontSize:19,fontWeight:500,color:"#fff"}}>Verify it's you</div>
        <div style={{fontSize:12,color:"rgba(255,255,255,0.5)"}}>Code sent to {email}</div>
      </div>
      <div style={{padding:"28px 20px",display:"flex",flexDirection:"column",gap:20}}>
        <div style={{fontSize:13,color:C.t2,textAlign:"center"}}>Enter any 6 digits for the demo</div>
        <div style={{display:"flex",gap:8,justifyContent:"center"}}>
          {otp.map((v,i)=><input key={i} id={`oc${i}`} type="text" inputMode="numeric" maxLength={1} value={v} onChange={e=>change(i,e.target.value)} onKeyDown={e=>{if(e.key==="Backspace"&&!v&&i>0)document.getElementById(`oc${i-1}`)?.focus();}} style={{width:44,height:52,textAlign:"center",fontSize:20,fontWeight:500,borderRadius:11,border:v?`1.5px solid ${C.brandPurple}`:`1px solid ${C.border}`,background:v?"rgba(127,119,221,0.07)":C.card,color:C.t1,outline:"none"}}/>)}
        </div>
        <button onClick={verify} disabled={loading||otp.join("").length<6} style={{padding:"14px 0",borderRadius:13,background:loading||otp.join("").length<6?"rgba(127,119,221,0.35)":`linear-gradient(135deg,${C.brandPurple},${C.brandPurpleMid})`,border:"none",color:"#fff",fontSize:15,fontWeight:500,cursor:otp.join("").length<6?"not-allowed":"pointer"}}>{loading?"Verifying...":"Verify & Enter →"}</button>
        <div style={{textAlign:"center",fontSize:13,color:C.t2}}>Didn't get it? <span style={{color:C.brandPurple,cursor:"pointer",fontWeight:500}}>Resend</span></div>
      </div>
    </div>
  );
}

// ── FAMILY DASHBOARD ──
function FamilyDashboard({goTo}) {
  const totalObs=OBSERVATIONS.length*3, totalAbn=9;
  const score=Math.round(100-(totalAbn/totalObs*100));
  return (
    <div style={{background:C.bg,minHeight:600,paddingBottom:8}}>
      <div style={{background:C.card,padding:"14px 16px 12px",display:"flex",alignItems:"center",justifyContent:"space-between",borderBottom:`0.5px solid ${C.border}`}}>
        <div style={{display:"flex",alignItems:"center",gap:8}}><span style={{fontSize:18}}>🌿</span><span style={{fontSize:16,fontWeight:500,color:C.t1}}>चेतना</span></div>
        <div style={{display:"flex",gap:8,alignItems:"center"}}><Chip label="EN" small/><button onClick={()=>goTo("settings")} style={{background:"none",border:"none",cursor:"pointer",fontSize:18,color:C.t2}}>⚙</button></div>
      </div>
      <div style={{margin:"14px 16px 0",padding:"16px",borderRadius:16,background:`linear-gradient(135deg,${C.brandPurpleLight},#e8f5ee)`,border:`1px solid rgba(127,119,221,0.2)`}}>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"flex-start"}}>
          <div>
            <div style={{fontSize:11,color:C.t2,fontWeight:500,marginBottom:4}}>FAMILY HEALTH SCORE</div>
            <div style={{fontSize:42,fontWeight:500,color:C.t1,lineHeight:1}}>{score}</div>
            <div style={{fontSize:12,color:C.t2,marginTop:4}}>Based on 3 reports · 2 members</div>
          </div>
          <div style={{textAlign:"right"}}><div style={{fontSize:22,color:C.urgentText}}>↓</div><div style={{fontSize:11,color:C.t3}}>vs last month</div></div>
        </div>
      </div>
      <div style={{padding:"16px 16px 8px"}}>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:12}}>
          <div style={{fontSize:14,fontWeight:500,color:C.t1}}>Family members</div>
          <span style={{fontSize:12,color:C.brandPurple,cursor:"pointer"}}>Manage →</span>
        </div>
        <div style={{display:"flex",gap:10,overflowX:"auto",paddingBottom:4}}>
          {MEMBERS.map(m=>(
            <div key={m.id} onClick={()=>goTo("memberDetail",m)} style={{minWidth:130,background:C.card,borderRadius:14,padding:"14px 12px",border:`1px solid ${C.border}`,cursor:"pointer",flexShrink:0}}>
              <div style={{width:44,height:44,borderRadius:22,background:m.color,display:"flex",alignItems:"center",justifyContent:"center",color:"#fff",fontSize:15,fontWeight:500,marginBottom:8}}>{m.initials}</div>
              <div style={{fontSize:14,fontWeight:500,color:C.t1,marginBottom:2}}>{m.name.split(" ")[0]}</div>
              <Chip label={m.rel} small bg="#F1EFE8" color={C.t2}/>
              <div style={{marginTop:8}}>{m.abnormal>0?<Chip label={`${m.abnormal} outside range`} small bg={C.highBg} color={C.highText}/>:<Chip label="All within range" small bg={C.normalBg} color={C.normalText}/>}</div>
              <div style={{fontSize:10,color:C.t3,marginTop:6}}>{m.lastReport}</div>
            </div>
          ))}
        </div>
      </div>
      <div style={{padding:"4px 16px"}}>
        <div style={{fontSize:14,fontWeight:500,color:C.t1,marginBottom:10}}>Recent reports</div>
        {[{member:MEMBERS[0],lab:"City Diagnostics",date:"Mar 15",abn:3},{member:MEMBERS[1],lab:"Apollo Labs",date:"Feb 28",abn:1}].map((r,i)=>(
          <div key={i} onClick={()=>goTo("result")} style={{background:C.card,borderRadius:12,padding:"12px 14px",marginBottom:8,border:`1px solid ${C.border}`,cursor:"pointer",display:"flex",alignItems:"center",gap:12}}>
            <div style={{width:36,height:36,borderRadius:18,background:r.member.color,display:"flex",alignItems:"center",justifyContent:"center",color:"#fff",fontSize:12,fontWeight:500,flexShrink:0}}>{r.member.initials}</div>
            <div style={{flex:1}}><div style={{fontSize:13,fontWeight:500,color:C.t1}}>{r.member.name}</div><div style={{fontSize:11,color:C.t3}}>{r.lab} · {r.date}</div></div>
            {r.abn>0?<Chip label={`${r.abn} outside range`} small bg={C.highBg} color={C.highText}/>:<Chip label="All within range" small bg={C.normalBg} color={C.normalText}/>}
          </div>
        ))}
      </div>
      <div onClick={()=>goTo("capture")} style={{position:"sticky",bottom:72,float:"right",marginRight:16,marginTop:8,width:56,height:56,borderRadius:28,background:C.brandPurple,display:"flex",alignItems:"center",justifyContent:"center",cursor:"pointer",fontSize:24,color:"#fff",boxShadow:`0 2px 8px rgba(127,119,221,0.4)`}}>+</div>
    </div>
  );
}

// ── MEMBER DETAIL ──
function MemberDetailScreen({member,goTo,goBack}) {
  const [tab,setTab]=useState("reports");
  const tabs=["reports","trends","insights","follow-ups"];
  const memberInsights=INSIGHTS.filter(i=>i.member===member.name);
  return (
    <div style={{background:C.bg,minHeight:600}}>
      <div style={{background:C.card,borderBottom:`0.5px solid ${C.border}`}}>
        <div style={{padding:"14px 16px 12px",display:"flex",alignItems:"center",gap:10}}>
          <button onClick={goBack} style={{background:"none",border:"none",cursor:"pointer",fontSize:18,color:C.t2}}>←</button>
          <div style={{flex:1}}><div style={{fontSize:16,fontWeight:500,color:C.t1}}>{member.name}</div><div style={{fontSize:11,color:C.t3}}>{member.rel} · FHIR Patient/{member.id}</div></div>
          <div style={{width:38,height:38,borderRadius:19,background:member.color,display:"flex",alignItems:"center",justifyContent:"center",color:"#fff",fontSize:13,fontWeight:500}}>{member.initials}</div>
        </div>
        <div style={{display:"flex",borderTop:`0.5px solid ${C.border}`}}>
          {tabs.map(t=>(
            <button key={t} onClick={()=>setTab(t)} style={{flex:1,padding:"11px 0",background:"none",border:"none",borderBottom:tab===t?`2px solid ${C.brandPurple}`:"2px solid transparent",cursor:"pointer",fontSize:11,fontWeight:tab===t?500:400,color:tab===t?C.brandPurple:C.t3,textTransform:"capitalize",whiteSpace:"nowrap"}}>
              {t==="follow-ups"?"Follow-ups":t.charAt(0).toUpperCase()+t.slice(1)}
            </button>
          ))}
        </div>
      </div>
      <div style={{padding:16}}>
        {tab==="reports"&&[{date:"Mar 15, 2024",lab:"City Diagnostics",obs:8,abn:3},{date:"Feb 10, 2024",lab:"Apollo Labs",obs:6,abn:1},{date:"Jan 5, 2024",lab:"SRL Diagnostics",obs:7,abn:0}].map((r,i)=>(
          <div key={i} onClick={()=>goTo("reportDetail")} style={{background:C.card,borderRadius:12,padding:"14px",marginBottom:10,border:`1px solid ${C.border}`,cursor:"pointer"}}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:6}}>
              <div style={{fontSize:14,fontWeight:500,color:C.t1}}>{r.date}</div>
              {r.abn>0?<Chip label={`${r.abn} outside range`} small bg={C.highBg} color={C.highText}/>:<Chip label="All within range" small bg={C.normalBg} color={C.normalText}/>}
            </div>
            <div style={{fontSize:12,color:C.t3}}>{r.lab} · {r.obs} tests · FHIR DiagnosticReport</div>
          </div>
        ))}
        {tab==="trends"&&(
          <div>
            {[{name:"Glucose",loinc:"2339-0",vals:[95,102,110],low:70,high:100,unit:"mg/dL"},{name:"HbA1c",loinc:"4548-4",vals:[5.8,6.0,6.2],low:4.0,high:5.6,unit:"%"},{name:"LDL",loinc:"18262-6",vals:[98,105,112],low:0,high:100,unit:"mg/dL"}].map((t,i)=>(
              <div key={i} style={{background:C.card,borderRadius:12,padding:"14px",marginBottom:12,border:`1px solid ${C.border}`}}>
                <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:4}}>
                  <div>
                    <div style={{fontSize:14,fontWeight:500,color:C.t1}}>{t.name}</div>
                    <div style={{fontSize:10,color:C.t3,fontFamily:"monospace"}}>LOINC {t.loinc}</div>
                  </div>
                  <div style={{fontSize:13,fontWeight:500,color:t.vals[2]>t.high?C.highText:C.normalText}}>{t.vals[2]} {t.unit} {t.vals[2]>t.high?"↑":"↓"}</div>
                </div>
                <div style={{height:60,position:"relative",background:"rgba(0,0,0,0.02)",borderRadius:8,overflow:"hidden"}}>
                  <div style={{position:"absolute",top:`${(1-t.high/(t.high*1.4))*100}%`,bottom:`${(t.low/(t.high*1.4))*100}%`,left:0,right:0,background:"rgba(29,158,117,0.1)"}}/>
                  <svg width="100%" height="60" viewBox="0 0 200 60" preserveAspectRatio="none">
                    {t.vals.map((v,vi)=>{const x=vi===0?20:vi===1?100:180;const y=60-((v/(t.high*1.4))*60);const isAbn=v>t.high;return <g key={vi}>{vi>0&&<line x1={vi===1?20:100} y1={60-((t.vals[vi-1]/(t.high*1.4))*60)} x2={x} y2={y} stroke={isAbn?C.urgentBorder:C.forest} strokeWidth="1.5" fill="none"/>}<circle cx={x} cy={y} r="4" fill={isAbn?C.urgentBorder:C.forest}/></g>;})}
                  </svg>
                </div>
                <div style={{display:"flex",justifyContent:"space-between",fontSize:10,color:C.t3,marginTop:4}}><span>Jan</span><span>Feb</span><span>Mar</span></div>
                <div style={{fontSize:10,color:C.t3,marginTop:4}}>Ref band: {t.low}–{t.high} {t.unit} (lab's range)</div>
              </div>
            ))}
          </div>
        )}
        {tab==="insights"&&memberInsights.map((ins,i)=><InsightCardComp key={i} ins={ins}/>)}
        {tab==="follow-ups"&&FOLLOWUPS.map((f,i)=><FollowUpCard key={i} f={f}/>)}
      </div>
    </div>
  );
}

// ── REPORT DETAIL ──
function ReportDetailScreen({goTo,goBack}) {
  const confidence=0.95;
  return (
    <div style={{background:C.bg,minHeight:600,paddingBottom:80}}>
      <TopBar title="Report detail" back onBack={goBack} right={<span style={{fontSize:18,color:C.t2,cursor:"pointer"}}>↑</span>}/>
      <div style={{padding:"14px 16px 0"}}>
        <div style={{background:"#111",borderRadius:12,height:200,display:"flex",alignItems:"center",justifyContent:"center",marginBottom:10,position:"relative",overflow:"hidden"}}>
          <div style={{textAlign:"center",color:"rgba(255,255,255,0.5)"}}>
            <div style={{fontSize:32,marginBottom:6}}>📄</div>
            <div style={{fontSize:12}}>Lab report image</div>
            <div style={{fontSize:11,marginTop:4,opacity:0.6}}>Pinch to zoom</div>
          </div>
          <div style={{position:"absolute",bottom:8,right:8,background:"rgba(0,0,0,0.5)",borderRadius:6,padding:"3px 8px",fontSize:10,color:"rgba(255,255,255,0.7)"}}>City Diagnostics · Mar 15, 2024</div>
        </div>
        <div style={{marginBottom:12}}><ConfidenceBadge confidence={confidence}/></div>
        <div style={{background:C.brandPurpleLight,borderRadius:10,padding:"9px 12px",marginBottom:14,fontSize:12,color:C.brandPurpleMid,border:`1px solid rgba(127,119,221,0.2)`}}>
          FHIR Patient match: <strong>Rahul Sharma</strong> · Self ✓
        </div>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:8}}>
          <div style={{fontSize:14,fontWeight:500,color:C.t1}}>Lab results · FHIR Observation</div>
          <div style={{fontSize:12,color:C.highText}}>3 outside range</div>
        </div>
        {OBSERVATIONS.map((o,i)=>{
          const s=interpStyle(o.interp);
          return (
            <div key={i} style={{background:C.card,borderRadius:11,padding:"11px 13px",marginBottom:7,border:`1px solid ${C.border}`,borderLeft:`4px solid ${s.border}`}}>
              <div style={{display:"flex",justifyContent:"space-between",alignItems:"center"}}>
                <div><div style={{fontSize:13,fontWeight:500,color:C.t1}}>{o.name}</div><div style={{fontSize:10,color:C.t3,fontFamily:"monospace"}}>LOINC {o.loinc}</div></div>
                <div style={{textAlign:"right"}}>
                  <div style={{fontSize:14,fontWeight:500,color:s.text}}>{o.value} <span style={{fontSize:11}}>{o.unit}</span></div>
                  <span style={{fontSize:10,padding:"2px 7px",borderRadius:8,background:s.bg,color:s.text,fontWeight:500}}>{SaMD.interpLabel(o.interp)}</span>
                </div>
              </div>
              <div style={{fontSize:10,color:C.t3,marginTop:4}}>Lab ref range: {o.normalLow}–{o.normalHigh} {o.unit}</div>
            </div>
          );
        })}
        <div style={{marginTop:8,padding:"10px 13px",borderRadius:10,background:"rgba(127,119,221,0.06)",border:`1px solid rgba(127,119,221,0.15)`,fontSize:12,color:C.t2,lineHeight:1.6}}>
          ⓘ AI-assisted summary. Always verify with your original report and consult your doctor.
        </div>
      </div>
      <div style={{position:"sticky",bottom:0,padding:"10px 16px 0",background:C.bg}}>
        <div style={{display:"flex",gap:8,marginBottom:4}}>
          <button onClick={()=>goTo("chat")} style={{flex:2,padding:"13px 0",borderRadius:13,background:`linear-gradient(135deg,${C.brandPurple},${C.brandPurpleMid})`,border:"none",color:"#fff",fontSize:13,fontWeight:500,cursor:"pointer"}}>Chat about this report →</button>
          <button style={{flex:1,padding:"13px 0",borderRadius:13,background:"none",border:`1px solid ${C.border}`,color:C.t2,fontSize:13,cursor:"pointer"}}>Delete</button>
        </div>
        <DisclaimerBar variant="short"/>
      </div>
    </div>
  );
}

// ── CAPTURE ──
function CaptureScreen({goTo,goBack}) {
  return (
    <div style={{background:"#111",minHeight:600,display:"flex",flexDirection:"column",position:"relative"}}>
      <div style={{position:"absolute",top:0,left:0,right:0,padding:"14px 16px",display:"flex",alignItems:"center",gap:10,zIndex:2}}>
        <button onClick={goBack} style={{background:"rgba(0,0,0,0.4)",border:"none",borderRadius:20,width:36,height:36,cursor:"pointer",color:"#fff",fontSize:16}}>×</button>
        <div style={{flex:1,textAlign:"center",fontSize:15,fontWeight:500,color:"#fff"}}>Scan lab report</div>
      </div>
      <div style={{flex:1,display:"flex",alignItems:"center",justifyContent:"center",minHeight:480,position:"relative"}}>
        <div style={{width:280,height:380,position:"relative"}}>
          <div style={{position:"absolute",top:0,left:0,width:28,height:28,borderTop:"2px solid rgba(255,255,255,0.8)",borderLeft:"2px solid rgba(255,255,255,0.8)",borderRadius:"4px 0 0 0"}}/>
          <div style={{position:"absolute",top:0,right:0,width:28,height:28,borderTop:"2px solid rgba(255,255,255,0.8)",borderRight:"2px solid rgba(255,255,255,0.8)",borderRadius:"0 4px 0 0"}}/>
          <div style={{position:"absolute",bottom:0,left:0,width:28,height:28,borderBottom:"2px solid rgba(255,255,255,0.8)",borderLeft:"2px solid rgba(255,255,255,0.8)",borderRadius:"0 0 0 4px"}}/>
          <div style={{position:"absolute",bottom:0,right:0,width:28,height:28,borderBottom:"2px solid rgba(255,255,255,0.8)",borderRight:"2px solid rgba(255,255,255,0.8)",borderRadius:"0 0 4px 0"}}/>
          <div style={{position:"absolute",inset:0,display:"flex",alignItems:"center",justifyContent:"center",flexDirection:"column",gap:8}}>
            <div style={{fontSize:32,opacity:0.4}}>📄</div>
            <div style={{fontSize:13,color:"rgba(255,255,255,0.6)",textAlign:"center"}}>Position full report within frame</div>
          </div>
        </div>
      </div>
      <div style={{padding:"16px 24px 28px",background:"rgba(0,0,0,0.7)",display:"flex",alignItems:"center",justifyContent:"space-around"}}>
        <button onClick={()=>goTo("processing")} style={{width:52,height:52,borderRadius:26,background:"rgba(255,255,255,0.2)",border:"none",cursor:"pointer",fontSize:22,color:"#fff"}}>🖼</button>
        <button onClick={()=>goTo("processing")} style={{width:68,height:68,borderRadius:34,background:C.brandPurple,border:"3px solid rgba(255,255,255,0.4)",cursor:"pointer",fontSize:20,color:"#fff"}}>◉</button>
        <button onClick={()=>goTo("processing")} style={{width:52,height:52,borderRadius:26,background:"rgba(255,255,255,0.2)",border:"none",cursor:"pointer",fontSize:22,color:"#fff"}}>📎</button>
      </div>
    </div>
  );
}

// ── PROCESSING ──
function ProcessingScreen({goTo}) {
  const msgs=["Reading report header...","Identifying test values...","Mapping to LOINC codes...","Cross-referencing your history...","Generating insights..."];
  const [idx,setIdx]=useState(0);
  const [dots,setDots]=useState(0);
  useEffect(()=>{const t=setInterval(()=>setIdx(i=>(i+1)%msgs.length),2200);const d=setInterval(()=>setDots(i=>(i+1)%4),400);return()=>{clearInterval(t);clearInterval(d);};},[]);
  return (
    <div style={{background:C.bg,minHeight:600,display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"center",padding:32,textAlign:"center",gap:20}}>
      <div style={{width:80,height:80,borderRadius:24,background:C.brandPurpleLight,border:`1.5px solid rgba(127,119,221,0.3)`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:36}}>🌿</div>
      <div>
        <div style={{fontSize:20,fontWeight:500,color:C.t1,marginBottom:6}}>Nova is reading your report</div>
        <div style={{fontSize:14,color:C.brandPurple,minHeight:20}}>{msgs[idx]}</div>
      </div>
      <div style={{display:"flex",gap:6}}>{[0,1,2].map(i=><div key={i} style={{width:7,height:7,borderRadius:4,background:i<=dots%3?C.brandPurple:"rgba(127,119,221,0.2)"}}/>)}</div>
      <div style={{fontSize:12,color:C.t3}}>This takes 10–20 seconds</div>
      <button onClick={()=>goTo("result")} style={{marginTop:16,padding:"13px 36px",borderRadius:12,background:`linear-gradient(135deg,${C.brandPurple},${C.brandPurpleMid})`,border:"none",color:"#fff",fontSize:14,fontWeight:500,cursor:"pointer"}}>View Results (demo)</button>
      <div style={{fontSize:12,color:C.t3,cursor:"pointer"}}>You can navigate away ↗</div>
    </div>
  );
}

// ── RESULT ──
function ResultScreen({goTo,goBack}) {
  const confidence = 0.95;
  const cs = confidenceStyle(confidence);
  return (
    <div style={{background:C.bg,minHeight:600,paddingBottom:80}}>
      <TopBar title="Extraction result" back onBack={goBack}/>
      <div style={{padding:"14px 16px 0"}}>

        {/* Low-confidence warning card — SaMD addendum §1.5 */}
        {confidence < 0.70 && (
          <div style={{background:C.attnBg,borderRadius:12,padding:"12px 14px",marginBottom:10,border:`1px solid ${C.attnBorder}`}}>
            <div style={{fontSize:13,fontWeight:500,color:C.attnText,marginBottom:4}}>⚠ Confidence below 70% — verify manually</div>
            <div style={{fontSize:12,color:C.attnText,lineHeight:1.6}}>Some values may be incorrectly extracted. Please verify all results against your original paper report before sharing with your doctor.</div>
          </div>
        )}

        {/* Success banner */}
        <div style={{background:"#E1F5EE",borderRadius:12,padding:"12px 14px",display:"flex",alignItems:"center",gap:10,marginBottom:10,border:`1px solid rgba(29,158,117,0.2)`}}>
          <span style={{fontSize:22,color:C.forest}}>✓</span>
          <div>
            <div style={{fontSize:14,fontWeight:500,color:C.forest}}>Report extracted successfully</div>
            <div style={{fontSize:12,color:C.forest,opacity:0.8}}>City Diagnostics · Mar 15, 2024</div>
          </div>
        </div>

        {/* Confidence badge — SaMD + FHIR */}
        <div style={{marginBottom:10}}>
          <ConfidenceBadge confidence={confidence}/>
        </div>

        {/* FHIR member match */}
        <div style={{background:C.brandPurpleLight,borderRadius:10,padding:"9px 12px",marginBottom:14,fontSize:12,color:C.brandPurpleMid,border:`1px solid rgba(127,119,221,0.2)`}}>
          FHIR Patient match: <strong>Rahul Sharma</strong> · Self ✓ <span style={{marginLeft:8,cursor:"pointer",textDecoration:"underline"}}>Edit</span>
        </div>

        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:8}}>
          <div style={{fontSize:14,fontWeight:500,color:C.t1}}>Lab results · FHIR Observation</div>
          <div style={{fontSize:12,color:C.t2}}>8 tests · <span style={{color:C.highText}}>3 outside range</span></div>
        </div>

        {OBSERVATIONS.map((o,i)=>{
          const s=interpStyle(o.interp);
          const isCrit=o.interp==="HH"||o.interp==="LL";
          return (
            <div key={i}>
              {isCrit && (
                <div style={{background:s.bg,borderRadius:8,padding:"8px 12px",marginBottom:4,fontSize:12,color:s.text,fontWeight:500}}>
                  ⚠ This value requires prompt medical attention. Please contact your doctor today.
                </div>
              )}
              <div style={{background:C.card,borderRadius:11,padding:"11px 13px",marginBottom:7,border:`1px solid ${C.border}`,borderLeft:`4px solid ${s.border}`}}>
                <div style={{display:"flex",justifyContent:"space-between",alignItems:"center"}}>
                  <div>
                    <div style={{fontSize:13,fontWeight:500,color:C.t1}}>{o.name}</div>
                    {/* FHIR: LOINC code visible */}
                    <div style={{fontSize:10,color:C.t3,fontFamily:"monospace"}}>LOINC {o.loinc}</div>
                  </div>
                  <div style={{textAlign:"right"}}>
                    <div style={{fontSize:14,fontWeight:500,color:s.text}}>{o.value} <span style={{fontSize:11}}>{o.unit}</span></div>
                    {/* SaMD: SaMDStringHelper.interpretationLabel() */}
                    <span style={{fontSize:10,padding:"2px 7px",borderRadius:8,background:s.bg,color:s.text,fontWeight:500}}>{SaMD.interpLabel(o.interp)}</span>
                  </div>
                </div>
                {/* SaMD: lab's own ref range, not hardcoded */}
                <div style={{fontSize:10,color:C.t3,marginTop:4}}>Lab ref range: {o.normalLow}–{o.normalHigh} {o.unit}</div>
              </div>
            </div>
          );
        })}

        <div style={{fontSize:14,fontWeight:500,color:C.t1,marginTop:16,marginBottom:8}}>AI insights</div>
        <InsightCardComp ins={INSIGHTS[0]}/>
        <InsightCardComp ins={INSIGHTS[1]}/>

        {/* SaMD: extraction disclaimer */}
        <div style={{marginTop:8,padding:"10px 13px",borderRadius:10,background:"rgba(127,119,221,0.06)",border:`1px solid rgba(127,119,221,0.15)`,fontSize:12,color:C.t2,lineHeight:1.6}}>
          ⓘ This is an AI-assisted summary of your lab report. Always verify with your original report and consult your doctor.
        </div>
      </div>
      <div style={{position:"sticky",bottom:0,padding:"10px 16px 0",background:C.bg}}>
        <button onClick={()=>goTo("chat")} style={{width:"100%",padding:"14px 0",borderRadius:13,background:`linear-gradient(135deg,${C.brandPurple},${C.brandPurpleMid})`,border:"none",color:"#fff",fontSize:14,fontWeight:500,cursor:"pointer"}}>
          Chat about this report →
        </button>
        <DisclaimerBar variant="short"/>
      </div>
    </div>
  );
}

// ── CHAT ──
function ChatScreen({goBack}) {
  const [msgs,setMsgs]=useState(CHAT_INIT);
  const [input,setInput]=useState("");
  const [typing,setTyping]=useState(false);
  const [activeMember,setActiveMember]=useState(MEMBERS[0]);
  const [showMemberPicker,setShowMemberPicker]=useState(false);
  const endRef=useRef();
  useEffect(()=>endRef.current?.scrollIntoView({behavior:"smooth"}),[msgs,typing]);

  function send(text) {
    const t=text||input.trim(); if(!t)return;
    setInput(""); setShowMemberPicker(false);
    setMsgs(m=>[...m,{role:"user",text:t,obs:[]}]);
    setTyping(true);
    setTimeout(()=>{
      setTyping(false);
      const BLOCKED = ["diagnose","you have diabetes","you have hypertension","i recommend taking","prescribe"];
      const isBlocked = BLOCKED.some(p=>t.toLowerCase().includes(p));
      const reply = isBlocked
        ? `I'm not able to answer that directly. Please consult your doctor for a diagnosis or treatment recommendation.\n\n${SaMD.disclaimer.short}`
        : (CHAT_REPLIES[t]||`I can help with that. Based on ${activeMember.name}'s records, could you be more specific?\n\n${SaMD.disclaimer.short}`);
      const obs=INSIGHTS.find(i=>t.toLowerCase().includes("glucose")||t.toLowerCase().includes("report"))?.obs||[];
      setMsgs(m=>[...m,{role:"assistant",text:reply,obs}]);
    },1800);
  }

  return (
    <div style={{background:C.bg,minHeight:600,display:"flex",flexDirection:"column"}}>
      {/* Header with member switcher */}
      <div style={{background:C.card,padding:"12px 16px",borderBottom:`0.5px solid ${C.border}`,position:"relative"}}>
        <div style={{display:"flex",alignItems:"center",gap:10}}>
          <button onClick={goBack} style={{background:"none",border:"none",cursor:"pointer",fontSize:18,color:C.t2}}>←</button>
          <div style={{width:34,height:34,borderRadius:17,background:activeMember.color,display:"flex",alignItems:"center",justifyContent:"center",color:"#fff",fontSize:12,fontWeight:500,flexShrink:0}}>{activeMember.initials}</div>
          <div style={{flex:1,cursor:"pointer"}} onClick={()=>setShowMemberPicker(s=>!s)}>
            <div style={{display:"flex",alignItems:"center",gap:4}}>
              <div style={{fontSize:14,fontWeight:500,color:C.t1}}>{activeMember.name}</div>
              <span style={{fontSize:11,color:C.t3}}>{showMemberPicker?"▲":"▼"}</span>
            </div>
            <div style={{fontSize:11,color:C.t3}}>{activeMember.rel} · Tap to switch</div>
          </div>
          <Chip label="EN" small/>
        </div>
        {/* Member picker dropdown */}
        {showMemberPicker&&(
          <div style={{position:"absolute",top:"100%",left:0,right:0,background:C.card,border:`0.5px solid ${C.border}`,zIndex:10,borderRadius:"0 0 12px 12px",padding:"4px 0"}}>
            {MEMBERS.map(m=>(
              <div key={m.id} onClick={()=>{setActiveMember(m);setShowMemberPicker(false);setMsgs(CHAT_INIT);}} style={{display:"flex",alignItems:"center",gap:10,padding:"10px 16px",cursor:"pointer",background:activeMember.id===m.id?C.brandPurpleLight:"none"}}>
                <div style={{width:30,height:30,borderRadius:15,background:m.color,display:"flex",alignItems:"center",justifyContent:"center",color:"#fff",fontSize:11,fontWeight:500}}>{m.initials}</div>
                <div><div style={{fontSize:13,fontWeight:500,color:C.t1}}>{m.name}</div><div style={{fontSize:11,color:C.t3}}>{m.rel}</div></div>
                {activeMember.id===m.id&&<span style={{marginLeft:"auto",color:C.brandPurple,fontSize:12}}>✓</span>}
              </div>
            ))}
          </div>
        )}
      </div>

      <div style={{flex:1,overflowY:"auto",padding:"12px 14px",display:"flex",flexDirection:"column",gap:10}}>
        {msgs.map((m,i)=>(
          <div key={i} style={{display:"flex",justifyContent:m.role==="user"?"flex-end":"flex-start",gap:8,alignItems:"flex-start"}}>
            {m.role==="assistant"&&<div style={{width:26,height:26,borderRadius:13,background:C.brandPurpleLight,display:"flex",alignItems:"center",justifyContent:"center",fontSize:12,flexShrink:0}}>🌿</div>}
            <div style={{maxWidth:"78%"}}>
              <div style={{
                padding:"10px 13px",
                borderRadius:m.role==="user"?"12px 12px 4px 12px":"12px 12px 12px 4px",
                background:m.role==="user"?C.brandPurple:C.card,
                border:m.role==="assistant"?`1px solid ${C.border}`:"none",
                color:m.role==="user"?"#fff":C.t1,fontSize:13,lineHeight:1.6,
                whiteSpace:"pre-wrap"
              }}>{m.text}</div>
              {/* FHIR: cited Observation chips with LOINC */}
              {m.obs?.length>0&&(
                <div style={{display:"flex",gap:6,flexWrap:"wrap",marginTop:5}}>
                  {m.obs.map((o,oi)=><span key={oi} style={{fontSize:10,background:"#E1F5EE",color:"#0F6E56",padding:"3px 8px",borderRadius:8,cursor:"pointer",fontFamily:"monospace"}}>{o.name} · {o.loinc}</span>)}
                </div>
              )}
            </div>
          </div>
        ))}
        {typing&&(
          <div style={{display:"flex",gap:8,alignItems:"flex-start"}}>
            <div style={{width:26,height:26,borderRadius:13,background:C.brandPurpleLight,display:"flex",alignItems:"center",justifyContent:"center",fontSize:12}}>🌿</div>
            <div style={{background:C.card,border:`1px solid ${C.border}`,borderRadius:"12px 12px 12px 4px",padding:"12px 16px",display:"flex",gap:4,alignItems:"center"}}>
              {[0,1,2].map(i=><div key={i} style={{width:6,height:6,borderRadius:3,background:C.t3,animation:`bounce${i} 1.2s ease-in-out infinite`,animationDelay:`${i*0.2}s`}}/>)}
            </div>
          </div>
        )}
        <div ref={endRef}/>
      </div>

      {msgs.length<=1&&!typing&&(
        <div style={{padding:"0 12px 8px",display:"flex",gap:6,flexWrap:"wrap"}}>
          {SUGGESTIONS.map(s=><button key={s} onClick={()=>send(s)} style={{padding:"6px 12px",borderRadius:20,background:C.card,border:`1px solid ${C.border}`,fontSize:12,color:C.brandPurple,cursor:"pointer",fontWeight:500,whiteSpace:"nowrap"}}>{s}</button>)}
        </div>
      )}

      <div style={{background:C.card}}>
        <div style={{padding:"8px 12px",display:"flex",gap:8,alignItems:"center",borderTop:`0.5px solid ${C.border}`}}>
          <div style={{fontSize:18,color:C.t3,cursor:"pointer"}}>🎙</div>
          <input value={input} onChange={e=>setInput(e.target.value)} onKeyDown={e=>{if(e.key==="Enter")send();}} placeholder="Ask about Rahul's health..." style={{flex:1,padding:"10px 12px",borderRadius:20,border:`1px solid ${C.border}`,background:C.bg,color:C.t1,fontSize:13,outline:"none"}}/>
          <button onClick={()=>send()} disabled={!input.trim()||typing} style={{width:36,height:36,borderRadius:18,background:input.trim()&&!typing?C.brandPurple:"rgba(127,119,221,0.25)",border:"none",cursor:input.trim()&&!typing?"pointer":"default",color:"#fff",fontSize:14}}>→</button>
        </div>
        {/* SaMD: structural disclaimer — non-dismissible, below input */}
        <DisclaimerBar variant="full"/>
      </div>
    </div>
  );
}

// ── INSIGHT CARD — with mark-as-read + low-confidence card ──
function InsightCardComp({ins, isRead, onMarkRead}) {
  const s=sevStyle(ins.severity);
  const [expanded,setExpanded]=useState(false);
  const read = isRead !== undefined ? isRead : ins.read;
  return (
    <div style={{background:read?C.card:"rgba(127,119,221,0.03)",borderRadius:12,padding:"13px 14px",marginBottom:10,border:`1px solid ${read?C.border:"rgba(127,119,221,0.2)"}`,borderLeft:`4px solid ${s.border}`}}>
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:6}}>
        <span style={{fontSize:10,padding:"2px 9px",borderRadius:10,background:s.bg,color:s.text,fontWeight:500}}>● {SaMD.sevLabel(ins.severity)}</span>
        <div style={{display:"flex",gap:6,alignItems:"center"}}>
          <Chip label={ins.member.split(" ")[0]} small bg="#F1EFE8" color={C.t2}/>
          <span style={{fontSize:10,color:C.t3}}>{ins.date}</span>
        </div>
      </div>
      <div style={{fontSize:14,fontWeight:500,color:C.t1,marginBottom:5}}>{ins.title}</div>
      <div style={{fontSize:12,color:C.t2,lineHeight:1.6,marginBottom:4}}>{expanded?ins.summary:ins.summary.slice(0,90)+"..."}</div>
      <span onClick={()=>setExpanded(e=>!e)} style={{fontSize:11,color:C.brandPurple,cursor:"pointer"}}>{expanded?"Show less":"Read more"}</span>
      {ins.obs?.length>0&&(
        <div style={{display:"flex",gap:5,flexWrap:"wrap",marginTop:8}}>
          {ins.obs.map((o,i)=><span key={i} style={{fontSize:10,background:"#E1F5EE",color:"#0F6E56",padding:"3px 8px",borderRadius:8,fontFamily:"monospace"}}>{o.name} · {o.loinc}</span>)}
        </div>
      )}
      <div style={{marginTop:8,fontSize:11,color:C.t2,fontStyle:"italic"}}>↳ {ins.action}</div>
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginTop:8}}>
        <div style={{fontSize:11,color:C.t3}}>{SaMD.disclaimer.inline}</div>
        {!read&&onMarkRead&&<button onClick={onMarkRead} style={{fontSize:11,color:C.brandPurple,background:"none",border:"none",cursor:"pointer",padding:0,fontWeight:500}}>Mark read ✓</button>}
      </div>
    </div>
  );
}

function FollowUpCard({f}) {
  const [status,setStatus]=useState(f.status);
  return (
    <div style={{background:C.card,borderRadius:12,padding:"13px 14px",marginBottom:10,border:`1px solid ${C.border}`,opacity:status==="dismissed"?0.5:1}}>
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"flex-start",marginBottom:5}}>
        <div style={{fontSize:14,fontWeight:500,color:C.t1}}>{f.test}</div>
        {/* FHIR: LOINC code badge */}
        <span style={{fontSize:9,background:"#F1EFE8",color:C.t3,padding:"2px 7px",borderRadius:6,fontFamily:"monospace"}}>LOINC {f.loinc}</span>
      </div>
      <div style={{fontSize:12,color:C.t2,fontStyle:"italic",marginBottom:6,lineHeight:1.5}}>{f.reason}</div>
      <div style={{fontSize:12,color:C.attnText,marginBottom:10,fontWeight:500}}>Suggested: {f.date}</div>
      {status==="pending"&&(
        <div style={{display:"flex",gap:8}}>
          <button onClick={()=>setStatus("accepted")} style={{flex:1,padding:"8px 0",borderRadius:10,background:C.normalBg,border:`1px solid rgba(29,158,117,0.3)`,color:C.normalText,fontSize:12,fontWeight:500,cursor:"pointer"}}>Accept ✓</button>
          <button onClick={()=>setStatus("dismissed")} style={{flex:1,padding:"8px 0",borderRadius:10,background:"#F1EFE8",border:`1px solid ${C.border}`,color:C.t2,fontSize:12,cursor:"pointer"}}>Dismiss ✗</button>
        </div>
      )}
      {status==="accepted"&&<Chip label="Accepted ✓" bg={C.normalBg} color={C.normalText} small/>}
    </div>
  );
}

// ── INSIGHTS FEED — with proper secondary tab ──
function InsightsFeedScreen() {
  const [mainTab,setMainTab]=useState("insights");
  const [filter,setFilter]=useState("all");
  const [readIds,setReadIds]=useState(new Set(INSIGHTS.filter(i=>i.read).map(i=>i.id)));
  const filtered=(filter==="all"?INSIGHTS:INSIGHTS.filter(i=>i.severity===filter));
  const unread=INSIGHTS.filter(i=>!readIds.has(i.id)).length;
  function markRead(id){setReadIds(s=>new Set([...s,id]));}
  return (
    <div style={{background:C.bg,minHeight:600,paddingBottom:8}}>
      <div style={{background:C.card,padding:"14px 16px 0",borderBottom:`0.5px solid ${C.border}`}}>
        <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:10}}>
          <div style={{fontSize:17,fontWeight:500,color:C.t1}}>Insights</div>
          {unread>0&&<span style={{background:"#E24B4A",color:"#fff",borderRadius:10,fontSize:11,padding:"2px 8px",fontWeight:500}}>{unread} unread</span>}
        </div>
        {/* Secondary tab row — Insights | Follow-ups */}
        <div style={{display:"flex",gap:0,marginBottom:0}}>
          {["insights","followups"].map(t=>(
            <button key={t} onClick={()=>setMainTab(t)} style={{flex:1,padding:"10px 0",background:"none",border:"none",borderBottom:mainTab===t?`2px solid ${C.brandPurple}`:"2px solid transparent",cursor:"pointer",fontSize:13,fontWeight:mainTab===t?500:400,color:mainTab===t?C.brandPurple:C.t3}}>
              {t==="insights"?"Insights":"Follow-ups"}
            </button>
          ))}
        </div>
      </div>

      {mainTab==="insights"&&(
        <div style={{padding:"12px 16px"}}>
          <div style={{display:"flex",gap:6,marginBottom:12,flexWrap:"wrap"}}>
            {["all","urgent","attention","informational"].map(f=>(
              <button key={f} onClick={()=>setFilter(f)} style={{padding:"5px 12px",borderRadius:20,background:filter===f?C.brandPurple:"none",border:`1px solid ${filter===f?C.brandPurple:C.border}`,color:filter===f?"#fff":C.t2,fontSize:11,cursor:"pointer",fontWeight:filter===f?500:400,textTransform:"capitalize"}}>
                {f==="informational"?"Info":f.charAt(0).toUpperCase()+f.slice(1)}
              </button>
            ))}
          </div>
          {filtered.map((ins,i)=><InsightCardComp key={i} ins={ins} isRead={readIds.has(ins.id)} onMarkRead={()=>markRead(ins.id)}/>)}
        </div>
      )}

      {mainTab==="followups"&&(
        <div style={{padding:"12px 16px"}}>
          {FOLLOWUPS.map((f,i)=><FollowUpCard key={i} f={f}/>)}
        </div>
      )}
    </div>
  );
}

// ── SETTINGS ──
function SettingsScreen({goBack}) {
  const [lang,setLang]=useState("en");
  const [voice,setVoice]=useState(true);
  const langs=[{code:"en",label:"English"},{code:"hi",label:"हिंदी"},{code:"mr",label:"मराठी"},{code:"ta",label:"தமிழ்"},{code:"bn",label:"বাংলা"},{code:"te",label:"తెలుగు"},{code:"kn",label:"ಕನ್ನಡ"},{code:"gu",label:"ગુજરાતી"}];
  return (
    <div style={{background:C.bg,minHeight:600,paddingBottom:16}}>
      <TopBar title="Settings" back onBack={goBack}/>
      <div style={{padding:16,display:"flex",flexDirection:"column",gap:14}}>
        <div style={{background:C.card,borderRadius:14,overflow:"hidden",border:`1px solid ${C.border}`}}>
          <div style={{padding:"12px 16px",borderBottom:`0.5px solid ${C.border}`}}>
            <div style={{fontSize:12,fontWeight:500,color:C.t3,textTransform:"uppercase",letterSpacing:0.8,marginBottom:10}}>Language</div>
            <div style={{display:"flex",flexWrap:"wrap",gap:8}}>
              {langs.map(l=>(
                <button key={l.code} onClick={()=>setLang(l.code)} style={{padding:"7px 14px",borderRadius:20,background:lang===l.code?C.brandPurple:C.bg,border:`1px solid ${lang===l.code?C.brandPurple:C.border}`,color:lang===l.code?"#fff":C.t1,fontSize:13,cursor:"pointer",fontWeight:lang===l.code?500:400}}>{l.label}</button>
              ))}
            </div>
          </div>
          <div style={{padding:"13px 16px",display:"flex",alignItems:"center",justifyContent:"space-between"}}>
            <div><div style={{fontSize:14,color:C.t1}}>Voice in chat</div><div style={{fontSize:11,color:C.t3}}>English & Hindi only</div></div>
            <div onClick={()=>setVoice(v=>!v)} style={{width:44,height:26,borderRadius:13,background:voice?C.brandPurple:"rgba(0,0,0,0.15)",cursor:"pointer",position:"relative",transition:"background .2s"}}>
              <div style={{position:"absolute",top:3,left:voice?20:3,width:20,height:20,borderRadius:10,background:"#fff",transition:"left .2s"}}/>
            </div>
          </div>
        </div>
        <div style={{background:C.card,borderRadius:14,border:`1px solid ${C.border}`}}>
          <div style={{padding:"12px 16px",borderBottom:`0.5px solid ${C.border}`}}>
            <div style={{fontSize:12,fontWeight:500,color:C.t3,textTransform:"uppercase",letterSpacing:0.8,marginBottom:10}}>Family members</div>
            {MEMBERS.map(m=>(
              <div key={m.id} style={{display:"flex",alignItems:"center",gap:10,padding:"8px 0",borderBottom:`0.5px solid ${C.border}`}}>
                <div style={{width:36,height:36,borderRadius:18,background:m.color,display:"flex",alignItems:"center",justifyContent:"center",color:"#fff",fontSize:12,fontWeight:500}}>{m.initials}</div>
                <div style={{flex:1}}><div style={{fontSize:13,fontWeight:500,color:C.t1}}>{m.name}</div><div style={{fontSize:11,color:C.t3}}>{m.rel} · FHIR Patient/{m.id}</div></div>
                <span style={{fontSize:12,color:C.t3,cursor:"pointer"}}>Edit</span>
              </div>
            ))}
            <button style={{width:"100%",padding:"10px 0",marginTop:10,borderRadius:10,background:"none",border:`1px dashed ${C.border}`,color:C.brandPurple,fontSize:13,cursor:"pointer"}}>+ Add member</button>
          </div>
        </div>
        {/* FHIR + SaMD standards in About */}
        <div style={{background:C.card,borderRadius:14,padding:"13px 16px",border:`1px solid ${C.border}`}}>
          <div style={{fontSize:12,fontWeight:500,color:C.t3,textTransform:"uppercase",letterSpacing:0.8,marginBottom:8}}>Data standards</div>
          <div style={{fontSize:12,color:C.t2,lineHeight:1.9}}>
            <div><span style={{fontWeight:500,color:C.t1}}>FHIR R4</span> — DiagnosticReport, Observation, Patient</div>
            <div><span style={{fontWeight:500,color:C.t1}}>LOINC</span> — laboratory test coding (loinc.org)</div>
            <div><span style={{fontWeight:500,color:C.t1}}>HL7 v3</span> — interpretation codes N/H/L/HH/LL</div>
            <div><span style={{fontWeight:500,color:C.t1}}>Class I SaMD</span> — informational, non-diagnostic</div>
          </div>
          <div style={{marginTop:10,fontSize:12,fontWeight:500,color:C.t3,textTransform:"uppercase",letterSpacing:0.8,marginBottom:6}}>About</div>
          <div style={{fontSize:12,color:C.t2,lineHeight:1.7}}>Chetana v1.0.0 · Hackathon build<br/>Powered by Amazon Bedrock · Nova AI<br/><br/>{SaMD.disclaimer.full}</div>
        </div>
      </div>
    </div>
  );
}

// ── ROOT ──
export default function App() {
  const [screen,setScreen]=useState("splash");
  const [email,setEmail]=useState("");
  const [selectedMember,setSelectedMember]=useState(MEMBERS[0]);
  const [mainScreen,setMainScreen]=useState("family");
  const [history,setHistory]=useState([]);

  function goTo(s,data){setHistory(h=>[...h,screen]);if(s==="memberDetail"&&data)setSelectedMember(data);setScreen(s);}
  function goBack(){const h=[...history];const prev=h.pop()||"family";setHistory(h);setScreen(prev);}
  function navTo(tab){setMainScreen(tab);setScreen("main");setHistory([]);}

  const mainScreens={
    family:<FamilyDashboard goTo={goTo}/>,
    upload:<CaptureScreen goTo={goTo} goBack={()=>navTo("family")}/>,
    chat:<ChatScreen goBack={()=>navTo("family")}/>,
    insights:<InsightsFeedScreen/>,
  };

  return (
    <div style={{padding:"16px 0",background:"var(--color-background-tertiary)",minHeight:800}}>
      <style>{`@keyframes bounce0{0%,100%{transform:translateY(0)}50%{transform:translateY(-4px)}}@keyframes bounce1{0%,100%{transform:translateY(0)}50%{transform:translateY(-4px)}}@keyframes bounce2{0%,100%{transform:translateY(0)}50%{transform:translateY(-4px)}}`}</style>
      <Phone screen={mainScreen} nav={screen==="main"?navTo:null}>
        {screen==="splash"   && <SplashScreen onNext={()=>setScreen("login")}/>}
        {screen==="login"    && <LoginScreen onNext={e=>{setEmail(e);setScreen("otp");}}/>}
        {screen==="otp"      && <OtpScreen email={email} onNext={()=>{setScreen("main");setMainScreen("family");}}/>}
        {screen==="main"     && mainScreens[mainScreen]}
        {screen==="memberDetail" && <MemberDetailScreen member={selectedMember} goTo={goTo} goBack={goBack}/>}
        {screen==="capture"  && <CaptureScreen goTo={goTo} goBack={goBack}/>}
        {screen==="processing"&& <ProcessingScreen goTo={goTo}/>}
        {screen==="result"       && <ResultScreen goTo={goTo} goBack={goBack}/>}
        {screen==="reportDetail" && <ReportDetailScreen goTo={goTo} goBack={goBack}/>}
        {screen==="settings"     && <SettingsScreen goBack={goBack}/>}
      </Phone>
      <div style={{textAlign:"center",marginTop:12,fontSize:12,color:"var(--color-text-tertiary)"}}>Click splash to begin · All screens navigable · Chat is live</div>
    </div>
  );
}
