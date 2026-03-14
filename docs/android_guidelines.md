# Android Integration Guide: MediAgent APIs

This document outlines how the Android client should interact with the MediAgent backend APIs to provide a rich, interactive, data-driven user experience using Amazon Bedrock and DynamoDB.

---

## 🚀 1. Image & Document Upload

Do **not** upload Base64 encoded data directly to the API. Instead, use the Pre-Signed URL pattern which allows direct binary streaming to S3.

### Supported Formats
- **Images**: `image/jpeg`, `image/png`, `image/webp` (standard for Android `Bitmap` or camera intent).
- **Documents**: `application/pdf` (standard for medical records shared via files).

### Flow:
1.  **Request URL**: `GET /families/{fid}/members/{mid}/reports/upload-url?contentType={mimeType}`
    -   Example mimeType: `image/png` or `application/pdf`.
2.  **Stream Binary**: Perform a `PUT` request with the raw data to the `url` received in Step 1.
    -   *Crucial*: Set the `Content-Type` header of your `PUT` request to match the `mimeType` sent in Step 1.
3.  **Trigger Processing**: `POST /families/{fid}/members/{mid}/reports/upload` with the `s3Key`.
    -   The system will automatically detect the format based on the file extension in the key.
    -   **Processing Time**: This takes **10-20 seconds**. Use an indeterminate progress spinner with text like: *"Decoding Lab Results with AI..."*

---

## 📉 2. Visualization & Health Trends

Utilize structured FHIR-like observations to render native Android charts (e.g., MPAndroidChart).

### API: `GET /families/{fid}/members/{mid}/observations`
This returns a timeline of all lab parameters.

### Graph Mapping:
- **X-Axis**: Use the ISO 8601 `date` string.
- **Y-Axis**: Use the `value` number.
- **Reference Ranges**: Draw translucent background bands using `normalLow` and `normalHigh`.
- **Alerting**: If `isAbnormal` is `true`, color the data point **Red**.

---

## 🤖 3. Conversational AI (Multi-turn Chat)

The Chat API supports multi-turn context using a `sessionId`.

### API: `POST /families/{fid}/members/{mid}/chat`
- **Request**: `{"message": "What do these results mean?", "sessionId": ""}`
- **Response**: `{"reply": "...", "sessionId": "abc-123"}`

### Implementation Guide:
1.  **Start State**: Initialize a `currentSessionId` variable as an empty string.
2.  **First Turn**: Send the message with an empty `sessionId`.
3.  **Persist Context**: On every response, update `currentSessionId` with the value returned from the backend.
4.  **Subsequent Turns**: Pass the stored `sessionId` in the payload. This allows the AI (Bedrock Agent) to remember the patient's history and previous questions.

---

## 🧹 4. Data Hygiene (Soft Delete)

The backend implements a soft-delete pattern to prevent accidental data loss.

### API: `DELETE /families/{fid}/members/{mid}/reports/{reportId}`
-   This marks the report and its child observations as `isDeleted: true` in the `meta` block.
-   The GET APIs automatically filter out deleted items.

---

## 👨‍👩‍👧‍👦 5. Hierarchical Navigation

Ensure your Android navigation (Fragments/Compose) follows the breadcrumb model of the API:
-   **Root**: List Families (`GET /families`)
-   **Level 1**: Select Member (`GET /families/{fid}/members`)
-   **Level 2**: Dashboard (Observations + Reports + Insights)

This structure ensures that the `familyId` and `memberId` are always available for child API calls.
