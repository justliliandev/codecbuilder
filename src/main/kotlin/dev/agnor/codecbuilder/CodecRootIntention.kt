package dev.agnor.codecbuilder

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction

abstract class CodecRootIntention : PsiElementBaseIntentionAction() {

    override fun getText() = "Registry-/Codec-Root: "
    override fun getFamilyName() = "CodecBuilder"

}