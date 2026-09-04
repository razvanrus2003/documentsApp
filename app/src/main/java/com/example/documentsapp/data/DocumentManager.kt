package com.example.documentsapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.documentsapp.ui.DocumentModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DocumentManager {

    private const val PREFS_NAME = "recent_docs"
    private const val KEY_DOCS_LIST = "docs_list"

    fun getRecentDocuments(context: Context): List<DocumentModel> {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = sharedPrefs.getString(KEY_DOCS_LIST, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<DocumentModel>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                DocumentModel(
                    id = obj.getString("uri"),
                    name = obj.getString("name"),
                    uriString = obj.getString("uri"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun saveDocument(context: Context, uri: Uri) {
        val name = getFileName(context, uri) ?: generateUntitledName(context)
        val uriString = uri.toString()
        
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = sharedPrefs.getString(KEY_DOCS_LIST, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        
        val newList = mutableListOf<JSONObject>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("uri") != uriString) {
                newList.add(obj)
            }
        }
        
        val newObj = JSONObject().apply {
            put("name", name)
            put("uri", uriString)
            put("timestamp", System.currentTimeMillis())
        }
        newList.add(0, newObj)
        
        val finalArray = JSONArray()
        newList.take(10).forEach { finalArray.put(it) }
        
        sharedPrefs.edit().putString(KEY_DOCS_LIST, finalArray.toString()).apply()
    }

    fun deleteDocument(context: Context, uri: Uri) {
        val uriString = uri.toString()
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = sharedPrefs.getString(KEY_DOCS_LIST, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        
        val newList = JSONArray()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("uri") != uriString) {
                newList.put(obj)
            }
        }
        
        sharedPrefs.edit().putString(KEY_DOCS_LIST, newList.toString()).apply()
        
        // Delete the file if it's in the app's internal storage or cache
        if (uri.scheme == "file") {
            try {
                val file = File(uri.path!!)
                if (file.exists() && (file.absolutePath.startsWith(context.cacheDir.absolutePath) || 
                    file.absolutePath.startsWith(context.filesDir.absolutePath))) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun renameDocument(context: Context, uri: Uri, newName: String): Uri? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = sharedPrefs.getString(KEY_DOCS_LIST, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        
        var newUri = uri
        val uriString = uri.toString()
        
        // 1. Update the list
        val newList = JSONArray()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("uri") == uriString) {
                obj.put("name", if (newName.endsWith(".pdf", ignoreCase = true)) newName else "$newName.pdf")
                newList.put(obj)
            } else {
                newList.put(obj)
            }
        }
        sharedPrefs.edit().putString(KEY_DOCS_LIST, newList.toString()).apply()

        // 2. Attempt to rename physical file if it's in internal storage
        if (uri.scheme == "file") {
            try {
                val oldFile = File(uri.path!!)
                if (oldFile.exists() && (oldFile.absolutePath.startsWith(context.filesDir.absolutePath) || 
                    oldFile.absolutePath.startsWith(context.cacheDir.absolutePath))) {
                    
                    val extension = oldFile.extension
                    val sanitizedNewName = newName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
                    val finalNewName = if (sanitizedNewName.endsWith(".$extension", ignoreCase = true)) {
                        sanitizedNewName
                    } else {
                        "$sanitizedNewName.$extension"
                    }
                    
                    val newFile = File(oldFile.parentFile, finalNewName)
                    if (oldFile.renameTo(newFile)) {
                        newUri = Uri.fromFile(newFile)
                        // Update the URI in the list too since it changed
                        val updatedList = JSONArray()
                        for (i in 0 until newList.length()) {
                            val obj = newList.getJSONObject(i)
                            if (obj.getString("uri") == uriString) {
                                obj.put("uri", newUri.toString())
                            }
                            updatedList.put(obj)
                        }
                        sharedPrefs.edit().putString(KEY_DOCS_LIST, updatedList.toString()).apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return newUri
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        }
        
        if (name == null) {
            val fileName = uri.lastPathSegment
            if (fileName != null && isTemporaryFileName(fileName)) {
                name = null
            } else {
                name = fileName
            }
        }
        
        return name
    }

    private fun isTemporaryFileName(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.startsWith("generated_document_") || 
               lower.startsWith("generate_document_") ||
               lower.startsWith("temp_") || 
               lower.startsWith("signed_") ||
               lower.startsWith("capture_") ||
               lower.startsWith("deskewed_") ||
               lower.startsWith("rotated_") ||
               lower.startsWith("filter_") ||
               lower.startsWith("untitled_document_")
    }

    fun generateUntitledName(context: Context): String {
        val recentDocs = getRecentDocuments(context)
        val existingNames = recentDocs.map { it.name.lowercase() }.toSet()
        
        if (!existingNames.contains("untitled.pdf")) {
            return "Untitled.pdf"
        }
        
        var counter = 1
        while (existingNames.contains("untitled $counter.pdf")) {
            counter++
        }
        
        return "Untitled $counter.pdf"
    }
}
