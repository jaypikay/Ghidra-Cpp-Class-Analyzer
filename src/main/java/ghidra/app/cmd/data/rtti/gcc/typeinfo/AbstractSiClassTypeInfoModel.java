package ghidra.app.cmd.data.rtti.gcc.typeinfo;

import java.util.Collections;
import java.util.Set;

import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.program.model.reloc.Relocation;
import ghidra.program.model.reloc.RelocationTable;
import ghidra.app.cmd.data.rtti.ClassTypeInfo;
import ghidra.app.cmd.data.rtti.TypeInfo;
import ghidra.app.cmd.data.rtti.gcc.ExternalClassTypeInfo;
import ghidra.app.cmd.data.rtti.gcc.TypeInfoUtils;
import ghidra.app.cmd.data.rtti.gcc.factory.TypeInfoFactory;

import static ghidra.app.util.datatype.microsoft.MSDataTypeUtils.getAbsoluteAddress;

/**
 * Base Model for __si_class_type_info and its derivatives.
 */
abstract class AbstractSiClassTypeInfoModel extends AbstractClassTypeInfoModel {

    protected AbstractSiClassTypeInfoModel(Program program, Address address) {
        super(program, address);
    }

    private static Address getBaseTypeAddress(Program program, Address address) {
        Address pointerAddress = address.add(program.getDefaultPointerSize() << 1);
        return getAbsoluteAddress(program, pointerAddress);
    }

    @Override
    public boolean hasParent() {
        return true;
    }

    @Override
    public ClassTypeInfo[] getParentModels() {
        Address baseAddress = getBaseTypeAddress(program, address);
        if (baseAddress != null && program.getMemory().getBlock(baseAddress).isInitialized()) {
            TypeInfo parent = TypeInfoFactory.getTypeInfo(program, baseAddress);
            if (parent instanceof ClassTypeInfo) {
                return new ClassTypeInfo[]{
                    (ClassTypeInfo) parent
                    };
            }
        }
        RelocationTable table = program.getRelocationTable();
		Relocation reloc = table.getRelocation(baseAddress);
		if (reloc == null) {
			reloc = table.getRelocation(address.add(program.getDefaultPointerSize() << 1));
		}
        if (reloc != null && reloc.getSymbolName() != null) {
            TypeInfo parent = TypeInfoUtils.getExternalTypeInfo(program, reloc);
            if (parent instanceof ClassTypeInfo) {
                return new ClassTypeInfo[]{
                    (ClassTypeInfo) parent
                    };
            }
        }
        return new ClassTypeInfo[0];
    }

    @Override
    public Set<ClassTypeInfo> getVirtualParents() {
		ClassTypeInfo[] parents = getParentModels();
		if (parents[0] instanceof ExternalClassTypeInfo) {
			// TODO need more data to know if this is acceptable or not
			return Collections.emptySet();
		}
        return parents[0].getVirtualParents();
    }

}
