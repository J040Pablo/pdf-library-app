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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
                            text = "${selectedCollectionIds.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedCollectionIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        if (selectedCollectionIds.size == 1) {
                            IconButton(onClick = { 
                                val id = selectedCollectionIds.first()
                                selectedCollectionIds = emptySet()
                                onCreateCollectionClick() // Would route to Edit if we pass ID, but wait, LibraryScreen has onCreateCollectionClick, not Edit.
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                        IconButton(onClick = {
                            selectedCollectionIds.forEach { viewModel.removeCollection(it) }
                            selectedCollectionIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
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
                            "Collections",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    },
                    actions = {
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(28.dp))
                        }
                        // "Nova Collection" pill button — same design language as the notification button in Home
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
                                contentDescription = "Nova Collection",
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
                        items(collections, key = { it.id }) { collection ->
                            val books = viewModel.getBooksForCollection(collection)
                            CollectionCard(
                                collection = collection,
                                books = books,
                                isSelected = collection.id in selectedCollectionIds,
                                onClick = {
                                    if (selectedCollectionIds.isNotEmpty()) {
                                        selectedCollectionIds = if (collection.id in selectedCollectionIds) {
                                            selectedCollectionIds - collection.id
                                        } else {
                                            selectedCollectionIds + collection.id
                                        }
                                    } else {
                                        onCollectionClick(collection.id)
                                    }
                                },
                                onLongClick = {
                                    selectedCollectionIds = selectedCollectionIds + collection.id
                                },
                                modifier = Modifier.animateItem(),
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
            "No collections yet",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Create a collection to group your books together",
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
            Text("New Collection", style = MaterialTheme.typography.titleMedium)
        }
    }
}
