package io.eels.util

sealed class Option<out T> {

  abstract fun <U> map(f: (T) -> U): Option<U>
  abstract fun <U> fold(emptyValue: U, f: (T) -> U): U
  abstract fun orNull(): T?
  abstract fun forEach(fn: (T) -> Unit): Unit
  abstract fun exists(predicate: (T) -> Boolean): Boolean

  companion object {
    fun <T> none(): Option<T> = None
    operator fun <T> invoke(t: T?) = when (t) {
      null -> None
      else -> Some(t)
    }
  }

  object None : Option<Nothing>() {
    override fun exists(predicate: (Nothing) -> Boolean) = false
    override fun forEach(fn: (Nothing) -> Unit) {}
    override fun orNull(): Nothing? = null
    override fun <U> fold(emptyValue: U, f: (Nothing) -> U): U = emptyValue
    override fun <U> map(f: (Nothing) -> U): Option<U> = None
  }

  class Some<T>(val value: T) : Option<T>() {
    override fun exists(predicate: (T) -> Boolean) = predicate(value)
    override fun forEach(fn: (T) -> Unit) = fn(value)
    override fun orNull(): T? = value
    override fun <U> fold(emptyValue: U, f: (T) -> U): U = f(value)
    override fun <U> map(f: (T) -> U): Option<U> = Some(f(value))
  }

}

fun <T> Option<T>.getOrElse(other: () -> T): T = when (this) {
  Option.None -> other()
  is Option.Some -> this.value
}

fun <T> Option<T>.getOrElse(other: T): T = when (this) {
  Option.None -> other
  is Option.Some -> this.value
}

fun <T> Option<T>.orElse(other: Option<T>): Option<T> = when (this) {
  Option.None -> other
  is Option.Some -> this
}
