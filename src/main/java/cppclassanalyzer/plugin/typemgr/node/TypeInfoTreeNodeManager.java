package cppclassanalyzer.plugin.typemgr.node;

import ghidra.app.util.SymbolPath;
import ghidra.framework.model.DomainObject;
import ghidra.framework.model.DomainObjectChangedEvent;
import ghidra.framework.model.DomainObjectListener;
import ghidra.util.Disposable;
import ghidra.util.Lock;
import ghidra.util.datastruct.RedBlackLongKeySet;
import ghidra.util.exception.AssertException;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

import cppclassanalyzer.data.ClassTypeInfoManager;
import cppclassanalyzer.data.typeinfo.ClassTypeInfoDB;
import cppclassanalyzer.database.SchemaMismatchException;
import cppclassanalyzer.database.record.TypeInfoTreeNodeRecord;
import cppclassanalyzer.database.schema.TypeInfoTreeNodeSchema;
import cppclassanalyzer.database.tables.TypeInfoTreeNodeTable;
import db.*;
import docking.widgets.tree.GTree;
import docking.widgets.tree.GTreeNode;

import static cppclassanalyzer.database.record.TypeInfoTreeNodeRecord.*;
import static cppclassanalyzer.database.schema.fields.TypeInfoTreeNodeSchemaFields.*;

import java.io.IOException;
import java.util.*;

public class TypeInfoTreeNodeManager implements Disposable, DomainObjectListener {

	private final TypeInfoTreeNodeTable table;
	private final ClassTypeInfoManager manager;
	private final TransactionHandler handler;
	private final DBHandle handle;
	private final Lock lock = new Lock(getClass().getSimpleName());
	private AbstractManagerNode root;

	public TypeInfoTreeNodeManager(ClassTypeInfoManager manager, DBHandle handle) {
		this(manager, TypeInfoTreeNodeManager.class.getSimpleName(), handle);
	}

	public TypeInfoTreeNodeManager(ClassTypeInfoManager manager, DBHandle handle, String name) {
		this(manager, name + " " + TypeInfoTreeNodeManager.class.getSimpleName(), handle);
	}

	private TypeInfoTreeNodeManager(ClassTypeInfoManager manager, String name, DBHandle handle) {
		this.handle = handle;
		this.manager = manager;
		this.handler = new TransactionHandler(handle);
		this.table = getTable(name);
		manager.addListener(this);
	}

	@Override
	public void domainObjectChanged(DomainObjectChangedEvent event) {

		if (getTree() == null || !getTree().isVisible()) {
			return;
		}

		if (event.containsEvent(DomainObject.DO_OBJECT_RESTORED)) {
			DomainObject source = (DomainObject) event.getSource();
			if (root.getName().equals(source.getName())) {
				root.rebuild();
			}
		}
	}

	@Override
	public void dispose() {
		manager.removeListener(this);
	}

	private TypeInfoTreeNodeTable getTable(String name) {
		Table rawTable = handle.getTable(name);
		if (rawTable == null) {
			try {
				handler.start();
				rawTable = handle.createTable(
					name,
					TypeInfoTreeNodeSchema.SCHEMA,
					TypeInfoTreeNodeSchema.INDEXED_COLUMNS);
				handler.end();
			} catch (IOException e) {
				dbError(e);
			}
		}
		try {
			return new TypeInfoTreeNodeTable(rawTable);
		} catch (AssertException e) {
			throw new SchemaMismatchException(TypeInfoTreeNodeTable.class);
		}
	}

	TypeInfoTreeNodeRecord getRootRecord() {
		TypeInfoTreeNodeRecord record = getRecord("/");
		if (record == null) {
			record = createRootRecord();
		}
		return record;
	}

	void setRootNode(AbstractManagerNode node) {
		this.root = node;
	}

	private TypeInfoTreeNodeRecord createRootRecord() {
		TypeInfoTreeNodeRecord record = createRecord();
		record.setStringValue(NAME, "Root");
		record.setStringValue(SYMBOL_PATH, "/");
		updateRecord(record);
		return record;
	}

	TypeInfoTreeNodeRecord createRecord(SymbolPath path, byte type) {
		lock.acquire();
		try {
			TypeInfoTreeNodeRecord record = createRecord();
			record.setStringValue(SYMBOL_PATH, path.getPath());
			record.setStringValue(NAME, path.getName());
			record.setByteValue(TYPE_ID, type);
			updateRecord(record);
			return record;
		} finally {
			lock.release();
		}
	}

	public TypeInfoTreeNodeRecord getRecord(long key) {
		lock.acquire();
		try {
			return table.getRecord(key);
		} catch (IOException e) {
			dbError(e);
		} finally {
			lock.release();
		}
		return null;
	}

	private TypeInfoTreeNodeRecord getRecord(String path) {
		lock.acquire();
		try {
			StringField field = new StringField(path);
			long[] keys = table.getTable().findRecords(field, SYMBOL_PATH.ordinal());
			if (keys.length == 1) {
				return table.getRecord(keys[0]);
			}
		} catch (IOException e) {
			dbError(e);
		} finally {
			lock.release();
		}
		return null;
	}

	TypeInfoTreeNodeRecord createRecord() {
		lock.acquire();
		try {
			handler.start();
			long key = table.getTable().getKey();
			TypeInfoTreeNodeSchema schema = table.getSchema();
			db.Record record = schema.createRecord(key);
			table.getTable().putRecord(record);
			handler.end();
			return schema.getRecord(record);
		} catch (IOException e) {
			dbError(e);
		} finally {
			lock.release();
		}
		return null;
	}

	GTreeNode createNamespaceNode(SymbolPath path) {
		TypeInfoTreeNodeRecord record =
			createRecord(path, TypeInfoTreeNodeRecord.NAMESPACE_NODE);
		return new NamespacePathNode(this, record);
	}

	GTreeNode createTypeNode(ClassTypeInfoDB type) {
		TypeInfoTreeNodeRecord record =
			createRecord(type.getSymbolPath(), TypeInfoTreeNodeRecord.TYPEINFO_NODE);
		record.setLongValue(TYPE_KEY, type.getKey());
		updateRecord(record);
		return new TypeInfoNode(type, record);
	}

	private void dbError(IOException e) {
		manager.dbError(e);
	}

	GTreeNode createNode(TypeInfoTreeNodeRecord record) {
		long key = record.getLongValue(TYPE_KEY);
		switch (record.getByteValue(TYPE_ID)) {
			case NAMESPACE_NODE:
				return new NamespacePathNode(this, record);
			case TYPEINFO_NODE:
				return new TypeInfoNode(manager.getType(key), record);
			default:
				throw new AssertException("Unknown TypeInfoTreeNode ID");
		}
	}

	public void updateRecord(TypeInfoTreeNodeRecord record) {
		lock.acquire();
		try {
			handler.start();
			table.getTable().putRecord(record.getRecord());
			handler.end();
		} catch (IOException e) {
			dbError(e);
		} finally {
			lock.release();
		}
	}

	ClassTypeInfoManager getManager() {
		return manager;
	}

	GTree getTree() {
		if (root == null) {
			return null;
		}
		return ((GTreeNode) root).getTree();
	}

	List<GTreeNode> generateChildren(TypeInfoTreeNode node, TaskMonitor monitor)
			throws CancelledException {
		TypeInfoTreeNodeRecord record = node.getRecord();
		long[] keys = record.getLongArray(CHILDREN_KEYS);
		RedBlackLongKeySet keySet = new RedBlackLongKeySet();
		List<GTreeNode> children = new ArrayList<>(keys.length);
		monitor.initialize(keys.length);
		for (long key : keys) {
			monitor.checkCanceled();
			if (!keySet.containsKey(key)) {
				keySet.put(key);
				TypeInfoTreeNodeRecord child = getRecord(key);
				children.add(createNode(child));
			}
			monitor.incrementProgress(1);
		}
		children.sort(null);
		return children;
	}

	// dbHandle transactions are different
	private static class TransactionHandler {
		final DBHandle handle;
		long id;

		TransactionHandler(DBHandle handle) {
			this.handle = handle;
			this.id = -1;
		}

		void start() {
			id = handle.isTransactionActive() ? -1 : handle.startTransaction();
		}

		void end() throws IOException {
			if (id != -1) {
				handle.endTransaction(id, true);
			}
		}
	}
}
