package cppclassanalyzer.plugin.typemgr;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.Icon;
import javax.swing.tree.TreePath;

import cppclassanalyzer.plugin.typemgr.node.ProjectArchiveTypeInfoNode;
import cppclassanalyzer.plugin.typemgr.node.TypeInfoArchiveNode;
import cppclassanalyzer.plugin.typemgr.node.TypeInfoNode;
import cppclassanalyzer.plugin.typemgr.node.TypeInfoRootNode;
import ghidra.app.plugin.core.datamgr.util.DataTypeUtils;
import ghidra.util.exception.AssertException;
import ghidra.util.task.Task;
import ghidra.util.task.TaskMonitor;

import cppclassanalyzer.data.ClassTypeInfoManager;
import cppclassanalyzer.data.manager.LibraryClassTypeInfoManager;
import cppclassanalyzer.data.manager.ProjectClassTypeInfoManager;
import cppclassanalyzer.data.typeinfo.ClassTypeInfoDB;
import cppclassanalyzer.plugin.ClassTypeInfoManagerPlugin;
import cppclassanalyzer.plugin.TypeInfoManagerListener;
import docking.widgets.tree.GTree;
import docking.widgets.tree.GTreeNode;
import docking.widgets.tree.support.GTreeDragNDropHandler;

public final class TypeInfoArchiveGTree extends GTree implements TypeInfoManagerListener {

	private static final long serialVersionUID = 1L;

	private final TypeInfoDragNDropHandler dropHandler;
	private final ClassTypeInfoManagerPlugin plugin;

	public TypeInfoArchiveGTree(ClassTypeInfoManagerPlugin plugin) {
		super(new TypeInfoArchiveGTreeRootNode());
		this.dropHandler = new TypeInfoDragNDropHandler();
		this.plugin = plugin;
	}

	private TypeInfoArchiveGTreeRootNode getRoot() {
		return (TypeInfoArchiveGTreeRootNode) getModelRoot();
	}

	public ClassTypeInfoManagerPlugin getPlugin() {
		return plugin;
	}

	@Override
	public GTreeDragNDropHandler getDragNDropHandler() {
		return dropHandler;
	}

	@Override
	public void setDragNDropHandler(GTreeDragNDropHandler dummy) {
	}

	@Override
	public void dispose() {
		super.dispose();
	}

	@Override
	public void managerOpened(ClassTypeInfoManager manager) {
		if (manager instanceof LibraryClassTypeInfoManager) {
			LibraryClassTypeInfoManager libMan = (LibraryClassTypeInfoManager) manager;
			ProjectArchiveTypeInfoNode node =
				(ProjectArchiveTypeInfoNode) getRoot().getNode(libMan.getProjectManager());
			node.addNode(libMan);
		} else {
			getRoot().addNode(manager);
		}
	}

	@Override
	public void managerClosed(ClassTypeInfoManager manager) {
		getRoot().removeNode(manager);
	}

	private TypeInfoArchiveNode getManagerNode(ClassTypeInfoDB type) {
		return getRoot().getNode(type.getManager());
	}

	@Override
	public void typeAdded(ClassTypeInfoDB type) {
		BackgroundTask task = new BackgroundTask(this::doAddType, type);
		plugin.getTool().execute(task);
	}

	private void doAddType(ClassTypeInfoDB type) {
		getManagerNode(type).addNode(type);
	}

	@Override
	public void typeRemoved(ClassTypeInfoDB type) {
		BackgroundTask task = new BackgroundTask(this::doRemoveType, type);
		plugin.getTool().execute(task);
	}

	private void doRemoveType(ClassTypeInfoDB type) {
		GTreeNode node = getNode(type);
		if (node != null && node.getName().equals(type.getName())) {
			GTreeNode root = (GTreeNode) getManagerNode(type);
			root.removeNode(node);
		}
	}

	@Override
	public void typeUpdated(ClassTypeInfoDB type) {
		BackgroundTask task = new BackgroundTask(this::doUpdateType, type);
		plugin.getTool().execute(task);
	}

	private void doUpdateType(ClassTypeInfoDB type) {
		TypeInfoNode node = getNode(type);
		node.typeUpdated(type);
	}

	TypeInfoNode getNode(ClassTypeInfoDB type) {
		return getManagerNode(type).getNode(type);
	}

	public List<GTreeNode> getSelectedNodes() {
		TreePath[] selectionPaths = getSelectionPaths();
		if (selectionPaths == null || selectionPaths.length == 0) {
			return Collections.emptyList();
		}
		return Arrays.stream(selectionPaths)
			.map(TreePath::getLastPathComponent)
			.filter(GTreeNode.class::isInstance)
			.map(GTreeNode.class::cast)
			.collect(Collectors.toList());
	}

	private static class TypeInfoArchiveGTreeRootNode extends GTreeNode {

		@Override
		public String getName() {
			return "TypeInfo Archives";
		}

		@Override
		public Icon getIcon(boolean expanded) {
			return DataTypeUtils.getRootIcon(expanded);
		}

		@Override
		public String getToolTip() {
			return null;
		}

		@Override
		public boolean isLeaf() {
			return false;
		}

		@Override
		public void addNode(GTreeNode node) {
			if (isLoaded()) {
				List<GTreeNode> kids = children();
				int index = Collections.binarySearch(kids, node);
				if (index >= 0) {
					String msg = "Child node " + node.getName() + " already exists in " + getName();
					throw new AssertException(msg);
				}
				super.addNode(-(index + 1), node);
			}
		}

		void addNode(ClassTypeInfoManager manager) {
			if (manager instanceof ProjectClassTypeInfoManager) {
				addNode(new ProjectArchiveTypeInfoNode((ProjectClassTypeInfoManager) manager));
			} else {
				addNode(new TypeInfoRootNode(manager));
			}
		}

		void removeNode(ClassTypeInfoManager manager) {
			GTreeNode node = getNode(manager).getNode();
			removeNode(node);
			node.dispose();
		}

		TypeInfoArchiveNode getNode(ClassTypeInfoManager manager) {
			if (manager instanceof LibraryClassTypeInfoManager) {
				LibraryClassTypeInfoManager libMan = (LibraryClassTypeInfoManager) manager;
				ProjectArchiveTypeInfoNode node =
					(ProjectArchiveTypeInfoNode) getNode(libMan.getProjectManager());
				return (TypeInfoArchiveNode) node.getChild(manager.getName());
			}
			return (TypeInfoArchiveNode) getChild(manager.getName());
		}

	}

	private static class BackgroundTask extends Task {

		private final Consumer<ClassTypeInfoDB> consumer;
		private final ClassTypeInfoDB type;

		BackgroundTask(Consumer<ClassTypeInfoDB> consumer, ClassTypeInfoDB type) {
			super("", false, false, false);
			this.consumer = consumer;
			this.type = type;
		}

		@Override
		public void run(TaskMonitor monitor) {
			consumer.accept(type);
		}
	}

}
