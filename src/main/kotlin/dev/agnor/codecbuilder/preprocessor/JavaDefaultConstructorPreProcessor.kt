package dev.agnor.codecbuilder.preprocessor

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.impl.source.PsiImmediateClassType
import dev.agnor.codecbuilder.psiwrapper.Method

class JavaDefaultConstructorPreProcessor: PreProcessor {
    override fun getConstructors(clazz: PsiClass, constructors: Array<Method>): Array<Method> {
        if (constructors.isNotEmpty()) {
            return arrayOf()
        }
        return arrayOf(Method(PsiImmediateClassType(clazz, PsiSubstitutor.UNKNOWN), arrayOf(), "new", true, false))
    }
}