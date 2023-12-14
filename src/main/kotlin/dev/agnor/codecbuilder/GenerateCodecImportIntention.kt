package dev.agnor.codecbuilder

class GenerateCodecImportIntention : GenerateCodecIntention() {

    override fun getText() = "Generate Codec for type with imports"
    override fun shouldImport() = true;
}