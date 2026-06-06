#!/usr/bin/env python3
"""Generate workout details.md from app sources."""
import json
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXERCISES_JSON = ROOT / "app/src/main/assets/exercises.json"
OUTPUT = ROOT / "workout details.md"

EXERCISE_TYPES = [
    "Chest", "Back", "Shoulders", "Biceps", "Triceps",
    "Legs", "Glutes", "Core", "Full Body", "Cardio",
]
ROUTINE_TAGS = ["Push", "Pull", "Legs", "Upper", "Lower", "Full Body"]

BUILT_IN = [
    ("Push", [
        ("Bench Press", "Chest", "barbell", 4, 8),
        ("Overhead Press", "Shoulders", "barbell", 4, 8),
        ("Incline DB Press", "Chest", "dumbbell", 3, 10),
        ("Lateral Raise", "Shoulders", "dumbbell", 3, 12),
        ("Tricep Pushdown", "Triceps", "cable", 3, 12),
        ("Tricep Dip", "Triceps", "bodyweight", 3, 10),
    ]),
    ("Pull", [
        ("Deadlift", "Back", "barbell", 4, 5),
        ("Pull Up", "Back", "bodyweight", 4, 8),
        ("Barbell Row", "Back", "barbell", 4, 8),
        ("Lat Pulldown", "Back", "cable", 3, 10),
        ("Face Pull", "Back", "cable", 3, 15),
        ("Barbell Curl", "Biceps", "barbell", 3, 10),
    ]),
    ("Legs", [
        ("Barbell Squat", "Legs", "barbell", 4, 8),
        ("Romanian Deadlift", "Back", "barbell", 3, 10),
        ("Leg Press", "Legs", "machine", 4, 10),
        ("Walking Lunge", "Legs", "bodyweight", 3, 12),
        ("Leg Curl", "Legs", "machine", 3, 12),
        ("Calf Raise", "Legs", "machine", 4, 15),
    ]),
    ("Upper Body", [
        ("Bench Press", "Chest", "barbell", 4, 8),
        ("Barbell Row", "Back", "barbell", 4, 8),
        ("Overhead Press", "Shoulders", "barbell", 3, 8),
        ("Lat Pulldown", "Back", "cable", 3, 10),
        ("Lateral Raise", "Shoulders", "dumbbell", 3, 12),
        ("Barbell Curl", "Biceps", "barbell", 3, 10),
        ("Tricep Pushdown", "Triceps", "cable", 3, 12),
    ]),
    ("Lower Body", [
        ("Barbell Squat", "Legs", "barbell", 4, 8),
        ("Romanian Deadlift", "Back", "barbell", 3, 10),
        ("Leg Press", "Legs", "machine", 4, 10),
        ("Leg Curl", "Legs", "machine", 3, 12),
        ("Walking Lunge", "Legs", "bodyweight", 3, 12),
        ("Hip Thrust", "Glutes", "barbell", 3, 10),
        ("Calf Raise", "Legs", "machine", 4, 15),
    ]),
    ("Full Body", [
        ("Barbell Squat", "Legs", "barbell", 3, 8),
        ("Bench Press", "Chest", "barbell", 3, 8),
        ("Deadlift", "Back", "barbell", 3, 5),
        ("Overhead Press", "Shoulders", "barbell", 3, 8),
        ("Pull Up", "Back", "bodyweight", 3, 8),
        ("Plank", "Core", "bodyweight", 3, 60),
    ]),
    ("Core & Abs", [
        ("Plank", "Core", "bodyweight", 3, 60),
        ("Cable Crunch", "Core", "cable", 3, 15),
        ("Hanging Leg Raise", "Core", "bar", 3, 12),
        ("Crunch", "Core", "bodyweight", 3, 15),
        ("Russian Twist", "Core", "bodyweight", 3, 20),
    ]),
    ("Cardio", [
        ("Treadmill Run", "Cardio", "machine", 1, 20),
        ("Rowing Machine", "Cardio", "machine", 1, 15),
        ("Battle Ropes", "Cardio", "machine", 3, 30),
        ("Kettlebell Swing", "Full Body", "kettlebell", 3, 15),
        ("Farmer Walk", "Full Body", "dumbbell", 3, 40),
    ]),
]

SPLITS = {
    1: [("Full Body", "Full Body", "Full Body")],
    2: [("Upper Body", "Upper", "Upper"), ("Lower Body", "Lower", "Lower")],
    3: [
        ("Push Day", "Push", "Chest/Shoulders/Triceps"),
        ("Pull Day", "Pull", "Back/Biceps"),
        ("Leg Day", "Legs", "Quads/Hams/Glutes"),
    ],
    4: [
        ("Upper A", "Upper", "Upper"),
        ("Lower A", "Lower", "Lower"),
        ("Upper B", "Upper", "Upper"),
        ("Lower B", "Lower", "Lower"),
    ],
    5: [
        ("Push Day", "Push", "Push"),
        ("Pull Day", "Pull", "Pull"),
        ("Leg Day", "Legs", "Legs"),
        ("Upper Day", "Upper", "Upper"),
        ("Lower Day", "Lower", "Lower"),
    ],
    6: [
        ("Push A", "Push", "Push"),
        ("Pull A", "Pull", "Pull"),
        ("Legs A", "Legs", "Legs"),
        ("Push B", "Push", "Push"),
        ("Pull B", "Pull", "Pull"),
        ("Legs B", "Legs", "Legs"),
    ],
    7: [
        ("Push", "Push", "Push"),
        ("Pull", "Pull", "Pull"),
        ("Legs", "Legs", "Legs"),
        ("Upper", "Upper", "Upper"),
        ("Lower", "Lower", "Lower"),
        ("Full Body", "Full Body", "Full Body"),
        ("Active Recovery", "Full Body", "Light"),
    ],
}


def main() -> None:
    with EXERCISES_JSON.open(encoding="utf-8") as f:
        raw_exercises = json.load(f)["exercises"]

    seen: set[str] = set()
    unique_exercises: list[dict] = []
    for entry in raw_exercises:
        if entry["id"] not in seen:
            seen.add(entry["id"])
            unique_exercises.append(entry)

    lines: list[str] = []

    def add(text: str = "") -> None:
        lines.append(text)

    add("# Workout Details")
    add()
    add(
        "Complete reference of all workouts, exercises, and routines available in the "
        "**Gym AI** Android app."
    )
    add()
    add(
        "**Source files:** "
        "`app/src/main/assets/exercises.json`, "
        "`RoutineTemplateSeeder.kt`, "
        "`WorkoutSplitGenerator.kt`, "
        "`WorkoutOverlays.kt`"
    )
    add()
    add("---")
    add()
    add("## Table of Contents")
    add()
    toc = [
        "Overview",
        "Muscle Types",
        "Equipment Types",
        "Routine Tags (Exercise Filters)",
        "Built-in Workout Routines",
        "Workout Split Plans (By Days Per Week)",
        "Session Types",
        "Complete Exercise Catalog (55 Exercises)",
        "Exercises Grouped by Muscle Type",
        "Exercises by Routine Tag",
        "Built-in Routine Exercise Index",
        "Data Model Reference",
        "Notes for Image Creation",
    ]
    for i, title in enumerate(toc, 1):
        anchor = title.lower().replace(" ", "-").replace("(", "").replace(")", "")
        anchor = anchor.replace("--", "-")
        add(f"{i}. [{title}](#{anchor})")
    add()
    add("---")
    add()
    add("## Overview")
    add()
    add("| Item | Count |")
    add("|------|-------|")
    add(
        f"| Bundled exercises in catalog | {len(unique_exercises)} unique "
        f"({len(raw_exercises)} entries in JSON; 1 duplicate ID) |"
    )
    add(f"| Built-in routine templates | {len(BUILT_IN)} |")
    add(f"| Muscle / exercise types | {len(EXERCISE_TYPES)} |")
    add(f"| Routine filter tags | {len(ROUTINE_TAGS)} |")
    add("| User-created custom exercises | Unlimited (stored in app database) |")
    add("| User-created custom routines | Unlimited (stored in app database) |")
    add()
    add(
        "The app loads exercises from `exercises.json` on startup. Built-in routines are "
        "seeded from `RoutineTemplateSeeder` on first launch. Users can add custom exercises "
        "and routines at any time."
    )
    add()
    add("---")
    add()
    add("## Muscle Types")
    add()
    add("Used when creating custom exercises and for exercise categorization in the app:")
    add()
    for muscle_type in EXERCISE_TYPES:
        add(f"- **{muscle_type}**")
    add()
    add("---")
    add()
    add("## Equipment Types")
    add()
    equip_all = sorted(
        set((e.get("equipment") or "").strip() for e in unique_exercises)
        | {"barbell", "dumbbell", "cable", "bodyweight", "machine", "bar", "kettlebell"}
    )
    for eq in equip_all:
        label = eq if eq else "(none / not specified)"
        add(f"- **{label}**")
    add()
    add("---")
    add()
    add("## Routine Tags (Exercise Filters)")
    add()
    add(
        "Each exercise in the catalog is tagged with one or more routine categories. "
        "These tags filter exercises when searching during a workout day:"
    )
    add()
    for tag in ROUTINE_TAGS:
        count = sum(1 for e in unique_exercises if tag in e.get("routines", []))
        add(f"- **{tag}** — {count} exercises")
    add()
    add(
        "**Mapping:** Workout day labels like \"Upper Body\" or \"Push Day\" map to tags "
        "`Upper` and `Push` respectively when filtering the exercise search."
    )
    add()
    add("---")
    add()
    add("## Built-in Workout Routines")
    add()
    add(
        "These 8 templates are created automatically when the app is first installed. "
        "Users can edit exercises in each routine. Custom routines can be deleted; "
        "built-in routines cannot."
    )
    add()
    for idx, (name, exercises) in enumerate(BUILT_IN, 1):
        add(f"### {idx}. {name}")
        add()
        add(f"**Total exercises:** {len(exercises)}")
        add()
        add("| # | Exercise | Muscle Type | Equipment | Sets | Reps |")
        add("|---|----------|-------------|-----------|------|------|")
        for i, (ex_name, ex_type, equipment, sets, reps) in enumerate(exercises, 1):
            add(f"| {i} | {ex_name} | {ex_type} | {equipment} | {sets} | {reps} |")
        add()

    add("---")
    add()
    add("## Workout Split Plans (By Days Per Week)")
    add()
    add(
        "Generated by `WorkoutSplitGenerator` based on user profile `workoutDaysPerWeek` "
        "and fitness goal."
    )
    add()
    add("**Goal styles:**")
    add("- Gain Muscle → Hypertrophy")
    add("- Athletic Performance → Performance")
    add("- Lose Fat → Metabolic")
    add("- General Fitness / other → Hypertrophy")
    add()
    for days, plans in SPLITS.items():
        add(f"### {days} day(s) per week")
        add()
        add("| Label Pattern | Routine Filter | Focus |")
        add("|---------------|----------------|-------|")
        for label, routine, focus in plans:
            add(f"| {label} (Hypertrophy/Performance/Metabolic) | {routine} | {focus} |")
        add()

    add("---")
    add()
    add("## Session Types")
    add()
    add("| Session | Description |")
    add("|---------|-------------|")
    add(
        "| **Start Routine** | Starts a built-in or custom routine for the selected day. "
        "Seeds exercises from routine template if none logged yet. |"
    )
    add(
        "| **Start Empty Workout** | Blank session labeled \"Empty Workout\". "
        "User adds exercises manually. |"
    )
    add(
        "| **Continue / View Workout** | Re-opens a previously logged workout for that date "
        "from the workout log card. |"
    )
    add(
        "| **AI Routine Suggestion** | When AI is configured, suggests a routine based on "
        "yesterday's training and user goal. |"
    )
    add(
        "| **Custom Exercise** | User-defined exercise with name, muscle type, sets, reps, "
        "optional default weight. |"
    )
    add(
        "| **AI Exercise Suggestion** | AI can suggest up to 4 exercises when creating a "
        "custom exercise (same muscle types as catalog). |"
    )
    add()
    add("---")
    add()
    add("## Complete Exercise Catalog (55 Exercises)")
    add()
    add(
        "All exercises from `exercises.json`. Default sets/reps apply when adding from "
        "search unless overridden."
    )
    add()
    add(
        "| ID | Name | Muscle Type | Equipment | Sets | Reps | Secondary Muscles | "
        "Aliases | Routine Tags |"
    )
    add(
        "|----|------|-------------|-----------|------|------|-------------------|"
        "---------|--------------|"
    )
    for entry in sorted(unique_exercises, key=lambda x: (x["type"], x["name"])):
        sec = ", ".join(entry.get("secondaryMuscles") or []) or "—"
        aliases = ", ".join(entry.get("aliases") or []) or "—"
        tags = ", ".join(entry.get("routines") or []) or "—"
        eq = entry.get("equipment") or "—"
        add(
            f"| `{entry['id']}` | {entry['name']} | {entry['type']} | {eq} | "
            f"{entry.get('defaultSets', 3)} | {entry.get('defaultReps', 10)} | "
            f"{sec} | {aliases} | {tags} |"
        )
    add()
    add(
        "> **Note:** `bulgarian_split_squat` appears twice in the source JSON file "
        "(duplicate entry). Only one is listed above."
    )
    add()
    add("---")
    add()
    add("## Exercises Grouped by Muscle Type")
    add()
    by_type: OrderedDict[str, list[dict]] = OrderedDict()
    for entry in unique_exercises:
        by_type.setdefault(entry["type"], []).append(entry)
    for muscle in sorted(by_type.keys()):
        add(f"### {muscle} ({len(by_type[muscle])} exercises)")
        add()
        for entry in sorted(by_type[muscle], key=lambda x: x["name"]):
            eq = entry.get("equipment") or "not specified"
            sec = ", ".join(entry.get("secondaryMuscles") or []) or "none"
            add(f"#### {entry['name']}")
            add()
            add(f"- **ID:** `{entry['id']}`")
            add(f"- **Equipment:** {eq}")
            add(
                f"- **Default:** {entry.get('defaultSets', 3)} sets × "
                f"{entry.get('defaultReps', 10)} reps"
            )
            add(f"- **Secondary muscles:** {sec}")
            if entry.get("aliases"):
                add(f"- **Also known as:** {', '.join(entry['aliases'])}")
            if entry.get("routines"):
                add(f"- **Routine tags:** {', '.join(entry['routines'])}")
            add()

    add("---")
    add()
    add("## Exercises by Routine Tag")
    add()
    for tag in ROUTINE_TAGS:
        tagged = [e for e in unique_exercises if tag in e.get("routines", [])]
        add(f"### {tag} ({len(tagged)} exercises)")
        add()
        for entry in sorted(tagged, key=lambda x: x["name"]):
            add(f"- {entry['name']} ({entry['type']})")
        add()

    add("---")
    add()
    add("## Built-in Routine Exercise Index")
    add()
    add("Cross-reference: which built-in routines include each catalog exercise by name.")
    add()
    routine_map: dict[str, list[str]] = {}
    for routine_name, exercises in BUILT_IN:
        for ex_name, _, _, _, _ in exercises:
            routine_map.setdefault(ex_name.lower(), []).append(routine_name)
    for entry in sorted(unique_exercises, key=lambda x: x["name"].lower()):
        in_routines = routine_map.get(entry["name"].lower(), [])
        if in_routines:
            add(f"- **{entry['name']}** → {', '.join(in_routines)}")
    add()
    add("Exercises in catalog not assigned to any built-in routine template:")
    add()
    for entry in sorted(unique_exercises, key=lambda x: x["name"].lower()):
        if entry["name"].lower() not in routine_map:
            add(f"- {entry['name']} ({entry['type']})")
    add()

    add("---")
    add()
    add("## Data Model Reference")
    add()
    add("### ExerciseSet (logged workout data)")
    add()
    add("| Field | Type | Description |")
    add("|-------|------|-------------|")
    add("| exerciseName | String | Exercise name |")
    add("| exerciseType | String | Primary muscle type |")
    add("| workoutDayLabel | String | Routine/session name (e.g. Push, Empty Workout) |")
    add("| setNumber | Int | Set index (1-based) |")
    add("| weight | Float | Weight in kg |")
    add("| reps | Int | Rep count (or duration for holds/cardio in templates) |")
    add("| isCompleted | Boolean | Set marked complete |")
    add("| timestamp | Long | Log date/time |")
    add()
    add("### WorkoutRoutine")
    add()
    add("| Field | Description |")
    add("|-------|-------------|")
    add("| name | Routine display name |")
    add("| isBuiltIn | true for seeded templates |")
    add("| sortOrder | Display order in dropdown |")
    add()
    add("### RoutineExercise (template within a routine)")
    add()
    add("| Field | Description |")
    add("|-------|-------------|")
    add("| exerciseName | Exercise name |")
    add("| exerciseType | Muscle type |")
    add("| equipment | Equipment used |")
    add("| defaultSets | Sets when starting workout |")
    add("| defaultReps | Reps when starting workout |")
    add("| defaultWeight | Optional preset weight (kg) |")
    add()
    add("### CustomExercise (user-created)")
    add()
    add("| Field | Description |")
    add("|-------|-------------|")
    add("| name | User-defined name |")
    add("| exerciseType | One of the 10 muscle types |")
    add("| defaultSets | Default set count |")
    add("| defaultReps | Default rep count |")
    add("| defaultWeight | Optional default weight |")
    add()
    add("---")
    add()
    add("## Notes for Image Creation")
    add()
    add("Use this section as a checklist when creating exercise demonstration images for users.")
    add()
    add("### Priority exercises (in all major built-in routines)")
    add()
    priority = [
        "Bench Press", "Overhead Press", "Deadlift", "Barbell Squat", "Pull Up",
        "Barbell Row", "Lat Pulldown", "Romanian Deadlift", "Leg Press", "Plank",
    ]
    for name in priority:
        match = next((e for e in unique_exercises if e["name"].lower() == name.lower()), None)
        if match:
            equip = match.get("equipment") or "bodyweight/various"
            add(f"- **{match['name']}** — {match['type']}, {equip}")
    add()
    add("### Time-based / special rep values")
    add()
    add("| Exercise | Reps value meaning |")
    add("|----------|-------------------|")
    add("| Plank | 60 — typically seconds (hold) |")
    add("| Battle Ropes | 30 — typically seconds |")
    add("| Farmer Walk | 40 — steps or meters (app stores as rep count) |")
    add("| Treadmill Run | 20 — minutes (1 set) |")
    add("| Rowing Machine | 15 — minutes (1 set) |")
    add()
    add("### Full alphabetical exercise list (for image file naming)")
    add()
    add("Suggested filename format: `{id}.png` or `{id}_demo.png`")
    add()
    for entry in sorted(unique_exercises, key=lambda x: x["name"].lower()):
        add(f"- `{entry['id']}` — {entry['name']}")

    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT} ({len(lines)} lines, {len(unique_exercises)} exercises)")


if __name__ == "__main__":
    main()
