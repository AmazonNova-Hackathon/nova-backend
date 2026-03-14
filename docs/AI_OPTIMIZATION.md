# AI Optimization & Workflow Guide

This document defines the optimized workflow for AI agents (like Roo Code) working on the MediAgent project. Following these guidelines will reduce token costs and improve code quality.

## 🚀 Efficiency First
- **Verify Existence**: Never assume a file exists. Use `list_files` or `search_files` first.
- **Limit Context**: When reading files, use `indentation` mode to get specific functions or use `limit` to avoid reading thousands of lines of boilerplate.
- **Batch Operations**: Group related `edit` or `write_to_file` operations to minimize the number of tool cycles.

## 🏗️ Backend Development (AWS SAM + Python)
- **File Structure**:
  - `app.py`: Lambda entry point. Use for event parsing and error handling.
  - `*_service.py`: Where the logic lives.
  - `repositories/`: Where the data lives.
- **Pydantic**: Always define request/response models in `backend/lambdas/shared/models/`.
- **Infrastructure**: Always check `backend/template.yaml` before adding new AWS services or changing environment variables.

## 📱 Frontend Development
- **Android**: Reference `docs/android_guidelines.md` for specific implementation details on S3 streaming and FHIR data visualization.
- **React**: Use the Vite proxy for API calls. Check `frontend-web/src/services/api.ts` for existing API integration patterns.

## 🧪 Quality Assurance
- Run `pytest` from the root or `backend/` directory after backend changes.
- Check `frontend-web` linting with `npm run lint`.

## 📜 Communication Pattern
- Always link to the specific file and line number when discussing code.
- Provide a summary of changes after every major edit.
- If a task is ambiguous, use `ask_followup_question` with concrete options.
