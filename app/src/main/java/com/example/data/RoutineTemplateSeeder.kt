package com.example.data

/**
 * Seeds built-in routine templates on first launch.
 */
object RoutineTemplateSeeder {

    data class TemplateExercise(
        val name: String,
        val type: String,
        val equipment: String = "",
        val sets: Int = 3,
        val reps: Int = 10
    )

    data class Template(val name: String, val exercises: List<TemplateExercise>)

    val builtInTemplates: List<Template> = listOf(
        Template("Push", listOf(
            TemplateExercise("Bench Press", "Chest", "barbell", 4, 8),
            TemplateExercise("Overhead Press", "Shoulders", "barbell", 4, 8),
            TemplateExercise("Incline DB Press", "Chest", "dumbbell", 3, 10),
            TemplateExercise("Lateral Raise", "Shoulders", "dumbbell", 3, 12),
            TemplateExercise("Tricep Pushdown", "Triceps", "cable", 3, 12),
            TemplateExercise("Tricep Dip", "Triceps", "bodyweight", 3, 10)
        )),
        Template("Pull", listOf(
            TemplateExercise("Deadlift", "Back", "barbell", 4, 5),
            TemplateExercise("Pull Up", "Back", "bodyweight", 4, 8),
            TemplateExercise("Barbell Row", "Back", "barbell", 4, 8),
            TemplateExercise("Lat Pulldown", "Back", "cable", 3, 10),
            TemplateExercise("Face Pull", "Back", "cable", 3, 15),
            TemplateExercise("Barbell Curl", "Biceps", "barbell", 3, 10)
        )),
        Template("Legs", listOf(
            TemplateExercise("Barbell Squat", "Legs", "barbell", 4, 8),
            TemplateExercise("Romanian Deadlift", "Back", "barbell", 3, 10),
            TemplateExercise("Leg Press", "Legs", "machine", 4, 10),
            TemplateExercise("Walking Lunge", "Legs", "bodyweight", 3, 12),
            TemplateExercise("Leg Curl", "Legs", "machine", 3, 12),
            TemplateExercise("Calf Raise", "Legs", "machine", 4, 15)
        )),
        Template("Upper Body", listOf(
            TemplateExercise("Bench Press", "Chest", "barbell", 4, 8),
            TemplateExercise("Barbell Row", "Back", "barbell", 4, 8),
            TemplateExercise("Overhead Press", "Shoulders", "barbell", 3, 8),
            TemplateExercise("Lat Pulldown", "Back", "cable", 3, 10),
            TemplateExercise("Lateral Raise", "Shoulders", "dumbbell", 3, 12),
            TemplateExercise("Barbell Curl", "Biceps", "barbell", 3, 10),
            TemplateExercise("Tricep Pushdown", "Triceps", "cable", 3, 12)
        )),
        Template("Lower Body", listOf(
            TemplateExercise("Barbell Squat", "Legs", "barbell", 4, 8),
            TemplateExercise("Romanian Deadlift", "Back", "barbell", 3, 10),
            TemplateExercise("Leg Press", "Legs", "machine", 4, 10),
            TemplateExercise("Leg Curl", "Legs", "machine", 3, 12),
            TemplateExercise("Walking Lunge", "Legs", "bodyweight", 3, 12),
            TemplateExercise("Hip Thrust", "Glutes", "barbell", 3, 10),
            TemplateExercise("Calf Raise", "Legs", "machine", 4, 15)
        )),
        Template("Full Body", listOf(
            TemplateExercise("Barbell Squat", "Legs", "barbell", 3, 8),
            TemplateExercise("Bench Press", "Chest", "barbell", 3, 8),
            TemplateExercise("Deadlift", "Back", "barbell", 3, 5),
            TemplateExercise("Overhead Press", "Shoulders", "barbell", 3, 8),
            TemplateExercise("Pull Up", "Back", "bodyweight", 3, 8),
            TemplateExercise("Plank", "Core", "bodyweight", 3, 60)
        )),
        Template("Core & Abs", listOf(
            TemplateExercise("Plank", "Core", "bodyweight", 3, 60),
            TemplateExercise("Cable Crunch", "Core", "cable", 3, 15),
            TemplateExercise("Hanging Leg Raise", "Core", "bar", 3, 12),
            TemplateExercise("Crunch", "Core", "bodyweight", 3, 15),
            TemplateExercise("Russian Twist", "Core", "bodyweight", 3, 20)
        )),
        Template("Cardio", listOf(
            TemplateExercise("Treadmill Run", "Cardio", "machine", 1, 20),
            TemplateExercise("Rowing Machine", "Cardio", "machine", 1, 15),
            TemplateExercise("Battle Ropes", "Cardio", "machine", 3, 30),
            TemplateExercise("Kettlebell Swing", "Full Body", "kettlebell", 3, 15),
            TemplateExercise("Farmer Walk", "Full Body", "dumbbell", 3, 40)
        ))
    )
}
