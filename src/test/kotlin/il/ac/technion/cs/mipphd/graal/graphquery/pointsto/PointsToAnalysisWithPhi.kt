package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.graphquery.WholeMatchQuery
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import java.lang.reflect.Method

open class PointsToAnalysisWithPhi protected constructor(
    graal: GraalAdapter,
    summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity,
    addAssociations: Boolean = true, nopNodes: Collection<String>, notValueNodes: Collection<String>
) : PointsToAnalysis(graal, summaryFunc, addAssociations, nopNodes, notValueNodes) {

    constructor(method: Method?, summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity, addAssociations: Boolean = true)
            : this(GraalAdapter.fromGraal(methodToGraph.getCFG(method!!)), summaryFunc, addAssociations)

    constructor(graal: GraalAdapter, summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity, addAssociations: Boolean = true)
            : this(graal, summaryFunc, addAssociations, NOP_NODES, NOT_VALUE_NODES)

    protected companion object {
        val NOP_NODES = listOf(
            "Pi",
            "VirtualInstance",
            "VirtualObjectState",
            "MaterializedObjectState"
        ).map(::addNodeSuffix)
        val NOT_VALUE_NODES = listOf(
            "Pi",
            "VirtualInstance",
            "Begin",
            "Merge",
            "End",
            "VirtualObjectState",
            "MaterializedObjectState",
            "ExceptionObject"
        ).map(::addNodeSuffix)
    }

    val phiQuery by WholeMatchQuery(
        """
digraph G {
    phiNode [ label="(?P<phi>)|is('PhiNode')" ];
    nop [ label="(?P<nop>)|${NOP_NODES.joinToString(" or ") { "is('$it')" }}" ];
	value [ label="(?P<value>)|${NOT_VALUE_NODES.joinToString(" and ") { "not is('$it')" }}" ];
    framestate [ label="(?P<framestate>)|is('FrameState')" ];

	value -> nop [ label="*|is('DATA')" ];
    nop -> phiNode [ label="is('DATA')" ];
    phiNode -> framestate [ label="is('DATA') and name() = 'values'" ];
}
"""
    ) { captureGroups: Map<String, List<NodeWrapper>> ->
        val storeNode = captureGroups["framestate"]!!.first()
        val equivNode = GenericObjectWithField(getEquivalentNode(storeNode), "phi${captureGroups["phi"]!!.first().node.id}")
        state.getOrPut(equivNode) { AssociationInformation() }.storedValues.addAll(captureGroups["value"]!!.map(::getEquivalentNode))
        state[equivNode]!!.memoryLocations.addAll(captureGroups["framestate"]!!.map(::getEquivalentNode))
    }
}
