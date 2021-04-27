package analysis;

import notquitejava.ast.NQJFunctionDecl;
import notquitejava.ast.NQJVarDecl;

import java.util.HashMap;

/**
 * This class represents a global function's or
 * a method's scope.
 */
public class MethodScope {
    public NQJFunctionDecl decl;
    private Type returnType;
    private final HashMap<String, VarRef> env;

    public MethodScope(HashMap<String, VarRef> env, Type type, NQJFunctionDecl decl) {
        this.env = env;
        returnType = type;
        this.decl = decl;
    }

    public Type getReturnType() {
        return returnType;
    }

    public void putVar(String varName, Type type, NQJVarDecl decl) {
        env.put(varName, new VarRef(decl, type));
    }

    public VarRef getVar(String varName) {
        return env.get(varName);
    }

    public void setReturnType(Type returnType) {
        this.returnType = returnType;
    }

    public String getMethodName() {
        return decl.getName();
    }

    public MethodScope copy() {
        return new MethodScope(new HashMap<>(this.env), this.returnType, this.decl);
    }

}
