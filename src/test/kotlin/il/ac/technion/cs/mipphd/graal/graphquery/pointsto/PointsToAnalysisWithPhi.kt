package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisGraph
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisNode
import il.ac.technion.cs.mipphd.graal.graphquery.WholeMatchQuery
import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph
import java.lang.reflect.Method

open class PointsToAnalysisWithPhi protected constructor(
    graal: AnalysisGraph,
    summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity,
    addAssociations: Boolean = true, nopNodes: Collection<String>, notValueNodes: Collection<String>
) : PointsToAnalysis(graal, summaryFunc, addAssociations, nopNodes, notValueNodes) {

    constructor(method: Method?, summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity, addAssociations: Boolean = true)
            : this(GraalIRGraph.fromGraal(methodToGraph.getCFG(method!!)), summaryFunc, addAssociations)

    constructor(graal: GraalIRGraph, summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity, addAssociations: Boolean = true)
            : this(AnalysisGraph.fromIR(graal), summaryFunc, addAssociations, NOP_NODES, NOT_VALUE_NODES)

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
    ) { captureGroups: Map<String, List<AnalysisNode>> ->
        val storeNode = captureGroups["framestate"]!!.first()
        val equivNode = GenericObjectWithField(getEquivalentNode(storeNode),
            "phi${(captureGroups["phi"]!!.first() as AnalysisNode.IR).node().id}")
        state.getOrPut(equivNode) { AssociationInformation() }.storedValues.addAll(captureGroups["value"]!!.map(::getEquivalentNode))
        state[equivNode]!!.memoryLocations.addAll(captureGroups["framestate"]!!.map(::getEquivalentNode))
    }
}
