package il.ac.technion.cs.mipphd.graal.graphquery

import il.ac.technion.cs.mipphd.graal.Listable
import il.ac.technion.cs.mipphd.graal.utils.MethodToGraph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.reflect.jvm.javaMethod

val maximumQueryText = """
    digraph G {
      n2021302077 [ label="is('Invoke') and method().name = 'get' and method().className = 'java.util.List'" ];
      n1948571966 [ label="is('ValueNode')" ];
      n1689892436 [ label="is('calc.ConditionalNode')" ];
      n818409230 [ label="is('LogicNode')" ];
      n484969820 [ label="is('PhiNode')" ];
      n1314376406 [ label="is('ValueProxyNode')" ];
      n1744166494 [ label="is('ReturnNode')" ];
      n2021302077 -> n1948571966 [ label="*|is('DATA')" ];
      n1948571966 -> n1689892436 [ label="is('DATA')" ];
      n818409230 -> n1689892436 [ label="is('DATA') or is('CONTROL')" ];
      n1689892436 -> n484969820 [ label="is('DATA')" ];
      n484969820 -> n1689892436 [ label="is('DATA')" ];
      n484969820 -> n1314376406 [ label="*|is('DATA')" ];
      n1314376406 -> n1744166494 [ label="is('DATA')" ];
    }
""".trimIndent()

val repeatedNodesQueryText = """
    digraph G {
    	framestate [ label="is('FrameState')"];
    	merge [ label="1 = 1"]
    	values [ label="[]|1 = 1"]

    	values -> framestate [ label = "is('DATA') and name() = 'values'"];
    	framestate -> merge [ label = "is('DATA') and name() = 'stateAfter'"];
    }
""".trimIndent()

internal class GraphQueryTest {
    val methodToGraph = MethodToGraph()
    private val maximum = Listable::maximum.javaMethod

    @Test
    fun `repeated query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val results = GraphQuery.importQuery(repeatedNodesQueryText).match(cfg)

        println(results)

        assertEquals(19, results.size)
    }

    @Test
    fun `imported maximum query matches maximum function ir`() {
        val cfg = methodToGraph.getCFG(maximum)
        val query = GraphQuery.importQuery(StringReader(maximumQueryText))


        val writer = StringWriter()
        query.exportQuery(writer)
        println(writer.buffer.toString())

        val set = mutableSetOf(*cfg.asCFG().graph.nodes.toList().toTypedArray())
        query.vertexSet().forEach { queryNode ->
            assertTrue(set.any(queryNode::match), "Couldn't find $queryNode")
        }
        assertEquals(18, query.match(cfg).size)
    }
}