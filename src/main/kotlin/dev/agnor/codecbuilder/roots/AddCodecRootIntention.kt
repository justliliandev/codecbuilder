package dev.agnor.codecbuilder.roots

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import dev.agnor.codecbuilder.CodecBuilderIntention
import dev.agnor.codecbuilder.addStoredCodecRoot
import dev.agnor.codecbuilder.getStoredCodecRoots

class AddCodecRootIntention : CodecBuilderIntention() {

    override fun getText(): String {
        return super.getText() + "Add"
    }
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val qfn = findClass(project, element)?.qualifiedName?: return false
        return !getStoredCodecRoots().contains(qfn)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val clazz = findClass(project, element)?.qualifiedName ?: return
        addStoredCodecRoot(clazz);
    }
}