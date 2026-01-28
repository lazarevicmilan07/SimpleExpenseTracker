# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Install debug APK on connected device/emulator
./gradlew installDebug

# Release build
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean
./gradlew clean
```

Requires Java 17. Gradle wrapper version 8.4, AGP 8.2.0, Kotlin 1.9.21.

## Architecture

Android expense tracker using **Clean MVVM** with Jetpack Compose and Hilt DI.

**Three layers:**
- **Presentation** (`ui/`): Compose screens + `@HiltViewModel` ViewModels exposing `StateFlow<UiState>`
- **Domain** (`domain/`): Plain Kotlin models (`Expense`, `Category`, `Account`) and use cases (`ExportUseCase`, `BackupRestoreUseCase`)
- **Data** (`data/`): Room database with DAOs returning `Flow<List<Entity>>`, repositories that map entities to domain models, DataStore for preferences

**Key patterns:**
- All DI wiring is in `di/AppModule.kt` (singleton-scoped database, DAOs, repositories)
- Navigation defined in `navigation/NavGraph.kt` using sealed class `Screen` routes
- Bottom nav has 3 tabs: Transactions (dashboard), Monthly Reports, Yearly Reports
- Room database (`ExpenseDatabase`) is at version 3 with migrations for accounts (v2) and subcategories (v3)
- Entity↔Domain mapping in `data/mapper/Mappers.kt`

**Monetization:**
- AdMob banner ads (test IDs in debug, production in release build variants defined in `build.gradle.kts`)
- Google Play Billing one-time purchase (`premium_unlock`) managed by `billing/BillingManager.kt`
- Premium status persisted in DataStore; premium users get no ads, CSV/PDF export, backup/restore, unlimited categories

**Data flow:** Compose screen observes `ViewModel.uiState: StateFlow` → ViewModel collects from `Repository.getX(): Flow` → Repository delegates to `Dao` returning `Flow<List<Entity>>` and maps to domain models.

## Key Conventions

- Min SDK 26 (Android 8.0), Target/Compile SDK 34
- Compose BOM 2024.02.00 with Material 3
- KSP for Room and Hilt annotation processing (not kapt)
- `LocalDate` used for dates (type converter in `data/local/Converters.kt`)
- Transaction types: `TransactionType.EXPENSE` and `TransactionType.INCOME`
- 10 default categories seeded via `RoomDatabase.Callback` in `ExpenseDatabase`
- ProGuard rules configured in `app/proguard-rules.pro` for Room, Kotlin serialization, Hilt, iText, Billing, and AdMob
