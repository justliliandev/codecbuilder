package dev.agnor.codecbuilder

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType

class AddCodecRootIntention : CodecRootIntention() {

    override fun getText(): String {
        return super.getText() + "Add"
    }
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val clazz = element.parentOfType<PsiClass>() ?: return false
        return !getCodecRoots(project).contains(clazz);
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val clazz = element.parentOfType<PsiClass>()?.qualifiedName ?: return
        addStoredCodecRoot(clazz);
    }
}