package dev.agnor.codecbuilder

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType

class ClearCodecRootIntention : CodecRootIntention() {

    override fun getText(): String {
        return super.getText() + "Clear"
    }
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return element.parentOfType<PsiClass>() != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        clearStoredCodecRoots()
    }
}