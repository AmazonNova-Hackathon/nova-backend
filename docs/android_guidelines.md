# Android Integration Guide: MediAgent APIs

This document outlines how the Android client should interact with the MediAgent backend APIs to provide a rich, interactive, data-driven user experience using Amazon Bedrock and DynamoDB FHIR schemas.

---

## 🚀 1. Image Upload & Processing State

Do **not** upload Base64 encoded images directly. Instead, stream binary data to S3 using Pre-Signed URLs so that you can attach progress listeners to your HTTP client.

### Flow:
1. **Request URL**: `GET /reports/upload-url?patientId={id}`
2. **Stream Binary**: `PUT` the raw Kotlin `ByteArray` directly to the `url` string provided in Step 1.
   - *UI Suggestion*: Attach a standard `ProgressBar` listener to track the upload.
3. **Trigger Processing**: `POST /reports/upload` with the `s3Key` from Step 1.
   - **Crucial**: This is a synchronous connection! The request will hold open for **~5 to 15 seconds** while Amazon Nova Multimodal processes the image text.
   - *UI Suggestion*: Replace the upload progress bar with an indeterminate spinner reading: `"Analyzing Report with Amazon Nova..."`.
   - Once you receive the `200 OK`, the report is strictly persisted and immediately available.

---

## 📈 2. Generating Graphs & Tables (No AI Chat Needed)

Do not rely on the AI agent to output raw data for plotting. Instead, utilize the structured FHIR APIs to natively render beautiful graphs in Android using libraries like MPAndroidChart.

### API: `GET /observations?patientId={id}`
This returns a timeline array of every individual lab parameter ever extracted across all reports for the given patient.

### Key Data Points for Rendering:
- **`date`**: Use for your X-Axis (Timeline).
- **`value`**: Use for your Y-Axis plotting point.
- **Reference Bands**: Draw green shaded background bands on your graph using the `normalLow` and `normalHigh` numeric boundaries.

---

## 🚨 3. Alerting & Abnormal Highlighting

The backend extraction service actively determines clinical abnormality at the time of ingestion based on the printed reference ranges. 

### Implementation:
Every FHIR object returned from `/observations` and the initial `/reports/upload` response contains an `isAbnormal` boolean.
- *UI Suggestion*: If `isAbnormal == true`, unconditionally tint that row of the Table View or data point on the graph **Red**.
- *UI Suggestion*: You can also leverage the `interpretation` string enum (`"H"`, `"HH"`, `"L"`, `"LL"`, `"N"`) to render specific iconography (like an Up or Down arrow) next to the value.

---

## 📎 4. Sourcing & Citations

Medical professionals require trust. Whenever data is visually rendered, the user must be able to view the raw truth.

- **From Graphs / Tables**: Every `Observation` dict contains a `reportId`. You can lookup the parent `DiagnosticReport` (via the `GET /reports` API) to grab the `s3Key`.
- **From Agent Chat**: The response from `POST /chat` includes two arrays: `referencedReports` and `referencedObservations`. 
   - *UI Suggestion*: Render these as clickable **Citation Chips** below the Chat Bubble. If tapped, resolve the `reportId`, download the raw image byte sequence from S3 using the `/upload-url` logic in reverse, and display the original source document inside a modal Viewer!

---

## 👨‍👩‍👧‍👦 5. Family Hub & Smart Onboarding

The backend is designed for a low-friction "Family First" onboarding flow. You can either register a family explicitly or let the system bootstrap it from the first report.

### Option A: Manual Setup (Registration Screen)
1. **Create Family**: `POST /families` (Optionally payload: `{"name": "The Smiths"}`). Store the `familyId`.
2. **Add Member**: `POST /families/{familyId}/members` (Payload: `{"name": "Alice"}`). Store the `memberId`.

### Option B: Smart Onboarding (Frictionless)
1. **Upload Report**: Use `memberId = "detect"` in your `POST /reports/upload` call.
2. **Behavior**: 
   - If the report belongs to a new person, the backend **automatically creates** a new member using the patient name extracted from the lab report.
   - The response will include the new `memberId`.
   - *UI Suggestion*: This is perfect for the "First Lab Report Scan" where the user hasn't set up a profile yet.

### Dashboard: `GET /families/{familyId}/members`
Use this to render the "Switch Profile" or "Family Dashboard" screen, listing all members and their basic metadata.
