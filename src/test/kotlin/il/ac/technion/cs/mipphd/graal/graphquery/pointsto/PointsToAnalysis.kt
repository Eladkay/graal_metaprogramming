package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.graphquery.QueryExecutor
import il.ac.technion.cs.mipphd.graal.graphquery.WholeMatchQuery
import il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.nodes.PhiNode
import org.graalvm.compiler.nodes.calc.FloatingNode
import org.graalvm.compiler.nodes.java.LoadFieldNode
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode
import java.io.StringWriter
import java.lang.reflect.Method

open class PointsToAnalysis(
    graal: GraalAdapter,
    private val summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity,
    val addAssociations: Boolean = true,
    nopNodes: Collection<String> = DEFAULT_NOP_NODES,
    notValueNodes: Collection<String> = DEFAULT_NOT_VALUE_NODES
) :
    QueryExecutor<AssociationInformation>(graal, { AssociationInformation() }) {

    private val summaries = mutableMapOf<Any, PointsToNode>()
    private val associations = mutableMapOf<NodeWrapper, PointsToNode>()
    protected fun getNode(nodeWrapper: NodeWrapper): NodeWrapper {
        assert(nodeWrapper.node != null)
        if (nodeWrapper in associations) {
            return associations[nodeWrapper]!!
        }
        if (nodeWrapper.node !is FloatingNode) {
            return nodeWrapper // heuristic, todo
        }
        if (nodeWrapper.node !is AllocatedObjectNode && nodeWrapper.node !is PhiNode) {
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
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val storeNode = captureGroups["store"]!!.first()
        val equivNode = getNode(storeNode)
        state.getOrPut(equivNode) { AssociationInformation() }.storedValues.addAll(captureGroups["value"]!!.map(::getNode))
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
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val storeNode = captureGroups["store"]!!.first()
        val equivNode = if (storeNode.node is AllocatedObjectNode) getNode(storeNode) else storeNode
        state.getOrPut(equivNode) { AssociationInformation() }.memoryLocations.addAll(
            captureGroups["value"]!!.map(
                ::getNode
            )
        )
    }

    constructor(method: Method?, summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity, addAssociations: Boolean = true)
            : this(GraalAdapter.fromGraal(methodToGraph.getCFG(method!!)), summaryFunc, addAssociations)

    val pointsToGraph: GraalAdapter by lazy {
        val results = iterateUntilFixedPoint().toList()

        val associated = results.flatMap { pair ->
            pair.second.memoryLocations.map {
                Triple(
                    GenericObjectWithField(it, getFieldEdgeName(pair.first)),
                    it,
                    pair
                )
            }
        }.associate { (key, alloc, itt) ->
            val value = mutableListOf<NodeWrapper>()
            for (node in (results.firstOrNull { it.first == itt.first }?.second?.storedValues ?: listOf())) {
                if (node.node is LoadFieldNode) {
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
        val nodes = associated.flatMap { it.value }.toSet().union(associated.keys.mapNotNull { it.obj })
            .filter { it !is GenericObjectWithField }.toMutableSet()
        val edges = mutableSetOf<Triple<NodeWrapper, String, NodeWrapper>>()
        associated.filter { it.key.obj != null }.forEach { item ->
            edges.addAll(item.value.map { Triple(item.key.obj!!, item.key.field, it) })
        }
        while (true) {
            val toRemove = mutableSetOf<Triple<NodeWrapper, String, NodeWrapper>>()
            val toAdd = mutableSetOf<Triple<NodeWrapper, String, NodeWrapper>>()
            for ((from, field, to) in edges) {
                if (to is GenericObjectWithField) {
                    toRemove.add(Triple(from, field, to))
                    toAdd.addAll(associated[to]!!.map { Triple(from, field, it) }.filterNot { it in edges })
                }
            }
            if (toRemove.isEmpty() && toAdd.isEmpty()) break
            edges.removeAll(toRemove)
            edges.addAll(toAdd)
        }

        val graph = GraalAdapter()
        nodes.forEach(graph::addVertex)
        edges.forEach { graph.addEdge(it.first, it.third, EdgeWrapper(EdgeWrapper.ASSOCIATED, it.second)) }

        if(addAssociations) {
            // association edges
            val associationNodes = associations.keys.union(associations.values)
            val associationEdges = associations.map { (from, to) ->
                Triple(from, to, EdgeWrapper(EdgeWrapper.ASSOCIATED, "association"))
            }
            associationNodes.forEach(graph::addVertex)
            associationEdges.forEach { graph.addEdge(it.first, it.second, it.third) }
        }
        graph
    }

    override fun toString(): String {
        val sw = StringWriter()
        writeQueryInternal(pointsToGraph, sw)
        return sw.buffer.toString()
    }

}
