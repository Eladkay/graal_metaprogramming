package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
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

class PointsToAnalysisWithPhiTests {

    val methodToGraph = MethodToGraph()
    private val autoOpenFiles = true

    private fun openGraph(str: String) {
        println(str)
        if(autoOpenFiles) {
            val file = File("tmp_graph.dot")
            file.writeText(str)
            val proc1 = Runtime.getRuntime().exec("dot -Tpng tmp_graph.dot -o tmp_graph.png")
            proc1.waitFor()
            Runtime.getRuntime().exec("open tmp_graph.png")
            file.delete()
        }
    }

    @Test
    fun `get graal graph for phiTest1`() {
        val method = ::phiTest1.javaMethod
        val graph = methodToGraph.getCFG(method)
        val adapter = GraalAdapter.fromGraal(graph)
        val writer = StringWriter()
        adapter.exportQuery(writer)
        openGraph(writer.buffer.toString())
    }

    @Test
    fun `get pointsto graph with phi for phiTest1`() {
        println("# phiTest1")
        val analysis = PointsToAnalysisWithPhi(::phiTest1.javaMethod)
        openGraph(analysis.toString())
    }

}
