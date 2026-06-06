package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import com.example.data.BitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AiRouteResolver
import com.example.GymViewModel
import com.example.data.CustomFoodItem
import com.example.data.FoodItem
import com.example.data.FoodNutritionCalculator
import com.example.data.MealEntry
import com.example.data.MealTypes
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private sealed class MealSheetPage {
    data object Detail : MealSheetPage()
    data object FoodList : MealSheetPage()
    data class FoodWeight(val food: FoodItem) : MealSheetPage()
    data class CustomFood(val prefilledName: String = "") : MealSheetPage()
    data class PlateAnalysis(val bitmap: Bitmap) : MealSheetPage()
}

@Composable
fun MealDetailOverlay(
    mealType: String,
    budget: MealTypes.MealBudget,
    entries: List<MealEntry>,
    logDayStart: Long,
    viewModel: GymViewModel,
    onDismiss: () -> Unit
) {
    var page by remember { mutableStateOf<MealSheetPage>(MealSheetPage.Detail) }
    val customFoods by viewModel.customFoods.collectAsState()
    val searchState by viewModel.foodSearchState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoadingImage by remember { mutableStateOf(false) }
    var imageLoadError by remember { mutableStateOf<String?>(null) }
    var pendingImageAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun openPlateAnalysis(bitmap: Bitmap) {
        scope.launch {
            isLoadingImage = true
            imageLoadError = null
            try {
                val safeBitmap = withContext(Dispatchers.Default) {
                    BitmapLoader.ensureSoftwareBitmap(bitmap)
                }
                page = MealSheetPage.PlateAnalysis(safeBitmap)
            } catch (e: Exception) {
                imageLoadError = e.message ?: "Failed to prepare image for analysis"
            } finally {
                isLoadingImage = false
            }
        }
    }

    fun loadPlateAnalysisFromUri(uri: Uri) {
        scope.launch {
            isLoadingImage = true
            imageLoadError = null
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapLoader.loadScaledFromUri(context, uri)
                }
                page = MealSheetPage.PlateAnalysis(bitmap)
            } catch (e: Exception) {
                imageLoadError = e.message ?: "Failed to load image. Try a smaller photo."
            } finally {
                isLoadingImage = false
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingImageAction?.invoke()
        } else {
            imageLoadError = "Camera permission is required to take food photos."
        }
        pendingImageAction = null
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingImageAction?.invoke()
        } else {
            imageLoadError = "Photo access permission is required to pick images from your gallery."
        }
        pendingImageAction = null
    }

    fun requestCameraPermissionThen(action: () -> Unit) {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> action()
            else -> {
                pendingImageAction = action
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    fun requestGalleryPermissionThen(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED -> action()
                else -> {
                    pendingImageAction = action
                    galleryPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
        } else {
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED -> action()
                else -> {
                    pendingImageAction = action
                    galleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) openPlateAnalysis(bitmap)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadPlateAnalysisFromUri(uri)
    }
    val consumedCal = entries.sumOf { it.calories }
    val consumedPro = entries.sumOf { it.protein }
    val consumedCarb = entries.sumOf { it.carbs }
    val consumedFat = entries.sumOf { it.fat }
    val consumedFib = entries.sumOf { it.fiber }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearFoodSearch() }
    }

    BackHandler {
        page = when (page) {
            is MealSheetPage.FoodWeight, is MealSheetPage.CustomFood -> MealSheetPage.FoodList
            is MealSheetPage.PlateAnalysis, MealSheetPage.FoodList -> MealSheetPage.Detail
            else -> {
                onDismiss()
                return@BackHandler
            }
        }
    }

    if (imageLoadError != null) {
        AlertDialog(
            onDismissRequest = { imageLoadError = null },
            title = { Text("Image Error") },
            text = { Text(imageLoadError!!) },
            confirmButton = {
                TextButton(onClick = { imageLoadError = null }) { Text("OK") }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.6f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clickable(enabled = false) {}
                    .background(Background, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    MealSheetTopBar(
                        page = page,
                        mealType = mealType,
                        onBack = {
                            page = when (page) {
                                is MealSheetPage.FoodWeight -> MealSheetPage.FoodList
                                is MealSheetPage.CustomFood -> MealSheetPage.FoodList
                                is MealSheetPage.PlateAnalysis -> MealSheetPage.Detail
                                else -> MealSheetPage.Detail
                            }
                        },
                        onClose = onDismiss
                    )

                    AnimatedContent(
                        targetState = page,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        transitionSpec = {
                            slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300)) togetherWith
                                slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300))
                        },
                        label = "meal_page"
                    ) { currentPage ->
                        when (currentPage) {
                            MealSheetPage.Detail -> MealDetailPage(
                                budget = budget,
                                consumedCal = consumedCal,
                                consumedPro = consumedPro,
                                consumedCarb = consumedCarb,
                                consumedFat = consumedFat,
                                consumedFib = consumedFib,
                                entries = entries,
                                onAddClick = {
                                    viewModel.loadFoodSuggestions()
                                    page = MealSheetPage.FoodList
                                },
                                onCameraClick = {
                                    requestCameraPermissionThen { cameraLauncher.launch() }
                                },
                                onGalleryClick = {
                                    requestGalleryPermissionThen { galleryLauncher.launch("image/*") }
                                },
                                viewModel = viewModel
                            )
                            MealSheetPage.FoodList -> FoodSearchPage(
                                searchState = searchState,
                                customFoods = customFoods,
                                onQueryChange = viewModel::onFoodSearchQueryChanged,
                                onCreateCustom = {
                                    page = MealSheetPage.CustomFood(searchState.query.trim())
                                },
                                onFoodSelected = { page = MealSheetPage.FoodWeight(it) },
                                viewModel = viewModel
                            )
                            is MealSheetPage.CustomFood -> CustomFoodCreatePage(
                                viewModel = viewModel,
                                initialName = currentPage.prefilledName,
                                onSave = { item: CustomFoodItem ->
                                    viewModel.addCustomFoodAndReturn(item) { food ->
                                        page = MealSheetPage.FoodWeight(food)
                                    }
                                }
                            )
                            is MealSheetPage.FoodWeight -> FoodWeightPage(
                                food = currentPage.food,
                                onAdd = { grams: Int ->
                                    viewModel.addFoodToMeal(mealType, currentPage.food, grams, logDayStart)
                                    page = MealSheetPage.Detail
                                }
                            )
                            is MealSheetPage.PlateAnalysis -> PlateAnalysisPage(
                                bitmap = currentPage.bitmap,
                                viewModel = viewModel,
                                mealType = mealType,
                                logDayStart = logDayStart,
                                onCompleted = { page = MealSheetPage.Detail },
                                onCancel = { page = MealSheetPage.Detail }
                            )
                        }
                    }
                }
            }
            if (isLoadingImage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Loading image…", color = OnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun MealSheetTopBar(
    page: MealSheetPage,
    mealType: String,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (page !is MealSheetPage.Detail) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
        Text(
            when (page) {
                MealSheetPage.Detail -> mealType
                MealSheetPage.FoodList -> "Add Item"
                is MealSheetPage.CustomFood -> "Custom Item"
                is MealSheetPage.FoodWeight -> page.food.name
                is MealSheetPage.PlateAnalysis -> "AI Analysis"
            },
            style = Typography.headlineMedium,
            color = Primary,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, "Close", tint = OnSurface)
        }
    }
}

@Composable
private fun FoodSearchPage(
    searchState: com.example.FoodSearchUiState,
    customFoods: List<CustomFoodItem>,
    onQueryChange: (String) -> Unit,
    onCreateCustom: () -> Unit,
    onFoodSelected: (FoodItem) -> Unit,
    viewModel: GymViewModel
) {
    var foodToDelete by remember { mutableStateOf<CustomFoodItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchState.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Search foods… e.g. idli, sandwich, rice", color = OnSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Primary) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = OutlineVariant.copy(0.4f),
                focusedTextColor = OnSurface,
                unfocusedTextColor = OnSurface
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "700+ foods · Real-time search · Type to find anything",
            style = Typography.labelMedium,
            color = OnSurfaceVariant.copy(0.6f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (searchState.isLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }

        searchState.error?.let { err ->
            Text(err, color = Error, style = Typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "custom_create") {
                val query = searchState.query.trim()
                FoodListItem(
                    name = "Create Custom Item",
                    subtitle = if (query.isNotBlank()) {
                        "Add \"$query\" — name pre-filled, edit or use AI Autofill"
                    } else {
                        "Add your own food with per 100g nutrition"
                    },
                    icon = Icons.Default.Create,
                    highlight = true,
                    onClick = onCreateCustom
                )
            }

            if (customFoods.isNotEmpty()) {
                item(key = "custom_header") {
                    SectionHeader("YOUR CUSTOM ITEMS")
                }
                items(customFoods, key = { "custom_${it.id}" }) { cf ->
                    val food = FoodNutritionCalculator.fromCustomEntity(cf)
                    FoodListItem(
                        name = food.name,
                        subtitle = "Per 100g: ${food.caloriesPer100g} kcal · Custom",
                        icon = Icons.Default.Delete,
                        onClick = { onFoodSelected(food) },
                        onIconClick = { foodToDelete = cf }
                    )
                }
            }

            val localLabel = if (searchState.query.isBlank()) "SUGGESTED FOR YOU" else "SEARCH RESULTS"
            if (searchState.localResults.isNotEmpty()) {
                item(key = "local_header") {
                    SectionHeader(localLabel)
                }
                items(searchState.localResults, key = { it.id }) { food ->
                    FoodListItem(
                        name = food.name,
                        subtitle = "Per 100g: ${food.caloriesPer100g} kcal · P${food.proteinPer100g.toInt()}g C${food.carbsPer100g.toInt()}g F${food.fatPer100g.toInt()}g",
                        onClick = { onFoodSelected(food) }
                    )
                }
            }

            if (searchState.isApiLoading) {
                item(key = "api_loading") {
                    Column {
                        SectionHeader("PACKAGED PRODUCTS")
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Primary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Searching Open Food Facts…",
                                style = Typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (searchState.apiResults.isNotEmpty()) {
                item(key = "api_header") {
                    SectionHeader("PACKAGED PRODUCTS · Open Food Facts")
                }
                items(searchState.apiResults, key = { it.id }) { food ->
                    FoodListItem(
                        name = food.name,
                        subtitle = "Per 100g: ${food.caloriesPer100g} kcal · P${food.proteinPer100g.toInt()}g C${food.carbsPer100g.toInt()}g F${food.fatPer100g.toInt()}g",
                        onClick = { onFoodSelected(food) }
                    )
                }
            }
        }
    }

    if (foodToDelete != null) {
        AlertDialog(
            onDismissRequest = { foodToDelete = null },
            title = { Text("Delete Custom Food?") },
            text = { Text("Are you sure you want to remove '${foodToDelete?.name}' from your custom foods?") },
            confirmButton = {
                TextButton(onClick = { 
                    foodToDelete?.let { viewModel.deleteCustomFood(it) }
                    foodToDelete = null
                }) { Text("Delete", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { foodToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = Typography.labelMedium,
        color = OnSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFoodCreatePage(
    viewModel: GymViewModel,
    initialName: String = "",
    onSave: (CustomFoodItem) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    
    var isAiLoading by remember { mutableStateOf(false) }
    val profile by viewModel.userProfile.collectAsState()
    val scope = rememberCoroutineScope()
    var aiSuggestions by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }

    fun triggerAiAutofill() {
        if (name.isBlank() || !viewModel.isAiConfigured()) return
        isAiLoading = true
        scope.launch {
            val suggestions = viewModel.generateFoodSuggestions(name)
            if (suggestions.isNotEmpty()) {
                aiSuggestions = suggestions
                showSuggestions = true
            }
            isAiLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Enter nutrition per 100g", style = Typography.bodyMedium, color = OnSurfaceVariant)
            if (viewModel.isAiConfigured()) {
                TextButton(onClick = { triggerAiAutofill() }, enabled = !isAiLoading && name.isNotBlank()) {
                    if (isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Primary)
                    } else {
                        Text("✨ AI Autofill", color = Primary)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OnboardingTextField("Food Name", name, { name = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Text)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (showSuggestions) {
            Column(modifier = Modifier.fillMaxWidth().background(SurfaceContainerHighest, RoundedCornerShape(12.dp)).padding(8.dp)) {
                Text("AI Suggestions", style = Typography.labelMedium, color = Primary, modifier = Modifier.padding(bottom = 8.dp))
                aiSuggestions.take(4).forEach { suggestion ->
                    Text(
                        "${suggestion.name} (${suggestion.caloriesPer100g} kcal)",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                name = suggestion.name
                                calories = suggestion.caloriesPer100g.toString()
                                protein = suggestion.proteinPer100g.toString()
                                carbs = suggestion.carbsPer100g.toString()
                                fat = suggestion.fatPer100g.toString()
                                fiber = suggestion.fiberPer100g.toString()
                                showSuggestions = false
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        style = Typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OnboardingTextField("Calories", calories, { if (it.all { c -> c.isDigit() }) calories = it }, suffix = "kcal")
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingTextField("Protein", protein, { if (it.all { c -> c.isDigit() || c == '.' }) protein = it }, suffix = "g")
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingTextField("Carbs", carbs, { if (it.all { c -> c.isDigit() || c == '.' }) carbs = it }, suffix = "g")
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingTextField("Fat", fat, { if (it.all { c -> c.isDigit() || c == '.' }) fat = it }, suffix = "g")
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingTextField("Fiber", fiber, { if (it.all { c -> c.isDigit() || c == '.' }) fiber = it }, suffix = "g")
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                onSave(
                    CustomFoodItem(
                        name = name.trim(),
                        caloriesPer100g = calories.toIntOrNull() ?: 0,
                        proteinPer100g = protein.toFloatOrNull() ?: 0f,
                        carbsPer100g = carbs.toFloatOrNull() ?: 0f,
                        fatPer100g = fat.toFloatOrNull() ?: 0f,
                        fiberPer100g = fiber.toFloatOrNull() ?: 0f
                    )
                )
            },
            enabled = name.isNotBlank() && (calories.toIntOrNull() ?: 0) > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
        ) {
            Text("Save & Continue", style = Typography.headlineMedium.copy(fontSize = 18.sp))
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FoodListItem(
    name: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Add,
    highlight: Boolean = false,
    onClick: () -> Unit,
    onIconClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlight) Primary.copy(0.1f) else SurfaceContainerHighest.copy(0.3f),
                RoundedCornerShape(12.dp)
            )
            .border(1.dp, if (highlight) Primary.copy(0.3f) else OutlineVariant.copy(0.2f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = Typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = if (highlight) Primary else OnSurface)
            Text(subtitle, style = Typography.bodySmall, color = OnSurfaceVariant)
        }
        IconButton(onClick = { onIconClick?.invoke() ?: onClick() }) {
            Icon(icon, null, tint = Primary)
        }
    }
}

@Composable
private fun MealDetailPage(
    budget: MealTypes.MealBudget,
    consumedCal: Int,
    consumedPro: Int,
    consumedCarb: Int,
    consumedFat: Int,
    consumedFib: Int,
    entries: List<MealEntry>,
    onAddClick: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    viewModel: GymViewModel
) {
    var showCameraOptions by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<MealEntry?>(null) }
    var entryToEdit by remember { mutableStateOf<MealEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Max ${budget.calories} kcal for this meal", style = Typography.bodySmall, color = OnSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            MealProgressHeader(
                kcal = consumedCal,
                target = budget.calories,
                statusColor = if (consumedCal > budget.calories) Error else Secondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMacroBar("PRO", "${consumedPro}g", Primary, progressRatio(consumedPro, budget.protein), Modifier.weight(1f))
                MiniMacroBar("CAR", "${consumedCarb}g", Tertiary, progressRatio(consumedCarb, budget.carbs), Modifier.weight(1f))
                MiniMacroBar("FAT", "${consumedFat}g", Error, progressRatio(consumedFat, budget.fat), Modifier.weight(1f))
                MiniMacroBar("FIB", "${consumedFib}g", Secondary, progressRatio(consumedFib, budget.fiber), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("LOGGED ITEMS", style = Typography.labelMedium, color = OnSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No items yet. Tap Add Item below.", style = Typography.bodyMedium, color = OnSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        LoggedFoodRow(
                            entry = entry,
                            onDelete = { entryToDelete = entry },
                            onEdit = { entryToEdit = entry }
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 32.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddClick,
                modifier = Modifier.weight(0.8f).height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Item", style = Typography.headlineMedium.copy(fontSize = 18.sp))
            }

            Button(
                onClick = { showCameraOptions = true },
                modifier = Modifier.weight(0.2f).height(56.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh, contentColor = Primary)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
            }
        }
    }

    if (showCameraOptions) {
        val profile by viewModel.userProfile.collectAsState()
        val visionSlot = remember(profile) { AiRouteResolver.visionSlot(profile) }
        val aiReady = remember(profile, visionSlot) { viewModel.aiStatus().isReady }
        val visionReady = remember(profile, visionSlot) { viewModel.supportsVision() }
        when {
            !aiReady -> {
                AlertDialog(
                    onDismissRequest = { showCameraOptions = false },
                    title = { Text("AI Not Configured") },
                    text = { Text("Add an API key or download an offline model in AI Settings.") },
                    confirmButton = { TextButton(onClick = { showCameraOptions = false }) { Text("OK") } }
                )
            }
            !visionReady -> {
                AlertDialog(
                    onDismissRequest = { showCameraOptions = false },
                    title = { Text("Not Available") },
                    text = {
                        Text(
                            "Food photo analysis is not available with ${com.example.data.AiProviderConfig.displayNameFor(visionSlot.provider)}. " +
                                "Choose a vision-capable provider in AI Settings."
                        )
                    },
                    confirmButton = { TextButton(onClick = { showCameraOptions = false }) { Text("OK") } }
                )
            }
            else -> {
                AlertDialog(
                    onDismissRequest = { showCameraOptions = false },
                    title = { Text("AI Vision") },
                    text = {
                        Column {
                            ListItem(
                                headlineContent = { Text("Capture Image") },
                                leadingContent = { Icon(Icons.Default.CameraAlt, null) },
                                modifier = Modifier.clickable { onCameraClick(); showCameraOptions = false }
                            )
                            ListItem(
                                headlineContent = { Text("Add from Gallery") },
                                leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                                modifier = Modifier.clickable { onGalleryClick(); showCameraOptions = false }
                            )
                        }
                    },
                    confirmButton = {}
                )
            }
        }
    }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Remove Logged Item?") },
            text = { Text("Are you sure you want to remove '${entryToDelete?.foodName}'?") },
            confirmButton = {
                TextButton(onClick = { 
                    entryToDelete?.let { viewModel.deleteMeal(it) }
                    entryToDelete = null
                }) { Text("Remove", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (entryToEdit != null) {
        EditMealEntryDialog(
            entry = entryToEdit!!,
            onDismiss = { entryToEdit = null },
            onSave = { updated ->
                viewModel.updateMeal(updated)
                entryToEdit = null
            }
        )
    }
}

@Composable
private fun EditMealEntryDialog(entry: MealEntry, onDismiss: () -> Unit, onSave: (MealEntry) -> Unit) {
    var weight by remember(entry.id) { mutableStateOf(entry.weightGrams.toString()) }
    val weightInt = weight.toIntOrNull() ?: 0
    val preview = remember(entry, weightInt) {
        if (weightInt > 0) FoodNutritionCalculator.recalculateEntryForWeight(entry, weightInt) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Weight: ${entry.foodName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { v ->
                        if (v.all { it.isDigit() } && v.length <= 5) weight = v
                    },
                    label = { Text("Weight") },
                    suffix = { Text("g") },
                    singleLine = true
                )
                if (preview != null) {
                    Text(
                        "Calculated for ${preview.weightGrams}g",
                        style = Typography.labelMedium,
                        color = Primary
                    )
                    Text(
                        "${preview.calories} kcal · P${preview.protein}g · C${preview.carbs}g · F${preview.fat}g · Fiber ${preview.fiber}g",
                        style = Typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                } else if (weight.isNotEmpty() && weightInt <= 0) {
                    Text("Enter a valid weight in grams", color = Error, style = Typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { preview?.let(onSave) },
                enabled = preview != null
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PlateAnalysisPage(
    bitmap: Bitmap,
    viewModel: GymViewModel,
    mealType: String,
    logDayStart: Long,
    onCompleted: () -> Unit,
    onCancel: () -> Unit
) {
    var detectedItems by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(true) }
    val profile by viewModel.userProfile.collectAsState()
    val visionSlot = remember(profile) { AiRouteResolver.visionSlot(profile) }
    val aiStatus = remember(profile, visionSlot) { viewModel.aiStatus() }

    LaunchedEffect(bitmap, visionSlot.provider, visionSlot.modelId, profile.offlineModelId) {
        isAnalyzing = true
        analysisError = null
        when (val result = viewModel.analyzeFoodImage(bitmap)) {
            is com.example.data.AiAnalysisResult.Success -> {
                detectedItems = result.items
                analysisError = null
            }
            is com.example.data.AiAnalysisResult.Error -> {
                detectedItems = emptyList()
                analysisError = result.message
            }
        }
        isAnalyzing = false
    }

    if (isAnalyzing) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Primary)
                Spacer(Modifier.height(16.dp))
                Text(
                    when (visionSlot.provider) {
                        "offline" -> "Running on-device AI…"
                        "groq" -> "Analyzing with Groq…"
                        else -> "Analyzing with Gemini…"
                    },
                    color = OnSurfaceVariant
                )
            }
        }
    } else if (analysisError != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("Analysis failed", style = Typography.headlineSmall, color = Error)
                Spacer(Modifier.height(8.dp))
                Text(analysisError!!, color = OnSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("${aiStatus.label}: ${aiStatus.detail}", style = Typography.bodySmall, color = OnSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onCancel) { Text("Go Back") }
            }
        }
    } else if (detectedItems.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("No items detected.", style = Typography.headlineSmall, color = OnSurface)
                Spacer(Modifier.height(8.dp))
                Text("AI couldn't clearly identify food in this image.", color = OnSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onCancel) { Text("Retake Image") }
            }
        }
    } else {
        var finalItems by remember { mutableStateOf(detectedItems.map { it to "100" }) }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("AI Detected ${finalItems.size} items", style = Typography.headlineSmall, color = Primary)
            Spacer(Modifier.height(16.dp))
            
            finalItems.forEachIndexed { index, (food, weight) ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest.copy(0.3f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(food.name, style = Typography.titleMedium, color = OnSurface)
                            IconButton(onClick = { finalItems = finalItems.filterIndexed { i, _ -> i != index } }) {
                                Icon(Icons.Default.Close, null, tint = Error)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = weight,
                                onValueChange = { v ->
                                    if (v.all { it.isDigit() }) {
                                        finalItems = finalItems.toMutableList().apply { this[index] = food to v }
                                    }
                                },
                                label = { Text("Weight") },
                                suffix = { Text("g") },
                                modifier = Modifier.width(120.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            val w = weight.toIntOrNull() ?: 0
                            val n = FoodNutritionCalculator.nutritionForWeight(food, w)
                            Text("${n.calories} kcal · P${n.protein}g C${n.carbs}g F${n.fat}g", style = Typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        finalItems.forEach { (food, weight) ->
                            viewModel.addFoodToMeal(mealType, food, weight.toIntOrNull() ?: 100, logDayStart)
                        }
                        onCompleted()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Add All Items") }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FoodWeightPage(food: FoodItem, onAdd: (Int) -> Unit) {
    var grams by remember { mutableStateOf("100") }
    val gramsInt = grams.toIntOrNull() ?: 0
    val preview = if (gramsInt > 0) FoodNutritionCalculator.nutritionForWeight(food, gramsInt) else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text("Nutrition per 100g", style = Typography.labelMedium, color = OnSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                "${food.caloriesPer100g} kcal · Protein ${food.proteinPer100g}g · Carbs ${food.carbsPer100g}g · Fat ${food.fatPer100g}g · Fiber ${food.fiberPer100g}g",
                style = Typography.bodyMedium,
                color = OnSurface
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        OnboardingTextField(
            label = "Weight",
            value = grams,
            onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) grams = v },
            suffix = "g"
        )
        preview?.let { n ->
            Spacer(modifier = Modifier.height(24.dp))
            Text("CALCULATED FOR ${gramsInt}g", style = Typography.labelMedium, color = Primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutrientChip("${n.calories} kcal", Primary)
                NutrientChip("P ${n.protein}g", Secondary)
                NutrientChip("C ${n.carbs}g", Tertiary)
                NutrientChip("F ${n.fat}g", Error)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { if (gramsInt > 0) onAdd(gramsInt) },
            enabled = gramsInt > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
        ) {
            Text("Add to Meal", style = Typography.headlineMedium.copy(fontSize = 18.sp))
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun NutrientChip(text: String, color: Color) {
    Text(
        text,
        style = Typography.labelMedium,
        color = color,
        modifier = Modifier
            .background(color.copy(0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun LoggedFoodRow(entry: MealEntry, onDelete: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(10.dp))
            .clickable(onClick = onEdit)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.foodName, style = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
            Text("${entry.weightGrams}g", style = Typography.bodySmall, color = OnSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${entry.calories} kcal", style = Typography.bodyMedium, color = Secondary)
                Text("P${entry.protein} C${entry.carbs} F${entry.fat}", style = Typography.labelMedium, color = OnSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = OnSurfaceVariant.copy(0.4f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MealProgressHeader(kcal: Int, target: Int, statusColor: Color) {
    val progressVal = if (target > 0) (kcal.toFloat() / target).coerceIn(0f, 1f) else 0f
    val status = when {
        kcal == 0 -> "Empty"
        kcal > target -> "Over target"
        else -> "Within limit"
    }
    Column {
        Text("$kcal / $target kcal", style = Typography.headlineMedium, color = OnSurface)
        Text(status, style = Typography.labelMedium, color = statusColor)
    }
    Spacer(modifier = Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(8.dp).background(OutlineVariant.copy(0.3f), RoundedCornerShape(50))) {
        Box(Modifier.fillMaxWidth(progressVal).fillMaxHeight().background(statusColor, RoundedCornerShape(50)))
    }
}

private fun progressRatio(consumed: Int, target: Int): Float =
    if (target > 0) (consumed.toFloat() / target).coerceIn(0f, 1f) else 0f
