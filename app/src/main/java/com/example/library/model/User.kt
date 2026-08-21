package com.example.library.model

data class User(
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    val annualGoal: Int = 12,
    val monthlyGoal: Int = 1,
    val totalTimeReadingHours: Int = 0
) {
    companion object {
        fun default() = User(
            name = "Reader",
            email = ""
        )
    }
}
