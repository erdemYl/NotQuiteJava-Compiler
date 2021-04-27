package analysis;

import java.util.*;
import notquitejava.ast.*;

/**
 * Name table for global functions and all declared arrays
 * of an NQJ program.
 */
public class NameTable {
    private final Map<Type, ArrayType> arrayTypes = new HashMap<>();

    private final Map<String, NQJFunctionDecl> globalFunctions = new HashMap<>();

    private final Analysis analysis;

    NameTable(Analysis analysis, NQJProgram prog) {
        this.analysis = analysis;
        globalFunctions.put("printInt", NQJ.FunctionDecl(NQJ.TypeInt(), "main",
                NQJ.VarDeclList(NQJ.VarDecl(NQJ.TypeInt(), "elem")), NQJ.Block()));
        for (NQJFunctionDecl f : prog.getFunctionDecls()) {
            var old = globalFunctions.put(f.getName(), f);
            if (old != null) {
                analysis.addError(f, "There already is a global function with name " + f.getName()
                        + " defined in " + old.getSourcePosition().getLine()
                         + ":" + old.getSourcePosition().getColumn() + ".");
            }
        }
    }

    public NQJFunctionDecl lookupFunction(String functionName) {
        return globalFunctions.get(functionName);
    }

    /**
     * Transform base type to array type.
     */
    public ArrayType getArrayType(Type baseType) {
        if (!arrayTypes.containsKey(baseType)) {
            arrayTypes.put(baseType, new ArrayType(baseType));
        }
        return arrayTypes.get(baseType);
    }
}
