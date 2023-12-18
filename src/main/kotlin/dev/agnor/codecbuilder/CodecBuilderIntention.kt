package dev.agnor.codecbuilder

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType

abstract class CodecBuilderIntention : PsiElementBaseIntentionAction() {

    override fun getText() = "Registry-/Codec-Root: "
    override fun getFamilyName() = "CodecBuilder"
    fun findClass(project: Project, element: PsiElement) : PsiClass? {
        val import = element.parentOfType<PsiImportStatement>();
        if (import != null) {
            val qualifiedName = import.qualifiedName
            if (qualifiedName != null) {
                val importClazz = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project))
                if (importClazz != null)
                    return importClazz;
            }
        }
        return element.parentOfType<PsiClass>()
    }

}