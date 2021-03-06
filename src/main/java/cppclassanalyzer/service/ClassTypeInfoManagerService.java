package cppclassanalyzer.service;

import java.io.File;
import java.io.IOException;
import java.util.List;

import ghidra.app.cmd.data.rtti.ClassTypeInfo;
import ghidra.app.plugin.core.datamgr.archive.DuplicateIdException;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.ServiceInfo;

import cppclassanalyzer.data.ClassTypeInfoManager;
import cppclassanalyzer.data.ProgramClassTypeInfoManager;
import cppclassanalyzer.data.typeinfo.ArchivedClassTypeInfo;
import cppclassanalyzer.data.vtable.ArchivedVtable;
import cppclassanalyzer.decompiler.DecompilerAPI;
import cppclassanalyzer.plugin.ClassTypeInfoManagerPlugin;
import cppclassanalyzer.utils.CppClassAnalyzerUtils;
import docking.widgets.tree.GTree;

import ghidra.program.model.listing.Program;

//@formatter:off
@ServiceInfo(
	defaultProvider = ClassTypeInfoManagerPlugin.class,
	description = "Service to provide ClassTypeInfoManagers"
)
//@formatter:on
public interface ClassTypeInfoManagerService {

	public void closeManager(ClassTypeInfoManager manager);

	public default void openArchive(File archive)
			throws IOException, DuplicateIdException {
		openArchive(archive, false);
	}

	public void openArchive(File archive, boolean updateable)
		throws IOException, DuplicateIdException;

	public void createArchive(File archive)
		throws IOException, DuplicateIdException;

	public ProgramClassTypeInfoManager getManager(Program program);

	public List<ClassTypeInfoManager> getManagers();

	public void managerAdded(ClassTypeInfoManager manager);
	public void managerRemoved(ClassTypeInfoManager manager);

	public GTree getTree();

	public static boolean isEnabled(Program program) {
		PluginTool tool = CppClassAnalyzerUtils.getTool(program);
		if (tool == null) {
			return false;
		}
		return tool.getService(ClassTypeInfoManagerService.class) != null;
	}

	public ClassTypeInfo getExternalClassTypeInfo(Program program, String mangled);

	public ArchivedClassTypeInfo getArchivedClassTypeInfo(String symbolName);
	public ArchivedVtable getArchivedVtable(String symbolName);

	public DecompilerAPI getDecompilerAPI(Program program);

	public ProgramClassTypeInfoManager getCurrentManager();

}
