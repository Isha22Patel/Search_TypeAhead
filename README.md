# Search Typeahead System

A highly scalable, real-time search typeahead (autocomplete) system designed using **Spring Boot 3**, **React (Vite)**, and **PostgreSQL**.

It features an in-memory custom-built **Consistent Hashing Cache Cluster**, **Background Batch DB Writes** for high throughput, and a **Trending Engine** with sliding-window recency scoring.

## Features
- **In-Memory Cache Cluster:** Distributed caching simulated via Consistent Hashing (`CacheNode`).
- **Batch DB Writes:** High-frequency searches are buffered and flushed to PostgreSQL asynchronously.
- **Trending Engine:** Ranks searches using a blend of historical popularity and 1-hour sliding-window recency.
- **Dockerized Postgres:** Automatically initializes and pre-loads a massive real-world dataset on startup.

---

## 🚀 Setup Instructions

### 1. Prepare the Dataset
We load a real-world dataset into PostgreSQL via Docker initialization.
You can use the **Amazon Shopping Queries (ESCI)** dataset, or a Kaggle E-commerce search queries dataset.

**Format requirement:** The file must be a CSV with two columns (`query`, `count`), including a header row.

1. Download your CSV and name it `dataset.csv` in the root folder.
2. Run the Python conversion script to generate the compressed SQL payload:
   ```bash
   python scripts/csv_to_sql.py dataset.csv
   ```
   *(This creates `init/02_data.sql.gz`, securely staying under GitHub's 50MB file size limits).*

### 2. Start PostgreSQL (with Auto-Load)
Run Docker Compose to start PostgreSQL. Since we mounted the `init/` folder, Docker will automatically execute `01_schema.sql` and decompress/load `02_data.sql.gz`.
```bash
docker compose down -v  # Clear any old database volumes
docker compose up -d
```
*Note: Wait a few seconds for the logs to say `database system is ready to accept connections`.*

### 3. Run the Backend (Spring Boot)
```bash
cd backend
# On Linux/Mac:
./mvnw spring-boot:run
# On Windows:
mvnw.cmd spring-boot:run
```

### 4. Run the Frontend (React)
```bash
cd frontend
npm install
npm run dev
```

Your system is now completely live at **http://localhost:5173** and fully populated with real-world trending data!

---

## Architecture

* **Database Schema:** `search_queries` table with a specialized `text_pattern_ops` index for ultra-fast prefix matching.
* **Data Load:** Shifted strictly to Docker (`docker-entrypoint-initdb.d`), keeping the Spring application lightweight (`ddl-auto: validate`).
