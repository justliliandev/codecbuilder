package dev.agnor.codecbuilder.preprocessor

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClass
import com.intellij.util.containers.map2Array
import dev.agnor.codecbuilder.psiwrapper.Method

class JavaPreProcessor: PreProcessor {
    override fun getConstructors(clazz: PsiClass, constructors: Array<Method>): Array<Method> {
        return clazz.constructors.map2Array { Method(it)}
    }

    @Suppress("UnstableApiUsage")
    override fun getAllMethods(clazz: PsiClass, getters: Array<Method>): Array<Method> {
        return clazz.allMethods.filter{!it.hasModifier(JvmModifier.STATIC)}.map2Array { Method(it) }
    }
}