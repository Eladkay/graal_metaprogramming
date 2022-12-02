package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.graphquery.*
import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph
import org.graalvm.compiler.nodes.FrameState
import org.graalvm.compiler.nodes.PhiNode
import org.graalvm.compiler.nodes.calc.FloatingNode
import org.graalvm.compiler.nodes.java.LoadFieldNode
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode
import java.io.StringWriter
import java.lang.reflect.Method

open class PointsToAnalysis(
    graal: AnalysisGraph,
    private val summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity,
    val addAssociations: Boolean = true,
    nopNodes: Collection<String> = DEFAULT_NOP_NODES,
    notValueNodes: Collection<String> = DEFAULT_NOT_VALUE_NODES
) :
    QueryExecutor<AssociationInformation>(graal, { AssociationInformation() }) {

    private val summaries = mutableMapOf<Any, PointsToNode>()
    private val associations = mutableMapOf<AnalysisNode.IR, PointsToNode>()
    protected fun getEquivalentNode(nodeWrapper: AnalysisNode): AnalysisNode {
        if(nodeWrapper !is AnalysisNode.IR) return nodeWrapper

        assert(nodeWrapper.node() != null)
        val node = nodeWrapper.node()
        if (nodeWrapper in associations) {
            return associations[nodeWrapper]!!
        }
        if (node !is FloatingNode && node !is FrameState) {
            return nodeWrapper // heuristic, todo
        }
        if (node !is AllocatedObjectNode && node !is PhiNode && node !is FrameState) {
            val ret = PointsToNode(nodeWrapper)
            associations[nodeWrapper] = ret
            ret.representing.add(nodeWrapper)
            return ret
        }
        val ret = summaries.getOrPut(summaryFunc.getSummaryKey(nodeWrapper)) {
            PointsToNode(summaryFunc.getSummaryKey(nodeWrapper))
        }
        ret.representing.add(nodeWrapper)
        associations[nodeWrapper] = ret
        return ret
    }

    companion object {
        @JvmStatic
        protected fun addNodeSuffix(it: String) = if (it.endsWith("State")) it else "${it}Node"

        val DEFAULT_NOP_NODES = listOf(
            "Pi",
            "VirtualInstance",
            "ValuePhi",
            "VirtualObjectState",
            "MaterializedObjectState"
        ).map(::addNodeSuffix)
        val DEFAULT_NOT_VALUE_NODES = listOf(
            "Pi",
            "VirtualInstance",
            "ValuePhi",
            "Begin",
            "Merge",
            "End",
            "FrameState",
            "VirtualObjectState",
            "MaterializedObjectState",
            "ExceptionObject"
        ).map(::addNodeSuffix)
    }

    val storeQuery by WholeMatchQuery(
        """
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode')" ];
    nop [ label="(?P<nop>)|${nopNodes.joinToString(" or ") { "is('$it')" }}" ];
	value [ label="(?P<value>)|${notValueNodes.joinToString(" and ") { "not is('$it')" }}" ];

	value -> nop [ label="*|is('DATA')" ];
    nop -> storeNode [ label="name() = 'value'" ];
}
"""
    ) { captureGroups: Map<String, List<AnalysisNode>> ->
        val storeNode = captureGroups["store"]!!.first()
        val equivNode = getEquivalentNode(storeNode)
        state.getOrPut(equivNode) { AssociationInformation() }.storedValues.addAll(captureGroups["value"]!!.map(::getEquivalentNode))
    }
    val assocQuery by WholeMatchQuery(
        """
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode') or is('LoadFieldNode')" ];
    nop [ label="(?P<nop>)|${nopNodes.joinToString(" or ") { "is('$it')" }}" ];
	allocated [ label="(?P<value>)|is('AllocatedObjectNode')" ];

	allocated -> nop [ label="*|is('DATA')" ];
    nop -> storeNode [ label="name() = 'object'" ];
}
"""
    ) { captureGroups: Map<String, List<AnalysisNode>> ->
        val storeNode = captureGroups["store"]!!.first()
        val equivNode = getEquivalentNode(storeNode)
        state.getOrPut(equivNode) { AssociationInformation() }.memoryLocations.addAll(
            captureGroups["value"]!!.map(
                ::getEquivalentNode
            )
        )
    }

    constructor(method: Method?, summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity, addAssociations: Boolean = true)
            : this(AnalysisGraph.fromIR(GraalIRGraph.fromGraal(methodToGraph.getCFG(method!!))), summaryFunc, addAssociations)

    val pointsToGraph: AnalysisGraph by lazy {
        val results = iterateUntilFixedPoint().toList()

        val associated = results.flatMap { pair ->
            pair.second.memoryLocations.map {
                Triple(
                    GenericObjectWithField(it, getFieldEdgeName(pair.first)),
                    it,
                    pair
                )
            }
        }.map { (key, alloc, itt) ->
            val value = mutableListOf<AnalysisNode>()
            for (node in results.filter { it.first == itt.first }.flatMap { it.second.storedValues }) {
                if (node is AnalysisNode.IR && node.node() is LoadFieldNode) {
                    value.add(
                        GenericObjectWithField(
                            alloc,
                            getFieldEdgeName(node)
                        )
                    )
                } else value.add(node)
            }
            key to value
        }
        val nodes = associated.flatMap { it.second }.toSet().union(associated.map { it.first }.mapNotNull { it.obj })
            .filter { it !is GenericObjectWithField }.toMutableSet()
        val edges = mutableSetOf<Triple<AnalysisNode, String, AnalysisNode>>()
        associated.filter { it.first.obj != null }.forEach { item ->
            edges.addAll(item.second.map { Triple(item.first.obj!!, item.first.field, it) })
        }
        while (true) {
            val toRemove = mutableSetOf<Triple<AnalysisNode, String, AnalysisNode>>()
            val toAdd = mutableSetOf<Triple<AnalysisNode, String, AnalysisNode>>()
            for ((from, field, to) in edges) {
                if (to is GenericObjectWithField) {
                    toRemove.add(Triple(from, field, to))
                    toAdd.addAll(associated.asSequence().filter { it.first == to }
                        .map { it.second }
                        .flatten().map { Triple(from, field, it) }.filterNot { it in edges }.toList())
                }
            }
            if (toRemove.isEmpty() && toAdd.isEmpty()) break
            edges.removeAll(toRemove)
            edges.addAll(toAdd)
        }

        val graph = AnalysisGraph()
        nodes.forEach(graph::addVertex)
        edges.forEach { graph.addEdge(it.first, it.third, PointsToEdge(it.second)) }

        if(addAssociations) {
            // association edges
            val associationNodes = associations.keys.union(associations.values)
            val associationEdges = associations.map { (from, to) ->
                Triple(from, to, PointsToEdge("represents"))
            }
            associationNodes.forEach(graph::addVertex)
            associationEdges.forEach { graph.addEdge(it.first, it.second, it.third) }
        }
        graph
    }

    override fun toString(): String {
        val sw = StringWriter()
        // writeQueryInternal(pointsToGraph, sw) todo
        return sw.buffer.toString()
    }

}
