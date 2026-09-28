# Personal Fitness

A small fitness tracker for a daily calorie goal and a log of the meals you eat. Sprint 0–1 cover accounts, auth, and the calorie goal. Sprint 2 adds free-text meal estimates, saving meals to a day, editing and deleting them, and a day view of totals against the calorie goal.

Weekly tips, an activity tracker, and weight-journey charts are later work and are not in this pass.

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
| `NVIDIA_API_KEY` | Server-side key for NVIDIA NIM meal estimates. Optional: without it, meals can still be entered by hand |
| `NVIDIA_API_BASE_URL` | NIM base URL, default `https://integrate.api.nvidia.com/v1` |
| `NVIDIA_MODEL` | NIM model id, default `meta/llama-3.3-70b-instruct` |
| `NVIDIA_TIMEOUT_SECONDS` | Estimate request timeout, default 25 |

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

## Meal estimates

Free-text meals are estimated with [NVIDIA NIM](https://build.nvidia.com/) through its OpenAI-compatible API:

`POST https://integrate.api.nvidia.com/v1/chat/completions`

**Model:** `meta/llama-3.3-70b-instruct`

That is an instruction-following Llama model hosted on NIM. The API asks it for one JSON object with a short meal name, whole-number kilocalories, and grams of protein, carbohydrates, and fat. The number is an estimate for planning. It is not medical advice. Set `NVIDIA_MODEL` if you want a different NIM model id.

The browser never sees the key. `./scripts/dev-backend.sh` exports `.env` when that file exists, and the API reads `NVIDIA_API_KEY` from the environment. Restart the API after you change it.

```bash
cp .env.example .env
# edit .env and set NVIDIA_API_KEY
./scripts/dev-backend.sh
```

On the dashboard, describe a meal and choose **Estimate calories**. The name, calories, and macros fill in, and you can change any of them before **Save meal**. Nothing is stored until you save.

If the key is missing, NIM is down, the request times out, or the reply is not usable, `POST /api/meals/estimate` returns a clear error and does not save a meal:

| Status | `error` | When |
| --- | --- | --- |
| 503 | `estimate_unavailable` | `NVIDIA_API_KEY` is blank |
| 502 | `estimate_failed` | NIM HTTP error, timeout, or an unusable reply |

The form stays on the page either way, so you can type calories and macros yourself and save.

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
| Meal text | Required to estimate, 500 characters or fewer |
| Meal name | Required to save, 300 characters or fewer |
| Meal calories | Whole number from 0 to 10000. A fractional value such as `380.5` is rejected. |
| Protein, carbs, fat | 0–1000 g, at most one decimal place |
| Meal day | `YYYY-MM-DD`. Saving is limited to 2000-01-01 through two years ahead. The day view can still open other dates. |

## API

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/health` | No | Process health |
| `POST` | `/api/auth/signup` | No | Create account, compute goal, return a JWT |
| `POST` | `/api/auth/login` | No | Return a JWT |
| `POST` | `/api/auth/logout` | Yes | `204`. The token is not stored server-side; the client deletes it |
| `GET` | `/api/profile` | Yes | Current profile and goal |
| `PUT` | `/api/profile` | Yes | Replace bio, recompute, set or clear the override |
| `POST` | `/api/meals/estimate` | Yes | Estimate calories and macros for free text. Does not save |
| `POST` | `/api/meals` | Yes | Save a meal on the signed-in user's day. `201` |
| `PUT` | `/api/meals/{id}` | Yes | Edit that user's meal, including moving it to another day |
| `DELETE` | `/api/meals/{id}` | Yes | Delete that user's meal. `204`. Another user's id is `404` |
| `GET` | `/api/days/{date}` | Yes | That day's meals, macro totals, calorie goal, and calories remaining |

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

Estimate, then save, for the worked-example account:

```bash
TOKEN="<jwt from signup or login>"

curl -s -X POST http://localhost:8080/api/meals/estimate \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"text":"two eggs and toast with butter"}'

curl -s -X POST http://localhost:8080/api/meals \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "day": "2026-09-28",
    "description": "Two eggs and toast",
    "calories": 380,
    "proteinG": 18,
    "carbsG": 28,
    "fatG": 22
  }'

curl -s http://localhost:8080/api/days/2026-09-28 \
  -H "Authorization: Bearer $TOKEN"
```

The day payload includes `meals`, `totals` (calories, protein, carbs, fat), `calorieGoal` (the manual number when one is set, otherwise the formula), and `remainingCalories`. A negative remaining value means the day is over the goal.

The SPA sends the calendar date from the browser, so “today” follows the person using it rather than the server’s timezone.

## Out of scope

Weekly tips, an activity tracker, weight-journey charts, and revoking JWTs on the server. Logout still only removes the token in the browser.
