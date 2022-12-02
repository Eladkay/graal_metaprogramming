package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisGraph
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisNode
import il.ac.technion.cs.mipphd.graal.graphquery.WholeMatchQuery
import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph
import java.lang.reflect.Method

class FlowSensitivePointsToAnalysis private constructor(
    graal: GraalIRGraph,
    summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity,
    addAssociations: Boolean = true, nopNodes: Collection<String>, notValueNodes: Collection<String>
) : PointsToAnalysisWithPhi(AnalysisGraph.fromIR(graal), summaryFunc, addAssociations, nopNodes, notValueNodes) {

    constructor(method: Method?, summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity, addAssociations: Boolean = true)
            : this(GraalIRGraph.fromGraal(methodToGraph.getCFG(method!!)), summaryFunc, addAssociations)

    constructor(graal: GraalIRGraph, summaryFunc: SummaryKeyFunction = SummaryKeyByNodeIdentity, addAssociations: Boolean = true)
            : this(graal, summaryFunc, addAssociations, NOP_NODES, NOT_VALUE_NODES)

    private companion object {
        val CONTROL_FLOW_NODES = listOf(
            "If"
        ).map(::addNodeSuffix)
    }


    val splitQuery by WholeMatchQuery(
        """
digraph G {
    beginNode [ label="(?P<begin>)|is('BeginNode')" ];
    controlFlowNode [ label="(?P<controlFlowNode>)|${CONTROL_FLOW_NODES.joinToString(" or ") { "is('$it')" }}" ];
    nop [ label="(?P<nop>)|not is('MergeNode')" ]; # works only if we don't have nested begin/cf/merge nodes
    mergeNode [ label="(?P<mergeNode>)|is('MergeNode')" ];

    beginNode -> controlFlowNode [ label="name() = 'next'" ];
    controlFlowNode -> nop [ label="*|is('CONTROL')" ];
    nop -> mergeNode [ label="name() = '???'" ]; # todo? the actual name on the graph is "???"
}
"""
    ) { captureGroups: Map<String, List<AnalysisNode>> ->
        val beginNode = captureGroups["begin"]!!.first()
        val res = state.getOrPut(beginNode) { AssociationInformation() }.storedValues.addAll(captureGroups["nop"]!!)
        if(res) {
            println("$beginNode -> ${captureGroups["nop"]!!} -> ${captureGroups["mergeNode"]!!}")
        }
    }
}