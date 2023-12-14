package dev.agnor.codecbuilder

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope

@Service
@State(name = "codecbuilder", storages = [Storage("codecbuilder.xml")])
class DataStorage : SimplePersistentStateComponent<InternalState>(InternalState())

class InternalState : BaseState() {
    var value by list<String>()
}

fun getCodecRoots(project: Project) : kotlin.collections.List<PsiClass> {
    val defaultRoots = ArrayList<String>();
    defaultRoots.addAll(DefaultSources.values().map{src -> src.qualifiedname });
    defaultRoots.addAll(getStoredCodecRoots());

    return defaultRoots.mapNotNull { root -> JavaPsiFacade.getInstance(project).findClass(root, GlobalSearchScope.allScope(project)) };
}

fun getCodecRoot(project: Project, sources: DefaultSources) : PsiClass? {
    return JavaPsiFacade.getInstance(project).findClass(sources.qualifiedname, GlobalSearchScope.allScope(project))
}

fun getStoredCodecRoots(): MutableList<String> {
    return service<DataStorage>().state.value
}
fun removeStoredCodecRoots(root: String): Boolean {
    val stored = getStoredCodecRoots()
    val removeSuccessful = stored.remove(root)
    setStoredCodecRoots(stored)
    return removeSuccessful
}
fun clearStoredCodecRoots() {
    setStoredCodecRoots(ArrayList())
}
fun addStoredCodecRoot(root: String) {
    val stored = getStoredCodecRoots()
    stored.add(root)
    setStoredCodecRoots(stored)
}

private fun setStoredCodecRoots(roots: MutableList<String>) {
    service<DataStorage>().state.value = roots;
}

enum class DefaultSources(val qualifiedname: String) {
    CODEC("com.mojang.serialization.Codec"),
    EXTRACODECS("net.minecraft.util.ExtraCodecs"),
    BUILTINREGISTRIES("net.minecraft.core.registries.BuiltInRegistries")
}