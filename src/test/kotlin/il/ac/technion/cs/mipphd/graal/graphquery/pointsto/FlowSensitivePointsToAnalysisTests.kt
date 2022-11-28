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


class FlowSensitivePointsToAnalysisTests {

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
    fun `get flow sensitive pointsto graph for phiTest1`() {
        println("# phiTest1")
        val analysis = FlowSensitivePointsToAnalysis(::phiTest1.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get flow sensitive pointsto graph for phiTest2`() {
        println("# phiTest2")
        val analysis = FlowSensitivePointsToAnalysis(::phiTest2.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get flow sensitive pointsto graph for testSelfAssignment`() {
        println("# testSelfAssignment")
        val analysis = FlowSensitivePointsToAnalysis(::testSelfAssignment.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get flow sensitive pointsto graph for phiTest3`() {
        println("# phiTest3")
        val analysis = FlowSensitivePointsToAnalysis(::phiTest3.javaMethod, summaryFunc = SummaryKeyByNodeSourcePosOrIdentity)
        openGraph(analysis.toString())
    }


    @Test
    fun `get flow sensitive pointsto graph for phiTest4`() {
        println("# phiTest4")
        val analysis = FlowSensitivePointsToAnalysis(::phiTest4.javaMethod)
        openGraph(analysis.toString())
    }

    @Test
    fun `get flow sensitive pointsto graph for phiTest5`() {
        println("# phiTest5")
        val analysis = FlowSensitivePointsToAnalysis(::phiTest5.javaMethod)
        openGraph(analysis.toString())
    }

}
