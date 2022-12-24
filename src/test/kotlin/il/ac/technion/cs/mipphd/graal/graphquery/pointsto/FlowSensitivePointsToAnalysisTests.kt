package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisGraph
import il.ac.technion.cs.mipphd.graal.graphquery.AnalysisNode
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.junit.jupiter.api.Test
import java.io.File
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
