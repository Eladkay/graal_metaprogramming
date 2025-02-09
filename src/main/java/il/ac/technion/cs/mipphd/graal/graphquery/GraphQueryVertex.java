package il.ac.technion.cs.mipphd.graal.graphquery;

import edu.umd.cs.findbugs.annotations.NonNull;
import il.ac.technion.cs.mipphd.graal.utils.WrappedIRNodeImpl;
import org.graalvm.compiler.graph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class GraphQueryVertex {
    @NonNull
    private MQuery mQuery;

    public GraphQueryVertex(@NotNull MQuery mQuery) {
        this.mQuery = mQuery;
    }

    @NonNull
    public static GraphQueryVertex fromQuery(@NonNull String query) {
        return new GraphQueryVertex(MQueryKt.parseMQuery(query));
    }

    @NonNull
    public static GraphQueryVertex fromName(@NonNull String name) {
        GraphQueryVertex v = fromQuery("1 = 1");
        v.setName(name);
        return v;
    }

    @NonNull
    public MQuery getMQuery() {
        return mQuery;
    }

    public void setMQuery(@NonNull MQuery mQuery) {
        this.mQuery = mQuery;
    }

    @NonNull
    public String label() {
        return mQuery.serialize();
    }

    @NonNull
    public Optional<String> captureGroup() {
        Metadata metadata = (Metadata) this.mQuery;
        return metadata.getOptions().stream()
                .filter(option -> option instanceof MetadataOption.CaptureName)
                .map(captureName -> ((MetadataOption.CaptureName) captureName).getName())
                .findAny();
    }

    @Override
    public String toString() {
        return "GraphQueryVertex{" +
                "mQuery=" + mQuery.serialize() +
                '}';
    }

    private String name = "n" + this.hashCode();


    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }


    public boolean match(Node value) {
        return match(new WrappedIRNodeImpl(value));
    }

    public boolean match(WrappedIRNodeImpl value) {
        return match(new AnalysisNode.IR(value));
    }

    public boolean match(AnalysisNode value) {
        return mQuery.interpret(new QueryTargetNode(value));
    }

    /* do not override equals/hashCode! */
}
