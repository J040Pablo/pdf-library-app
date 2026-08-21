package com.example.library.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.library.R
import com.example.library.model.Collection
import com.example.library.ui.components.CollectionCard
import com.example.library.viewmodel.CollectionViewModel

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.example.library.ui.theme.Dimens
import com.example.library.ui.theme.Spacing
import com.example.library.screens.home.SunnyShape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.example.library.screens.home.DragState
import com.example.library.ui.components.reorderableItemGesture
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LibraryScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSearchClick: () -> Unit,
    onBookClick: (String, String) -> Unit = { _, _ -> },
    onCollectionClick: (String) -> Unit = {},
    onCreateCollectionClick: () -> Unit = {},
    paddingValues: PaddingValues = PaddingValues(0.dp),
    viewModel: CollectionViewModel = viewModel()
) {
    val collections by viewModel.collections.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()

    var localCollections by remember(collections) { mutableStateOf(collections) }
    var collectionDragState by remember { mutableStateOf<DragState?>(null) }
    var dragReadyCollectionId by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    var selectedCollectionIds by remember { mutableStateOf(setOf<String>()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        // Hook for PDF import — delegates to BookRepository in a real impl
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            if (selectedCollectionIds.isNotEmpty()) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.selected_count, selectedCollectionIds.size),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedCollectionIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        if (selectedCollectionIds.size == 1) {
                            IconButton(onClick = { 
                                val id = selectedCollectionIds.first()
                                selectedCollectionIds = emptySet()
                                onCreateCollectionClick() // Route to Edit if needed
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                            }
                        }
                        IconButton(onClick = {
                            selectedCollectionIds.forEach { viewModel.removeCollection(it) }
                            selectedCollectionIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    windowInsets = WindowInsets.statusBars,
                    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.collections),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    },
                    actions = {
                        IconButton(onClick = onSearchClick) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(end = Spacing.Medium)
                                .size(Dimens.NotificationButtonSize)
                                .clip(SunnyShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onCreateCollectionClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.new_collection),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(Dimens.NotificationIconSize)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    windowInsets = WindowInsets.statusBars,
                    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                )
            }
        },
    ) { screenPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = screenPadding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AnimatedContent(
                targetState = collections.isEmpty(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "LibraryContent"
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyCollectionsView(onCreateClick = onCreateCollectionClick)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = paddingValues.calculateBottomPadding() + 80.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localCollections, key = { it.id }) { collection ->
                            val books = viewModel.getBooksForCollection(collection)
                            val isSelected = collection.id in selectedCollectionIds
                            val isDragging = collectionDragState?.itemId == collection.id
                            val isDragReady = dragReadyCollectionId == collection.id

                            CollectionCard(
                                collection = collection,
                                books = books,
                                isSelected = isSelected,
                                onClick = null,
                                modifier = Modifier
                                    .then(if (isDragging) Modifier else Modifier.animateItem())
                                    .zIndex(if (isDragging) 100f else if (isDragReady) 10f else 0f)
                                    .reorderableItemGesture(
                                        itemId = collection.id,
                                        isSelectedModeActive = selectedCollectionIds.isNotEmpty(),
                                        onTap = {
                                            if (selectedCollectionIds.isNotEmpty()) {
                                                selectedCollectionIds = if (isSelected) selectedCollectionIds - collection.id else selectedCollectionIds + collection.id
                                            } else {
                                                onCollectionClick(collection.id)
                                            }
                                        },
                                        onLongPress = {
                                            selectedCollectionIds = selectedCollectionIds + collection.id
                                        },
                                        onDragReady = { id: String ->
                                            dragReadyCollectionId = if (id.isNotEmpty()) id else null
                                        },
                                        onDragStart = { id: String ->
                                            val initIdx = localCollections.indexOfFirst { it.id == id }
                                            if (initIdx != -1) {
                                                collectionDragState = DragState(
                                                    itemId = id,
                                                    initialIndex = initIdx,
                                                    currentIndex = initIdx,
                                                    pointerOffset = Offset.Zero
                                                )
                                            }
                                            dragReadyCollectionId = null
                                        },
                                        onDrag = { dragAmount: Offset ->
                                            val state = collectionDragState ?: return@reorderableItemGesture
                                            val newPointerOffset = state.pointerOffset + dragAmount
                                            val itemStepY = with(density) { (100.dp + 8.dp).toPx() }

                                            val indexDelta = (newPointerOffset.y / itemStepY).roundToInt()
                                            val targetIndex = (state.initialIndex + indexDelta).coerceIn(0, localCollections.lastIndex)

                                            if (targetIndex != state.currentIndex) {
                                                val updated = localCollections.toMutableList()
                                                val movedItem = updated.removeAt(state.currentIndex)
                                                updated.add(targetIndex, movedItem)
                                                localCollections = updated

                                                collectionDragState = state.copy(
                                                    currentIndex = targetIndex,
                                                    pointerOffset = newPointerOffset
                                                )
                                            } else {
                                                collectionDragState = state.copy(pointerOffset = newPointerOffset)
                                            }
                                        },
                                        onDragEnd = {
                                            collectionDragState = null
                                            dragReadyCollectionId = null
                                            viewModel.updateCollectionOrder(localCollections)
                                        }
                                    )
                                    .graphicsLayer {
                                        if (isDragging && collectionDragState != null) {
                                            val state = collectionDragState!!
                                            val itemStepY = (100.dp + 8.dp).toPx()
                                            val slotShiftY = (state.currentIndex - state.initialIndex) * itemStepY

                                            translationX = state.pointerOffset.x
                                            translationY = state.pointerOffset.y - slotShiftY
                                            shadowElevation = 8.dp.toPx()
                                            scaleX = 1.04f
                                            scaleY = 1.04f
                                        } else if (isDragReady) {
                                            shadowElevation = 4.dp.toPx()
                                            scaleX = 1.02f
                                            scaleY = 1.02f
                                        }
                                    },
                                coverModifier = Modifier.sharedElement(
                                    rememberSharedContentState(key = "collection-cover-${collection.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ -> tween(durationMillis = 400) },
                                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(Dimens.CornerCoverInner))
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyCollectionsView(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            stringResource(R.string.no_collections_yet),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.no_collections_yet_subtitle),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCreateClick,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.new_collection), style = MaterialTheme.typography.titleMedium)
        }
    }
}
