package com.example.library.model

data class User(
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    val annualGoal: Int = 30,
    val monthlyGoal: Int = 4,
    val totalTimeReadingHours: Int = 18
)
