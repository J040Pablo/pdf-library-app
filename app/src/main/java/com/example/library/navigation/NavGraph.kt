package com.example.library.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedContentTransitionScope
import com.example.library.screens.bookdetail.BookDetailScreen
import com.example.library.screens.collectiondetail.CollectionDetailScreen
import com.example.library.screens.createcollection.CreateCollectionScreen
import com.example.library.screens.home.HomeScreen
import com.example.library.screens.profile.ProfileScreen
import com.example.library.screens.search.SearchScreen
import com.example.library.screens.library.LibraryScreen
import com.example.library.screens.upload.UploadScreen
import com.example.library.viewmodel.ThemeViewModel
import com.example.library.model.Book
import com.example.library.model.Chapter

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues,
    themeViewModel: ThemeViewModel
) {
    val enterTrans: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        val initialRoute = initialState.destination.route?.substringBefore("/")?.substringBefore("?")
        val targetRoute = targetState.destination.route?.substringBefore("/")?.substringBefore("?")
        val routes = listOf(Screen.Home.route, Screen.Upload.route, Screen.Library.route, Screen.Profile.route)
        val initialIndex = routes.indexOf(initialRoute)
        val targetIndex = routes.indexOf(targetRoute)
        if (initialIndex != -1 && targetIndex != -1) {
            if (targetIndex > initialIndex) slideIntoContainer(SlideDirection.Left)
            else slideIntoContainer(SlideDirection.Right)
        } else null
    }

    val exitTrans: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        val initialRoute = initialState.destination.route?.substringBefore("/")?.substringBefore("?")
        val targetRoute = targetState.destination.route?.substringBefore("/")?.substringBefore("?")
        val routes = listOf(Screen.Home.route, Screen.Upload.route, Screen.Library.route, Screen.Profile.route)
        val initialIndex = routes.indexOf(initialRoute)
        val targetIndex = routes.indexOf(targetRoute)
        if (initialIndex != -1 && targetIndex != -1) {
            if (targetIndex > initialIndex) slideOutOfContainer(SlideDirection.Left)
            else slideOutOfContainer(SlideDirection.Right)
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
                enterTransition = enterTrans,
                exitTransition = exitTrans,
                popEnterTransition = enterTrans,
                popExitTransition = exitTrans
            ) {
                HomeScreen(
                    animatedVisibilityScope = this@composable,
                    paddingValues = paddingValues,
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onBookClick = { bookId, origin ->
                        navController.navigate(Screen.BookDetail.createRoute(bookId, origin))
                    }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    paddingValues = paddingValues,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = Screen.Upload.route,
                enterTransition = enterTrans,
                exitTransition = exitTrans,
                popEnterTransition = enterTrans,
                popExitTransition = exitTrans
            ) {
                UploadScreen(paddingValues = paddingValues)
            }
            composable(
                route = Screen.Library.route,
                enterTransition = enterTrans,
                exitTransition = exitTrans,
                popEnterTransition = enterTrans,
                popExitTransition = exitTrans
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
                        navController.navigate(Screen.CreateCollection.route)
                    }
                )
            }
            composable(
                route = Screen.Profile.route,
                enterTransition = enterTrans,
                exitTransition = exitTrans,
                popEnterTransition = enterTrans,
                popExitTransition = exitTrans
            ) {
                ProfileScreen(
                    paddingValues = paddingValues,
                    themeViewModel = themeViewModel
                )
            }
            composable(
                route = Screen.BookDetail.route
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                
                // Fetch book - ideally from ViewModel, mocking here for now based on ID
                val book = Book(
                    id = bookId,
                    title = "Book Title",
                    author = "Author Name",
                    chapters = listOf(
                        Chapter("1", "Chapter 1", "10 pages"),
                        Chapter("2", "Chapter 2", "15 pages")
                    )
                )
                val origin = backStackEntry.arguments?.getString("origin") ?: ""
                
                BookDetailScreen(
                    book = book,
                    origin = origin,
                    animatedVisibilityScope = this@composable,
                    onBackClick = { navController.popBackStack() }
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
                    onAddBooksClick = {
                        navController.navigate(Screen.CreateCollection.createRoute(collectionId))
                    },
                    onEditClick = {
                        navController.navigate(Screen.CreateCollection.createRoute(collectionId))
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
                    }
                )
            ) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getString("collectionId")
                CreateCollectionScreen(
                    collectionId = collectionId,
                    onSave = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
        }
    }
}
