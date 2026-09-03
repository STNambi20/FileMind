# FileMind

FileMind is a local-first intelligent document-management platform. It stores documents as reusable content chunks, maintains version history through immutable manifests, reconstructs selected versions for viewing or download, and adds lightweight document intelligence through a separate AI service.

## Current capabilities

- JWT registration, login, logout, and protected routes
- TXT, PDF, DOCX, PNG, JPG/JPEG, GIF, and BMP uploads
- Asynchronous upload processing with \`UPLOADED\`, \`PROCESSING\`, \`COMPLETED\`, and \`FAILED\` states
- Content-defined chunking, SHA-256 chunk identity, global chunk reuse, gzip compression when beneficial, and manifest-based reconstruction
- Document versions, historical download, restore, in-app preview, dashboard metrics, audit activity, and recent access tracking
- Owner/editor/viewer collaboration, document comments, and expiring public share links
- Text extraction, optional local Tesseract OCR, summaries, tags, rewrites, document questions with evidence, metadata extraction, lexical search, and related-document suggestions

The AI service currently uses deterministic local heuristics. It does not require a cloud LLM, vector database, or external API key.

## Technology stack

| Area | Technology |
| --- | --- |
| Frontend | React 18, Vite 5, React Router, Axios, Tailwind CSS |
| Backend | Java 17, Spring Boot 3.3.9, Spring Security, Spring Data JPA, Maven |
| Metadata database | Amazon DynamoDB |
| Document processing | Apache PDFBox, Apache POI, optional Tesseract OCR |
| AI service | Python, FastAPI, Pydantic, Uvicorn, HTTPX |
| Storage | Amazon S3 chunk store and JSON manifests, SHA-256, gzip |

## Repository structure

\`\`\`text
filemind/
|-- frontend/        React user interface and Node tests
|-- backend/         Spring Boot API, DynamoDB metadata, S3 storage engine, and Java tests
|-- ai-service/      FastAPI heuristic AI service and Python tests
|-- docs/            Project guide and smoke-test sample documents
|-- docker-compose.yml
|-- README.md
\`\`\`

Runtime files are intentionally excluded from Git: \`backend/storage\`, build output, dependency caches, logs, and local environment files. Metadata and content are stored in the configured DynamoDB table and S3 bucket.

## Prerequisites

- Node.js 20+ and npm
- Java 17+ and Maven (the repository does not include a Maven wrapper)
- Python 3.10+
- Optional: Tesseract on \`PATH\` when OCR is enabled

Check the installed tools:

\`\`\`powershell
node -v
npm -v
java -version
mvn -v
python --version
\`\`\`

## Configuration

Example files are tracked for reference:

- \`frontend/.env.example\`
- \`backend/.env.example\`
- \`ai-service/.env.example\`

### Frontend

Vite loads \`frontend/.env\` automatically. The checked-in example targets backend port \`8081\`, which matches the frontend fallback URL. Copy it before starting the frontend:

\`\`\`powershell
Set-Location frontend
Copy-Item .env.example .env
\`\`\`

Set \`VITE_API_BASE_URL\` to the backend API URL you choose, for example \`http://localhost:8081/api\`.

### Backend

Spring Boot reads operating-system environment variables and command-line properties. It does **not** automatically load \`backend/.env\`; use \`backend/.env.example\` as a reference and set values in your shell or IDE.

The main variables are:

| Variable | Purpose |
| --- | --- |
| \`FILEMIND_JWT_SECRET\` | JWT signing key. Set a unique, long random value before sharing the application beyond local development. |
| \`AWS_REGION\` | AWS region used for both DynamoDB and S3. |
| \`AWS_S3_BUCKET_NAME\` | S3 bucket for chunks and version manifests. |
| \`AWS_DYNAMODB_TABLE_NAME\` | DynamoDB table for application metadata; its partition key must be \`fileId\` (String). |
| \`FILEMIND_FRONTEND_ORIGIN\` | Allowed frontend origin for CORS. |
| \`FILEMIND_PUBLIC_APP_URL\` | Base URL used for public share links. |
| \`FILEMIND_AI_BASE_URL\` | AI service URL; default is \`http://127.0.0.1:8000\`. |
| \`FILEMIND_OCR_ENABLED\` | Set to \`true\` only when Tesseract is installed and intended for use. |

For PowerShell, set a value only for the current terminal session:

\`\`\`powershell
$env:FILEMIND_JWT_SECRET = "replace-with-a-long-random-development-secret"
$env:FILEMIND_FRONTEND_ORIGIN = "http://localhost:5173"
\`\`\`

### AI service

The FastAPI service loads \`ai-service/.env\` when present. Its current provider is \`heuristic\`; the example file is sufficient unless you want to label the local environment differently.

## Run locally

Start the services in this order, each in a separate PowerShell terminal.

### 1. AI service

\`\`\`powershell
Set-Location ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
\`\`\`

Health check: \`http://127.0.0.1:8000/health\`

### 2. Backend on port 8081

This keeps the backend aligned with the checked-in frontend example and frontend fallback.

\`\`\`powershell
Set-Location backend
mvn "-Dspring-boot.run.arguments=--server.port=8081" spring-boot:run
\`\`\`

Health check: \`http://127.0.0.1:8081/api/health\`

The backend uses the standard AWS SDK credential provider chain, so your Windows \`aws configure\` credentials are picked up automatically when you run it from your normal user account.

### 3. Frontend

\`\`\`powershell
Set-Location frontend
npm install
npm run dev
\`\`\`

Open \`http://127.0.0.1:5173\`.

If you instead run the backend on its configured default port \`8080\`, update \`frontend/.env\` to \`VITE_API_BASE_URL=http://localhost:8080/api\` and restart Vite.

## Build and test

\`\`\`powershell
# Frontend
Set-Location frontend
npm test
npm run build

# Backend
Set-Location ../backend
mvn test

# AI service (after creating its virtual environment)
Set-Location ../ai-service
.\.venv\Scripts\python.exe -m unittest discover -s tests
\`\`\`

The backend test suite needs a DynamoDB-compatible test table or a mocked DynamoDB client. The end-to-end workflow test includes a stubbed backend AI provider so it verifies orchestration without depending on a running FastAPI service.

## Docker Compose note

\`docker-compose.yml\` defines optional backend and AI containers, but it does not include the frontend and does not set the backend AI base URL to the Docker service hostname. Use the local three-terminal workflow above unless you configure that connection yourself.

## Important API groups

- \`/api/auth\` - registration, login, current user, logout
- \`/api/documents\` and \`/api/versions\` - upload, status, details, preview, download, versioning, restore
- \`/api/documents/{id}/ai\` - summary, tags, rewrite, and ask-document operations
- \`/api/dashboard\` and \`/api/activity\` - live metrics and audit/access data
- \`/api/documents/{id}/collaborators\` and \`/api/documents/{id}/share-link\` - access control and public links
- \`/api/storage\` - storage diagnostics and cleanup

## Key technical decisions

- File content is stored outside the database as chunks and manifests; the database retains metadata and references.
- The default chunking strategy is deterministic content-defined chunking with approximately 2 KB minimum, 4 KB average, and 8 KB maximum chunk sizes.
- Binary reconstruction verifies chunk hashes and final content hash. DOCX storage is package-aware and validates canonical package content rather than promising identical ZIP bytes.
- OCR is optional, bounded, and fails safely if Tesseract is unavailable.
- Search and related-document results are lightweight lexical/token-similarity features, not embedding-based semantic search.

## Known limitations and continuation work

- DynamoDB metadata queries currently use table scans for small deployments. Add DynamoDB GSIs before using it at larger scale.
- Downloads reconstruct content into memory rather than streaming a response.
- The heuristic AI provider is intentionally lightweight; it is not an LLM and has no benchmarked quality evaluation in the repository.
- OCR requires a separately installed Tesseract executable and is disabled by default.
- The checked-in JWT fallback is development-only and must be overridden with \`FILEMIND_JWT_SECRET\` before deployment or shared use.
- There is no password-reset flow in the current auth module.

For feature-level behavior and flow diagrams, see [docs/project-guide.md](docs/project-guide.md).
