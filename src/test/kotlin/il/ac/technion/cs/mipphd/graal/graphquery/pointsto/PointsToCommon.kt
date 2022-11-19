package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.SourcePosTool
import il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.nodes.ValueNode
import org.graalvm.compiler.nodes.java.AccessFieldNode
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import java.io.StringWriter

internal val methodToGraph = MethodToGraph()
internal val accessFieldNodeClass = Class.forName("org.graalvm.compiler.nodes.java.AccessFieldNode")
internal val getFieldMethod = accessFieldNodeClass.getDeclaredMethod("field")
internal val fieldClazz = Class.forName("jdk.vm.ci.meta.JavaField")
internal val getFieldNameMethod = fieldClazz.getDeclaredMethod("getName")

class GenericObjectWithField(val obj: NodeWrapper?, val field: String) : NodeWrapper(null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GenericObjectWithField) return false
        if (obj != other.obj) return false
        if (field != other.field) return false
        return true
    }

    override fun hashCode(): Int {
        var result = obj.hashCode()
        result = 31 * result + field.hashCode()
        return result
    }

    override fun toString(): String {
        return "($obj, $field)"
    }

    override fun isType(className: String?): Boolean {
        return className == "GenericObjectWithField"
    }
}

class AssociationInformation(
    val memoryLocations: MutableSet<NodeWrapper> = mutableSetOf(),
    val storedValues: MutableSet<NodeWrapper> = mutableSetOf()
)

class PointsToNode(private val key: Any) : NodeWrapper(null) {
    val representing = mutableSetOf<NodeWrapper>()
    override fun isType(className: String?): Boolean {
        return className == javaClass.canonicalName
    }

    override fun toString(): String {
        return "PointsToNode(representing ${if(representing.size == 1) representing.first().toString() else "${representing.size} nodes"}" +
                "${if(key == representing.firstOrNull()) "" else ", key=$key"})"
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as PointsToNode

        if (key != other.key) return false
        if (representing != other.representing) return false

        return true
    }
}


interface SummaryKeyFunction {
    fun getSummaryKey(node: NodeWrapper): Any
}

object SummaryKeyByNodeIdentity : SummaryKeyFunction {
    override fun getSummaryKey(node: NodeWrapper): Any {
        return node
    }
}

object SummaryKeyByNodeSourcePos : SummaryKeyFunction {
    override fun getSummaryKey(node: NodeWrapper): Any {
        if(node.node is ValueNode) {
            val stacktrace = SourcePosTool.getStackTraceElement(node.node as ValueNode)
            return stacktrace.methodName + ":" + stacktrace.lineNumber
        }
        throw RuntimeException("illegal node for summary")
    }
}

internal val edgeColor = mapOf(
    EdgeWrapper.DATA to "blue",
    EdgeWrapper.CONTROL to "red",
    EdgeWrapper.ASSOCIATED to "black"
)
internal val edgeStyle = mapOf(
    EdgeWrapper.DATA to "",
    EdgeWrapper.CONTROL to "",
    EdgeWrapper.ASSOCIATED to "dashed"
)

internal fun writeQueryInternal(graalph: GraalAdapter, output: StringWriter) {
    val exporter = DOTExporter<NodeWrapper, EdgeWrapper> { v: NodeWrapper ->
        v.node?.id?.toString() ?: (v as PointsToNode).hashCode().toString()
    }

    exporter.setVertexAttributeProvider { v: NodeWrapper ->
        val attrs: MutableMap<String, Attribute> =
            HashMap()
        attrs["label"] = DefaultAttribute.createAttribute(v.toString())
        attrs
    }

    exporter.setEdgeAttributeProvider { e: EdgeWrapper ->
        val attrs: MutableMap<String, Attribute> =
            HashMap()
        attrs["label"] = DefaultAttribute.createAttribute(e.name)
        attrs["color"] = DefaultAttribute.createAttribute(edgeColor[e.label])
        attrs["style"] = DefaultAttribute.createAttribute(edgeStyle[e.label])
        attrs
    }
    exporter.exportGraph(graalph, output)
}

internal fun getFieldEdgeName(node: NodeWrapper): String {
    if(node.node !is AccessFieldNode) return "is"
    return getFieldNameMethod(getFieldMethod(node.node)).toString()
}
