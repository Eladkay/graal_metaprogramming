package il.ac.technion.cs.mipphd.graal.graphquery.pointsto

import il.ac.technion.cs.mipphd.graal.utils.NodeWrapper

class PointsToNode(private val key: Any) : NodeWrapper(null) {
    val representing = mutableSetOf<NodeWrapper>()
    override fun isType(className: String?): Boolean {
        return className == javaClass.canonicalName
    }

    override fun toString(): String {
        return "PointsToNode(representing ${if(representing.size == 1) representing.first().toString() else "${representing.size} nodes"}" +
                "${if(key == representing.firstOrNull()) "" else ", key=$key"})"
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as PointsToNode

        if (key != other.key) return false
        if (representing != other.representing) return false

        return true
    }
}
