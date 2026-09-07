# Screen Free MTG Search

A better interface for searching Magic: The Gathering singles from [Screen Free Games](https://screenfreegames.com/collections/magic-singles), a local hobby store.

This project is not affiliated with Screen Free Games. It interfaces with their public Shopify API and does not handle purchases or payments. After selecting cards, the interface redirects to the store's Shopify cart with your selection.

https://chua-mbt.github.io/screenfree-magic-singles/

## Build

### Frontend

Requires sbt and Java 21.

```sh
sbt deckbuilder/fullLinkJS
```

The compiled output is at `deckbuilder/target/scala-3.3.3/deckbuilder-opt/`. To test locally:

```sh
npx serve deckbuilder/target/scala-3.3.3/deckbuilder-opt
```

### Search Proxy

Requires Node.js.

```sh
cd search-proxy
npm install
npm run typecheck
npm run dev
```

### Ingest

Requires sbt and Java 21.

```sh
BONSAI_URL=<url> sbt screen-free-ingest/run
```

## Architecture

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "fontSize": "14px"
  }
}}%%

graph TB
    subgraph Pages["GitHub Pages"]
        Frontend["Frontend (Scala.js)"]
    end

    subgraph CF["Cloudflare"]
        Worker["Search Proxy (TS)"]
    end

    subgraph ES["Bonsai (Managed ES)"]
        Bonsai["Elasticsearch Index"]
    end

    subgraph SH["Shopify"]
        ShopifyAPI["Products API"]
    end

    subgraph GA["GitHub Actions"]
        Scheduler["Scheduler"]
        IngestScript["Ingest Script (Scala)"]
        DeployFrontend["Deploy Frontend"]
        DeployWorker["Deploy Worker"]
    end

    User -->|&nbsp;paste deck list&nbsp;| Frontend
    Frontend -->|&nbsp;searches&nbsp;| Worker
    Worker -->|&nbsp;queries&nbsp;| Bonsai
    Frontend -->|&nbsp;opens cart&nbsp;| ShopifyAPI
    Scheduler -->|&nbsp;triggers&nbsp;| IngestScript
    IngestScript -->|&nbsp;fetches products&nbsp;| ShopifyAPI
    IngestScript -->|&nbsp;indexes products&nbsp;| Bonsai
    DeployFrontend -->|&nbsp;deploys&nbsp;| Frontend
    DeployWorker -->|&nbsp;deploys&nbsp;| Worker

    %% GitHub — charcoal
    classDef github fill:#F6F8FA,stroke:#24292F,color:#24292F,stroke-width:2px

    %% Cloudflare — orange
    classDef cloudflare fill:#FFF4E8,stroke:#F38020,color:#9A4A00,stroke-width:2px

    %% Bonsai — distinct forest/teal green
    classDef bonsai fill:#EAF3EE,stroke:#287A5A,color:#18563F,stroke-width:2px

    %% Shopify — brighter, more recognizable Shopify green
    classDef shopify fill:#EFF8F1,stroke:#008060,color:#006044,stroke-width:2px

    class Frontend github
    class Worker cloudflare
    class Bonsai bonsai
    class ShopifyAPI shopify
    class Scheduler,IngestScript,DeployFrontend,DeployWorker github

    %% Subgraph borders
    style Pages stroke:#24292F,stroke-width:2px
    style CF stroke:#F38020,stroke-width:2px
    style ES stroke:#287A5A,stroke-width:2px
    style SH stroke:#008060,stroke-width:2px
    style GA stroke:#24292F,stroke-width:2px

    %% Neutral connections
    linkStyle default stroke:#64748B,stroke-width:1.5px
```

## Search Request

```mermaid
sequenceDiagram
    actor User
    participant Frontend
    participant Worker
    participant Bonsai

    User->>Frontend: Paste deck list
    Frontend->>Proxy: POST / with ES query
    Worker->>Bonsai: Forward to index
    Bonsai-->>User: Results
```

## Product Ingestion

```mermaid
sequenceDiagram
    participant Scheduler
    participant Ingest
    participant Shopify
    participant Bonsai

    Scheduler->>Ingest: Trigger
    Ingest->>Shopify: Fetch all products
    Ingest->>Bonsai: Index into magic-singles
```
