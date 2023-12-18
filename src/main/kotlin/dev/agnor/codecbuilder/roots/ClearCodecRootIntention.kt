package dev.agnor.codecbuilder.roots

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import dev.agnor.codecbuilder.CodecBuilderIntention
import dev.agnor.codecbuilder.clearStoredCodecRoots

class ClearCodecRootIntention : CodecBuilderIntention() {

    override fun getText(): String {
        return super.getText() + "Clear"
    }
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return findClass(project, element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        clearStoredCodecRoots()
    }
}