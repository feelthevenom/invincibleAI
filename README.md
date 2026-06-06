# Gym AI

**Gym AI** is a native Android fitness companion that combines structured workout tracking, nutrition logging, hydration reminders, and an AI coach in one app. It runs fully **online** with cloud AI providers or in **complete offline mode** using on-device **Gemma 4** models — your data stays on your phone when you choose offline inference.

Built with **Kotlin** and **Jetpack Compose**, Gym AI is designed for daily use: log meals, follow routines, track cardio and strength sessions, and get personalized insights without leaving the app.

---

## Table of Contents

- [Features](#features)
- [Offline Mode (Gemma 4)](#offline-mode-gemma-4)
- [Technology Stack](#technology-stack)
- [Requirements](#requirements)
- [Getting Started](#getting-started)
- [API Keys & AI Configuration](#api-keys--ai-configuration)
- [AI Provider Overview](#ai-provider-overview)
- [Permissions](#permissions)
- [Project Structure](#project-structure)
- [Backup & Restore](#backup--restore)
- [Building for Release](#building-for-release)
- [License](#license)

---

## Features

### Home Dashboard

- **Diet & Exercise tabs** with at-a-glance stats
- Daily calorie intake vs goal (shows consumed, goal, and remaining — even when over target)
- Macro breakdown (protein, carbs, fat, fiber)
- **Water tracking** with progress and configurable reminders
- **Body composition** charts (weight, measurements)
- AI-powered **diet and exercise insights** when AI is configured
- Suggested workout cards based on your routine and weekly progress

### Diet

- Log meals across **Breakfast, Lunch, Dinner, and Snacks**
- Per-meal calorie and macro budgets based on your profile
- **700+ bundled foods** with instant local search
- **Open Food Facts** integration for packaged products
- **Custom foods** with manual or AI-assisted nutrition entry
- **AI food photo analysis** — snap a plate and get calorie/macro estimates (vision-capable model required)
- Calendar view with daily summaries (Yesterday / Today / custom date)

### Workout

- **Predefined routines** (Push, Pull, Legs, Upper, Lower, Full Body, Cardio, and more)
- Custom routine editor — add, reorder, and remove exercises
- **Active workout** session with set-by-set logging
- Smart weight defaults from history and profile (1RM estimates)
- **Cardio tracking** — timer, manual duration, calorie entry, and AI/MET-based estimates
- **Bodyweight exercises** — hides weight column when not applicable
- Switch between **cardio (timer)** and **weights (sets/reps)** per exercise with confirmation
- Exercise tutorials with GIFs, ExerciseDB integration, and inline **AI Fill** for steps
- Weekly volume, workout count, and **calories burned** progress on the Exercise tab
- Workout finish summary with volume and completion stats

### Coach AI

- Conversational fitness coach with context from your workouts, diet, and profile
- **Suggested prompt chips** (protein gap, leg day, recovery, etc.)
- **Chat history** — review past conversations
- Exercise step generation from the workout flow
- Works with cloud providers or **offline Gemma 4** models

### AI Settings

- **Unified model** — one multimodal model for text + vision
- **Split models** — separate text and vision providers/models
- Providers: **Gemini**, **Groq**, **OpenRouter**, **Offline (Gemma 4)**
- Secure API key storage with optional **biometric lock**
- Model picker with **free-model filter** and **search** (for large catalogs like OpenRouter)
- Download or import **Gemma 4 E2B / E4B** on-device models
- **Test API Connection** to verify keys and model access

### Reminders & Notifications

- **Hydration reminders** — interval, daily schedule, or custom windows
- **Workout reminders** at a chosen time
- Reschedules on boot and timezone changes
- Notification permission handling for Android 13+

### Privacy & Data

- Local **Room** database for all fitness data
- API keys stored in **EncryptedSharedPreferences**
- Optional **full offline** operation — no cloud AI required after model download
- **Backup & Restore** for profile, logs, and settings

---

## Offline Mode (Gemma 4)

Gym AI supports **complete offline AI** using Google’s **Gemma 4** models via **LiteRT-LM** (same stack as Google AI Edge Gallery).

| Model | Size | Min RAM | Vision |
|-------|------|---------|--------|
| **Gemma 4 E2B-it** | ~2.4 GB | 4 GB | Yes |
| **Gemma 4 E4B-it** | ~3.4 GB | 5 GB | Yes |

### How to enable offline mode

1. Open **Settings → AI Settings**
2. Set provider to **Offline** (unified) or assign Offline to text/vision slots in split mode
3. Tap **Download** next to the model you want (or **Import** a `.litertlm` file)
4. Wait for the download to finish — the model is stored on device
5. Select the downloaded model and confirm **AI Status** shows ready

Offline mode supports:

- Coach chat (text)
- Food photo analysis (vision)
- Exercise suggestions and step generation
- Calorie estimates for cardio
- Diet and exercise insights

No API key is required in offline mode. Internet is only needed for the initial model download.

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM, StateFlow, ViewModel |
| Database | Room (SQLite) |
| Networking | OkHttp, Retrofit, Moshi |
| Images | Coil (+ GIF support) |
| Cloud AI | Google Generative AI SDK (Gemini), Groq API, OpenRouter API |
| On-device AI | LiteRT-LM (Gemma 4) |
| Security | AndroidX Security Crypto, Biometric API |
| Async | Kotlin Coroutines |
| Build | Gradle, KSP, Secrets Gradle Plugin |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

### External data sources

- **Open Food Facts** — packaged food nutrition
- **ExerciseDB** — exercise metadata, GIFs, and tutorials (when online)
- **Bundled assets** — `foods.json`, `exercises.json` for offline search

---

## Requirements

- **Android Studio** Ladybug or newer (recommended)
- **JDK 11+**
- **Android device or emulator** running API 24+
- For **offline AI**: device with sufficient storage (~3–4 GB free) and RAM (4–5 GB+)
- For **cloud AI**: API key from at least one supported provider (see below)

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/gym-ai.git
cd gym-ai
```

### 2. Optional — Gemini key for build-time defaults

Create a `.env` file in the project root (see `.env.example`):

```env
GEMINI_API_KEY=your_gemini_api_key_here
```

This is used by the Secrets Gradle Plugin at build time. **Runtime keys are configured inside the app** in AI Settings and are the recommended approach for personal use.

### 3. Open in Android Studio

1. **File → Open** and select the project folder
2. Allow Gradle sync to complete
3. Connect a device or start an emulator

### 4. Run

```bash
./gradlew assembleDebug
```

Or press **Run ▶** in Android Studio.

Install the APK from:

```
app/build/outputs/apk/debug/app-debug.apk
```

### 5. First launch

1. Complete **onboarding** (goals, body stats, workout schedule)
2. Open **AI Settings** and choose **Offline** or add a cloud API key
3. Grant **notifications** and **camera** permissions when prompted

---

## API Keys & AI Configuration

All runtime API keys are managed in the app:

**Sidebar → Settings → AI Settings**

Keys are encrypted on device. You can optionally require fingerprint or device PIN to view or edit keys.

### Where to get API keys

| Provider | Get a key | Notes |
|----------|-----------|-------|
| **Google Gemini** | [Google AI Studio](https://aistudio.google.com/apikey) | Free tier available. Supports text + vision. |
| **Groq** | [Groq Console](https://console.groq.com/keys) | Fast inference. Free tier with rate limits. |
| **OpenRouter** | [OpenRouter Keys](https://openrouter.ai/keys) | Access to 100+ models via one key. Use **Refresh models** after saving. |
| **Offline (Gemma 4)** | *No key required* | Download model in AI Settings. |

### How to add a key in the app

#### Gemini

1. Go to **AI Settings**
2. Select **Gemini** as provider (unified or text/vision slot)
3. Tap **Add API Key** under the Gemini section
4. Paste your key from [Google AI Studio](https://aistudio.google.com/apikey)
5. Tap **Save** (authenticate if prompted)
6. Tap **Test API Connection**

#### Groq

1. Select **Groq** as provider
2. Tap **Add API Key** under Groq API
3. Paste your key from [console.groq.com](https://console.groq.com/keys)
4. Save and **Test API Connection**

#### OpenRouter

1. Select **OpenRouter** as provider
2. Add your key from [openrouter.ai/keys](https://openrouter.ai/keys)
3. Tap **Refresh models** to load your available model list
4. Pick a model (use the **Free** filter or search if needed)
5. **Test API Connection**

### Unified vs split model mode

| Mode | Best for |
|------|----------|
| **Unified** | One model handles coach, food vision, and insights (e.g. Gemini Flash, Groq Llama 4 Scout) |
| **Split** | Cheaper text model for chat + separate vision model for food photos |

Configure under **Model Configuration Mode** in AI Settings.

### Model picker tips

- **Free filter** — show only free-tier models (OpenRouter, Groq, Gemini Flash variants)
- **Search** — appears when a provider has more than 6 models; fuzzy match supported
- After switching from **Offline → Online**, wait a moment for the cloud provider to initialize

---

## AI Provider Overview

```
┌─────────────────────────────────────────────────────────┐
│                      Gym AI App                         │
├─────────────┬─────────────┬──────────────┬──────────────┤
│   Gemini    │    Groq     │  OpenRouter  │   Offline    │
│  (Google)   │  (Cloud)    │  (Gateway)   │  Gemma 4     │
├─────────────┴─────────────┴──────────────┴──────────────┤
│  Coach · Food Vision · Exercise AI · Insights · Tutorials │
└─────────────────────────────────────────────────────────┘
```

**Offline path:** LiteRT-LM → Gemma 4 E2B/E4B → no network after download

---

## Permissions

| Permission | Purpose |
|------------|---------|
| Internet | Cloud AI, Open Food Facts, ExerciseDB |
| Camera | Food photo logging, AI plate analysis |
| Read media images | Gallery food photos |
| Post notifications | Water and workout reminders |
| Schedule exact alarm | Reliable reminder delivery |
| Boot completed | Reschedule reminders after reboot |

---

## Project Structure

```
gym-ai/
├── app/src/main/
│   ├── java/com/example/
│   │   ├── data/           # Room, repositories, AI manager, calculators
│   │   ├── notifications/  # Water & workout reminders
│   │   ├── ui/             # Compose screens and components
│   │   └── GymViewModel.kt # Central app state
│   ├── assets/
│   │   ├── foods.json      # Bundled food database
│   │   └── exercises.json  # Bundled exercise catalog
│   └── res/                # Themes, icons, backup rules
├── gradle/                 # Version catalog
├── scripts/                # Utility scripts (exercise image scraping)
├── .env.example            # Build-time Gemini key template
└── README.md
```

---

## Backup & Restore

Access via **Sidebar → Backup & Restore**.

Exports and imports:

- User profile and goals
- Meal, workout, water, and weight logs
- Custom foods and exercises
- Routines and settings

Use this before switching devices or reinstalling the app.

---

## Building for Release

Release signing expects environment variables or a keystore at the project root:

```bash
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=your_store_password
export KEY_PASSWORD=your_key_password

./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

---

## Troubleshooting

| Issue | Suggestion |
|-------|------------|
| AI not responding | Check **AI Status** in AI Settings; run **Test API Connection** |
| OpenRouter models empty | Save key → **Refresh models** |
| Offline model won't load | Ensure enough free storage and RAM; re-download the model |
| Reminders not firing | Enable notifications in system settings; allow exact alarms if prompted |
| Food vision fails | Confirm a **vision-capable** model is selected (Gemini Flash, Groq Scout, Gemma 4, or OpenRouter vision model) |

---

## License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE).

Copyright (c) 2026 RAHUL Ravikumar

---

## Acknowledgments

- [Google Gemma](https://deepmind.google/models/gemma/) and [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) for on-device inference
- [Open Food Facts](https://world.openfoodfacts.org/) for open nutrition data
- [OpenRouter](https://openrouter.ai/) for multi-model API access
- [Groq](https://groq.com/) for fast cloud inference

---

<p align="center">
  <strong>Gym AI</strong> — Train smarter. Eat better. Stay consistent.<br>
  Works online, offline, or both.
</p>
