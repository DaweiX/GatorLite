package presto.android.gui;
import java.util.Set;

import presto.android.Hierarchy;
import presto.android.xml.XMLParser;
import com.google.common.collect.Sets;

public class GUIAnalysis {

	public Hierarchy hierarchy;
	public XMLParser xmlParser;

	public Set<Integer> allLayoutIds = Sets.newHashSet();
	public Set<Integer> allMenuIds = Sets.newHashSet();
	public Set<Integer> allWidgetIds = Sets.newHashSet();
	public Set<Integer> allStringIds = Sets.newHashSet();
	public FlowGraph flowgraph;
	public FixpointSolver fixpointSolver;
	public VariableValueQueryInterface variableValueQueryInterface;

	GUIAnalysis(Hierarchy hierarchy, XMLParser xmlParser) {
		this.hierarchy = hierarchy;
		this.xmlParser = xmlParser;
	}

	private static GUIAnalysis instance;

	public static synchronized GUIAnalysis v() {
		if (instance == null) {
			instance = new GUIAnalysis(Hierarchy.v(), XMLParser.Factory.getXMLParser());
		}
		return instance;
	}

	/**
	 * Populate ID containers with information from XML files, and print out
	 * some statistics as a sanity check. The ID containers are used in the
	 * construction of flowgraph.
	 */
	public void populateIDContainers() {
		// First, the layout ids
		allLayoutIds.addAll(xmlParser.getApplicationLayoutIdValues());
		allLayoutIds.addAll(xmlParser.getSystemLayoutIdValues());
		// Next, the menu ids (similarly to layouts, could be inflated):
		allMenuIds.addAll(xmlParser.getApplicationMenuIdValues());
		allMenuIds.addAll(xmlParser.getSystemMenuIdValues());
		// And the widget ids
		allWidgetIds.addAll(xmlParser.getApplicationRIdValues());
		allWidgetIds.addAll(xmlParser.getSystemRIdValues());
		allStringIds.addAll(xmlParser.getStringIdValues());

		System.out.println("[XML] Layout Ids: " + allLayoutIds.size() + ", Menu Ids: " + allMenuIds.size()
				+ ", Widget Ids: " + allWidgetIds.size() + ", String Ids: " + allStringIds.size());
		System.out.println("[XML] MainActivity: " + xmlParser.getMainActivity());
	}

	public void run() {
		System.out.println("[GUIAnalysis] Start");
		long startTime = System.nanoTime();

		// 0. Populate IDs
		populateIDContainers();

		// 1. Build flow graph
		System.out.println("  - Build flow graph");
		flowgraph = new FlowGraph(hierarchy, allLayoutIds, allMenuIds, allWidgetIds, allStringIds);
		flowgraph.build();

		// 2. Fix-point computation
		System.out.println("  - Fix-point computation");
		fixpointSolver = new FixpointSolver(flowgraph);
		fixpointSolver.solve();

		// 3. Variable value query interface
		System.out.println("  - Variable value query interface");
		variableValueQueryInterface = DemandVariableValueQuery.v(flowgraph, fixpointSolver);

		// 4. Construct the output
		System.out.println("  - Construct output");
		GUIAnalysisOutput output = new DefaultGUIAnalysisOutput(this);

		long estimatedTime = System.nanoTime() - startTime;
		output.setRunningTimeInNanoSeconds(estimatedTime);
		System.out.println("[GUIAnalysis] End: " + (estimatedTime * 1.0e-09) + " sec");

		// 5. Client analyses
		executeClientAnalyses(output);
	}

	void executeClientAnalyses(GUIAnalysisOutput output) {
		GUIHierarchyPrinterClient client = new GUIHierarchyPrinterClient();
		String clientName = "GUIHierarchyPrinterClient";
		System.out.println("[" + clientName + "] Start");
		long startTime = System.nanoTime();
		client.run(output);
		long estimatedTime = System.nanoTime() - startTime;
		System.out.println("[" + clientName + "] End: " + (estimatedTime * 1.0e-09) + " sec");
	}
}
