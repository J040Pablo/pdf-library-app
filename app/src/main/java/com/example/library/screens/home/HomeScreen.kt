package com.example.library.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.library.ui.components.RecentBookCard
import com.example.library.ui.components.TopRatedBookCard
import com.example.library.ui.theme.*
import com.example.library.viewmodel.BookViewModel
import kotlin.math.cos
import kotlin.math.sin

// Custom 8-pointed "Sunny" shape for notifications (Figma style)
val SunnyShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width / 2f
            val minRadius = maxRadius * 0.85f
            val avgRadius = (maxRadius + minRadius) / 2f
            val amplitude = (maxRadius - minRadius) / 2f
            val numPoints = 8
            val numSegments = 80

            for (i in 0..numSegments) {
                val angle = (i.toFloat() / numSegments) * 2f * Math.PI.toFloat()
                val r = avgRadius + amplitude * kotlin.math.cos(numPoints * angle)
                val x = center.x + r * kotlin.math.cos(angle - (Math.PI / 2).toFloat())
                val y = center.y + r * kotlin.math.sin(angle - (Math.PI / 2).toFloat())
                
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    viewModel: BookViewModel = viewModel()
) {
    val recentBooks by viewModel.recentBooks.collectAsState()
    val topRatedBooks by viewModel.topRatedBooks.collectAsState()
    
    var showNotifications by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    // Mocking notification count
    val notificationCount = 0 

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "App",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextTitle
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    navigationIconContentColor = TextTitle,
                    actionIconContentColor = TextTitle,
                    titleContentColor = TextTitle
                ),
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(
                            imageVector = Icons.Default.Menu, 
                            contentDescription = "Menu"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search, 
                            contentDescription = "Search"
                        )
                    }
                    
                    // Notification Button with Sunny Shape
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(50.dp)
                            .clip(SunnyShape)
                            .background(WavyActive) // Using the Figma purple
                            .clickable { showNotifications = true },
                        contentAlignment = Alignment.Center
                    ) {
                        // Bell Icon
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                        
                        // Red dot badge in the top right corner
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Red, androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    }
                }
            )
        }
    ) { screenPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = screenPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp, 
                end = 16.dp, 
                bottom = paddingValues.calculateBottomPadding() + 32.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Recents
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Recents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    color = TextTitle
                )
            }
            
            // Recents LazyRow
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(end = 64.dp) // Ensures ~2.2 books visibility
                ) {
                    items(recentBooks) { book ->
                        RecentBookCard(
                            book = book,
                            onClick = { /* TODO */ }
                        )
                    }
                }
            }
            
            // Header: Top Rated (Matching Figma)
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Top Rated",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    color = TextTitle
                )
            }
            
            // Grid of all books (Fixed 2 columns)
            items(topRatedBooks) { book ->
                TopRatedBookCard(
                    book = book,
                    onClick = { /* TODO */ },
                    onBookmarkClick = { /* TODO */ }
                )
            }
        }
    }

    if (showNotifications) {
        ModalBottomSheet(
            onDismissRequest = { showNotifications = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Nenhuma notificação.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    com.example.library.ui.theme.LibraryTheme {
        HomeScreen(onSearchClick = {})
    }
}
