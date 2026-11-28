package hu.grafit.data

import android.content.Context
import java.security.MessageDigest

fun accountHash(account: String) = MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
class StudyStore(context: Context) {
    private val store = SecureStore(context, "grafit_study")
    fun read(account: String) = StudyState.parse(store.read(accountHash(account)))
    fun save(account: String, state: StudyState) = store.write(accountHash(account), state.json())
    fun notifications(account: String): String? = store.read("notifications:${accountHash(account)}")
    fun saveNotifications(account: String, options: String) = store.write("notifications:${accountHash(account)}", options)
    fun appearance(account: String): String? = store.read("appearance:${accountHash(account)}")
    fun saveAppearance(account: String, appearance: String) = store.write("appearance:${accountHash(account)}", appearance)
}
