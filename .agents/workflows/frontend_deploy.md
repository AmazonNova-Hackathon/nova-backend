---
description: Build and deploy the MediAgent frontend to AWS S3 and CloudFront
---
# MediAgent Frontend Deployment

This workflow builds the React production bundle and syncs it to the clinical sanctuary's public edge (S3 + CloudFront).

1. Navigate to the frontend directory.
```powershell
cd frontend-web
```

2. Ensure your `.env` file has the deployment secrets:
```text
AWS_S3_BUCKET=mediagent-web-mediagent-623810446100
AWS_CLOUDFRONT_ID=E2QAHK3XRBL302
```

3. Run the unified deployment script.
// turbo
```powershell
.\deploy.ps1
```

> [!TIP]
> This script automatically handles environment variable loading, production builds with Vite, atomic S3 synchronization, and cache invalidation.
