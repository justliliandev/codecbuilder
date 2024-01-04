package dev.agnor.codecbuilder

class Member(val codec: String, val name: String, private val optional: Boolean, val getter: String) {
    override fun toString(): String {
        return "$codec.${if (optional) "optionalFieldOf" else "fieldOf"}(\"$name\").forGetter$getter"
    }
}