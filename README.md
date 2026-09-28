# Personal Fitness

A small fitness tracker for planning a daily calorie goal. This pass covers the foundation (Sprint 0) and accounts, auth, and the calorie goal (Sprint 1).

Sprint 2–3 (meal logging, NVIDIA NIM estimates, weekly tips, activity tracking, weight charts) are intentionally not here. `NVIDIA_API_KEY` is only a placeholder in `.env.example`. Nothing in the app reads it.

## Layout

```
backend/     Spring Boot API (Java 21, MongoDB, JWT)
frontend/    React + Vite SPA
docker-compose.yml
.env.example
```

## Prerequisites

- Java 21
- Node.js 20+
- MongoDB on `localhost:27017`

Start MongoDB with Docker:

```bash
docker compose up -d
```

Or run any local `mongod` that serves that port. The API defaults to `mongodb://localhost:27017/fitness`.

## Run

From the repo root, in two terminals:

```bash
# API on http://localhost:8080
./scripts/dev-backend.sh

# SPA on http://localhost:5173
./scripts/dev-frontend.sh
```

The frontend dev server proxies `/api` to the API, so the browser stays on port 5173.

Open http://localhost:5173, create an account, and you should land on the dashboard with a calorie goal. Profile edits recompute it. A manual override replaces the number the dashboard uses until you clear it.

Equivalent commands without the scripts:

```bash
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

Copy `.env.example` to `.env` only if you want to override the defaults. `./scripts/dev-backend.sh` exports that file when it exists. The defaults in `backend/src/main/resources/application.yml` are enough for local MongoDB. Do not commit `.env`.

| Variable | Purpose |
| --- | --- |
| `MONGODB_URI` | Mongo connection string |
| `JWT_SECRET` | HMAC secret, at least 32 bytes |
| `JWT_EXPIRATION_MS` | Token lifetime, default 24 hours |
| `SERVER_PORT` | API port, default 8080 |
| `CORS_ORIGINS` | Comma-separated browser origins |
| `NVIDIA_API_KEY` | Unused placeholder for a later sprint |

Check the API with:

```bash
curl -s http://localhost:8080/api/health
```

`{"status":"UP","service":"fitness-api"}` means the process is up. This check does not ping MongoDB.

### Tests

```bash
cd backend && ./mvnw test
cd frontend && npm run build
```

Backend tests do not need a running database.

## Calorie formula

The goal is stored when you sign up and again whenever profile bio is saved.

1. **BMR** uses Mifflin–St Jeor, with weight in kilograms, height in centimetres, and age in years.
   - Male: `10 × weight + 6.25 × height − 5 × age + 5`
   - Female: `10 × weight + 6.25 × height − 5 × age − 161`
2. **TDEE** (maintenance) is `BMR × activity multiplier`.

   | Activity | Multiplier | Meaning |
   | --- | --- | --- |
   | Sedentary | 1.2 | Desk job, little or no exercise |
   | Lightly active | 1.375 | Exercise 1–3 days a week |
   | Moderately active | 1.55 | Exercise 3–5 days a week |
   | Very active | 1.725 | Exercise 6–7 days a week |
   | Extra active | 1.9 | Physical job or twice-a-day training |

3. **Daily adjustment** spreads the gap between current and target weight across the timeline. One kilogram of body weight is treated as 7700 kcal.

   ```
   daily adjustment = (currentKg − targetKg) × 7700 / (timelineWeeks × 7)
   ```

   A positive adjustment is a deficit (weight loss). A negative adjustment is a surplus (weight gain). An unchanged weight produces 0.

4. **Raw goal** = `TDEE − daily adjustment`.
5. The raw goal is rounded half up to the nearest kilocalorie. BMR, TDEE, and the adjustment shown in the UI are rounded the same way for display. The stored goal uses the unrounded TDEE and adjustment, then a single rounding, so the three rounded figures can disagree by a kilocalorie.
6. **Safety clamp**, stored as `calorieGoalClamp`:
   - Below 1500 kcal (male) or 1200 kcal (female) is raised to that floor (`FLOOR`).
   - Above 5000 kcal is lowered to 5000 (`CEILING`).
   - Otherwise `NONE`.

### Worked example

Male, 30 years, 180 cm, 90 kg, target 80 kg, moderately active, 20 weeks:

- BMR = `10×90 + 6.25×180 − 5×30 + 5` = **1880**
- TDEE = `1880 × 1.55` = **2914**
- Adjustment = `(90−80) × 7700 / 140` = **550** kcal/day deficit
- Goal = `2914 − 550` = **2364** kcal (`NONE`)

### Manual override

Profile save always recomputes and stores the formula result. Optionally send `calorieGoalOverride`:

- A number from **1000 to 5000** becomes the active goal. The response uses `calorieGoalSource: OVERRIDE` and still includes `computedCalorieGoal`.
- `null` clears the override. The active goal returns to the computed value (`COMPUTED`).

Changing age, sex, height, weight, activity, or timeline recomputes the formula even if an override is still set. The dashboard keeps showing the manual number until the override is cleared. The screen labels which one is in use.

## Validation

The API returns `400` with `error: "validation_failed"` and a `fields` map. Duplicate email returns `409` with `error: "email_taken"`. A wrong login returns `401` with `error: "invalid_credentials"` whether the email exists or the password is wrong.

| Field | Rule |
| --- | --- |
| Email | Required, valid email, stored lowercase, unique |
| Password | 8–72 characters |
| Age | 16–100 |
| Sex | `MALE` or `FEMALE` |
| Height | 120–250 cm, at most one decimal place |
| Current and target weight | 30–300 kg, at most one decimal place |
| Activity | One of the five levels above |
| Timeline | 1–104 weeks |
| Manual calorie goal | Empty, or a whole number from 1000 to 5000. A fractional value such as `2200.5` is rejected. |

## API

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/health` | No | Process health |
| `POST` | `/api/auth/signup` | No | Create account, compute goal, return a JWT |
| `POST` | `/api/auth/login` | No | Return a JWT |
| `POST` | `/api/auth/logout` | Yes | `204`. The token is not stored server-side; the client deletes it |
| `GET` | `/api/profile` | Yes | Current profile and goal |
| `PUT` | `/api/profile` | Yes | Replace bio, recompute, set or clear the override |

Send `Authorization: Bearer <token>` on protected routes. The SPA keeps the token in `localStorage` under `fitness.token` and removes it on logout. Routes under the dashboard and profile redirect to login when that token is missing.

Signup body for the worked example:

```bash
curl -s -X POST http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "ada@example.com",
    "password": "password123",
    "age": 30,
    "sex": "MALE",
    "heightCm": 180,
    "currentWeightKg": 90,
    "targetWeightKg": 80,
    "activityLevel": "MODERATELY_ACTIVE",
    "timelineWeeks": 20
  }'
```

## Out of scope

Meal logging, NVIDIA NIM meal estimates, weekly tips, an activity tracker, and weight-journey charts.
