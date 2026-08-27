package com.example.library.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.library.R
import com.example.library.data.BookRepository
import com.example.library.data.CollectionRepository
import com.example.library.model.Book
import com.example.library.screens.bookdetail.BookDetailScreen
import com.example.library.screens.bookpicker.BookPickerMode
import com.example.library.screens.bookpicker.BookPickerScreen
import com.example.library.screens.collectiondetail.CollectionDetailScreen
import com.example.library.screens.createcollection.CreateCollectionScreen
import com.example.library.screens.editbook.EditBookScreen
import com.example.library.screens.home.HomeScreen
import com.example.library.screens.library.LibraryScreen
import com.example.library.screens.profile.ProfileScreen
import com.example.library.screens.reading.ReadingScreen
import com.example.library.screens.search.SearchScreen
import com.example.library.screens.upload.UploadScreen
import com.example.library.viewmodel.ThemeViewModel

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues,
    themeViewModel: ThemeViewModel,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    onFullScreenExpansionChanged: (Float) -> Unit = {}
) {
    // Direct fade between bottom tabs — no directional slide that can feel like
    // hopping through intermediate destinations on multi-tab jumps.
    val tabFadeIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        val initialRoute = initialState.destination.route?.substringBefore("/")?.substringBefore("?")
        val targetRoute = targetState.destination.route?.substringBefore("/")?.substringBefore("?")
        val routes = listOf(Screen.Home.route, Screen.Upload.route, Screen.Library.route, Screen.Profile.route)
        if (initialRoute in routes && targetRoute in routes) {
            fadeIn(animationSpec = tween(180))
        } else null
    }

    val tabFadeOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        val initialRoute = initialState.destination.route?.substringBefore("/")?.substringBefore("?")
        val targetRoute = targetState.destination.route?.substringBefore("/")?.substringBefore("?")
        val routes = listOf(Screen.Home.route, Screen.Upload.route, Screen.Library.route, Screen.Profile.route)
        if (initialRoute in routes && targetRoute in routes) {
            fadeOut(animationSpec = tween(140))
        } else null
    }

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
        ) {
            composable(
                route = Screen.Home.route,
                enterTransition = tabFadeIn,
                exitTransition = tabFadeOut,
                popEnterTransition = tabFadeIn,
                popExitTransition = tabFadeOut
            ) {
                HomeScreen(
                    animatedVisibilityScope = this@composable,
                    drawerState = drawerState,
                    paddingValues = paddingValues,
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onBookClick = { bookId, origin ->
                        navController.navigate(Screen.BookDetail.createRoute(bookId, origin))
                    },
                    onEditBookClick = { bookId ->
                        navController.navigate(Screen.EditBook.createRoute(bookId))
                    },
                    onBooksClick = {
                        // Future redirection to external book-links catalog/website
                    },
                    onNavigateToLibrary = {
                        navController.navigate(Screen.Library.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Profile.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    animatedVisibilityScope = this@composable,
                    paddingValues = paddingValues,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onBookClick = { bookId, origin ->
                        navController.navigate(Screen.BookDetail.createRoute(bookId, origin))
                    }
                )
            }
            composable(
                route = Screen.Upload.route,
                enterTransition = tabFadeIn,
                exitTransition = tabFadeOut,
                popEnterTransition = tabFadeIn,
                popExitTransition = tabFadeOut
            ) {
                UploadScreen(paddingValues = paddingValues)
            }
            composable(
                route = Screen.Library.route,
                enterTransition = tabFadeIn,
                exitTransition = tabFadeOut,
                popEnterTransition = tabFadeIn,
                popExitTransition = tabFadeOut
            ) {
                LibraryScreen(
                    animatedVisibilityScope = this@composable,
                    paddingValues = paddingValues,
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onBookClick = { bookId, origin ->
                        navController.navigate(Screen.BookDetail.createRoute(bookId, origin))
                    },
                    onCollectionClick = { collectionId ->
                        navController.navigate(Screen.CollectionDetail.createRoute(collectionId))
                    },
                    onCreateCollectionClick = {
                        navController.navigate(Screen.CreateCollection.createRoute())
                    },
                    onEditCollectionClick = { collectionId ->
                        navController.navigate(Screen.CreateCollection.createRoute(collectionId = collectionId))
                    }
                )
            }
            composable(
                route = Screen.Profile.route,
                enterTransition = tabFadeIn,
                exitTransition = tabFadeOut,
                popEnterTransition = tabFadeIn,
                popExitTransition = tabFadeOut
            ) {
                ProfileScreen(
                    paddingValues = paddingValues,
                    themeViewModel = themeViewModel,
                    onAddBookClick = {
                        navController.navigate(Screen.Upload.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = Screen.BookDetail.route
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                val origin = backStackEntry.arguments?.getString("origin") ?: ""

                val books by BookRepository.books.collectAsState()
                val book = books.firstOrNull { it.id == bookId }
                    ?: Book(id = bookId, title = stringResource(R.string.unknown_book), author = "")

                BookDetailScreen(
                    book = book,
                    origin = origin,
                    animatedVisibilityScope = this@composable,
                    onBackClick = { navController.popBackStack() },
                    onFullScreenExpansionChanged = onFullScreenExpansionChanged,
                    onChapterClick = { chapter ->
                        navController.navigate(Screen.Reading.createRoute(book.id, chapter.id))
                    }
                )
            }
            composable(
                route = Screen.Reading.route
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""

                ReadingScreen(
                    bookId = bookId,
                    chapterId = chapterId,
                    onBackClick = { navController.popBackStack() },
                    themeViewModel = themeViewModel
                )
            }
            composable(
                route = Screen.EditBook.route
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                EditBookScreen(
                    bookId = bookId,
                    onSave = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.CollectionDetail.route
            ) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
                val addedCountKey = Screen.BookPicker.RESULT_ADDED_COUNT_KEY
                val addedCount by backStackEntry.savedStateHandle
                    .getStateFlow(addedCountKey, -1)
                    .collectAsState()

                CollectionDetailScreen(
                    collectionId = collectionId,
                    animatedVisibilityScope = this@composable,
                    pendingBooksAddedCount = addedCount.takeIf { it >= 0 },
                    onPendingBooksAddedConsumed = {
                        backStackEntry.savedStateHandle[addedCountKey] = -1
                    },
                    onBookClick = { bookId, origin ->
                        navController.navigate(Screen.BookDetail.createRoute(bookId, origin))
                    },
                    onCollectionClick = { childId ->
                        navController.navigate(Screen.CollectionDetail.createRoute(childId))
                    },
                    onBreadcrumbClick = { targetId ->
                        if (targetId == null) {
                            navController.popBackStack(Screen.Library.route, inclusive = false)
                        } else {
                            val popped = navController.popBackStack(
                                Screen.CollectionDetail.createRoute(targetId),
                                inclusive = false
                            )
                            if (!popped) {
                                navController.navigate(Screen.CollectionDetail.createRoute(targetId)) {
                                    popUpTo(Screen.Library.route) { inclusive = false }
                                }
                            }
                        }
                    },
                    onCreateSubcollectionClick = {
                        navController.navigate(
                            Screen.CreateCollection.createRoute(parentId = collectionId)
                        )
                    },
                    onAddExistingBooks = {
                        navController.navigate(
                            Screen.BookPicker.createRoute(
                                mode = "add",
                                collectionId = collectionId
                            )
                        )
                    },
                    onEditClick = {
                        navController.navigate(Screen.CreateCollection.createRoute(collectionId = collectionId))
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.CreateCollection.route,
                arguments = listOf(
                    navArgument("collectionId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("parentId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = {
                    slideInHorizontally(animationSpec = tween(320)) { it / 5 } + fadeIn(tween(280))
                },
                exitTransition = {
                    fadeOut(tween(200))
                },
                popEnterTransition = {
                    fadeIn(tween(220))
                },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = tween(280)) { it / 5 } + fadeOut(tween(220))
                }
            ) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getString("collectionId")
                val parentId = backStackEntry.arguments?.getString("parentId")
                val resultKey = Screen.BookPicker.RESULT_IDS_KEY
                val pickerResult by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(resultKey, null)
                    .collectAsState()
                val incomingSelectedIds = remember(pickerResult) {
                    pickerResult
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                }

                CreateCollectionScreen(
                    collectionId = collectionId,
                    parentId = parentId,
                    incomingSelectedIds = incomingSelectedIds,
                    onIncomingSelectedConsumed = {
                        backStackEntry.savedStateHandle.remove<String>(resultKey)
                    },
                    onBrowseLibrary = { currentSelectedIds ->
                        navController.currentBackStackEntry?.savedStateHandle?.set(
                            Screen.BookPicker.INITIAL_IDS_KEY,
                            currentSelectedIds.joinToString(",")
                        )
                        navController.navigate(Screen.BookPicker.createRoute(mode = "select"))
                    },
                    onSave = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.BookPicker.route,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "select"
                    },
                    navArgument("collectionId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = {
                    slideInHorizontally(animationSpec = tween(340)) { it } + fadeIn(tween(280))
                },
                exitTransition = {
                    fadeOut(tween(180))
                },
                popEnterTransition = {
                    fadeIn(tween(220))
                },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(tween(220))
                }
            ) { backStackEntry ->
                val modeArg = backStackEntry.arguments?.getString("mode") ?: "select"
                val collectionId = backStackEntry.arguments?.getString("collectionId")
                val books by BookRepository.books.collectAsState()
                val collections by CollectionRepository.collections.collectAsState()
                val collection = collectionId?.let { id -> collections.firstOrNull { it.id == id } }

                val pickerMode = if (modeArg == "add") {
                    BookPickerMode.ADD_TO_COLLECTION
                } else {
                    BookPickerMode.SELECT
                }

                val initialIds = remember(backStackEntry) {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.get<String>(Screen.BookPicker.INITIAL_IDS_KEY)
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.toSet()
                        ?: emptySet()
                }

                val availableBooks = remember(books, collection) {
                    if (pickerMode == BookPickerMode.ADD_TO_COLLECTION && collection != null) {
                        books.filter { it.id !in collection.bookIds }
                    } else {
                        books
                    }
                }

                val title = when (pickerMode) {
                    BookPickerMode.ADD_TO_COLLECTION -> stringResource(
                        R.string.book_picker_add_title,
                        collection?.name ?: stringResource(R.string.collections)
                    )
                    BookPickerMode.SELECT -> stringResource(R.string.book_picker_title)
                }
                val subtitle = stringResource(R.string.book_picker_subtitle, availableBooks.size)
                val confirmLabel = when (pickerMode) {
                    BookPickerMode.ADD_TO_COLLECTION -> stringResource(R.string.add_selected_books)
                    BookPickerMode.SELECT -> stringResource(R.string.confirm_selection)
                }

                BookPickerScreen(
                    books = availableBooks,
                    initialSelectedIds = if (pickerMode == BookPickerMode.SELECT) initialIds else emptySet(),
                    title = title,
                    subtitle = subtitle,
                    confirmLabel = confirmLabel,
                    mode = pickerMode,
                    onConfirm = { ids ->
                        when (pickerMode) {
                            BookPickerMode.SELECT -> {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(Screen.BookPicker.RESULT_IDS_KEY, ids.joinToString(","))
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.remove<String>(Screen.BookPicker.INITIAL_IDS_KEY)
                            }
                            BookPickerMode.ADD_TO_COLLECTION -> {
                                val targetId = collectionId
                                if (targetId != null && ids.isNotEmpty()) {
                                    val added = CollectionRepository.addBooksToCollection(targetId, ids)
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set(Screen.BookPicker.RESULT_ADDED_COUNT_KEY, added)
                                }
                            }
                        }
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                    onImportBook = {
                        navController.navigate(Screen.Upload.route) {
                            launchSingleTop = true
                        }
                    },
                    onCreateCollection = {
                        navController.navigate(Screen.CreateCollection.createRoute()) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
