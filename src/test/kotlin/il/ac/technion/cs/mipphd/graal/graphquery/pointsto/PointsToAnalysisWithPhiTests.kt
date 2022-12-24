package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisEdge
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisGraph
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisNode
import il.ac.technion.cs.mipphd.graal.utils.GraalIRGraph
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.graalvm.compiler.nodes.FixedNode
import org.graalvm.compiler.nodes.FrameState
import org.graalvm.compiler.nodes.PhiNode
import org.junit.jupiter.api.Test
import java.io.File
import java.io.StringWriter
import kotlin.reflect.jvm.javaMethod


fun phiTest1(param: String?): AnyHolder {
    var first = AnyHolder()
    anyUser(first)
    first.any = param
    if(param != null) {
        first = AnyHolder()
        anyUser(first)
        first.any = null
    }
    return first
}

fun phiTest2(param: String?): Any {
    var first = AnyHolder()
    anyUser(first)
    var second = AnyHolder()
    anyUser(second)

    if(anyUser(param)) {
        val oldFirst = first

        second.any = first
        first = second
        first.other = null

        second = oldFirst
        second.any = null
    } else {
        first.any = param
        second.any = null
        second.other = param
    }
    return first to second
}

fun testSelfAssignment(param: String?): AnyHolder {
    var first = AnyHolder()
    anyUser(first)
    val second = AnyHolder()
    anyUser(second)
    if(anyUser(param)) {
        first.any = first
        first.other = null
        anyUser(first)
        first = second
    }
    return first
}

fun phiTest3(param: String?): Any {
    val first = AnyHolder()
    anyUser(first)
    val second = AnyHolder()
    anyUser(second)

    if(anyUser(param)) {
        first.any = "2"
        second.any = "2"
    } else {
        first.any = "1"
        second.any = "1"
    }

    return first to second
}

fun phiTest4(any: Any?): Any {
    val first = AnyHolder()
    anyUser(first)
    if(anyUser(any)) {
        first.any = any
    } else {
        first.any = null
    }
    return first
}

fun phiTest5(any: Any?): Any {
    val first = AnyHolder()
    anyUser(first)
    val interior = if(anyUser(any)) any else null
    first.any = interior
    return first
}

// todo: multiple functions with connecting edges (interprocedual analysis)
class PointsToAnalysisWithPhiTests {

    val methodToGraph = MethodToGraph()
    private val autoOpenFiles = true

    private fun openGraph(str: String) {
        println(str)
        if(autoOpenFiles) {
            val file = File("tmp_graph.dot")
            file.writeText(str)
            Runtime.getRuntime().exec("dot -Tpng tmp_graph.dot -o tmp_graph.png").waitFor()
            Runtime.getRuntime().exec("open tmp_graph.png")
            file.delete()
        }
    }

    private inline fun <reified T> filterGraph(graalAdapter: AnalysisGraph): AnalysisGraph {
        val nodes = graalAdapter.vertexSet().filter { it is T || (it is AnalysisNode.IR && it.node() is T) }.toMutableSet()
        nodes.addAll(graalAdapter.vertexSet().filter { nodes.any { itt -> graalAdapter.containsEdge(itt, it) || graalAdapter.containsEdge(it, itt) }})
        val edges = nodes.flatMap { n1 -> nodes.map { n2 -> n1 to n2 } }
            .filter { graalAdapter.containsEdge(it.first, it.second) }
            .map { Triple(it.first, it.second, graalAdapter.getEdge(it.first, it.second)) }
        val ret = AnalysisGraph()
        nodes.forEach(ret::addVertex)
        edges.forEach {
            ret.addEdge(it.first, it.second, cloneEdge(it.third))
        }
        return ret
    }

    @Test
    fun `get graal graph for phiTest1`() {
        val method = ::phiTest1.javaMethod
        val graph = methodToGraph.getCFG(method)
        val adapter = filterGraph<PhiNode>(AnalysisGraph.fromIR(GraalIRGraph.fromGraal(graph)))
        openGraph(adapter.export())
    }

    @Test
    fun `get pointsto graph with phi for phiTest1`() {
        println("# phiTest1")
        val analysis = PointsToAnalysisWithPhi(::phiTest1.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get pointsto graph with phi for phiTest2`() {
        println("# phiTest2")
        val analysis = PointsToAnalysisWithPhi(::phiTest2.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get pointsto graph with phi for testSelfAssignment`() {
        println("# testSelfAssignment")
        val analysis = PointsToAnalysisWithPhi(::testSelfAssignment.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get pointsto graph with phi for phiTest3`() {
        println("# phiTest3")
        val analysis = PointsToAnalysisWithPhi(::phiTest3.javaMethod, summaryFunc = SummaryKeyByNodeSourcePosOrIdentity)
        openGraph(analysis.toString())
    }

    @Test
    fun `get graal graph for phiTest3`() {
        val method = ::phiTest3.javaMethod
        val graph = methodToGraph.getCFG(method)
        val adapter = filterGraph<FrameState>(AnalysisGraph.fromIR(GraalIRGraph.fromGraal(graph)))
        openGraph(adapter.export())
    }

    @Test
    fun `get pointsto graph with phi for phiTest4`() {
        println("# phiTest3")
        val analysis = PointsToAnalysisWithPhi(::phiTest4.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get graal graph for phiTest4`() {
        val method = ::phiTest4.javaMethod
        val graph = methodToGraph.getCFG(method)
        val adapter = filterGraph<FixedNode>(AnalysisGraph.fromIR(GraalIRGraph.fromGraal(graph)))
        openGraph(adapter.export())
    }

    @Test
    fun `get pointsto graph with phi for phiTest5`() {
        println("# phiTest3")
        val analysis = PointsToAnalysisWithPhi(::phiTest5.javaMethod)
        openGraph(analysis.toString())
    }

}
