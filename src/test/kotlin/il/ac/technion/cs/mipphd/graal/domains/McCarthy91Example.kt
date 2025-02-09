package il.ac.technion.cs.mipphd.graal.domains

import arrow.core.Either
import il.ac.technion.cs.mipphd.graal.graphquery.*
import il.ac.technion.cs.mipphd.graal.utils.NodeWrapperUtils

fun mccarthy91(l: Long): Long {
    var n = l
    var c = 1
    while (c != 0) {
        c -= 1
        if (n > 100) {
            n -= 10
        } else {
            n += 11
            c += 2
        }
    }
    return n
}

fun simple_loop(l: Long): Long {
    var i = 0L
    var sum = 0L
    while (i < l) {
        sum += i
        i += 1
    }
    return sum
}

data class Condition(
    val name: String,
    val rel: String,
    val rhs: Either<Number, String>
) {
    companion object {
        private fun parse(rhs: String): Either<Number, String> {
            val parsed = rhs.toDoubleOrNull()
            if (parsed != null)
                return Either.Left(parsed as Number)
            return Either.Right(rhs)
        }
    }

    constructor(name: String, rel: String, rhs: Number) : this(name, rel, Either.Left(rhs))
    constructor(name: String, rel: String, rhs: String) : this(name, rel, parse(rhs))

    fun negate(): Condition {
        return Condition(
            name,
            when (rel) {
                "==" -> "!="
                ">" -> "<="
                ">=" -> "<"
                "<" -> ">="
                "<=" -> "<"
                "!=" -> "=="
                else -> throw RuntimeException("Unknown rel '$rel'")
            },
            rhs
        )
    }
}

data class ElinaDataNode(
    val symbolicExpression: SymbolicLinExpr = SymbolicLinExpr(listOf(), 0.toMpq())
)
    : AnalysisNode.Specific()

class ElinaEdge() : AnalysisEdge.Extra("")
class ElinaNode() : AnalysisNode.Specific() {

}

data class Item(
    val expression: String,
    val symbolicExpression: SymbolicLinExpr = SymbolicLinExpr(listOf(), 0.toMpq()),
    val statements: String = "",
    val condition: String = "",
    val conditions: List<Condition> = listOf(),
    val if_conditions: List<Condition> = listOf(),
    val relatedValues: List<AnalysisNode> = listOf(),
    val nextIds: Set<String> = setOf(),
    val mergeValues: Map<AnalysisNode, Map<AnalysisNode, AnalysisNode>> = mapOf(),
    val mergeAssignments: Map<AnalysisNode, Map<String, String>> = mapOf(),
    val symbolicMergeAssignments: Map<AnalysisNode, Map<String, SymbolicLinExpr>> = mapOf(),
    val polyhedralAbstractState: ElinaAbstractState = OctagonElinaAbstractState.bottom(),
    val polyhedralAbstractState_in: ElinaAbstractState = OctagonElinaAbstractState.bottom()
) {
    companion object {
        fun default(): Item = Item("")
    }
}

class McCarthy91Analysis(
    graph: AnalysisGraph,
    initialPolyhedralState: ElinaAbstractState = OctagonElinaAbstractState.top()
) : QueryExecutor<Item>(graph, Item::default) {
    val arithmeticQuery by WholeMatchQuery(
        """
digraph G {
	arith [ label="(?P<arithmeticNode>)|" ];
	x [ label="(?P<x>)|" ];
	y [ label="(?P<y>)|" ];
#   elina [ label="(?P<elina>)|"

	x -> arith [ label="is('DATA') and name() = 'x'" ];
	y -> arith [ label="is('DATA') and name() = 'y'" ];
#    arith -> elina [ label="is('ELINA')" ]
}
"""
    ) { captureGroups ->
        val node = captureGroups.getValue("arithmeticNode").first()
        val x = captureGroups.getValue("x").first()
        val y = captureGroups.getValue("y").first()
        val xText = state.getValue(x).expression
        val xSym = state.getValue(x).symbolicExpression
        val yText = state.getValue(y).expression
        val ySym = state.getValue(y).symbolicExpression
        val newSym = when (arithmeticNodeToText(node)) {
            "+" -> xSym.plus(ySym)
            "*" -> xSym.times(ySym)
            "-" -> xSym.plus(ySym.times(SymbolicLinExpr(listOf(), (-1).toMpq())))
            else -> SymbolicLinExpr(listOf(), 0.toMpq()) // TODO: Probably not important...
        }
        var foo = graph.outgoingEdgesOf(node).map(graph::getEdgeTarget).find { it is ElinaDataNode }
        if (foo == null)
            foo = ElinaDataNode(symbolicExpression = newSym)
        graph.addVertex(foo)
        graph.addEdge(node, foo, ElinaEdge())

        state[node] = Item(
            "$xText ${arithmeticNodeToText(node)} $yText",
            conditions = listOf(Condition(xText, arithmeticNodeToText(node), yText)),
            symbolicExpression = newSym
        )
    }

    private fun arithmeticNodeToText(node: AnalysisNode): String = if (node is AnalysisNode.IR) node.node()
        .toString().replace(Regex("^[^|]*\\|"), "") else throw RuntimeException("Not IR node")

    val startNodeState by CaptureGroupQuery("""
        digraph G {
            n [ label = "(?P<start>)|is('StartNode')" ];
        }
    """.trimIndent(), "start" to { nodes ->
        val start = nodes.first()
        state.getValue(start).copy(polyhedralAbstractState_in = initialPolyhedralState)
    })

    val constantQuery by CaptureGroupQuery("""
digraph G {
    n [ label = "(?P<constant>)|is('ConstantNode')" ];
}
""", "constant" to { nodes ->
        val first = nodes.first() as AnalysisNode.IR
        Item(
            expression = NodeWrapperUtils.getConstantValue(first),
            symbolicExpression = SymbolicLinExpr(
                listOf(),
                (NodeWrapperUtils.getConstantValue(first).toDoubleOrNull() ?: 0).toMpq()
            )
        )
    })

    val valuePhiQuery by CaptureGroupQuery("""
digraph G {
    valuephi [ label = "(?P<valuephi>)|is('ValuePhiNode')" ];
}
""", "valuephi" to { nodes ->
        val node = nodes.first() as AnalysisNode.IR
        val name = "phi${node.id}"
        Item(
            expression = name,
            symbolicExpression = SymbolicLinExpr(listOf(Monom(name, 1.toMpq())), 0.toMpq())
        )
    })

    val valueProxyQuery by WholeMatchQuery(
        """
        digraph G {
            value [ label = "(?P<value>)|1 = 1" ];
            valueProxy [ label = "(?P<valueProxy>)|is('ValueProxyNode')" ];
            
            value -> valueProxy [ label = "is('DATA') and name() = 'value'" ];
        }
    """.trimIndent()
    ) { captures ->
        val proxy = captures.getValue("valueProxy").first()
        val value = captures.getValue("value").first()

        state[proxy] = state.getValue(proxy).copy(
            expression = state.getValue(value).expression,
            symbolicExpression = state.getValue(value).symbolicExpression
        )
    }

    val parameterQuery by CaptureGroupQuery("""
digraph G {
    n [ label = "(?P<parameter>)|is('ParameterNode')" ];
}
""", "parameter" to { nodes ->
        val node = nodes.first() as AnalysisNode.IR
        val name = "parameter${node.id}"
        Item(
            expression = name,
            symbolicExpression = SymbolicLinExpr(listOf(Monom(name, 1.toMpq())), 0.toMpq())
        )
    })


    val ifConditionQuery by WholeMatchQuery(
        """
digraph G {
	ifnode [ label="(?P<ifnode>)|is('IfNode')" ];
	cmp [ label="(?P<ifcondition>)|" ];

	cmp -> ifnode [ label="is('DATA') and name() = 'condition'" ];
}
"""
    ) { captureGroups ->
        val node = captureGroups.getValue("ifnode").first()
        val condition = captureGroups.getValue("ifcondition").first()
        state[node] = Item(state.getValue(condition).expression, if_conditions = state.getValue(condition).conditions)
    }

    val ifPathQuery by WholeMatchQuery(
        """
digraph G {
    ifnode [ label="(?P<ifpathnode>)|is('IfNode')" ];
    truepath [ label="(?P<truepath>)|" ];
    falsepath [ label="(?P<falsepath>)|" ];

    ifnode -> truepath [ label="is('CONTROL') and name() = 'trueSuccessor'" ];
    ifnode -> falsepath [ label="is('CONTROL') and name() = 'falseSuccessor'" ];
}"""
    ) { captureGroups ->
        val ifNode = captureGroups.getValue("ifpathnode").first()
        val nextTrue = captureGroups.getValue("truepath").first()
        val nextFalse = captureGroups.getValue("falsepath").first()

        state[nextTrue] = state.getValue(nextTrue).copy(
            condition = state.getValue(ifNode).expression,
            conditions = state.getValue(ifNode).if_conditions.toList()
        )
        state[nextFalse] = state.getValue(nextFalse).copy(
            condition = "!(${state.getValue(ifNode).expression})",
            conditions = state.getValue(ifNode).if_conditions.map { it.negate() })
    }

    val propagatePolyhedralAbstractStateQuery by WholeMatchQuery(
        """
digraph G {
    sources [ label="[](?P<sources>)|" ];
    destination [ label="(?P<destination>)|" ];
    
    sources -> destination [ label = "is('CONTROL')" ];
}""".trimIndent()
    ) { captureGroups ->
        val destination = captureGroups.getValue("destination").first() as AnalysisNode.IR
        val sources = captureGroups.getValue("sources")

        var polyState = sources.asSequence()
            .filterIsInstance<AnalysisNode.IR>()
            .sortedBy { it.id.toInt() } // for stability
            .map(state::getValue)
            .map(Item::polyhedralAbstractState)
            .reduce { o1, o2 ->
                val joined = o1.join(o2)
                joined
            }

        val prev = state.getValue(destination).polyhedralAbstractState_in
        if (destination.isType("AbstractMergeNode")) {
            println(destination)
            println("prev: $prev")
            println("Sources:")
            sources.asSequence()
                .filterIsInstance<AnalysisNode.IR>()
                .sortedBy { it.id.toInt() } // for stability
                .forEach { s -> println("  ${s.id}) ${state.getValue(s).polyhedralAbstractState}") }
            println("before join/widen: $polyState")
            val join = prev.join(polyState)
            println("join: $join")
            val widen = prev.widen(polyState)
            println("after join/widen: $widen")
            if (polyState.coeffs.size - widen.coeffs.size < 3)
                polyState = widen
            else
                println("too much information lost, not widening")

            if (sources.asSequence()
                    .filterIsInstance<AnalysisNode.IR>()
                    .sortedBy { it.id.toInt() } // for stability
                    .map(state::getValue)
                    .map(Item::polyhedralAbstractState)
                    .any { it.isBottom() }
            ) {
                println("Some sources are still bottom, taking first option")
                polyState = sources.asSequence()
                    .filterIsInstance<AnalysisNode.IR>()
                    .sortedBy { it.id.toInt() } // for stability
                    .map(state::getValue).map(Item::polyhedralAbstractState).first()
            }
        }



        state[destination] = state.getValue(destination).copy(
            polyhedralAbstractState_in = polyState
        )
    }

    val baseTranformQuery by CaptureGroupQuery("""
digraph G {
    n [ label = "(?P<node>)|not is('LoopEndNode') and not is('LoopExitNode') and not is ('EndNode')" ];
}
""", "node" to { nodes ->
        state.getValue(nodes.first()).copy(
            polyhedralAbstractState = state.getValue(nodes.first()).polyhedralAbstractState_in
        )
    })


    val frameStateQuery by WholeMatchQuery(
        """
digraph G {
	framestate [ label="is('FrameState')" ];
	merge [ label="(?P<mergenode>)|is('AbstractMergeNode')" ];
	values [ label="[](?P<phivalues>)|" ];
    sourcevalues [ label="[](?P<phisourcevalues>)|" ];

	values -> framestate [ label = "is('DATA') and name() = 'values'" ];
    merge -> values [ label = "name() = 'merge'" ];
    sourcevalues -> values [ label = "name() != 'merge'" ];
	framestate -> merge [ label = "is('DATA') and name() = 'stateAfter'" ];
}
"""
    ) { captureGroups ->
        val mergeNode = captureGroups.getValue("mergenode").first()
        val values = captureGroups.getValue("phivalues")
        val sourceValues = captureGroups.getValue("phisourcevalues")

        val map = mutableMapOf<AnalysisNode, MutableMap<AnalysisNode, AnalysisNode>>()
        val assignments = mutableMapOf<AnalysisNode, MutableMap<String, String>>()
        val symbolicAssignments = mutableMapOf<AnalysisNode, MutableMap<String, SymbolicLinExpr>>()
        for ((n, s) in state.getValue(mergeNode).mergeAssignments) {
            assignments[n] = HashMap(s)
        }
        for (phi in values) {
            for (value in sourceValues) {
                val edge = graph.getEdge(value, phi) as AnalysisEdge.Phi? // TODO: Capture from graph?
                if (edge != null) {
                    assignments.computeIfAbsent(edge.from) { mutableMapOf() }[state.getValue(phi).expression] =
                        state.getValue(value).expression
                    symbolicAssignments.computeIfAbsent(edge.from) { mutableMapOf() }[state.getValue(phi).expression] =
                        state.getValue(value).symbolicExpression
                }
            }
        }
        state[mergeNode] =
            state.getValue(mergeNode).copy(
                relatedValues = values, mergeValues = map, mergeAssignments = assignments,
                symbolicMergeAssignments = symbolicAssignments
            )
    }

    val loopQuery by WholeMatchQuery(
        """
digraph G {
  loopPrev  [ label="(?P<loopPrev>)|not is ('LoopEndNode')" ];
  loopBegin [ label="(?P<loopBegin>)|is('LoopBeginNode')" ];
  loopEnd [ label="(?P<loopEnd>)|is('LoopExitNode') or is('LoopEndNode')" ];
  someNode [ label="(?P<firstInPath>)|not is('LoopEndNode') and not is('LoopExitNode')" ]
  someNodeKleene [ label="(?P<innerPath>)|not is('LoopEndNode') and not is('LoopExitNode')" ]

  loopPrev -> loopBegin [ label="is('CONTROL')" ];
  loopBegin -> loopEnd [ label="is('ASSOCIATED') and name() = 'loopBegin'" ];
  loopBegin -> someNode [ label="is('CONTROL')" ];
  someNode -> someNodeKleene [ label="*|is('CONTROL')" ];
  someNodeKleene -> loopEnd [ label="is('CONTROL')"];
}
"""
    ) { captureGroups ->
        val begin = captureGroups.getValue("loopBegin").first() as AnalysisNode.IR
        val prev = captureGroups.getValue("loopPrev").first() as AnalysisNode.IR
        val end = captureGroups.getValue("loopEnd").first() as AnalysisNode.IR
        val nodes = captureGroups.getValue("firstInPath") + captureGroups.getValue("innerPath") + end

        val condition =
            nodes.asSequence().map(state::getValue).map(Item::condition).filter(String::isNotEmpty).joinToString(" && ")
        val conditions =
            nodes.asSequence().map(state::getValue).flatMap(Item::conditions).toList()

        val next = if (state.getValue(end).nextIds.isNotEmpty()) state.getValue(end).nextIds.first() else "???"

        val values =
            state.getValue(begin).mergeAssignments[end]?.entries?.joinToString(";\n") { (k, v) -> "$k := $v" } ?: ""
        val symbolicAssignments = state.getValue(begin).symbolicMergeAssignments[end]?.entries ?: listOf()

        // get polyState
        val polyStateTemp = conditions.fold(state.getValue(begin).polyhedralAbstractState) { acc, c ->
            c.rhs.fold({ acc.assume(c.name, c.rel, it) }, { acc })
        }
        val polyState = symbolicAssignments.fold(polyStateTemp) { acc, (k, symExpr) ->
            if (symExpr.constant == 0.toMpq() && symExpr.monoms.size == 1 && symExpr.monoms.single().coeff.isEqual(1.toMpq()))
                acc.substitute(symExpr.monoms.single().name, SymbolicLinExpr(0, Monom(k)))
            else
                acc.assign(k, symExpr)
        }
        // Make the new state (and text thing)
        state[end] = state.getValue(end).copy(
            statements = """
${end.id}:
    assume $condition;
${values.prependIndent("    ")}
    goto $next
    # $polyState, ${state[end]?.polyhedralAbstractState_in} (loopQuery)
""".trimIndent(),
            polyhedralAbstractState = polyState,
            polyhedralAbstractState_in = state.getValue(begin).polyhedralAbstractState
        )
    }

    val loopBeginQuery by WholeMatchQuery(
        """
digraph G {
        loopBegin [ label="(?P<begin>)|is('LoopBeginNode')" ];
        loopEnd [ label="[](?P<end>)|is('LoopEndNode') or is('LoopExitNode')" ];
        
        loopBegin -> loopEnd [ label="is('ASSOCIATED')" ];
}
    """
    ) { captureGroups ->
        val begin = captureGroups.getValue("begin").first() as AnalysisNode.IR
        val ends = captureGroups.getValue("end").filterIsInstance<AnalysisNode.IR>()

        state[begin] = state.getValue(begin).copy(
            statements = """
            ${begin.id}:
                goto ${ends.joinToString(", ") { it.id }}
                # (loopBeginQuery)
        """.trimIndent()
        )
        println("loopBegin in: ${state[begin]!!.polyhedralAbstractState_in}")
    }

    val loopNextQuery by WholeMatchQuery(
        """
digraph G {
    loopEnd [ label="(?P<loopEnd>)|1=1" ];
    merge [ label="(?P<merge>)|1=1" ];
    next [ label="(?P<next>)|1=1" ];
    
    merge -> loopEnd [ label="is('ASSOCIATED')" ];
    loopEnd -> next [ label="is('CONTROL')" ];
}
    """
    ) { capture ->
        val end = capture.getValue("loopEnd").first() as AnalysisNode.IR
        val next = capture.getValue("next").first() as AnalysisNode.IR

        state[end] = state.getValue(end).copy(nextIds = setOf(next.id))
    }

    val mergePathQuery by WholeMatchQuery(
        """
digraph G {
    mergeBegin [ label="(?P<mergeBegin>)|is('StartNode') or is('AbstractMergeNode') or is('LoopExitNode')" ];
    someNode [ label="(?P<mergePath>)|not is('AbstractMergeNode') and not is ('ReturnNode') and not is('LoopEndNode') and not is ('LoopExitNode')" ];
    mergeEnd [ label="(?P<mergeEnd>)|is('AbstractMergeNode')" ];
    
    mergeBegin -> someNode [ label="*|is('CONTROL')" ];
    someNode -> mergeEnd [ label="is('CONTROL')" ];
}
    """
    ) { captureGroupActions ->
        val begin = captureGroupActions.getValue("mergeBegin").first() as AnalysisNode.IR
        val end = captureGroupActions.getValue("mergeEnd").first() as AnalysisNode.IR
        val nodes = listOf(begin) + captureGroupActions.getValue("mergePath")
        val lastNode = nodes.last() as AnalysisNode.IR

        val condition =
            nodes.asSequence().map(state::getValue).map(Item::condition).filter(String::isNotEmpty).joinToString(" && ")
        val conditions =
            nodes.asSequence().map(state::getValue).flatMap(Item::conditions).toList()

        val values =
            state.getValue(end).mergeAssignments[lastNode]?.entries?.joinToString(";\n") { (k, v) -> "$k := $v" }
                ?: "???"
        val symbolicAssignments = state.getValue(end).symbolicMergeAssignments[lastNode]?.entries ?: listOf()

        val nextIds = state.getValue(begin).nextIds + lastNode.id

        // get polyState
        val polyStateTemp = conditions.fold(state.getValue(begin).polyhedralAbstractState) { acc, c ->
            c.rhs.fold({ acc.assume(c.name, c.rel, it) }, { acc })
        }
        val polyState = symbolicAssignments.fold(polyStateTemp) { acc, (k, symExpr) ->
            if (symExpr.constant == 0.toMpq() && symExpr.monoms.size == 1 && symExpr.monoms.single().coeff.isEqual(1.toMpq()))
                acc.substitute(symExpr.monoms.single().name, SymbolicLinExpr(0, Monom(k)))
            else
                acc.assign(k, symExpr)
        } // .forgetByFilter { it.startsWith("parameter") }

        state[begin] = state.getValue(begin).copy(
            nextIds = nextIds, statements = """
            ${begin.id}:
                ${nextIds.joinToString(", ", "goto ") { it.toString() }}
                # (mergePathQuery/begin)
        """.trimIndent()
        )
        val assume = if (condition.isNotEmpty()) "assume $condition;" else ""
        state[lastNode] = state.getValue(lastNode).copy(
            statements = """
${lastNode.id}:
    $assume
${values.prependIndent("    ")}
    goto ${end.id}
    # (mergePathQuery/lastNode)
""".trimIndent(),
            polyhedralAbstractState = polyState,
            polyhedralAbstractState_in = state.getValue(begin).polyhedralAbstractState
        )
    }

    val returnNodeQuery by WholeMatchQuery(
        """
digraph G {
    r [ label = "(?P<returnNode>)|is('ReturnNode')" ];
    v [ label = "(?P<value>)|" ];
    
    v -> r [ label = "is('DATA')" ];
}
    """
    ) { captureGroups ->
        val returnNode = captureGroups.getValue("returnNode").first() as AnalysisNode.IR
        val value = captureGroups.getValue("value").first()

        state[returnNode] = state.getValue(returnNode).copy(
            statements = """
            ${returnNode.id}:
                return ${state.getValue(value).expression}
                # (returnNodeQuery)
        """.trimIndent()
        )
    }
}