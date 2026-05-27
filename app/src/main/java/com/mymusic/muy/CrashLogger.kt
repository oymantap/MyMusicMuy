package com.mymusic.muy

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    fun log(
        context: Context,
        tag: String,
        error: Throwable
    ) {

        try {

            val prefs =
                context.getSharedPreferences(
                    "MusicPrefs",
                    Context.MODE_PRIVATE
                )

            val treeUri =
                prefs.getString(
                    "last_folder",
                    null
                ) ?: return

            val root =
                DocumentFile.fromTreeUri(
                    context,
                    Uri.parse(treeUri)
                ) ?: return

            var logFile =
                root.findFile(
                    "Muy_CrashLog.txt"
                )

            if (logFile == null) {

                logFile = root.createFile(
                    "text/plain",
                    "Muy_CrashLog.txt"
                )
            }

            val time =
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

            val text = buildString {

                append("\n\n")
                append("===== CRASH =====\n")
                append("TIME: $time\n")
                append("TAG: $tag\n\n")

                append(error.toString())
                append("\n\nSTACKTRACE:\n")

                error.stackTrace.forEach {

                    append(it.toString())
                    append("\n")
                }
            }

            context.contentResolver
                .openOutputStream(
                    logFile!!.uri,
                    "wa"
                )?.use {

                    it.write(
                        text.toByteArray()
                    )
                }

        } catch (_: Exception) {
        }
    }
}