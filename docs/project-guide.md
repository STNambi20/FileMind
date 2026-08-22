# FileMind Project Guide

## 1. Product Purpose

FileMind is a document management platform designed to show how storage optimization and document intelligence can work together in one system.

Its main value is:

- reduce physical storage through chunk-level deduplication
- preserve exact document reconstruction through manifest-based retrieval
- support real document operations such as versioning, sharing, and AI-assisted editing

## 2. Functional Modules

### Authentication and User Access

FileMind supports:

- user registration
- login
- logout
- current user retrieval
- JWT-protected API access

How it works:

- a user registers with name, email, and password
- the backend stores the user with hashed credentials
- login returns an access token
- protected routes use that token for authorization
- the frontend restores the authenticated session on refresh

### Document Management

FileMind supports:

- upload document
- list documents
- search and sort
- rename document
- delete document
- download current document
- open full document details

Supported formats:

- TXT
- PDF
- DOCX

How it works:

- the frontend submits the document as multipart form data
- the backend validates the request and extracts the content
- the document record is created or updated
- the version pipeline processes the uploaded content

### Deduplication and Storage Optimization

This is the core backend feature of FileMind.

How it works:

1. File bytes are received by the backend.
2. The storage engine splits the content using the configured chunking strategy.
3. Each chunk receives a SHA-256 hash.
4. Existing matching hashes are reused.
5. Only new unique chunks are stored physically.
6. New unique chunks are compressed before storage when beneficial.
7. A manifest is created for the version with the ordered chunk references.
8. Metrics are calculated from the stored result.

This gives FileMind two views of storage:

- `Logical Size`: original document bytes visible to the user
- `Actual Storage`: compressed bytes of unique stored chunks only

The platform also tracks:

- `Storage Saved`
- `Dedup Ratio`
- `Total Chunks`
- `Unique Chunks`
- `Reused Chunks`

### Versioning

FileMind treats each meaningful content change as a version.

How it works:

- the first upload creates version 1
- uploading changed content creates the next version
- identical content does not waste storage
- each version stores its own manifest and metadata
- the current document view resolves to the latest active version
- an older version can be restored as the current version
- any version can be downloaded independently

### Collaboration and Sharing

FileMind supports:

- owner access
- editor access
- viewer access
- collaborator management
- public read-only share links

How it works:

- the owner controls collaborator assignments
- editors can work on document content where permitted
- viewers can only read
- public share links expose a read-only public view of the latest document state

### AI Document Assistance

The backend orchestrates AI features through the FastAPI service.

AI features include:

- summary
- tags
- title suggestion
- key insights
- bullet highlights
- readability note
- rewrite modes

Rewrite modes:

- Professional
- Formal
- Simplify
- Shorten
- Key Points
- Readability

How it works:

- the backend extracts text from the latest document version
- the backend sends the extracted text to the AI service
- the response is normalized and persisted to the current version where appropriate
- a rewrite preview can be saved as a new document version

### Dashboard and Activity

The dashboard shows:

- total documents
- total versions
- logical size
- actual storage
- storage saved
- dedup ratio
- recent uploads
- recent activity

How it works:

- metrics are derived from live backend state
- activity is written for meaningful actions such as upload, rename, delete, restore, share, and AI actions

## 3. End-to-End Workflow

### Upload Workflow

1. User uploads a file.
2. Backend validates the request.
3. Text extraction runs for supported formats.
4. Storage engine chunks, hashes, reuses, compresses, and stores content.
5. Manifest is written.
6. Version metadata is saved.
7. AI metadata enrichment runs on extracted text when available.
8. Activity entry is created.
9. Dashboard and document screens refresh with updated metrics.

### Download Workflow

1. User requests current or historical version download.
2. Backend loads the target version manifest.
3. Backend resolves each chunk from the chunk store.
4. Compressed chunks are decompressed if needed.
5. Chunks are assembled in manifest order.
6. Integrity is verified.
7. The exact reconstructed bytes are returned as the download.

### AI Rewrite as New Version Workflow

1. User chooses a rewrite mode.
2. Backend extracts text from the latest version.
3. AI service returns rewritten content.
4. Frontend shows the preview.
5. If the user saves it, the rewrite is sent through the same version pipeline.
6. The new version gets chunked, deduplicated, manifested, and measured like any other version.

## 4. Local Storage Layout

Backend local storage uses:

- `backend/storage/chunks/`
- `backend/storage/manifests/`
- `backend/storage/uploads/`

Metadata is stored locally in:

- `backend/data/filemind-local-v2`

## 5. Manual Run Guide Without Codex

Open three normal terminal windows.

### Terminal 1: AI Service

```powershell
cd "C:\Users\GAUTAM KEWAT\OneDrive\Documents\New project\FileMind\ai-service"
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

### Terminal 2: Backend

```powershell
cd "C:\Users\GAUTAM KEWAT\OneDrive\Documents\New project\FileMind\backend"
$env:JAVA_HOME="C:\Program Files\Java\jdk-25"
mvn "-Dmaven.repo.local=C:\Users\GAUTAM KEWAT\OneDrive\Documents\New project\FileMind\backend\.m2" "-Dspring-boot.run.arguments=--server.port=8081" spring-boot:run
```

### Terminal 3: Frontend

```powershell
cd "C:\Users\GAUTAM KEWAT\OneDrive\Documents\New project\FileMind\frontend"
& "C:\Program Files\nodejs\npm.cmd" install
& "C:\Program Files\nodejs\npm.cmd" run dev
```

Open:

- frontend: [http://127.0.0.1:5173](http://127.0.0.1:5173)
- backend health: [http://127.0.0.1:8081/api/health](http://127.0.0.1:8081/api/health)
- AI health: [http://127.0.0.1:8000/health](http://127.0.0.1:8000/health)

## 6. How to Stop the Project

- press `Ctrl + C` in each terminal
- close the terminal windows if needed

If a process does not stop:

```powershell
cmd /c "netstat -ano | findstr :5173"
cmd /c "netstat -ano | findstr :8081"
cmd /c "netstat -ano | findstr :8000"
taskkill /PID <pid> /F
```

## 7. Suggested Demo Flow for Evaluation

1. Register a new user and sign in.
2. Upload one document.
3. Open dashboard and file details.
4. Show logical size, actual storage, storage saved, and dedup ratio.
5. Upload a slightly modified new version of the same document.
6. Show version count increase and chunk reuse.
7. Download the current file and a previous version.
8. Run summary, tags, and rewrite.
9. Save a rewrite as a new version.
10. Open version history and recent activity.

## 8. Testing Commands

### Backend

```powershell
cd "C:\Users\GAUTAM KEWAT\OneDrive\Documents\New project\FileMind\backend"
$env:JAVA_HOME="C:\Program Files\Java\jdk-25"
mvn "-Dmaven.repo.local=C:\Users\GAUTAM KEWAT\OneDrive\Documents\New project\FileMind\backend\.m2" test
```

### Frontend

```powershell
cd "C:\Users\GAUTAM KEWAT\OneDrive\Documents\New project\FileMind\frontend"
& "C:\Program Files\nodejs\npm.cmd" test
& "C:\Program Files\nodejs\npm.cmd" run build
```

### AI Service

```powershell
cd "C:\Users\GAUTAM KEWAT\OneDrive\Documents\New project\FileMind\ai-service"
.\.venv\Scripts\python.exe -m unittest discover -s tests
```

## 9. What Makes the Project Technically Strong

- real deduplication instead of simulated storage reduction
- exact reconstruction from manifests and chunk storage
- immutable versioning
- role-aware collaboration
- backend-driven AI orchestration
- consistent storage metrics across dashboard, document list, and document details
- local-first architecture that can be extended later
