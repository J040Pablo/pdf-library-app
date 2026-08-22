package com.example.library.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedContentTransitionScope
import com.example.library.R
import com.example.library.screens.bookdetail.BookDetailScreen
import com.example.library.screens.collectiondetail.CollectionDetailScreen
import com.example.library.screens.createcollection.CreateCollectionScreen
import com.example.library.screens.editbook.EditBookScreen
import com.example.library.screens.home.HomeScreen
import com.example.library.screens.profile.ProfileScreen
import com.example.library.screens.search.SearchScreen
import com.example.library.screens.library.LibraryScreen
import com.example.library.screens.reading.ReadingScreen
import com.example.library.screens.upload.UploadScreen
import com.example.library.viewmodel.ThemeViewModel
import com.example.library.model.Book
import com.example.library.model.Chapter
import com.example.library.data.BookRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues,
    themeViewModel: ThemeViewModel,
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
                    paddingValues = paddingValues,
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onBookClick = { bookId, origin ->
                        navController.navigate(Screen.BookDetail.createRoute(bookId, origin))
                    },
                    onEditBookClick = { bookId ->
                        navController.navigate(Screen.EditBook.createRoute(bookId))
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
                CollectionDetailScreen(
                    collectionId = collectionId,
                    animatedVisibilityScope = this@composable,
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
                            // Pop until we reach the target, or navigate if not on stack.
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
                )
            ) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getString("collectionId")
                val parentId = backStackEntry.arguments?.getString("parentId")
                CreateCollectionScreen(
                    collectionId = collectionId,
                    parentId = parentId,
                    onSave = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
        }
    }
}
