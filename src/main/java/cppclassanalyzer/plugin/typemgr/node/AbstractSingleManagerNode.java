package cppclassanalyzer.plugin.typemgr.node;

import cppclassanalyzer.data.ClassTypeInfoManager;
import cppclassanalyzer.data.typeinfo.ClassTypeInfoDB;
import cppclassanalyzer.database.record.TypeInfoTreeNodeRecord;
import docking.widgets.tree.GTreeNode;

import static cppclassanalyzer.database.schema.fields.TypeInfoTreeNodeSchemaFields.*;

import java.util.*;
import java.util.stream.Collectors;

import ghidra.app.util.SymbolPath;
import ghidra.util.Msg;
import ghidra.util.exception.AssertException;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

abstract class AbstractSingleManagerNode extends AbstractManagerNode {

	private final Map<SymbolPath, GTreeNode> treePaths;

	AbstractSingleManagerNode(ClassTypeInfoManager manager) {
		super(manager);
		this.treePaths = Collections.synchronizedMap(new HashMap<>(manager.getTypeCount()));
	}

	@Override
	public final void addNode(int index, GTreeNode node) {
		if (node instanceof TypeInfoTreeNode) {
			TypeInfoTreeNode treeNode = (TypeInfoTreeNode) node;
			TypeInfoTreeNodeRecord record = getRecord();
			long key = treeNode.getKey();
			long[] children = record.getLongArray(CHILDREN_KEYS);
			if (Arrays.binarySearch(children, key) < 0) {
				Set<Long> kids = Arrays.stream(children)
					.boxed()
					.collect(Collectors.toCollection(TreeSet::new));
				kids.add(key);
				children = kids.stream()
					.mapToLong(Long::longValue)
					.toArray();
				record.setLongArray(CHILDREN_KEYS, children);
				getManager().updateRecord(record);
			}
		}
		super.addNode(index, node);
	}

	@Override
	public final List<GTreeNode> generateChildren(TaskMonitor monitor) throws CancelledException {
		return getManager().generateChildren(this, monitor);
	}

	private void convertToTypeNode(NamespacePathNode node, ClassTypeInfoDB type) {
		TypeInfoTreeNodeManager treeManager = getManager();
		GTreeNode parent = node.getParent();
		parent.removeNode(node);
		TypeInfoTreeNodeRecord record = ((TypeInfoTreeNode) node).getRecord();
		node.dispose();
		record.setByteValue(TYPE_ID, TypeInfoTreeNodeRecord.TYPEINFO_NODE);
		record.setLongValue(TYPE_KEY, type.getKey());
		treeManager.updateRecord(record);
		GTreeNode child = new TypeInfoNode(type, record);
		treePaths.put(type.getSymbolPath(), child);
		parent.addNode(child);
	}

	private void createTypeNode(GTreeNode node, ClassTypeInfoDB type) {
		TypeInfoTreeNodeManager treeManager = getManager();
		GTreeNode child = treeManager.createTypeNode(type);
		treePaths.put(type.getSymbolPath(), child);
		node.addNode(child);
		return;
	}

	private GTreeNode createNamespaceNode(GTreeNode node, SymbolPath path) {
		TypeInfoTreeNodeManager treeManager = getManager();
		GTreeNode child = treeManager.createNamespaceNode(path);
		treePaths.put(path, child);
		node.addNode(child);
		return child;
	}

	@Override
	public final void addNode(ClassTypeInfoDB type) {
		SymbolPath path = type.getSymbolPath();
		if (treePaths.containsKey(path)) {
			GTreeNode node = treePaths.get(path);
			if (node instanceof TypeInfoNode) {
				Msg.warn(this, "Node for "+type.getFullName()+" already exists");
			} else if (node instanceof NamespacePathNode) {
				convertToTypeNode((NamespacePathNode) node, type);
			}
			return;
		}
		path = path.getParent();
		if (path != null && treePaths.containsKey(path)) {
			GTreeNode node = treePaths.get(path);
			createTypeNode(node, type);
		} else {
			int size = path != null ? path.asList().size() : 0;
			ArrayDeque<SymbolPath> stack = new ArrayDeque<>(size);
			while (!treePaths.containsKey(path) && path != null) {
				stack.push(path);
				path = path.getParent();
			}
			GTreeNode node = path != null ? treePaths.get(path) : this;
			while (!stack.isEmpty()) {
				node = createNamespaceNode(node, stack.pop());
			}
			createTypeNode(node, type);
		}
	}

	@Override
	public final TypeInfoNode getNode(ClassTypeInfoDB type) {
		SymbolPath path = type.getSymbolPath();
		if (!treePaths.containsKey(path)) {
			Msg.warn(this, "Node for "+type.getName()+" not found");
			addNode(type);
		}
		GTreeNode node = treePaths.get(path);
		if (node instanceof TypeInfoNode) {
			return (TypeInfoNode) node;
		}
		// should be unreachable
		throw new AssertException("Node for "+type.getName()+" is not the correct node type");
	}
}
