package dev.agnor.codecbuilder.lang

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.parentOfTypes
import dev.agnor.codecbuilder.Member
import org.apache.commons.lang3.function.TriFunction
import kotlin.reflect.KClass

abstract class SourceProcessor<ClassType : PsiElement, ImportType : PsiElement> {
    protected abstract fun classType() : KClass<ClassType>

    protected abstract fun importType() : KClass<ImportType>

    protected abstract fun classOfImport(import: ImportType): String?

    fun findImport(element: PsiElement): String? {
        val import = element.parentOfTypes(importType())?: return null
        return classOfImport(import)
    }

    abstract fun findClassBody(element: PsiElement, project: Project): PsiClass?

    abstract fun findClassesInBody(element: PsiFile, project: Project): List<Pair<PsiClass, SourceProcessor<ClassType, ImportType>>>

    abstract fun write(clazz: PsiClass, project: Project, codecExpression:String, file: PsiFile)

    abstract fun generateUnitCodec(clazz: PsiClass): String

    abstract fun generateRCBCodec(clazz: PsiClass, members: List<Member>, constructorName: String): String

    abstract fun generateGetterLambdaForField(field: PsiField): String

    abstract fun wrapLambda(lambda: String): String
    companion object {
        val sourceProcessors = listOf(KotlinProcessor(), JavaSourceProcessor())
    }
    abstract fun findRecordConstructor(
        target: PsiClass,
        project: Project,
        source: PsiClass,
        getCodec: TriFunction<Project, PsiType, PsiClass, String>
    ): Pair<String, List<Member>>?
}