package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.graph.Node

class PointsToNode(private val key: Any, val stolenId: Int) : NodeWrapper(null) {
    val representing = mutableSetOf<NodeWrapper>()
    override fun isType(className: String?): Boolean {
        return className == javaClass.canonicalName
    }

    override fun toString(): String {
        return "PointsToNode(representing ${representing.size} nodes, key=$key)"
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}
