package dev.agnor.codecbuilder.lang

import com.intellij.codeInsight.generation.GenerateMembersUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.IncorrectOperationException
import dev.agnor.codecbuilder.*
import org.apache.commons.lang3.function.TriFunction

import kotlin.reflect.KClass

class JavaSourceProcessor: SourceProcessor<PsiClass, PsiImportStatement>() {
    override fun classType(): KClass<PsiClass> {
        return PsiClass::class
    }

    override fun importType(): KClass<PsiImportStatement> {
        return PsiImportStatement::class
    }

    override fun classOfImport(import: PsiImportStatement): String? {
        return import.qualifiedName
    }

    override fun findClassBody(element: PsiElement, project: Project): PsiClass? {
        return element.parentOfTypes(classType())
    }

    override fun findClassesInBody(element: PsiFile, project: Project): List<Pair<PsiClass, SourceProcessor<PsiClass, PsiImportStatement>>> {
        return element.childrenOfType<PsiClass>().map{Pair(it, this)}
    }

    override fun write(clazz: PsiClass, project: Project, codecExpression: String, file: PsiFile) {

        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val elementFactory = javaPsiFacade.elementFactory

        val allScope = GlobalSearchScope.allScope(project)
        val codecClazz = javaPsiFacade.findClass(CODEC_FQN, allScope)!!

        val clazzType = elementFactory.createType(clazz)
        val codecType = elementFactory.createType(codecClazz, PsiSubstitutor.EMPTY.putAll(codecClazz, arrayOf(clazzType)))

        val field = elementFactory.createField("CODEC", codecType)
        PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true)
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true)
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true)
        val initializer = elementFactory.createExpressionFromText(codecExpression, field)
        field.initializer = initializer
        WriteCommandAction.writeCommandAction(project, file).run<RuntimeException> {
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(field.initializer!!)
            GenerateMembersUtil.insert(clazz, field, clazz.lBrace, false)
        }
    }

    override fun generateUnitCodec(clazz: PsiClass): String {
        return "com.mojang.serialization.Codec.unit(${clazz.name}::new)"
    }

    override fun generateRCBCodec(clazz: PsiClass, members: List<Member>, constructorName: String): String {
        return members.joinToString(",", "$RCBUILDER_FQN.create(inst -> inst.group(", ").apply(inst, ${clazz.qualifiedName}::$constructorName))")
    }

    override fun generateGetterLambdaForField(field: PsiField): String {
        return "(obj -> ${field.name})"
    }

    override fun wrapLambda(lambda: String): String {
        return "($lambda)";
    }

    override fun findRecordConstructor(target: PsiClass, project: Project, source: PsiClass, getCodec: TriFunction<Project, PsiType, PsiClass, String>): Pair<String, List<Member>>? {
        if (target.isRecord) {
            return Pair("new", target.recordComponents.map { it.toMember(project, target, source, getCodec) })
        }
        return null
    }

    private fun PsiRecordComponent.toMember(project: Project, clazz: PsiClass, source: PsiClass, getCodec: TriFunction<Project, PsiType, PsiClass, String>): Member {
        val getter = "(${clazz.qualifiedName}  :: $name)"
        val codec = getCodec.apply(project, type, source)
        val optional = codec.endsWith(OPTIONAL_MARKER)
        if (codec.indexOf(OPTIONAL_MARKER) != codec.lastIndexOf(OPTIONAL_MARKER)) {
            throw IncorrectOperationException("Optional in unexpected place")
        }
        return Member(codec.replace(OPTIONAL_MARKER + "", ""), name!!, optional, getter)
    }

}