package dev.agnor.codecbuilder

import com.intellij.codeInsight.generation.GenerateMembersUtil
import com.intellij.codeInspection.isInheritorOf
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import com.intellij.util.IncorrectOperationException
import kotlin.streams.toList

private const val OPTIONAL_MARKER = '§'
private const val UNKNOWN_CODEC = "UNKNOWN_CODEC"
private const val UNKNOWN_PRIMITIVE_CODEC = "UNKNOWN_PRIMITIVE_CODEC"
private const val UNKNOWN_CLASS_CODEC = "UNKNOWN_CLASS_CODEC"
private const val MISSING_PRIMITIVE_STREAM_CODEC = "MISSING_PRIMITIVE_STREAM_CODEC"
private const val MULTI_DIMENSIONAL_ARRAY_CODEC = "MULTI_DIMENSIONAL_ARRAY_CODEC"
private const val MISSING_GETTER = "MISSING_GETTER"
private val ERROR_CODECS = setOf(
    UNKNOWN_CODEC,
    UNKNOWN_PRIMITIVE_CODEC,
    UNKNOWN_CLASS_CODEC,
    MISSING_PRIMITIVE_STREAM_CODEC,
    MULTI_DIMENSIONAL_ARRAY_CODEC
)

@Suppress("UnstableApiUsage")
class GenerateCodecIntention : CodecBuilderIntention() {
    private class CodecSource(val fqn: String, val preferred: Set<String>)

    private class Member(val codec: String, val name: String, val optional: Boolean, val default: String?, val getter: String) {
        override fun toString(): String {
            return "$codec.${if (optional || default != null) "optionalFieldOf" else "fieldOf"}(\"$name\"${if (default != null) ", $default" else ""}).forGetter($getter)"
        }
    }

    override fun getText() = "Generate Codec for type"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return findClass(project, element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val clazz = findClass(project, element)!!
        val file = element.parentOfType<PsiJavaFile>()!!
        val fileClazz = file.childrenOfType<PsiClass>()
                    .firstOrNull{ cls -> cls.name == file.name.substringBeforeLast(".java")}
        if (fileClazz == null) {
            notify("Missing Class of same name in current file", NotificationType.WARNING, project)
            return
        }
        val field = generateForClass(project, clazz, fileClazz)
        val success = field.initializer?.text?.let { text -> ERROR_CODECS.none { text.contains(it) } } ?: false
        if (success) {
            notify("Successfully generated codec", NotificationType.INFORMATION, project)
        } else {
            notify("Generated codec with some issues", NotificationType.WARNING, project)
        }
        WriteCommandAction.writeCommandAction(project, file).run<RuntimeException> {
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(field.initializer!!)
            GenerateMembersUtil.insert(fileClazz, field, fileClazz.lBrace, false)
        }
    }

    private fun getRegistrySources(): List<String> {
        val listOf = listOf("net.minecraft.core.registries.BuiltInRegistries", "net.neoforged.neoforge.registries.NeoForgeRegistries")
        return listOf + getStoredCodecRoots()
    }

    private fun getCodecSources(clazz: PsiClass, source: PsiClass): List<CodecSource> {
        val sources = mutableListOf<CodecSource>()
        sources.add(CodecSource(clazz.qualifiedName!!, setOf("CODEC")))
        sources.add(CodecSource("com.mojang.serialization.Codec", setOf()))
        sources.add(CodecSource(source.qualifiedName!!, setOf()))
        sources.add(CodecSource("net.minecraft.util.ExtraCodecs", setOf("JSON", "QUATERNIONF")))
        sources.addAll(getStoredCodecRoots().stream().map { root -> CodecSource(root, setOf()) }.toList())
        return sources
    }

    private fun notify(text: String, type: NotificationType, project: Project) {

        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("CodecConstructionComplete")
        notificationGroup.createNotification(text, type).notify(project)
    }

    private fun generateForClass(project: Project, target: PsiClass, source: PsiClass): PsiField {
        val constructors = target.constructors

         val factories = target.methods.filter {
            it.hasModifier(JvmModifier.STATIC) && PsiTypesUtil.getPsiClass(it.returnType) == target
        }
        val allConstructors = (constructors + factories).sortedByDescending { it.parameterList.parametersCount }
        val constructorMembers = allConstructors.map { toMembers(project, it, target, source) }
        val candidates = mutableListOf<List<Member>>()
        if (target.isRecord) {
            candidates.add(target.recordComponents.map { it.toMember(project, target, source) })
        }
        candidates.addAll(constructorMembers)
        candidates.sortBy { it.size } // sort this to prefer constructors with less parameters if they are missing the same amount of codecs/getters or to find the shorter valid codec
        val candidate = candidates.minByOrNull { candidate ->
            candidate.sumOf { member -> ERROR_CODECS.count { member.codec.contains(it) } + (if (member.getter == MISSING_GETTER) 1 else 0) }
        } ?: throw IncorrectOperationException()
        if (candidate.isEmpty()) {
            return generateUnitCodec(project, target)
        }
        if (candidate.size <= 16) {
            return generateRCBCodec(project, target, candidate)
        } else {
            notify("more then 16 elements in codec, 16 elements are the maximum", NotificationType.ERROR, project)
        }
        throw IncorrectOperationException()
    }

    private fun toMembers(project: Project, constructor: PsiMethod, target: PsiClass, source: PsiClass): List<Member> {

        val getters = target.allMethods.filter {
            it.returnType != PsiPrimitiveType.VOID && it.parameterList.isEmpty
        }.sortedBy {
            it.name.startsWith("get") && !it.isDeprecated
        }
        return constructor.parameterList.parameters.map { parameter ->
            parameter.toMember(project,
                    getters.find { it.returnType == parameter.type && (it.name == parameter.name || it.name == "get${parameter.name.capitalize()}") },
                    target.findFieldByName(parameter.name, true), target, source)
        }
    }

    private fun generateUnitCodec(project: Project, clazz: PsiClass): PsiField {
        return createCodec(project, clazz, "com.mojang.serialization.Codec.unit(${clazz.name}::new)")
    }

    private fun generateRCBCodec(project: Project, clazz: PsiClass, members: List<Member>): PsiField {
        val expression = members.joinToString(",", "com.mojang.serialization.codecs.RecordCodecBuilder.create(inst -> inst.group(", ").apply(inst, ${clazz.qualifiedName}::new))")
        return createCodec(project, clazz, expression)
    }

    private fun createCodec(project: Project, clazz: PsiClass, codecExpression: String): PsiField {
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val elementFactory = javaPsiFacade.elementFactory

        val allScope = GlobalSearchScope.allScope(project)
        val codecClazz = javaPsiFacade.findClass("com.mojang.serialization.Codec", allScope)!!

        val clazzType = elementFactory.createType(clazz)
        val codecType = elementFactory.createType(codecClazz, PsiSubstitutor.EMPTY.putAll(codecClazz, arrayOf(clazzType)))

        val field = elementFactory.createField("CODEC", codecType)
        PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true)
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true)
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true)
        val initializer = elementFactory.createExpressionFromText(codecExpression, field)
        field.initializer = initializer

        return field
    }

    private fun getCodec(project: Project, type: PsiType, targetClass: PsiClass, source: PsiClass): String {
        val codec = when (type) {
            is PsiPrimitiveType -> getPrimitiveCodec(type)
            is PsiClassType -> getObjectCodec(project, type, targetClass, source)
            is PsiArrayType -> getArrayCodec(project, type, targetClass, source)
            else -> UNKNOWN_CODEC
        }
        return codec
    }

    @Suppress("UnstableApiUsage")
    private fun getPrimitiveCodec(type: PsiPrimitiveType) = getPrimitiveCodec(type.kind)

    @Suppress("UnstableApiUsage")
    private fun getPrimitiveCodec(kind: JvmPrimitiveTypeKind) = when (kind) {
        JvmPrimitiveTypeKind.BOOLEAN -> "com.mojang.serialization.Codec.BOOL"
        JvmPrimitiveTypeKind.BYTE -> "com.mojang.serialization.Codec.BYTE"
        JvmPrimitiveTypeKind.SHORT -> "com.mojang.serialization.Codec.SHORT"
        JvmPrimitiveTypeKind.INT -> "com.mojang.serialization.Codec.INT"
        JvmPrimitiveTypeKind.LONG -> "com.mojang.serialization.Codec.LONG"
        JvmPrimitiveTypeKind.FLOAT -> "com.mojang.serialization.Codec.FLOAT"
        JvmPrimitiveTypeKind.DOUBLE -> "com.mojang.serialization.Codec.DOUBLE"
        JvmPrimitiveTypeKind.CHAR -> "com.mojang.serialization.Codec.STRING.comapFlatMap(s -> s.length() != 1 ? com.mojang.serialization.DataResult.error(() -> \"'\" + s + \"' is an invalid symbol (must be 1 character only).\") : com.mojang.serialization.DataResult.success(s.charAt(0)), String::valueOf)"
        else -> UNKNOWN_PRIMITIVE_CODEC
    }

    private fun getObjectCodec(project: Project, type: PsiClassType, targetClass: PsiClass, source: PsiClass): String {
        val clazz = type.resolve() ?: return UNKNOWN_CLASS_CODEC
        when (clazz.qualifiedName) {
            null -> return UNKNOWN_CLASS_CODEC
            "java.lang.String" -> return "com.mojang.serialization.Codec.STRING"
            "java.util.Optional" -> return getCodec(project, type.parameters.first(), targetClass, source) + OPTIONAL_MARKER
            "java.util.OptionalInt" -> return getPrimitiveCodec(PsiType.INT) + OPTIONAL_MARKER
            "java.util.OptionalLong" -> return getPrimitiveCodec(PsiType.LONG) + OPTIONAL_MARKER
            "java.util.OptionalDouble" -> return getPrimitiveCodec(PsiType.DOUBLE) + OPTIONAL_MARKER
            "com.mojang.datafixers.util.Pair" -> return getPairCodec(type, project, targetClass, source)
            "com.mojang.datafixers.util.Either" -> return getEitherCodec(type, project, targetClass, source)
            "java.util.List" -> {
                val listType = type.parameters.first()
                if (listType is PsiClassType) {
                    val inner = PsiUtil.resolveClassInClassTypeOnly(listType)
                    if (inner?.qualifiedName == "com.mojang.datafixers.util.Pair") {
                        val (first, second) = listType.parameters
                        return "com.mojang.serialization.Codec.compoundList(${getCodec(project, first, targetClass, source)}, ${getCodec(project, second, targetClass, source)})"
                    }
                }
                return "${getCodec(project, listType, targetClass, source)}.listOf()"
            }

            "java.util.Map" -> return getMapCodec(type, project, targetClass, source)
            "java.util.Set" -> return getSetCodec(type, project, targetClass, source)
            else -> return getObjectCodecFallback(clazz, project, source, type, targetClass)

        }
    }

    private fun getObjectCodecFallback(clazz: PsiClass, project: Project, source: PsiClass, type: PsiClassType, targetClass: PsiClass): String {

        val primitive = JvmPrimitiveTypeKind.getKindByFqn(clazz.qualifiedName)
        if (primitive != null)
            return getPrimitiveCodec(primitive)

        val scope = GlobalSearchScope.allScope(project)
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val codecSources = getCodecSources(clazz, source)
        for (codecSource in codecSources) {
            val from = javaPsiFacade.findClass(codecSource.fqn, scope) ?: continue
            val codecs = getStaticCodecs(from, targetClass, type).sortedBy { it.name in codecSource.preferred }
            if (codecs.isEmpty())
                continue
            return "${from.qualifiedName}.${codecs.first().name}"
        }
        if (clazz.isEnum)
            return getEnumCodec(type, clazz, targetClass)

        val isHolder = clazz.qualifiedName == "net.minecraft.core.Holder"
        val registryCodec = getRegistryCodec(project, targetClass, if (isHolder) type.parameters.first() else type, isHolder)
        if (registryCodec != null)
            return registryCodec
        return UNKNOWN_CLASS_CODEC
    }

    private fun getPairCodec(type: PsiClassType, project: Project, targetClass: PsiClass, source: PsiClass): String {
        val (first, second) = type.parameters
        val firstCodec = getCodec(project, first, targetClass, source)
        val secondCodec = getCodec(project, second, targetClass, source)
        return "com.mojang.serialization.Codec.pair($firstCodec, $secondCodec)"
    }

    private fun getEitherCodec(type: PsiClassType, project: Project, targetClass: PsiClass, source: PsiClass): String {
        val (first, second) = type.parameters
        val firstCodec = getCodec(project, first, targetClass, source)
        val secondCodec = getCodec(project, second, targetClass, source)
        return "com.mojang.serialization.Codec.either($firstCodec, $secondCodec)"
    }

    private fun getMapCodec(type: PsiClassType, project: Project, targetClass: PsiClass, source: PsiClass): String {
        val (first, second) = type.parameters
        val firstCodec = getCodec(project, first, targetClass, source)
        val secondCodec = getCodec(project, second, targetClass, source)
        return "com.mojang.serialization.Codec.unboundedMap($firstCodec, $secondCodec)"
    }

    private fun getSetCodec(type: PsiClassType, project: Project, targetClass: PsiClass, source: PsiClass): String {
        val setType = type.parameters.first()
        return "${getCodec(project, setType, targetClass, source)}.listOf().xmap(java.util.Set::copyOf, java.util.List::copyOf)"
    }

    private fun getEnumCodec(type: PsiClassType, clazz: PsiClass, targetClass: PsiClass): String {
        if (type.isInheritorOf("net.minecraft.util.StringRepresentable")) {
            val codec = getStaticCodecs(clazz, targetClass, type).firstOrNull()
            if (codec != null) return "${clazz.name}.${codec.name}"
            return "net.minecraft.util.StringRepresentable.fromEnum(${clazz.qualifiedName}::values)"
        }
        return "net.minecraft.util.ExtraCodecs.orCompressed(net.minecraft.util.ExtraCodecs.stringResolverCodec(${clazz.qualifiedName}::name, ${clazz.qualifiedName}::valueOf), net.minecraft.util.ExtraCodecs.idResolverCodec(${clazz.qualifiedName}::ordinal, i -> i >= 0 && i < ${clazz.qualifiedName}.values().length ? ${clazz.qualifiedName}.values()[i] : null, -1))"
    }

    @Suppress("UnstableApiUsage")
    private fun getArrayCodec(project: Project, type: PsiArrayType, targetClass: PsiClass, source: PsiClass): String {
        when (val componentType = type.componentType) {
            is PsiPrimitiveType -> {
                if (componentType.kind !in setOf(JvmPrimitiveTypeKind.DOUBLE, JvmPrimitiveTypeKind.INT, JvmPrimitiveTypeKind.LONG)) {
                    return MISSING_PRIMITIVE_STREAM_CODEC
                }
                return "${getCodec(project, componentType, targetClass, source)}.listOf().xmap(list -> list.stream().mapTo${componentType.name.capitalize()}(val -> val).toArray(), arr -> java.util.Arrays.stream(arr).boxed().toList())"
            }

            is PsiClassType -> return "${getCodec(project, componentType, targetClass, source)}.listOf().xmap(list -> list.toArray(new ${componentType.name}[0]), arr -> java.util.Arrays.stream(arr).toList())"
            else -> return MULTI_DIMENSIONAL_ARRAY_CODEC
        }
    }

    @Suppress("UnstableApiUsage")
    private fun getRegistryCodec(project: Project, targetClass: PsiClass, type: PsiType, wrapped: Boolean): String? {
        val scope = GlobalSearchScope.allScope(project)
        val facade = JavaPsiFacade.getInstance(project)

        val registrySources = getRegistrySources()
        val registry = registrySources.map { facade.findClass(it, scope) }.associateWith { sourceClass ->
                    sourceClass?.fields?.firstOrNull {
                        it.hasModifier(JvmModifier.STATIC) && PsiUtil.isMemberAccessibleAt(it, targetClass) && isRegistryOf(it.type, type)
                    }
                }.entries.firstOrNull { (clazz, field) -> clazz != null && field != null }
        if (registry != null) {
            val (clazz, field) = registry
            return "${clazz!!.qualifiedName}.${field!!.name}.${if (wrapped) "holderByNameCodec" else "byNameCodec"}()"
        }
        return null
    }

    @Suppress("UnstableApiUsage")
    private fun getStaticCodecs(clazz: PsiClass, targetClass: PsiClass, type: PsiClassType) = clazz.fields.filter {
        it.hasModifier(JvmModifier.STATIC) && PsiUtil.isMemberAccessibleAt(it, targetClass) && isCodecOf(it.type, type)
    }

    private fun isCodecOf(type: PsiType, type1: PsiClassType): Boolean {
        if (type !is PsiClassType) return false
        if (PsiUtil.resolveClassInClassTypeOnly(type)?.qualifiedName != "com.mojang.serialization.Codec") return false
        val codecType = type.parameters.firstOrNull() ?: return false
        return codecType == type1
    }

    private fun isRegistryOf(type: PsiType, type1: PsiType): Boolean {
        if (type !is PsiClassType) return false
        if (!type.isInheritorOf("net.minecraft.core.Registry")) return false
        val regType = type.parameters.firstOrNull() ?: return false
        return regType == type1
    }

    private fun PsiRecordComponent.toMember(project: Project, clazz: PsiClass, source: PsiClass): Member {
        val getter = "${clazz.qualifiedName}::$name"
        val codec = getCodec(project, type, clazz, source)
        val optional = codec.endsWith(OPTIONAL_MARKER)
        if (codec.indexOf(OPTIONAL_MARKER) != codec.lastIndexOf(OPTIONAL_MARKER)) {
            throw IncorrectOperationException("Optional in unexpected place")
        }
        val default: String? = null
        return Member(codec.replace(OPTIONAL_MARKER + "", ""), name!!, optional, default, getter)
    }

    private fun PsiParameter.toMember(project: Project, getter: PsiMethod?, fieldForGetter: PsiField?, clazz: PsiClass, source: PsiClass): Member {
        val codec = getCodec(project, type, clazz, source)
        val optional = codec.endsWith(OPTIONAL_MARKER)
        if (codec.indexOf(OPTIONAL_MARKER) != codec.lastIndexOf(OPTIONAL_MARKER)) {
            throw IncorrectOperationException("Optional in unexpected place")
        }
        val default: String? = null
        var getterString = MISSING_GETTER
        if (fieldForGetter != null && PsiUtil.isMemberAccessibleAt(fieldForGetter, source)) {
            getterString = "obj -> obj.${fieldForGetter.name}"
        }
        if (getter != null) {
            getterString = clazz.qualifiedName + "::" +getter.name
        }
        return Member(codec.replace(OPTIONAL_MARKER + "", ""), name, optional, default, getterString)
    }

    private fun String.capitalize(): String {
        return this.replaceFirstChar {
            it.uppercaseChar()
        }
    }
}
