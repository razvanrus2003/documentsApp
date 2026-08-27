package com.example.documentsapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.documentsapp.ui.DocumentModel
import org.json.JSONArray
import org.json.JSONObject

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
        val name = getFileName(context, uri) ?: "Unknown Document"
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

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }
}
