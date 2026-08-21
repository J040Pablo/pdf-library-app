package com.example.library.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.combinedClickable
import coil.compose.AsyncImage
import com.example.library.R
import com.example.library.model.Book
import com.example.library.ui.theme.*
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentBookCard(
    book: Book,
    onClick: (() -> Unit)? = null,
    onBookmarkClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
) {
    val selectedBorder = if (isSelected)
        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else
        androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick, onLongClick = onLongClick)
    } else Modifier

    Card(
        modifier = modifier
            .width(160.dp)
            .then(clickModifier),
        shape = RoundedCornerShape(Dimens.CornerCardLarge),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = selectedBorder
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(Spacing.Small)
                    .then(coverModifier)
                    .clip(RoundedCornerShape(Dimens.CornerCoverInner))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (book.coverUrl != null) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                    )
                }

                // Bookmark icon — hidden while in selection mode to avoid gesture conflict
                if (!isSelected) {
                    IconButton(
                        onClick = onBookmarkClick,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = if (book.isBookmarked) Icons.Default.Bookmark
                                          else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(Dimens.IconMedium)
                        )
                    }
                }

                // Selection overlay — drawn last so it sits on top of the cover
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            WavyProgressBar(
                progress = book.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 2.dp)
                    .height(12.dp)
            )

            Column(
                modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.SMedium)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopRatedBookCard(
    book: Book,
    onClick: (() -> Unit)? = null,
    onBookmarkClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier
) {
    val selectedBorder = if (isSelected)
        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else
        androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick, onLongClick = onLongClick)
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        shape = RoundedCornerShape(Dimens.CornerCardLarge),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = selectedBorder
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(Spacing.Small)
                    .then(coverModifier)
                    .clip(RoundedCornerShape(Dimens.CornerCoverInner))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                if (book.coverUrl != null) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.IconXLarge),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                        )
                    }
                }

                // Bookmark icon — hidden while in selection mode to avoid gesture conflict
                if (!isSelected) {
                    IconButton(
                        onClick = onBookmarkClick,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = if (book.isBookmarked) Icons.Default.Bookmark
                                          else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(Dimens.IconMedium)
                        )
                    }
                }

                // Selection overlay
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            WavyProgressBar(
                progress = book.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 2.dp)
                    .height(12.dp)
            )

            Column(
                modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.SMedium)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun WavyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    // Resolve adaptive wave colours from MaterialTheme
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.primaryContainer

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val waveAmplitude = 5f
        val waveFrequency = 0.12f

        val activePath = Path()
        val inactivePath = Path()

        activePath.moveTo(0f, centerY)
        inactivePath.moveTo(0f, centerY)

        for (x in 0..width.toInt()) {
            val y = centerY + (waveAmplitude * sin(x.toFloat() * waveFrequency))
            if (x <= width * progress) {
                activePath.lineTo(x.toFloat(), y)
            } else {
                if (x.toFloat() == (width * progress).toInt().toFloat() + 1f || x == 0) {
                    inactivePath.moveTo(x.toFloat(), y)
                }
                inactivePath.lineTo(x.toFloat(), y)
            }
        }

        drawPath(
            path = activePath,
            color = activeColor,
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )

        drawPath(
            path = inactivePath,
            color = inactiveColor,
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun LibraryBookItem(
    book: Book,
    onClick: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else
        MaterialTheme.colorScheme.surface

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick, onLongClick = onLongClick)
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
            .then(clickModifier)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(Dimens.CornerCard)
                ) else Modifier
            ),
        shape = RoundedCornerShape(Dimens.CornerCard),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.SMedium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp, 100.dp)
                    .then(coverModifier)
                    .clip(RoundedCornerShape(Dimens.CornerCard))
            ) {
                if (book.coverUrl != null) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    BookCoverPlaceholder(
                        title = book.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(Spacing.SMedium))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.percent_complete,
                                (book.progress * 100).toInt()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.pages_abbrev, book.pageCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.XSmall))

                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                }

                book.lastReadDate?.let {
                    Text(
                        text = stringResource(R.string.last_read, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = Spacing.Small)
                    )
                }
            }
        }
    }
}

@Composable
fun BookCoverPlaceholder(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(Dimens.IconMedium)
            )
            Spacer(modifier = Modifier.height(Spacing.XSmall))
            Text(
                text = title.take(2).uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// Long-click support modifier extension
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.clickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = this.then(
    Modifier.combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick
    )
)

@Preview(showBackground = true)
@Composable
fun RecentBookCardPreview() {
    LibraryTheme {
        RecentBookCard(
            book = Book("1", "Clean Code", "Robert C. Martin", progress = 0.45f),
            onClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RecentBookCardDarkPreview() {
    LibraryTheme(darkTheme = true) {
        RecentBookCard(
            book = Book("1", "Clean Code", "Robert C. Martin", progress = 0.45f),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TopRatedBookCardPreview() {
    LibraryTheme {
        TopRatedBookCard(
            book = Book("2", "The Pragmatic Programmer", "Andy Hunt", isBookmarked = true),
            onClick = {},
            onBookmarkClick = {}
        )
    }
}
