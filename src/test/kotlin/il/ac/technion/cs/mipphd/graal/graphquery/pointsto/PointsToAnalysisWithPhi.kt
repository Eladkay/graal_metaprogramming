package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.graphquery.QueryExecutor
import il.ac.technion.cs.mipphd.graal.graphquery.WholeMatchQuery
import il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import org.graalvm.compiler.nodes.PhiNode
import org.graalvm.compiler.nodes.calc.FloatingNode
import org.graalvm.compiler.nodes.java.AccessFieldNode
import org.graalvm.compiler.nodes.java.LoadFieldNode
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import java.io.StringWriter
import java.lang.reflect.Method

class PointsToAnalysisWithPhi(graal: GraalAdapter, private val summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity) :
    QueryExecutor<AssociationInformation>(graal, { AssociationInformation() }) {

    private val summaries = mutableMapOf<Any, PointsToNode>()
    private val associations = mutableMapOf<NodeWrapper, PointsToNode>()
    private fun getNode(nodeWrapper: NodeWrapper): NodeWrapper {
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
        private val methodToGraph = MethodToGraph()
        val NOP_NODES = listOf(
            "Pi",
            "VirtualInstance",
            "VirtualObjectState",
            "MaterializedObjectState"
        ).map { if (it.endsWith("State")) it else "${it}Node" }
        val NOT_VALUE_NODES = listOf(
            "Pi",
            "VirtualInstance",
            "Begin",
            "Merge",
            "End",
            "FrameState",
            "VirtualObjectState",
            "MaterializedObjectState",
            "ExceptionObject"
        ).map { if (it.endsWith("State")) it else "${it}Node" }
        private val accessFieldNodeClass = Class.forName("org.graalvm.compiler.nodes.java.AccessFieldNode")
        private val getFieldMethod = accessFieldNodeClass.getDeclaredMethod("field")
        private fun getFieldEdgeName(node: NodeWrapper): String {
            if(node.node !is AccessFieldNode) return "is"
            return getFieldNameMethod(getFieldMethod(node.node)).toString()
        }
        private val fieldClazz = Class.forName("jdk.vm.ci.meta.JavaField")
        private val getFieldNameMethod = fieldClazz.getDeclaredMethod("getName")


        // todo - code duplication w/ GraalAdapter
        private val edgeColor = mapOf(
            EdgeWrapper.DATA to "blue",
            EdgeWrapper.CONTROL to "red",
            EdgeWrapper.ASSOCIATED to "black"
        )
        private val edgeStyle = mapOf(
            EdgeWrapper.DATA to "",
            EdgeWrapper.CONTROL to "",
            EdgeWrapper.ASSOCIATED to "dashed"
        )

        private fun writeQueryInternal(graalph: GraalAdapter, output: StringWriter) {
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
    }

    val storeQuery by WholeMatchQuery(
        """
digraph G {
    storeNode [ label="(?P<store>)|is('StoreFieldNode')" ];
    nop [ label="(?P<nop>)|${NOP_NODES.joinToString(" or ") { "is('$it')" }}" ];
	value [ label="(?P<value>)|${NOT_VALUE_NODES.joinToString(" and ") { "not is('$it')" }}" ];

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
    nop [ label="(?P<nop>)|${NOP_NODES.joinToString(" or ") { "is('$it')" }}" ];
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
    val phiQuery by WholeMatchQuery(
        """
digraph G {
    phiNode [ label="(?P<phi>)|is('PhiNode')" ];
    nop [ label="(?P<nop>)|${NOP_NODES.joinToString(" or ") { "is('$it')" }}" ];
	value [ label="(?P<value>)|${NOT_VALUE_NODES.joinToString(" and ") { "not is('$it')" }}" ];

	value -> nop [ label="*|is('DATA')" ];
    nop -> phiNode [ label="is('DATA')" ];
}
"""
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val storeNode = captureGroups["phi"]!!.first()
        val equivNode = getNode(storeNode)
        state.getOrPut(equivNode) { AssociationInformation() }.storedValues.addAll(captureGroups["value"]!!.map(::getNode))
        state[equivNode]!!.memoryLocations.addAll(captureGroups["phi"]!!.map(::getNode))
    }

    constructor(method: Method?, summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity)
            : this(GraalAdapter.fromGraal(methodToGraph.getCFG(method!!)), summaryFunc)

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

        // association edges
        val associationNodes = associations.keys.union(associations.values)
        val associationEdges = associations.map { (from, to) ->
            Triple(from, to, EdgeWrapper(EdgeWrapper.ASSOCIATED, "association"))
        }
        associationNodes.forEach(graph::addVertex)
        associationEdges.forEach { graph.addEdge(it.first, it.second, it.third) }

        graph
    }

    override fun toString(): String {
        val sw = StringWriter()
        writeQueryInternal(pointsToGraph, sw)
        return sw.buffer.toString()
    }

}
