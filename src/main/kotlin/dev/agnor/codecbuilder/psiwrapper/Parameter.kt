package dev.agnor.codecbuilder.psiwrapper

import com.intellij.psi.PsiField
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType

class Parameter(val type: PsiType, val name: String) {
    constructor(parameter: PsiParameter): this(parameter.type, parameter.name)
    constructor(field: PsiField): this(field.type, field.name)

    override fun toString(): String {
        return "Parameter(type=$type, name=$name)"
    }
}