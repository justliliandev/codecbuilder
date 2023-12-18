package dev.agnor.codecbuilder

import com.intellij.openapi.components.*

@Service
@State(name = "codecbuilder", storages = [Storage("codecbuilder.xml")])
class DataStorage : SimplePersistentStateComponent<InternalState>(InternalState())

class InternalState : BaseState() {
    var value by list<String>()
}

fun getStoredCodecRoots(): MutableList<String> {
    return service<DataStorage>().state.value
}
fun removeStoredCodecRoots(root: String): Boolean {
    val stored = getStoredCodecRoots()
    val removeSuccessful = stored.remove(root)
    setStoredRoot(stored)
    return removeSuccessful
}
fun clearStoredCodecRoots() {
    setStoredRoot(ArrayList())
}
fun addStoredCodecRoot(root: String) {
    val stored = getStoredCodecRoots()
    stored.add(root)
    setStoredRoot(stored)
}

private fun setStoredRoot(roots: MutableList<String>) {
    service<DataStorage>().state.value = roots;
}