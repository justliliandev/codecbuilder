package dev.agnor.codecbuilder

import com.intellij.psi.PsiClass

data class RegistryCodecValue(val codecString: String, val source: PsiClass) {

}