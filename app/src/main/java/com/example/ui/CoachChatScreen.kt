@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.CoachChatMessage
import com.example.GymViewModel
import com.example.data.CoachHistorySession
import com.example.ui.theme.*

private enum class CoachNav { Chat, HistoryList, HistoryDetail }

@Composable
fun CoachChatScreen(
    viewModel: GymViewModel,
    onOpenSettings: () -> Unit
) {
    val messages by viewModel.coachChatMessages.collectAsState()
    val loading by viewModel.coachChatLoading.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val secretsRevision by viewModel.secretsRevision.collectAsState()
    val modelsRevision by viewModel.modelsRevision.collectAsState()
    val aiConfigRevision by viewModel.aiConfigRevision.collectAsState()
    val aiConfigured = remember(
        profile,
        secretsRevision,
        modelsRevision,
        aiConfigRevision
    ) { viewModel.isAiConfigured() }
    var inputText by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val sets by viewModel.allSets.collectAsState()
    val meals by viewModel.allMeals.collectAsState()
    val chips by viewModel.coachPromptChips.collectAsState()
    val historySessions by viewModel.coachHistorySessions.collectAsState()
    val pendingExercise by viewModel.pendingExerciseSteps.collectAsState()

    var coachNav by remember { mutableStateOf(CoachNav.Chat) }
    var selectedHistoryId by remember { mutableStateOf<String?>(null) }
    var historyDetail by remember { mutableStateOf<CoachHistorySession?>(null) }

    LaunchedEffect(Unit) {
        viewModel.ensureCoachWelcomeMessage()
    }
    LaunchedEffect(profile, sets, meals, aiConfigured, aiConfigRevision) {
        viewModel.refreshCoachPromptChips()
    }
    LaunchedEffect(pendingExercise) {
        pendingExercise?.let { name ->
            coachNav = CoachNav.Chat
            viewModel.sendExerciseStepsFromAi(name)
            viewModel.clearPendingExerciseSteps()
        }
    }
    LaunchedEffect(selectedHistoryId) {
        historyDetail = selectedHistoryId?.let { viewModel.loadCoachHistorySession(it) }
    }

    when (coachNav) {
        CoachNav.HistoryList -> CoachHistoryListScreen(
            sessions = historySessions,
            onBack = { coachNav = CoachNav.Chat },
            onOpenSession = { id ->
                selectedHistoryId = id
                coachNav = CoachNav.HistoryDetail
            }
        )
        CoachNav.HistoryDetail -> CoachHistoryDetailScreen(
            session = historyDetail,
            onBack = {
                selectedHistoryId = null
                historyDetail = null
                coachNav = CoachNav.HistoryList
            }
        )
        CoachNav.Chat -> CoachChatMainContent(
            messages = messages,
            loading = loading,
            aiConfigured = aiConfigured,
            chips = chips,
            inputText = inputText,
            onInputChange = { inputText = it },
            listState = listState,
            onOpenSettings = onOpenSettings,
            onOpenHistory = {
                viewModel.refreshCoachHistory()
                coachNav = CoachNav.HistoryList
            },
            onSend = { text ->
                viewModel.sendCoachMessage(text)
                inputText = ""
            }
        )
    }
}

@Composable
private fun CoachChatMainContent(
    messages: List<CoachChatMessage>,
    loading: Boolean,
    aiConfigured: Boolean,
    chips: List<com.example.CoachPromptChip>,
    inputText: String,
    onInputChange: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onSend: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex + if (loading) 1 else 0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        CoachChatTopBar(onOpenSettings = onOpenSettings, onOpenHistory = onOpenHistory)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (if (chips.isNotEmpty()) chips else FALLBACK_CHIPS).forEach { chip ->
                SuggestionChip(
                    label = chip.label,
                    onClick = { onSend(chip.prompt) }
                )
            }
        }

        if (!aiConfigured) {
            Text(
                "Configure AI in Settings for full Coach responses.",
                style = Typography.labelSmall,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }
            if (loading) {
                item(key = "typing") {
                    TypingIndicator()
                }
            }
        }

        CoachChatInputBar(
            value = inputText,
            onValueChange = onInputChange,
            enabled = !loading,
            onSend = {
                if (inputText.isNotBlank()) onSend(inputText)
            },
            onMicClick = {
                Toast.makeText(context, "Voice input coming soon", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        )
    }
}

@Composable
private fun CoachHistoryListScreen(
    sessions: List<CoachHistorySession>,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopAppBar(
            title = { Text("Chat History", color = Primary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to chat", tint = OnSurface)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
        )
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No past conversations yet.", color = OnSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    Card(
                        onClick = { onOpenSession(session.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                session.preview,
                                style = Typography.titleSmall,
                                color = OnSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                session.formattedTimestamp(),
                                style = Typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachHistoryDetailScreen(
    session: CoachHistorySession?,
    onBack: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("Past conversation", color = Primary, style = Typography.titleMedium)
                    session?.let {
                        Text(
                            it.formattedTimestamp(),
                            style = Typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to history", tint = OnSurface)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
        )
        Text(
            "Read-only — continue chatting from the main Coach screen.",
            style = Typography.labelSmall,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            val historyMessages = session?.messages.orEmpty()
            items(historyMessages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }
        }
    }
}

@Composable
private fun CoachChatTopBar(onOpenSettings: () -> Unit, onOpenHistory: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        coil.compose.AsyncImage(
            model = PROFILE_IMAGE,
            contentDescription = "Profile",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceContainerHighest)
        )
        Text(
            "Coach AI",
            style = Typography.titleLarge,
            color = Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = onOpenHistory) {
            Icon(Icons.Default.History, "History", tint = Primary)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, "Settings", tint = OnSurface)
        }
    }
    HorizontalDivider(color = OutlineVariant.copy(0.25f))
}

private val FALLBACK_CHIPS = listOf(
    com.example.CoachPromptChip("Leg Day Tips", "What should I focus on for leg day based on my recent training?"),
    com.example.CoachPromptChip("Protein Intake", "Am I hitting my protein target today? What should I eat?")
)

private const val PROFILE_IMAGE =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuDIOsswt2MZPJrY9NxjNjEGkusn6t4irxXiZNd7S9g1R8FKobj-FCLjxCO9GWjsVMoEE7BQnFJ7P9hKj6cs9WYYHH9ycIIPKS_2hQo1KfCGEK6LzTpTfXcKWs4DjdllMY50yP29c3ECZ210w6NIr0RYfFer8kUSJjoH6rkvnf6GSHb2ctOqoANY1ZlXPzHY-SGBGE9IFG4l48QPhMKJ2qHxCa3w8TYm8qYJbwlCbGXIA2Z5JYNP-CbHNey9KIxVp2iEa38YsAJN2Og2"

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.35f))
    ) {
        Text(
            label,
            style = Typography.labelMedium,
            color = OnSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ChatBubble(message: CoachChatMessage) {
    val isUser = message.role == com.example.CoachChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            CoachAvatar(modifier = Modifier.padding(end = 8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) SurfaceContainer else SurfaceContainerHigh
            ) {
                CoachMessageText(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        if (isUser) {
            coil.compose.AsyncImage(
                model = PROFILE_IMAGE,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(SurfaceContainerHighest)
            )
        }
    }
}

@Composable
private fun CoachAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Primary.copy(0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Psychology,
            contentDescription = "Coach AI",
            tint = Primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoachAvatar(modifier = Modifier.padding(end = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceContainerHigh) {
            Text(
                "…",
                style = Typography.bodyLarge,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun CoachMessageText(text: String, modifier: Modifier = Modifier) {
    val lines = text.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            if (line.isBlank()) {
                Spacer(Modifier.height(4.dp))
            } else {
                val display = line.trimStart().removePrefix("- ").removePrefix("• ")
                val isBullet = line.trimStart().startsWith("-") || line.trimStart().startsWith("•")
                Row {
                    if (isBullet) {
                        Text("• ", style = Typography.bodyMedium, color = OnSurface)
                    }
                    Text(
                        text = parseCoachMarkdown(display),
                        style = Typography.bodyMedium,
                        color = OnSurface
                    )
                }
            }
        }
    }
}

private fun parseCoachMarkdown(text: String) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val boldStart = text.indexOf("**", i)
        if (boldStart == -1) {
            append(text.substring(i))
            break
        }
        if (boldStart > i) append(text.substring(i, boldStart))
        val boldEnd = text.indexOf("**", boldStart + 2)
        if (boldEnd == -1) {
            append(text.substring(boldStart))
            break
        }
        withStyle(androidx.compose.ui.text.SpanStyle(color = Primary, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(boldStart + 2, boldEnd))
        }
        i = boldEnd + 2
    }
}

@Composable
private fun CoachChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceContainer,
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(0.15f))
    ) {
        Column {
            HorizontalDivider(color = OutlineVariant.copy(0.2f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHigh)
                ) {
                    Icon(Icons.Default.Mic, "Voice", tint = Primary)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceContainerLow)
                        .border(1.dp, OutlineVariant.copy(0.3f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        textStyle = Typography.bodyMedium.copy(color = OnSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                        modifier = Modifier.fillMaxWidth().padding(end = 40.dp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth()) {
                                if (value.isEmpty()) {
                                    Text(
                                        "Ask Coach AI...",
                                        style = Typography.bodyMedium,
                                        color = OnSurfaceVariant.copy(0.5f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    
                    if (value.isNotBlank()) {
                        IconButton(
                            onClick = onSend,
                            enabled = enabled,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(36.dp)
                                .background(Primary, CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "Send",
                                tint = OnPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            // Add a small spacer for the bottom safe area when keyboard is NOT present
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
        }
    }
}
