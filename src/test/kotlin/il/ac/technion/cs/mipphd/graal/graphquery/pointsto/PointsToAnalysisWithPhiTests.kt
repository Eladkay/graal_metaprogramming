package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.utils.EdgeWrapper
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.graalvm.compiler.nodes.BeginNode
import org.graalvm.compiler.nodes.FixedNode
import org.graalvm.compiler.nodes.FrameState
import org.graalvm.compiler.nodes.MergeNode
import org.graalvm.compiler.nodes.PhiNode
import org.graalvm.compiler.nodes.java.StoreFieldNode
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

    private inline fun <reified T> filterGraph(graalAdapter: GraalAdapter): GraalAdapter {
        val nodes = graalAdapter.vertexSet().filter { it.node is T }.toMutableSet()
        nodes.addAll(graalAdapter.vertexSet().filter { nodes.any { itt -> graalAdapter.containsEdge(itt, it) || graalAdapter.containsEdge(it, itt) }})
        val edges = nodes.flatMap { n1 -> nodes.map { n2 -> n1 to n2 } }
            .filter { graalAdapter.containsEdge(it.first, it.second) }
            .map { Triple(it.first, it.second, graalAdapter.getEdge(it.first, it.second)) }
        val ret = GraalAdapter()
        nodes.forEach(ret::addVertex)
        edges.forEach {
            ret.addEdge(it.first, it.second, EdgeWrapper(it.third.label, it.third.name))
        }
        return ret
    }

    @Test
    fun `get graal graph for phiTest1`() {
        val method = ::phiTest1.javaMethod
        val graph = methodToGraph.getCFG(method)
        val adapter = filterGraph<PhiNode>(GraalAdapter.fromGraal(graph))
        val writer = StringWriter()
        adapter.exportQuery(writer)
        openGraph(writer.buffer.toString())
    }

    @Test
    fun `get pointsto graph with phi for phiTest1`() {
        println("# phiTest1")
        val analysis = FlowSensitivePointsToAnalysis(::phiTest1.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get pointsto graph with phi for phiTest2`() {
        println("# phiTest2")
        val analysis = FlowSensitivePointsToAnalysis(::phiTest2.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get pointsto graph with phi for testSelfAssignment`() {
        println("# testSelfAssignment")
        val analysis = FlowSensitivePointsToAnalysis(::testSelfAssignment.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get pointsto graph with phi for phiTest3`() {
        println("# phiTest3")
        val analysis = FlowSensitivePointsToAnalysis(::phiTest3.javaMethod, summaryFunc = SummaryKeyByNodeSourcePosOrIdentity)
        openGraph(analysis.toString())
    }

    @Test
    fun `get graal graph for phiTest3`() {
        val method = ::phiTest3.javaMethod
        val graph = methodToGraph.getCFG(method)
        val adapter = filterGraph<FrameState>(GraalAdapter.fromGraal(graph))
        val writer = StringWriter()
        adapter.exportQuery(writer)
        openGraph(writer.buffer.toString())
    }

    @Test
    fun `get pointsto graph with phi for phiTest4`() {
        println("# phiTest3")
        val analysis = FlowSensitivePointsToAnalysis(::phiTest4.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get graal graph for phiTest4`() {
        val method = ::phiTest4.javaMethod
        val graph = methodToGraph.getCFG(method)
        val adapter = filterGraph<FixedNode>(GraalAdapter.fromGraal(graph))
        val writer = StringWriter()
        adapter.exportQuery(writer)
        openGraph(writer.buffer.toString())
    }

    @Test
    fun `get pointsto graph with phi for phiTest5`() {
        println("# phiTest3")
        val analysis = FlowSensitivePointsToAnalysis(::phiTest5.javaMethod)
        openGraph(analysis.toString())
    }

}
