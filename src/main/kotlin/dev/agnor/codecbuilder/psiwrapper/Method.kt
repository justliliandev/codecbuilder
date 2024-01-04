package dev.agnor.codecbuilder.psiwrapper

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.util.containers.map2Array

class Method(val returnType: PsiType?, val parameters: Array<Parameter>, val name: String, val isStatic: Boolean, val isDeprecated: Boolean) {
    @Suppress("UnstableApiUsage")
    constructor(method: PsiMethod):
            this(method.returnType,
                    method.parameterList.parameters.map2Array { Parameter(it) },
                    method.name,
                    method.hasModifier(JvmModifier.STATIC),
                    method.isDeprecated)

    override fun toString(): String {
        return "Method(returnType=$returnType, parameters=${parameters.contentToString()}, name='$name', isStatic=$isStatic, isDeprecated=$isDeprecated)"
    }

}
