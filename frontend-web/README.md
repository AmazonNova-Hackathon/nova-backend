# Chetana Patient Portal — Web Frontend

The primary web interface for the MediAgent clinical sanctuary. Built with **React 19**, **Vite**, and **TypeScript**, focused on glassmorphism aesthetics and medical data visualization.

---

## 🏗 Component Architecture

- **`App.tsx`**: Central cockpit managing the session, member selection, and view routing (Dashboard vs. Report Detail).
- **`ChetanaAssistant`**: Grounded AI sidebar utilizing Bedrock Agents for multi-turn clinical chat.
- **`LabResultsTable`**: Dynamic grouping of observations with abnormal-first sorting and clinical trend integration.
- **`HealthTimeline`**: High-performance SVG visualization for tracking biological markers over time.

---

## ⚡ Development

```bash
# Install dependencies
npm install

# Start local sanctuary
npm run dev
```

The app expects a `.env` file with:
- `VITE_API_URL`: Path to your SAM backend (usually `/api` under CloudFront).
- `VITE_API_KEY`: API Gateway key for secure transit.
- `AWS_S3_BUCKET`: Production frontend bucket.
- `AWS_CLOUDFRONT_ID`: Production distribution ID.

---

## 🚀 Deployment (Unified)

I have automated the entire deployment pipeline into a single PowerShell script. This handles TypeScript validation, Vite production building, atomic S3 synchronization, and CloudFront edge invalidation.

```powershell
.\deploy.ps1
```

> [!IMPORTANT]
> Ensure you have active AWS credentials and that the `AWS_S3_BUCKET` and `AWS_CLOUDFRONT_ID` are set in your `.env` before running the deploy script.

---

## 🌍 Multi-Lingual Support

The frontend supports user-selectable clinical interpretations in:
- **English** (Default)
- **Hindi** (हिंदी)
- **Marathi** (मराठी)
- **Tamil** (தமிழ்)

Select your preference via the **Nova Assistant** header in the chat sidebar.
