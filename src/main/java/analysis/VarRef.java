package analysis;

import notquitejava.ast.NQJVarDecl;

public class VarRef {
    public Type type;
    public NQJVarDecl decl;

    public VarRef(NQJVarDecl decl, Type t) {
        this.decl = decl;
        type = t;
    }
}
