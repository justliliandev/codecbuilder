package dev.agnor.codecbuilder.preprocessor

import com.intellij.psi.PsiClass
import dev.agnor.codecbuilder.psiwrapper.Method

/*
    Arrays of previous values are not to be modified, they are there to inspect previous context
 */
interface PreProcessor {

    fun getConstructors(clazz: PsiClass, constructors: Array<Method>):  Array<Method> {
        return arrayOf()
    }

    fun getAllMethods(clazz: PsiClass, getters: Array<Method>): Array<Method> {
        return arrayOf()
    }
}