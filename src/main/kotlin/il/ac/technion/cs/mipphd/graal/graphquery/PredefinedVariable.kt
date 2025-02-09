package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.utils.NodeWrapperUtils
import il.ac.technion.cs.mipphd.graal.utils.WrappedIREdge
import kotlin.RuntimeException


data class PredefinedVariable(val name: String, val type: MType, val contents: MValue)

private fun createMap(vararg l: PredefinedVariable) = l.map { Pair(it.name, it) }.toMap()
private fun functionVariable(name: String, type: MType, impl: (List<MQuery>, QueryTarget) -> MValue) =
    PredefinedVariable(name, type, FunctionValue(type, impl))

val predefined = createMap(
    functionVariable("is", MFunction(listOf(MString), MBoolean)) { p, t ->
        val cmp = (p[0] as StringValue).value
        when (t) {
            is QueryTargetNode -> BooleanValue(t.node.let { if (it is AnalysisNode.IR) it.isType(cmp) else false })
            is QueryTargetEdge -> BooleanValue(when(cmp) {
                WrappedIREdge.DATA -> t.edge is AnalysisEdge.Data
                WrappedIREdge.CONTROL -> t.edge is AnalysisEdge.Control
                WrappedIREdge.ASSOCIATED -> t.edge is AnalysisEdge.Association
                else -> false // TODO: Add custom kinds
            })
        }
    },
    functionVariable(
        "method",
        MFunction(listOf(), MStruct(mapOf(Pair("className", MString), Pair("name", MString))))
    ) { _, t ->
        if (t is QueryTargetNode) {
            val node = t.node
            if (node is AnalysisNode.IR) {
                val method = NodeWrapperUtils.getTargetMethod(node)

                StructValue(
                    mapOf(
                        Pair("className", StringValue(method.declaringClassName)),
                        Pair("name", StringValue(method.name))
                    )
                )
            } else {
                throw RuntimeException("Applied `method` to $node which is not an IR node")
            }
        } else { throw RuntimeException("Not a node")}
    },
    functionVariable("name", MFunction(listOf(), MString)) { _, t ->
        when (t) {
            is QueryTargetNode -> TODO("Implement name for nodes?")
            is QueryTargetEdge -> StringValue(t.edge.label)
        }
    },
    PredefinedVariable("five", MInteger, IntegerValue(5)), // for debug
    PredefinedVariable("true", MBoolean, BooleanValue(true)),
    PredefinedVariable("false", MBoolean, BooleanValue(false)),
)