package dev.agnor.codecbuilder.lang

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.IncorrectOperationException
import dev.agnor.codecbuilder.Member
import dev.agnor.codecbuilder.OPTIONAL_MARKER
import dev.agnor.codecbuilder.RCBUILDER_FQN
import dev.agnor.codecbuilder.preprocessor.constructors
import dev.agnor.codecbuilder.psiwrapper.Parameter
import org.apache.commons.lang3.function.TriFunction
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.kotlin.KotlinConverter.createAnalyzableProperty
import kotlin.reflect.KClass


class KotlinProcessor: SourceProcessor<KtClass, KtImportDirective>() {
    override fun classType(): KClass<KtClass> {
        return KtClass::class
    }

    override fun importType(): KClass<KtImportDirective> {
        return KtImportDirective::class
    }

    override fun findClassBody(element: PsiElement, project: Project): PsiClass? {

        val asString = element.parentOfTypes(classType())?.fqName?.asString()?: return null
        return JavaPsiFacade.getInstance(project).findClass(asString, GlobalSearchScope.allScope(project))
    }

    override fun findClassesInBody(element: PsiFile, project: Project): List<Pair<PsiClass, SourceProcessor<KtClass, KtImportDirective>>> {
        return element.childrenOfType<KtClass>()
            .mapNotNull { convert(it, project) }
            .map {Pair(it, this)}
    }

    override fun generateUnitCodec(clazz: PsiClass): String {
        return "com.mojang.serialization.Codec.unit{${clazz.name}()}"
    }

    override fun generateRCBCodec(clazz: PsiClass, members: List<Member>, constructorName: String): String {
        val constructorCall = if (constructorName == "new") "::${clazz.name}" else "::$constructorName"
        return members.joinToString(",", "$RCBUILDER_FQN.create{inst: RecordCodecBuilder.Instance<${clazz.name}> -> inst.group(", ").apply(inst, $constructorCall)}")
    }

    override fun generateGetterLambdaForField(field: PsiField): String {
        return wrapLambda(field.name)
    }

    override fun wrapLambda(lambda: String): String {
        return "{$lambda}"
    }

    override fun findRecordConstructor(
        target: PsiClass,
        project: Project,
        source: PsiClass,
        getCodec: TriFunction<Project, PsiType, PsiClass, String>
    ): Pair<String, List<Member>>? {

        if (target is KtUltraLightClass) {
        val origin = target.kotlinOrigin;
        if (origin is KtClass && origin.isData()) {
            return Pair("new", constructors(target)[0].parameters.map { it.toMember(project, target, source, getCodec) })
        }
    }
    return null
    }

    private fun Parameter.toMember(project: Project, clazz: PsiClass, source: PsiClass, getCodec: TriFunction<Project, PsiType, PsiClass, String>): Member {
        val codec = getCodec.apply(project, type, source)
        val optional = codec.endsWith(OPTIONAL_MARKER)
        if (codec.indexOf(OPTIONAL_MARKER) != codec.lastIndexOf(OPTIONAL_MARKER)) {
            throw IncorrectOperationException("Optional in unexpected place")
        }
        val getterString = "(${clazz.qualifiedName}::${name})"

        return Member(codec.replace(OPTIONAL_MARKER + "", ""), name, optional, getterString)
    }

    override fun write(clazz: PsiClass, project: Project, codecExpression: String, file: PsiFile) {
        val psiFactory = KtPsiFactory(project)
        if (clazz is KtUltraLightClass) {
            val origin = clazz.kotlinOrigin;
            if (origin is KtClass) {
                runWriteAction {
                    try {
                        val property = createProperty(psiFactory, "val CODEC = $codecExpression", file)
                        origin.add(property)
                        ShortenReferences.DEFAULT.process(origin)
                    } catch (e: Exception) {
                        println("error")
                    }
                }

            }
        }
    }
    private fun convert(clazz: KtClass, project: Project): PsiClass? {
        val fqn = clazz.fqName?.asString()?: return null
        return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
    }
    override fun classOfImport(import: KtImportDirective): String? {

        return import.importedFqName?.asString()
    }

    private fun createProperty(factory: KtPsiFactory, text: String, fileContext: PsiFile): KtProperty {
        val file: KtFile = factory.createAnalyzableFile("dummy.kt", text, fileContext)
        return file.declarations.first() as KtProperty
    }
}