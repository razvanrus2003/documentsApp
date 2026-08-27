package com.example.documentsapp.ui

data class DocumentModel(
    val id: String, // Use URI as ID
    val name: String,
    val uriString: String,
    val timestamp: Long
)
