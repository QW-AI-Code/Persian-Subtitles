package com.qwaicode.persiansubtitles.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Starts, stops and re-attaches the single translation job. */
object TranslationScheduler {

    const val UNIQUE_WORK = "persian_subtitles_translation"

    fun start(
        context: Context,
        wifiOnly: Boolean,
        forcePolish: Boolean = false,
        forceReview: Boolean = false,
    ) = enqueue(context, wifiOnly, ExistingWorkPolicy.REPLACE, forcePolish, forceReview)

    /**
     * Called when the app comes back: if a run was interrupted by a process death,
     * this re-attaches it without disturbing a job that is still alive.
     */
    fun resume(context: Context, wifiOnly: Boolean) =
        enqueue(context, wifiOnly, ExistingWorkPolicy.KEEP, forcePolish = false, forceReview = false)

    private fun enqueue(
        context: Context,
        wifiOnly: Boolean,
        policy: ExistingWorkPolicy,
        forcePolish: Boolean,
        forceReview: Boolean,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<TranslationWorker>()
            .setConstraints(constraints)
            .setInputData(
                androidx.work.workDataOf(
                    TranslationWorker.KEY_FORCE_POLISH to forcePolish,
                    TranslationWorker.KEY_FORCE_REVIEW to forceReview,
                )
            )
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(UNIQUE_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, policy, request)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        Notifications.cancelProgress(context)
    }
}
