package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.SourcePosTool
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.nodes.ValueNode
import kotlin.reflect.jvm.internal.ReflectProperties.Val

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
        if(node.node is ValueNode)
            return SourcePosTool.getStackTraceElement(node.node as ValueNode)
        throw RuntimeException("illegal node for summary")
    }
}


