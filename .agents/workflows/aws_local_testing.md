---
description: Test Lambda functions locally using SAM Local Invoke
---
# AWS SAM Local Testing Workflow

Use this workflow to test individual Lambdas dynamically without deploying to AWS.

1. Ensure Docker is running.
2. Build the latest function code.
// turbo
```bash
sam build
```
3. Create a mock event JSON file (e.g., `events/upload_event.json`).
4. Invoke the function locally. Substitute the function name appropriately.
```bash
sam local invoke "ExtractReportFunction" -e events/upload_event.json
```
5. Check the local terminal for output and logs.
