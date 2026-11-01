package com.deniscerri.ytdl.stoud

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object StoudDialogHelper {

    fun showStreamDialog(
        activity: Activity,
        url: String,
        title: String?,
        onActionCompleted: (() -> Unit)? = null
    ) {
        if (!StoudPreferences.isConfigured(activity)) {
            showConfigurePrompt(activity)
            return
        }

        val displayTitle = title ?: url
        val options = arrayOf(
            activity.getString(R.string.stream_now),
            activity.getString(R.string.stream_queue_add),
            activity.getString(R.string.stoud_open_monitor)
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.stoud_streamer_mode))
            .setMessage(displayTitle)
            .setIcon(R.drawable.baseline_live_tv_24)
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                val client = StoudClient(activity)
                val lifecycleOwner = activity as? AppCompatActivity

                when (which) {
                    0 -> { // Stream Now
                        Toast.makeText(activity, activity.getString(R.string.stoud_connecting), Toast.LENGTH_SHORT).show()
                        lifecycleOwner?.lifecycleScope?.launch {
                            val res = client.startStream(url)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, res.message, Toast.LENGTH_LONG).show()
                                if (res.success) onActionCompleted?.invoke()
                            }
                        }
                    }
                    1 -> { // Add to Queue
                        Toast.makeText(activity, activity.getString(R.string.stoud_connecting), Toast.LENGTH_SHORT).show()
                        lifecycleOwner?.lifecycleScope?.launch {
                            val res = client.addToQueue(url, displayTitle)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, res.message, Toast.LENGTH_LONG).show()
                                if (res.success) onActionCompleted?.invoke()
                            }
                        }
                    }
                    2 -> { // Open Monitor
                        openMonitor(activity)
                    }
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    fun showMultiStreamDialog(
        activity: Activity,
        items: List<Pair<String, String>>, // (url, title)
        onActionCompleted: (() -> Unit)? = null
    ) {
        if (items.isEmpty()) return

        if (!StoudPreferences.isConfigured(activity)) {
            showConfigurePrompt(activity)
            return
        }

        val options = arrayOf(
            activity.getString(R.string.stream_now) + " (${items.size})",
            activity.getString(R.string.stream_queue_add) + " (${items.size})",
            activity.getString(R.string.stoud_open_monitor)
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.stoud_streamer_mode))
            .setMessage("${items.size} videos selected for broadcast")
            .setIcon(R.drawable.baseline_live_tv_24)
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                val client = StoudClient(activity)
                val lifecycleOwner = activity as? AppCompatActivity

                when (which) {
                    0 -> { // Start with first, queue rest
                        Toast.makeText(activity, activity.getString(R.string.stoud_connecting), Toast.LENGTH_SHORT).show()
                        lifecycleOwner?.lifecycleScope?.launch {
                            val first = items.first()
                            val startRes = client.startStream(first.first)
                            if (startRes.success && items.size > 1) {
                                for (i in 1 until items.size) {
                                    client.addToQueue(items[i].first, items[i].second)
                                }
                            }
                            withContext(Dispatchers.Main) {
                                val msg = if (startRes.success) "Broadcasting started and ${items.size - 1} items queued!" else startRes.message
                                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                                if (startRes.success) onActionCompleted?.invoke()
                            }
                        }
                    }
                    1 -> { // Queue all
                        Toast.makeText(activity, activity.getString(R.string.stoud_connecting), Toast.LENGTH_SHORT).show()
                        lifecycleOwner?.lifecycleScope?.launch {
                            var count = 0
                            for (item in items) {
                                val res = client.addToQueue(item.first, item.second)
                                if (res.success) count++
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, "Added $count / ${items.size} items to Stoud broadcast queue", Toast.LENGTH_LONG).show()
                                onActionCompleted?.invoke()
                            }
                        }
                    }
                    2 -> {
                        openMonitor(activity)
                    }
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    fun openMonitor(activity: Activity) {
        val fragmentActivity = activity as? FragmentActivity ?: return
        val sheet = StoudMonitorBottomSheetDialog()
        sheet.show(fragmentActivity.supportFragmentManager, "StoudMonitorBottomSheetDialog")
    }

    private fun showConfigurePrompt(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.stoud_streamer_mode))
            .setMessage(activity.getString(R.string.stoud_not_configured))
            .setIcon(R.drawable.baseline_live_tv_24)
            .setPositiveButton(activity.getString(R.string.settings)) { _, _ ->
                val intent = Intent(activity, SettingsActivity::class.java)
                activity.startActivity(intent)
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }
}
