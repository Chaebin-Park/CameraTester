package com.kii.common

/**
 * 공용 로거 유틸리티
 *
 * @author chaebin
 * @since `10/31/25`
 */
object Logger {
    private const val TAG_PREFIX = "KII"

    var isEnabled = true
    var isDebugMode = true

    // 자동으로 호출한 클래스 이름을 태그로 사용
    private inline fun <reified T> getTag(): String {
        return T::class.java.simpleName
    }

    // 스택 트레이스에서 호출한 클래스 이름 추출
    private fun getAutoTag(): String {
        return Thread.currentThread().stackTrace
            .firstOrNull {
                it.className != Logger::class.java.name &&
                !it.className.startsWith("java.lang.Thread")
            }
            ?.className
            ?.substringAfterLast('.')
            ?: "Unknown"
    }

    // Debug
    fun d(tag: String, message: String) {
        if (isEnabled && isDebugMode) {
            println("D/$TAG_PREFIX-$tag: $message")
        }
    }

    fun d(message: String) {
        d(getAutoTag(), message)
    }

    // Info
    fun i(tag: String, message: String) {
        if (isEnabled) {
            println("I/$TAG_PREFIX-$tag: $message")
        }
    }

    fun i(message: String) {
        i(getAutoTag(), message)
    }

    // Warning
    fun w(tag: String, message: String) {
        if (isEnabled) {
            println("W/$TAG_PREFIX-$tag: $message")
        }
    }

    fun w(message: String) {
        w(getAutoTag(), message)
    }

    fun w(tag: String, message: String, throwable: Throwable?) {
        if (isEnabled) {
            println("W/$TAG_PREFIX-$tag: $message")
            throwable?.printStackTrace()
        }
    }

    fun w(message: String, throwable: Throwable?) {
        w(getAutoTag(), message, throwable)
    }

    // Error
    fun e(tag: String, message: String) {
        if (isEnabled) {
            println("E/$TAG_PREFIX-$tag: $message")
        }
    }

    fun e(message: String) {
        e(getAutoTag(), message)
    }

    fun e(tag: String, message: String, throwable: Throwable?) {
        if (isEnabled) {
            println("E/$TAG_PREFIX-$tag: $message")
            throwable?.printStackTrace()
        }
    }

    fun e(message: String, throwable: Throwable?) {
        e(getAutoTag(), message, throwable)
    }

    // Verbose
    fun v(tag: String, message: String) {
        if (isEnabled && isDebugMode) {
            println("V/$TAG_PREFIX-$tag: $message")
        }
    }

    fun v(message: String) {
        v(getAutoTag(), message)
    }
}