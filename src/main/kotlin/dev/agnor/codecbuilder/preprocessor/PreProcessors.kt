package dev.agnor.codecbuilder.preprocessor

import com.intellij.psi.PsiClass
import dev.agnor.codecbuilder.psiwrapper.Method
import org.apache.commons.lang3.function.TriFunction

//use java pre processor to get default data and at the end add a default constructor if no constructor is present
private val chain = listOf(JavaPreProcessor(), /*LombokPreProcessor(),*/ JavaDefaultConstructorPreProcessor())
private var log = false
fun constructors(clazz: PsiClass): Array<Method> {
    log = true
    return chain(clazz, PreProcessor::getConstructors)
}

fun allMethods(clazz: PsiClass): Array<Method> {
    return chain(clazz, PreProcessor::getAllMethods)
}

private inline fun <reified T> chain(clazz: PsiClass, chainMethod: TriFunction<PreProcessor, PsiClass, Array<T>, Array<T>>): Array<T> {
    var runningArray = arrayOf<T>()
    for (preProcessor in chain) {
        runningArray += chainMethod.apply(preProcessor, clazz, runningArray)
        if (log)
            println("after " + preProcessor.javaClass.name + ": " + runningArray.contentDeepToString())
    }
    log = false
    return runningArray
}