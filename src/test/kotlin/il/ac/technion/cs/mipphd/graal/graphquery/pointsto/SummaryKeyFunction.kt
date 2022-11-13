package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.SourcePosTool
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.nodes.ValueNode

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
