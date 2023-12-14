package dev.agnor.codecbuilder

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class AddCodecRootIntention : CodecRootIntention() {

    override fun getText(): String {
        return super.getText() + "Add"
    }
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return !getCodecRoots(project).contains(findClass(project, element))
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val clazz = findClass(project, element)?.qualifiedName ?: return
        addStoredCodecRoot(clazz);
    }
}