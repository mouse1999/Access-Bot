

* 🧱 Foundation
* 🗄 Domain & Persistence
* 🔄 Event-Driven Flow
* 🧠 Permutation Engine
* 🎯 Bet Processing
* 🤖 Scraping Layer
* 🔁 Scheduler & Recovery
* 🧪 Testing
* 🚀 Hardening & Production Readiness

This plan follows your **event-driven class diagram exactly**.

---

# 🧩 PERMUTATION BETTING SYSTEM – IMPLEMENTATION PLAN

---

# 🟦 LIST 1 — PROJECT SETUP & FOUNDATION

## 🎫 Card: Initialize Spring Boot Project

* Create Spring Boot project
* Dependencies:

    * Spring Web
    * Spring Data JPA
    * H2 Database
    * Lombok
    * Playwright
    * Spring Events
* Configure application.yml
* Enable H2 file-based persistence

---

## 🎫 Card: Configure Base Architecture

* Create packages:

  ```
  domain
  repository
  service
  scraper
  orchestrator
  event
  scheduler
  config
  ```
* Enable JPA auditing (createdAt fields)
* Configure transaction management

---

# 🟦 LIST 2 — DOMAIN MODEL IMPLEMENTATION

## 🎫 Card: Implement Enums

* BetStatus
* MarketType
* OutcomeType

---

## 🎫 Card: Implement Game Entity

* Fields:

    * id
    * homeTeam
    * awayTeam
    * startTime
* Method:

    * isStarted()

---

## 🎫 Card: Implement GameSelection + GameSelectionItem

* Bidirectional relationship
* Cascade persistence
* addGame(Game game)
* getSelectedGames()

---

## 🎫 Card: Implement PermutationSet

* Fields:

    * id
    * selection
    * status
    * betReference
    * createdAt
    * permGames
* Methods:

    * markProcessing()
    * markCompleted()
    * markFailed()

---

## 🎫 Card: Implement PermGame

* Fields:

    * permutationSet
    * game
    * market
    * outcome
    * positionIndex
* Validation method

---

# 🟦 LIST 3 — REPOSITORY LAYER

## 🎫 Card: Create Repositories

* GameRepository
* GameSelectionRepository
* PermutationSetRepository
* PermGameRepository

Add custom query:

```
findFirstByStatusOrderByCreatedAt(BetStatus.PENDING)
```

---

# 🟦 LIST 4 — EVENT SYSTEM

## 🎫 Card: Create GamesScrapedEvent

* Field: List<Game>
* Getter method

---

## 🎫 Card: Publish Event from GameService

* Inject ApplicationEventPublisher
* After saving scraped games:

  ```
  publisher.publishEvent(new GamesScrapedEvent(games));
  ```

---

## 🎫 Card: Listen in SelectionService

* Implement:

  ```
  @EventListener
  public void onGamesScraped(GamesScrapedEvent event)
  ```
* Possibly auto-create selection logic

---

# 🟦 LIST 5 — SCRAPING LAYER

## 🎫 Card: Implement GameScraperService

* Use Playwright
* Navigate betting site
* Extract:

    * homeTeam
    * awayTeam
    * startTime
* Convert to Game objects
* Call GameService.saveScrapedGames()

---

## 🎫 Card: Add Scraper Trigger

* REST endpoint OR CLI trigger
* Later convert to scheduler-based trigger

---

# 🟦 LIST 6 — PERMUTATION ENGINE

## 🎫 Card: Implement PermutationOrchestrator

### Responsibilities:

* generateBinaryMatrix(size)
* generatePermutations(selection, market)

Logic:

```
for i in 0 to 2^n - 1:
    binary = formatBinary(i, n)
```

Map:

* 0 → B_UNDER
* 1 → A_OVER

Expected:

* For 5 games → 32 permutations

---

## 🎫 Card: Implement ValidationService

* validateMarketForGames()
* validatePermutationCount()

Ensure:

```
permutations.size() == 2^n
```

---

# 🟦 LIST 7 — PERMUTATION CREATION FLOW

## 🎫 Card: Implement PermGameService

* createPermGames()
* For each permutation:

    * Create PermutationSet
    * Create 5 PermGame rows
* Persist transactionally

---

## 🎫 Card: Implement BetProcessingService.createPermutationSet()

Flow:

1. Fetch selection
2. Validate
3. Call Orchestrator
4. Call PermGameService
5. Persist PermutationSet

---

# 🟦 LIST 8 — EXECUTION ENGINE

## 🎫 Card: Implement Scheduler / ExecutionLoop

Use:

```
@Scheduled(fixedDelay = 5000)
```

Flow:

1. Fetch first PENDING PermutationSet
2. Call processPermutation(setId)

---

## 🎫 Card: Implement processPermutation()

Steps:

1. markProcessing()
2. Submit to Playwright
3. Update status:

    * COMPLETED
    * FAILED

---

# 🟦 LIST 9 — PLAYWRIGHT BETTING AUTOMATION

## 🎫 Card: Implement PlaywrightBettingService

Responsibilities:

* Login
* Navigate to game
* Select correct outcome
* Place bet
* Extract bet reference

Return:

```
String betReference
```

---

## 🎫 Card: Make Execution Idempotent

* If PROCESSING and app crashes → restart should retry
* Ensure transactional updates

---

# 🟦 LIST 10 — RESILIENCE & RECOVERY

## 🎫 Card: Crash Recovery Logic

On app startup:

* Find PROCESSING sets
* Reset to PENDING

---

## 🎫 Card: Logging & Monitoring

* Log every bet submission
* Log failed submissions
* Add structured logging

---

# 🟦 LIST 11 — TESTING

## 🎫 Card: Unit Tests

* PermutationOrchestrator
* ValidationService
* PermGame creation

---

## 🎫 Card: Integration Tests

* End-to-end:

    * Scrape → Select → Generate → Persist

---

## 🎫 Card: Simulation Mode

* Add "mock betting mode"
* Skip Playwright
* Randomly mark bets complete

---

# 🟦 LIST 12 — MATHEMATICAL VERIFICATION

## 🎫 Card: Coverage Guarantee Test

For 5 games:

* Assert 32 permutations exist
* Assert no duplicates
* Assert full coverage of binary space

---

# 🟦 LIST 13 — HARDENING & IMPROVEMENTS

## 🎫 Card: Stake Management

* Calculate required bankroll
* 32 bets × stake

---

## 🎫 Card: Concurrency Protection

* Prevent double execution
* Use DB locking or status atomic update

---

## 🎫 Card: Performance Optimization

* Batch inserts for PermGame
* Lazy loading optimization

---

# 🟦 FINAL IMPLEMENTATION FLOW

### Step 1

Scraper → Save Games → Publish Event

### Step 2

SelectionService → Create GameSelection

### Step 3

BetProcessingService → Generate 32 Permutations

### Step 4

Persist:

* 1 PermutationSet per permutation
* 5 PermGame per set

### Step 5

Scheduler:

* Fetch PENDING
* Execute via Playwright
* Update status

---

# 🧠 Result

You now have:

✔ Event-driven architecture
✔ Full permutation coverage (2^n)
✔ Crash recovery
✔ Idempotent execution
✔ Persistent state
✔ Automated betting execution

---


