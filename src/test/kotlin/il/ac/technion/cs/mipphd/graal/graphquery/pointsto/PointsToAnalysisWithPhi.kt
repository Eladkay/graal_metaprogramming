package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.graphquery.WholeMatchQuery
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper
import java.lang.reflect.Method

class PointsToAnalysisWithPhi(
    graal: GraalAdapter,
    summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity,
    addAssociations: Boolean = true,
    nopNodes: Collection<String> = NOP_NODES,
    notValueNodes: Collection<String> = NOT_VALUE_NODES
) : PointsToAnalysis(graal, summaryFunc, addAssociations, nopNodes, notValueNodes) {

    constructor(method: Method?, summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity, addAssociations: Boolean = true)
            : this(GraalAdapter.fromGraal(methodToGraph.getCFG(method!!)), summaryFunc, addAssociations)

    private companion object {
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
            "FrameState",
            "VirtualObjectState",
            "MaterializedObjectState",
            "ExceptionObject"
        ).map(::addNodeSuffix)
    }

    val phiQuery by WholeMatchQuery(
        """
digraph G {
    phiNode [ label="(?P<phi>)|is('PhiNode')" ];
    nop [ label="(?P<nop>)|${nopNodes.joinToString(" or ") { "is('$it')" }}" ];
	value [ label="(?P<value>)|${notValueNodes.joinToString(" and ") { "not is('$it')" }}" ];

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
}
