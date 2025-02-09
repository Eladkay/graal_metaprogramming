package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.SourcePosTool
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisEdge
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisGraph
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisNode
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.WrappedIREdge
import org.graalvm.compiler.nodes.PhiNode
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

class GenericObjectWithField(val obj: AnalysisNode?, val field: String) : AnalysisNode.Specific() {
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
}

class AssociationInformation(
    val memoryLocations: MutableSet<AnalysisNode> = mutableSetOf(),
    val storedValues: MutableSet<AnalysisNode> = mutableSetOf()
)

class PointsToNode(private val key: Any) : AnalysisNode.Specific() {
    val representing = mutableSetOf<AnalysisNode>()

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

class PointsToEdge(label: String) : AnalysisEdge.Extra(label)

interface SummaryKeyFunction {
    fun getSummaryKey(node: AnalysisNode): Any
}

object SummaryKeyByNodeIdentity : SummaryKeyFunction {
    override fun getSummaryKey(node: AnalysisNode): Any {
        return node
    }
}

object SummaryKeyByNodeSourcePos : SummaryKeyFunction {
    private fun error(node: AnalysisNode): Nothing = throw RuntimeException("illegal node for summary $node")
    override fun getSummaryKey(node: AnalysisNode): Any {
        if(node !is AnalysisNode.IR) error(node)
        if(node.node() is ValueNode) {
            val stacktrace = SourcePosTool.getStackTraceElement(node.node() as ValueNode)
            return stacktrace.methodName + ":" + stacktrace.lineNumber
        } else error(node)
    }
}

object SummaryKeyByNodeSourcePosOrIdentity : SummaryKeyFunction {
    override fun getSummaryKey(node: AnalysisNode): Any {
        return try {
            SummaryKeyByNodeSourcePos.getSummaryKey(node)
        } catch (e: RuntimeException) {
            SummaryKeyByNodeIdentity.getSummaryKey(node)
        }
    }
}

internal fun getFieldEdgeName(node: AnalysisNode): String {
    if(node is GenericObjectWithField) return node.field
    if(node !is AnalysisNode.IR) throw RuntimeException("unexpected operation on $node")
    val graalNode = node.node()
    if(graalNode is AccessFieldNode) return getFieldNameMethod(getFieldMethod(graalNode)).toString()
    if(graalNode is PhiNode) return "is"
    throw RuntimeException("unexpected operation on $node")
}

internal fun cloneEdge(edge: AnalysisEdge): AnalysisEdge {
    return when(edge) {
        is AnalysisEdge.Association -> AnalysisEdge.Association(edge.label)
        is AnalysisEdge.Control -> AnalysisEdge.Control(edge.label)
        is AnalysisEdge.Phi -> AnalysisEdge.Phi(edge.label, edge.from)
        is AnalysisEdge.PureData -> AnalysisEdge.PureData(edge.label)
        AnalysisEdge.Default -> edge
        is AnalysisEdge.Extra -> PointsToEdge(edge.label) /* todo */
    }
}