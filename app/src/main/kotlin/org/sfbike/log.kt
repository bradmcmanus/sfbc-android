package org.sfbike

import android.util.Log

private val tag = "sfbc"

object log {

	var enabled = true

	public fun init(loggingOn: Boolean) {
		enabled = loggingOn
	}

	@JvmStatic fun v(s: String): Unit {
		if (enabled) {
			Log.v(tag, s)
		}
	}

	@JvmStatic fun d(s: String): Unit {
		if (enabled) {
			Log.d(tag, s)
		}
	}

	@JvmStatic fun i(s: String): Unit {
		if (enabled) {
			Log.i(tag, s)
		}
	}

	@JvmStatic fun w(s: String, e: Throwable? = null): Unit {
		if (enabled) {
			Log.w(tag, s, e)
		}
	}

	@JvmStatic fun e(s: String, e: Throwable): Unit {
		if (enabled) {
			Log.e(tag, s, e)
		}
	}
}

fun log(s: String): Unit {
	if (log.enabled) {
		log.d(s)
	}
}
