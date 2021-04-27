package analysis.ClassRepresentations;

import notquitejava.ast.NQJClassDecl;
import notquitejava.ast.NQJExtendsClass;

/**
 * Represents a class, without its context.
 */
public class ClassRef {
    private String name;
    private String extendsSomething;
    private ClassType type;
    public NQJClassDecl decl;

    public ClassRef(String className, NQJClassDecl decl) {
        this.name = className;
        this.decl = decl;

        // decl has a super class
        if (decl.getExtended() instanceof NQJExtendsClass) {

            String extName = ((NQJExtendsClass) decl.getExtended()).getName();
            NQJClassDecl extDecl = decl.getDirectSuperClass();
            ClassRef extRef = new ClassRef(extName, extDecl);
            this.extendsSomething = extName;
            this.type = new ClassType(this, extRef);
        }

        // no super class
        else {
            extendsSomething = null;
            type = new ClassType(this, null);
        }
    }

    public String getName() {
        return name;
    }

    public String getSuperClass() {
        return extendsSomething;
    }

    public ClassType getType() {
        return type;
    }

}
