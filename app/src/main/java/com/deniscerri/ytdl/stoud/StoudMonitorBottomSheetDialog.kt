package com.deniscerri.ytdl.stoud

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StoudMonitorBottomSheetDialog : BottomSheetDialogFragment() {

    private lateinit var client: StoudClient
    private lateinit var serverUrlText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var loopBadge: TextView
    private lateinit var currentVideoTitle: TextView
    private lateinit var currentVideoUrl: TextView
    private lateinit var queueHeader: TextView
    private lateinit var queueContainer: LinearLayout
    private lateinit var emptyQueueText: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnStopStream: Button
    private lateinit var btnClearQueue: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_stoud_monitor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        client = StoudClient(requireContext())
        serverUrlText = view.findViewById(R.id.monitor_server_url)
        statusBadge = view.findViewById(R.id.status_badge)
        loopBadge = view.findViewById(R.id.loop_badge)
        currentVideoTitle = view.findViewById(R.id.current_video_title)
        currentVideoUrl = view.findViewById(R.id.current_video_url)
        queueHeader = view.findViewById(R.id.queue_header)
        queueContainer = view.findViewById(R.id.queue_container)
        emptyQueueText = view.findViewById(R.id.empty_queue_text)
        btnRefresh = view.findViewById(R.id.btn_refresh)
        btnStopStream = view.findViewById(R.id.btn_stop_stream)
        btnClearQueue = view.findViewById(R.id.btn_clear_queue)

        val serverUrl = StoudPreferences.getServerUrl(requireContext())
        serverUrlText.text = if (serverUrl.isNotBlank()) serverUrl else "No server configured"

        btnRefresh.setOnClickListener {
            refreshStatus()
        }

        btnStopStream.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.stoud_stop_stream)
                .setMessage("Are you sure you want to stop the live stream?")
                .setPositiveButton(R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        val res = client.stopStream()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            refreshStatus()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        btnClearQueue.setOnClickListener {
            lifecycleScope.launch {
                val res = client.clearQueue()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    refreshStatus()
                }
            }
        }

        refreshStatus()
    }

    @SuppressLint("SetTextI18n")
    private fun refreshStatus() {
        btnRefresh.isEnabled = false
        statusBadge.text = getString(R.string.stoud_connecting)

        lifecycleScope.launch {
            val result = client.getStatus()
            withContext(Dispatchers.Main) {
                btnRefresh.isEnabled = true
                result.onSuccess { status ->
                    if (status.isStreaming) {
                        statusBadge.text = "🔴 LIVE"
                        currentVideoTitle.text = status.currentVideo?.title ?: "Active Broadcast"
                        currentVideoUrl.text = status.currentVideo?.url ?: ""
                        btnStopStream.isEnabled = true
                    } else {
                        statusBadge.text = "⚪ IDLE"
                        currentVideoTitle.text = "No active broadcast"
                        currentVideoUrl.text = ""
                        btnStopStream.isEnabled = false
                    }

                    loopBadge.text = if (status.breakMode) "Break Mode" else if (status.loopCurrentVideo) "Looping" else "24/7 Engine"

                    val queue = status.queue
                    queueHeader.text = getString(R.string.stoud_queue_items, queue.size)

                    // Rebuild queue items
                    queueContainer.removeAllViews()
                    if (queue.isEmpty()) {
                        queueContainer.addView(emptyQueueText)
                        emptyQueueText.visibility = View.VISIBLE
                    } else {
                        queue.forEachIndexed { index, item ->
                            val row = LayoutInflater.from(context).inflate(
                                android.R.layout.simple_list_item_2,
                                queueContainer,
                                false
                            )
                            val t1 = row.findViewById<TextView>(android.R.id.text1)
                            val t2 = row.findViewById<TextView>(android.R.id.text2)
                            t1.text = "${index + 1}. ${item.title}"
                            t2.text = item.url
                            t2.alpha = 0.6f

                            row.setOnLongClickListener {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Remove from Queue")
                                    .setMessage(item.title)
                                    .setPositiveButton(R.string.ok) { _, _ ->
                                        lifecycleScope.launch {
                                            client.removeFromQueue(index)
                                            withContext(Dispatchers.Main) { refreshStatus() }
                                        }
                                    }
                                    .setNegativeButton(R.string.cancel, null)
                                    .show()
                                true
                            }
                            queueContainer.addView(row)
                        }
                    }
                }.onFailure { err ->
                    statusBadge.text = "⚠️ OFFLINE"
                    currentVideoTitle.text = "Connection Failed"
                    currentVideoUrl.text = err.localizedMessage ?: "Could not reach Stoud"
                    btnStopStream.isEnabled = false
                }
            }
        }
    }
}
