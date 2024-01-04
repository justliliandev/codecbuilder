package dev.agnor.codecbuilder

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import dev.agnor.codecbuilder.lang.SourceProcessor

abstract class CodecBuilderIntention : PsiElementBaseIntentionAction() {

    override fun getText() = "Registry-/Codec-Root: "
    override fun getFamilyName() = "CodecBuilder"
    fun findClass(project: Project, element: PsiElement) : PsiClass? {
        for (sourceProcessor in SourceProcessor.sourceProcessors) {
            val import = sourceProcessor.findImport(element);
            if (import != null) {
                val importClazz = JavaPsiFacade.getInstance(project).findClass(import, GlobalSearchScope.allScope(project))
                if (importClazz != null)
                    return importClazz
            }
            val bodyClass = sourceProcessor.findClassBody(element, project);
            if (bodyClass != null)
                return bodyClass
        }
        return null
    }

}