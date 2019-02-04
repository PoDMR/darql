@GrabConfig(systemClassLoader=true)
@Grab(group='org.jgrapht', module='jgrapht-core', version='1.0.0')
@Grab(group='org.jgrapht', module='jgrapht-ext', version='1.0.0')
import org.jgrapht.ext.*;
import org.jgrapht.*;
import org.jgrapht.graph.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

VertexProvider<String> vertexProvider = new VertexProvider<String>() {
	String buildVertex(String label, Map<String, String> attributes) {
		return label;
	}
};
EdgeProvider<String, String> edgeProvider = new EdgeProvider<String, String>() {
	String buildEdge(String from, String to, String label, Map<String, String> attributes) {
		return label;
	}
};

GmlImporter<String, String> gmlImporter = new GmlImporter<String, String>(vertexProvider, edgeProvider);

DirectedGraph<Object, DefaultEdge> graph = new DirectedPseudograph<>(DefaultEdge.class);

byte[] bytes = Files.readAllBytes(Paths.get(args[0]));
String gmlStr = new String(bytes, StandardCharsets.UTF_8);
Reader reader = new StringReader(gmlStr);
gmlImporter.importGraph(graph, reader);
System.out.println(graph.edgeSet());
