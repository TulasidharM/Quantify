# Quantify Android App — Code Audit Report

**Date:** 2026-10-30
**Scope:** All 46 Kotlin source files across UI, ViewModel, Data, DI, and Infrastructure layers.

---

## 1. Critical Issues

### 1.1 Dual Database Instantiation → Hilt injection returns different instance than seed/migration path
**Files:** `QuantifyDatabase.kt`, `AppModule.kt`, `SeedData.kt`

`QuantifyDatabase` has a hand-rolled singleton (`getInstance()`) AND Hilt `AppModule.provideDatabase()` builds a second instance via `Room.databaseBuilder()`. Hilt-injected DAOs come from the Hilt instance (with `MIGRATION_1_2`), while `SeedData.kt` calls `QuantifyDatabase.getInstance()` — a separate database instance without the migration. On first install after v1→v2 upgrade, seeding may query the wrong instance or hit a migration gap.

**Impact:** Stale data reads during seeding; potential migration loss if Hilt instance is destroyed and recreated without the migration having run.

**Fix:** Remove the `getInstance()` singleton. Either inject the database into `SeedData` via Hilt, or have `SeedData` use the Hilt-injected DAOs directly.

---

### 1.2 Unbound Coroutine in `QuantifyApplication.onCreate()`
**File:** `QuantifyApplication.kt`

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val seedData = SeedData()
    seedData.seedIfEmpty(this@QuantifyApplication)
}
```

The `CoroutineScope(Dispatchers.IO)` is never stored or supervised. If it throws, the exception is silently swallowed. If the process dies mid-seed, the job is orphaned with no restart guarantee.

**Impact:** Silent seeding failures; no error visibility.

**Fix:** Use `SupervisorJob()` + `Application` lifecycle, or delegate to a `CoroutineWorker` / `ProcessLifecycleOwner`.

---

### 1.3 Hardcoded `categoryId = "default"` in RecurringRule creation
**File:** `RecurringScreen.kt`, line in `NewRecurringRuleDialog`

```kotlin
onSave(RecurringRuleEntity(
    ...
    categoryId = "default",
    ...
))
```

No category selection UI exists. All recurring rules are created with `categoryId = "default"` — a category that doesn't exist in seed data. The generated transaction will reference a non-existent category.

**Impact:** All recurring transactions are orphaned — they won't display in category breakdowns and can't be filtered.

**Fix:** Add a category selector (dropdown or bottom sheet) to the dialog. Default to the first expense category.

---

### 1.4 Budget primary key collision — `id` equals `categoryId`, no month/year uniqueness
**File:** `BudgetEntity.kt`

```kotlin
@PrimaryKey val id: String,
val categoryId: String,
```

`id` is set to `categoryId` in `BudgetScreen.kt`. This means only one budget per category exists globally — not per month. If the user tries to set different budgets for January vs. February for the same category, the second `insert()` with `OnConflictStrategy.REPLACE` silently overwrites the first.

**Impact:** Budgets cannot be set per-month. Year and month fields exist but are not part of the primary key.

**Fix:** Make `id` a composite of `categoryId + year + month` (e.g., `"${categoryId}_${year}_${month}"`) and either use a composite `@PrimaryKey` annotation or derive `id` from the triplet.

---

## 2. High Severity

### 2.1 `BudgetRepository.getMonthRange()` has month overflow bug
**File:** `BudgetRepository.kt`

```kotlin
val end = LocalDateTime(year, month + 1.coerceAtMost(12), 1, 0, 0)
```

`1.coerceAtMost(12)` is always `1` — this is a no-op. The expression evaluates `1.coerceAtMost(12)` which is `1`, not `month + 1`. So `month + 1` is passed to `LocalDateTime` directly. When `month == 12`, this creates `LocalDateTime(year, 13, 1, 0, 0)` which throws. The `if (month == 12)` branch after `.let` tries to fix it but the crash happens before.

**Impact:** App crash when viewing budgets for December.

**Fix:** `val nextMonth = if (month == 12) 1 else month + 1; val nextYear = if (month == 12) year + 1 else year; LocalDateTime(nextYear, nextMonth, 1, 0, 0)`

---

### 2.2 `RecurringRuleRepository.computeNextRunEpoch()` — WEEKLY frequency has month overflow bug
**File:** `RecurringRuleRepository.kt`

```kotlin
val dayAdded = ld.dayOfMonth + 7
val daysInCurrentMonth = daysInMonth(ld.year, ld.monthNumber)
val (wYear, wMonth, wDay) = if (dayAdded > daysInCurrentMonth) {
    val overflow = dayAdded - daysInCurrentMonth
    val nextM = if (ld.monthNumber == 12) 1 else ld.monthNumber + 1
    val nextY = if (ld.monthNumber == 12) ld.year + 1 else ld.year
    Triple(nextY, nextM, overflow)
} else {
    Triple(ld.year, ld.monthNumber, dayAdded)
}
LocalDateTime(wYear, wMonth, wDay, 0, 0).toInstant(tz).epochSeconds
```

This only handles a single month boundary crossing. If `dayAdded` exceeds the *next* month's days (e.g., Jan 30 → dayAdded=37, overflow=7, Feb 7 → works). But if `dayAdded` is large enough to cross multiple months (not possible for +7, so this is actually safe for WEEKLY). However, the overflow calculation is fragile — it assumes adding 7 days crosses at most one month boundary, which is mathematically true but undocumented.

**Impact:** Low for WEEKLY, but the pattern is brittle. DAILY frequency has no month boundary handling at all — `dayOfMonth + 1` can produce invalid dates (e.g., Jan 31 → `LocalDateTime(year, 1, 32, ...)` → throws).

**Fix (DAILY):** Use `kotlinx.datetime`'s `plus(1.days)` instead of manual arithmetic. Same for WEEKLY: `plus(7.days)`.

---

### 2.3 `RecurringRuleRepository.processDueRules()` — No transactional atomicity
**File:** `RecurringRuleRepository.kt`

```kotlin
for (rule in dueRules) {
    val transaction = TransactionEntity(...)
    transactionDao.insert(transaction)
    val updatedRule = rule.copy(nextRunDateEpochSeconds = nextRunEpoch)
    recurringRuleDao.update(updatedRule)
}
```

If `transactionDao.insert()` succeeds but `recurringRuleDao.update()` fails, the rule's `nextRunDate` is not updated. On the next run, the same rule fires again, creating a duplicate transaction. No `@Transaction` or `QuantifyDatabase.transaction {}` wrapper.

**Impact:** Duplicate transactions from recurring rules after partial failures.

**Fix:** Wrap the loop in `QuantifyDatabase.transaction {}` or use Room's `@Transaction` on a repository method.

---

### 2.4 `BackupViewModel.restoreBackup()` — Deletes all data before re-inserting
**File:** `BackupViewModel.kt`

```kotlin
transactionRepository.deleteAll()
backup.transactions.forEach { transactionRepository.insert(it) }
categoryRepository.deleteAll()
backup.categories.forEach { categoryRepository.insert(it) }
// ... same for all 6 entity types
```

If the process crashes mid-restore (e.g., OOM during a large backup), all data is lost with no rollback. No transactional boundary. Also, transactions reference categories and payment methods by ID — if transactions are inserted before categories, foreign key references are dangling during the intermediate state (though SQLite may not enforce FK by default).

**Impact:** Total data loss on interrupted restore. Dangling references during intermediate state.

**Fix:** Wrap entire restore in a single database transaction. Insert categories and payment methods first, then transactions.

---

### 2.5 Room `getMonthlyTotalsFlow` timezone mismatch with `getMonthRangeEpoch`
**File:** `TransactionDao.kt`

```sql
SELECT strftime('%m', dateEpochSeconds, 'unixepoch') as monthNum, ...
WHERE strftime('%Y', dateEpochSeconds, 'unixepoch') = :yearStr
```

SQLite's `strftime(..., 'unixepoch')` interprets epoch seconds as **UTC**. However, `getMonthRangeEpoch()` in `TransactionRepository` uses `TimeZone.currentSystemDefault()` to compute month boundaries. A transaction at `2026-01-15 23:30 IST` has epoch `1737276600` which is `2026-01-15 18:00 UTC` — same month, no issue here. But near month boundaries in non-UTC timezones, the SQLite query and Kotlin range can disagree on which month a transaction belongs to.

**Impact:** Monthly totals can disagree between `getYearlyTrendFlow` (SQLite UTC) and `getMonthlyTotalsFlow` (Kotlin local TZ range). Dashboard income/expense vs. stats chart may show different numbers for the same month.

**Fix:** Either store dates in a timezone-independent way (e.g., LocalDate + separate time) or use consistent UTC boundaries in both paths.

---

## 3. Medium Severity

### 3.1 `formatCurrency()` ignores currency symbol
**File:** `DashboardScreen.kt`

```kotlin
fun formatCurrency(amount: Double): String {
    return String.format("%.0f", amount)
}
```

Despite `BackupViewModel` storing a `currency` preference (default "USD"), `formatCurrency()` never uses it — no currency symbol, no locale-specific formatting. All screens show bare integers.

**Impact:** Users don't see ₹ / $ / € prefixes. The currency setting is effectively dead.

---

### 3.2 `exportBackup()` doesn't include all budgets or tags
**File:** `BackupViewModel.kt`

```kotlin
budgets = budgetRepository.getByMonth(
    Clock.System.now()..., year, month
).first()
```

Only the *current* month's budgets are backed up. Historical budgets are lost. Tags are included in `BackupFile` but the export doesn't filter or process them — this is fine.

**Impact:** Cross-month budget data loss on restore.

---

### 3.3 `createBackupStream()` throws `NotImplementedError`
**File:** `BackupManager.kt`

```kotlin
suspend fun createBackupStream(): OutputStream {
    return withContext(Dispatchers.IO) {
        throw NotImplementedError("Use createBackupIntent() instead")
    }
}
```

Dead code that throws. The method exists but is never called — however, it's a public API that could be accidentally invoked.

**Fix:** Remove the method or mark it `@Deprecated` with `Level.ERROR`.

---

### 3.4 `QuantifyApplication` calls `SeedData(seedIfEmpty)` directly instead of using Hilt
**File:** `QuantifyApplication.kt`

```kotlin
val seedData = SeedData()
seedData.seedIfEmpty(this@QuantifyApplication)
```

`SeedData` is `@Inject`-annotated but constructed manually here. This means it uses the hand-rolled `QuantifyDatabase.getInstance()` (see §1.1) instead of the Hilt-injected database.

**Fix:** Use Hilt's `EntryPointAccessors` or make `QuantifyApplication` a `@HiltAndroidApp` with `@Inject` constructor (which it already is, but doesn't use injection in `onCreate`).

---

### 3.5 No foreign key constraints in Room database
**File:** `QuantifyDatabase.kt`

```kotlin
@Database(..., autoMigrations = [])
```

No `exportSchema = true` for schema tracking. No foreign key declarations on entities. Transactions reference `categoryId` and `paymentMethodId` with no FK enforcement. Deleting a category reassigns transactions at the ViewModel layer, but there's no database-level safety net.

**Impact:** Orphaned transactions after category/payment method deletion if ViewModel logic is bypassed (e.g., direct DB access, restore inconsistency).

---

### 3.6 `TransactionEntity.generateId()` uses a static counter — collision risk after process death
**File:** `TransactionEntity.kt`

```kotlin
private var counter = 0
@Synchronized
fun generateId(): String {
    return "${Clock.System.now().epochSeconds}_${counter++}"
}
```

If two transactions are created within the same second, the counter differentiates them. But the counter resets to `0` on process restart. If two transactions are created in the same second after a process death, IDs collide → Room `OnConflictStrategy.ABORT` silently drops the second insert.

**Impact:** Lost transactions after process death when creating multiple transactions quickly.

**Fix:** Use `UUID.randomUUID().toString()` or append a process-start random seed: `private val processSeed = Random.nextInt()`.

---

### 3.7 `BudgetScreen` items use unstable keys
**File:** `BudgetScreen.kt`

```kotlin
items(budgets) { bs -> ... }
```

No `key` parameter on `items()`. If budgets reorder (e.g., after a delete), Compose may reuse the wrong composables, causing animation glitches or stale state.

---

### 3.8 `RecurringScreen` items use unstable keys
**File:** `RecurringScreen.kt`

```kotlin
items(rules) { rule -> ... }
```

Same issue — no `key` parameter. Should use `key = { rule.id }`.

---

## 4. Low Severity / Code Quality

### 4.1 String literals instead of `R.string` resources
Throughout the codebase (TransactionsScreen, RecurringScreen, BudgetScreen, EditTransactionScreen), string literals are used directly: `"No transactions found"`, `"Delete Transaction"`, `"Income"`, `"Expense"`, `"Recurring Transactions"`, etc. This prevents localization and makes refactoring harder.

### 4.2 Magic strings for transaction type and frequency
`"INCOME"`, `"EXPENSE"`, `"DAILY"`, `"WEEKLY"`, `"MONTHLY"`, `"YEARLY"` are used as raw strings instead of Kotlin `enum` classes or `@StringConverters`. This allows typos and makes refactoring fragile.

### 4.3 `TransactionRepository` and `TransactionViewModel` both expose `transactionsByMonth`
The repository and ViewModel both build reactive month-filtered flows using nearly identical `combine` + `flatMapLatest` patterns. This is DRY-violation territory.

### 4.4 `DashboardViewModel.companion object NOW` freezes at class load time
```kotlin
companion object {
    private val NOW = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
```

If the app runs across midnight, `NOW.year` and `NOW.monthNumber` are frozen at process start. All DataStore defaults derived from `NOW` will be stale after a day boundary crossing. Same pattern in `TransactionViewModel` and `BackupViewModel`.

### 4.5 `TransactionListItem` and `TransactionRow` are nearly duplicate composables
`TransactionsScreen.TransactionListItem` and `DashboardScreen.TransactionRow` share ~80% of their layout. They differ in delete button and click behavior, but could be unified into a single composable with optional trailing actions.

### 4.6 `exportCsv()` uses transaction's `categoryId` instead of category name
**File:** `BackupViewModel.kt` — The CSV export writes raw category IDs, not human-readable names. The CSV import for PhonePe doesn't have a corresponding import — this export is not round-trippable.

### 4.7 `CategoryEntity` `icon` field is declared but never used in UI
The `icon` column is populated by seed data but never rendered in any screen. Categories display as text-only labels.

### 4.8 `TagEntity` CRUD exists but tags are never used in UI
Tags are seeded, stored, and backed up, but no screen allows attaching tags to transactions. The `TransactionEntity.tags` field exists but the EditTransactionScreen doesn't expose tag selection.

### 4.9 `RecurringRuleEntity.createdAtEpochSeconds` has a default in the data class — Room won't use it
```kotlin
val createdAtEpochSeconds: Long = Clock.System.now().epochSeconds
```

Room ignores Kotlin default parameter values for primary key generation unless a `@DefaultValue` annotation (Room 2.6+) is used. New recurring rules inserted without this field will get `0` (the Kotlin default for `Long`), not the current timestamp.

### 4.10 `StatsScreen` PieChart label collision avoidance is complex
The pie chart's label positioning with leader lines and collision detection is a significant amount of custom Canvas logic. It works but is hard to maintain and has no unit tests.

---

## 5. Architecture Observations

### 5.1 No architecture violations — Clean single-direction data flow
The app follows a clear MVVM + unidirectional data flow: UI → ViewModel → Repository → DAO → Room. Flows are used consistently for reactive data. This is a strong architectural choice.

### 5.2 HorizontalPager instead of NavHost for tabs — correct decision
Using `HorizontalPager` avoids ViewModel destruction on tab switches. Combined with activity-scoped ViewModels, this gives correct state retention.

### 5.3 DataStore for shared state — correct but overused
The selected year/month is stored in DataStore and read by three separate ViewModels (Dashboard, Transaction, Backup). Each creates its own `StateFlow` from the same preference keys, which works but creates three independent collection channels. A single shared `MonthSelector` singleton would be cleaner.

### 5.4 SQLite-based aggregation is well-optimized
Moving month-range filtering to SQLite (`getMonthlyTotalsFlow`, `getYearlyTrendFlow`) instead of collecting all transactions into Kotlin memory is a good performance choice.

---

## 6. Summary by Priority

| Priority | Count | Key Items |
|----------|-------|-----------|
| Critical | 4 | Dual DB instance, unbound coroutine, hardcoded categoryId, Budget PK collision |
| High | 5 | December budget crash, DAILY overflow crash, non-atomic recurring, destructive restore, TZ mismatch |
| Medium | 8 | Dead currency, partial budget backup, dead code, manual SeedData, no FKs, ID collisions, unstable keys (×2) |
| Low | 7 | String literals, magic strings, DRY violation, frozen NOW, duplicate composables, category ID in CSV, unused icon/tags, Room default value, complex Canvas |
