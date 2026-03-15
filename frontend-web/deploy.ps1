# MediAgent Frontend Unified Deployment Script
# Usage: .\deploy.ps1

Write-Host "🚀 Starting MediAgent Frontend Deployment..." -ForegroundColor Cyan

# 1. Load Environment Variables from .env
if (Test-Path ".env") {
    Write-Host "📦 Loading configuration from .env..." -ForegroundColor Gray
    Get-Content .env | Where-Object { $_ -match '=' -and $_ -notmatch '^#' } | ForEach-Object {
        $name, $value = $_.Split('=', 2)
        [System.Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim())
    }
} else {
    Write-Error "❌ .env file missing! Please create one based on the template."
    exit 1
}

$BUCKET = [System.Environment]::GetEnvironmentVariable("AWS_S3_BUCKET")
$DIST_ID = [System.Environment]::GetEnvironmentVariable("AWS_CLOUDFRONT_ID")

if (-not $BUCKET -or -not $DIST_ID) {
    Write-Error "❌ Missing deployment secrets in .env (AWS_S3_BUCKET or AWS_CLOUDFRONT_ID)."
    exit 1
}

# 2. Build the Project
Write-Host "🏗️  Building production assets..." -ForegroundColor Yellow
npm run build
if ($LASTEXITCODE -ne 0) {
    Write-Error "❌ Build failed. Aborting deployment."
    exit 1
}

# 3. Sync to S3
Write-Host "☁️  Syncing files to S3 bucket: $BUCKET..." -ForegroundColor Yellow
aws s3 sync dist "s3://$BUCKET" --delete
if ($LASTEXITCODE -ne 0) {
    Write-Error "❌ S3 Sync failed."
    exit 1
}

# 4. Invalidate CloudFront Cache
Write-Host "⚡ Invalidating CloudFront cache: $DIST_ID..." -ForegroundColor Yellow
$invalidation = aws cloudfront create-invalidation --distribution-id $DIST_ID --paths "/*" --query "Invalidation.Id" --output text
if ($LASTEXITCODE -ne 0) {
    Write-Warning "⚠️ CloudFront invalidation failed. Changes might take time to appear."
} else {
    Write-Host "✅ Deployment successful! Invalidation ID: $invalidation" -ForegroundColor Green
}

Write-Host "🌍 Dashboard is live at your CloudFront URL." -ForegroundColor Cyan
