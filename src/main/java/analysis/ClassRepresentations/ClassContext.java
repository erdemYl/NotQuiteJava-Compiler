package analysis.ClassRepresentations;

import analysis.MethodScope;
import analysis.VarRef;

import java.util.HashMap;

/**
 * Represents a class, with its context.
 */
public class ClassContext {
    private ClassRef thisClass;
    private final HashMap<String, VarRef> variables;
    private  final HashMap<String, MethodScope> methods;

    public ClassContext(ClassRef ref, HashMap<String, VarRef> vars, HashMap<String, MethodScope> meths) {
        thisClass = ref;
        variables = vars;
        methods = meths;
    }

    public VarRef lookupVar(String s) {
        return variables.get(s);
    }

    public MethodScope lookupMethod(String s) {
        return methods.get(s);
    }

    public void putVar(String s, VarRef ref) {
        variables.put(s, ref);
    }

    public void putMethod(String s, MethodScope meth) {
        methods.put(s, meth);
    }

    public ClassRef getClassRef() {
        return thisClass;
    }

}
