package com.yuchen.ailedger.agent

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import java.lang.ref.WeakReference

class AgentInputMethodService : InputMethodService() {
    override fun onCreate() {
        super.onCreate()
        activeService = WeakReference(this)
    }

    override fun onDestroy() {
        if (activeService?.get() === this) activeService = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        return View(this).apply {
            minimumHeight = 1
            alpha = 0f
        }
    }

    private fun commitTextInternal(text: String): Boolean {
        if (text.isBlank()) return false
        val connection: InputConnection = currentInputConnection ?: return false
        return runCatching {
            connection.beginBatchEdit()
            connection.commitText(text, 1)
            connection.endBatchEdit()
            true
        }.getOrElse {
            runCatching { connection.endBatchEdit() }
            false
        }
    }

    companion object {
        private var activeService: WeakReference<AgentInputMethodService>? = null

        fun isActive(): Boolean = activeService?.get() != null

        fun commitText(text: String): Boolean {
            return activeService?.get()?.commitTextInternal(text) == true
        }
    }
}
