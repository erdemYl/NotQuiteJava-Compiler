package analysis;

import analysis.ClassRepresentations.ClassContext;
import analysis.ClassRepresentations.ClassRef;
import analysis.ClassRepresentations.ClassTable;
import analysis.ClassRepresentations.ClassType;
import notquitejava.ast.*;

import java.util.*;
import java.util.function.Predicate;

/**
 * Performs name and type analysis of classes, and type analysis
 * for global declared functions of given NQJ program.
 *
 * This class is designed for analysing Object Oriented constructs
 * of an NQJ program.
 */
public class AnalyseProgram extends NQJElement.DefaultVisitor {
    private final Analysis analysis;
    private ClassTable classTable;
    private NQJProgram prog;

    private ClassContext currentClass;
    private final LinkedList<MethodScope> ctxtMethod = new LinkedList<>();

    AnalyseProgram(NQJProgram prog, Analysis analysis) {
        this.analysis = analysis;
        this.prog = prog;
    }

    /**
     * Performs the name analysis of declared NQJ classes
     */
    void createClassTable() {
        classTable = new ClassTable(prog.getClassDecls(), analysis);
    }

    Type checkExpr(NQJExpr e) {
        return e.match(new ExprChecker(analysis, currentClass, ctxtMethod.peek(), this));
    }

    Type checkExpr(NQJExprL e) {
        return e.match(new ExprChecker(analysis, currentClass, ctxtMethod.peek(), this));
    }

    @Override
    public void visit(NQJClassDecl classDecl) {
        String name = classDecl.getName();

        // find the class ref
        ClassRef ref = classTable.lookupClass(name);

        // creating the class context
        ClassContext newClass = new ClassContext(ref, new HashMap<>(), new HashMap<>());

        // variables and methods are unique.
        Set<String> vars = new HashSet<>();
        Set<String> meths = new HashSet<>();

        // name analysis variables
        for (NQJVarDecl v : classDecl.getFields()) {
            if (!vars.add(v.getName()))
                analysis.addError(v, "Variable with name "
                        + v.getName() + " is already defined in class " + ref.getName() + ".");
            else {
                // putting variable to context
                NQJType varType = v.getType();
                Type t = type(varType);
                newClass.putVar(v.getName(), new VarRef(v, t));
            }
        }

        // name analysis methods
        for (NQJFunctionDecl f : classDecl.getMethods()) {
            if (!meths.add(f.getName()))
                analysis.addError(f, "Method with name "
                        + f.getName() + " is already defined in class " + ref.getName() + ".");
            else {
                searchOverride(ref, f);
            }
        }
        // enter class context
        currentClass = newClass;

        // examine methods for type analysis
        for (NQJFunctionDecl f  : classDecl.getMethods()) {
            f.accept(this);
        }

        // context ends
        currentClass = null;
    }

    @Override
    public void visit(NQJFunctionDecl f) {
        MethodScope newMethod = ctxtMethod.isEmpty()
                ? new MethodScope(new HashMap<>(), type(f.getReturnType()), f)
                : ctxtMethod.peek().copy();

        Set<String> params = new HashSet<>();
        for (NQJVarDecl v : f.getFormalParameters()) {

            if (!params.add(v.getName()))
                analysis.addError(v, "Parameter with name "
                        + v.getName() + " is already exists.");

                NQJType varType = v.getType();
                Type t = type(varType);
                newMethod.putVar(v.getName(), t, v);
        }
        // method ctx begins
        ctxtMethod.push(newMethod);
        // visit body
        f.getMethodBody().accept(this);

        if (currentClass != null)
            currentClass.putMethod(f.getName(), ctxtMethod.peek().copy());

        // method ctx ends
        ctxtMethod.pop();
    }

    @Override
    public void visit(NQJBlock block) {
        MethodScope bctxt = ctxtMethod.peek().copy();

        for (NQJStatement s : block) {
            if (s instanceof NQJVarDecl) {

                String varName = ((NQJVarDecl) s).getName();

                if (bctxt.getVar(varName) != null) {
                    analysis.addError(s, "Variable with name "
                            + varName + " already exists in function " + bctxt.getMethodName() + ".");
                }

                // type of the variable
                NQJType varType = ((NQJVarDecl) s).getType();
                Type t = type(varType);
                bctxt.putVar(varName, t, (NQJVarDecl) s);
            }
            else {
                ctxtMethod.push(bctxt);
                s.accept(this);
                ctxtMethod.pop();
            }
        }
    }

    @Override
    public void visit(NQJStmtExpr stmtExpr) {
        checkExpr(stmtExpr.getExpr());
    }

    @Override
    public void visit(NQJStmtAssign stmtAssign) {
        Type lt = checkExpr(stmtAssign.getAddress());
        Type rt = checkExpr(stmtAssign.getValue());

        // assigning null
        if (rt == Type.NULL && lt instanceof ClassType)
            return;

        if (!rt.isSubtypeOf(lt)) {
            analysis.addError(stmtAssign.getValue(), "Cannot assign value of type " + rt.toString()
                    + " to " + lt.toString() + ".");
        }
    }

    @Override
    public void visit(NQJStmtReturn stmtReturn) {
        Type actualReturn = checkExpr(stmtReturn.getResult());
        Type expectedReturn = ctxtMethod.peek().getReturnType();
        if (!actualReturn.isSubtypeOf(expectedReturn)) {
            analysis.addError(stmtReturn, "Should return value of type " + expectedReturn
                    + ", but found " + actualReturn + ".");
        }
    }

    @Override
    public void visit(NQJStmtWhile s) {
        Type type = checkExpr(s.getCondition());

        if (!type.isSubtypeOf(Type.BOOL)) {
            analysis.addError(s, "while-condition must be of type boolean. This is of type "
                    + type);
        }
        super.visit(s);
    }

    @Override
    public void visit(NQJStmtIf s) {
        Type type = checkExpr(s.getCondition());

        if (!type.isSubtypeOf(Type.BOOL)) {
            analysis.addError(s, "if-condition must be of type boolean. This is of type "
                    + type);
        }
        super.visit(s);
    }

    @Override
    public void visit(NQJVarDecl varDecl) {
        // Declarations are handled in visit funDecl and classDecl
        throw new RuntimeException();
    }

    // NQJ Type converter
    public Type type(NQJType type) {
        Type result = type.match(new NQJType.Matcher<Type>() {

            @Override
            public Type case_TypeArray(NQJTypeArray typeArray) {
                return analysis.getNameTable().getArrayType(type(typeArray.getComponentType()));
            }

            @Override
            public Type case_TypeClass(NQJTypeClass typeClass) {
                String className = typeClass.getName();
                ClassRef ref = classTable.lookupClass(className);

                if (ref == null) {
                    analysis.addError(type, "The type "
                            + typeClass.getName() + " is an undeclared class.");
                    return Type.ANY;
                }

                return ref.getType();
            }

            @Override
            public Type case_TypeInt(NQJTypeInt typeInt) {
                return Type.INT;
            }

            @Override
            public Type case_TypeBool(NQJTypeBool typeBool) {
                return Type.BOOL;
            }
        });
        type.setType(result);
        return result;
    }

    /**
     * Search, if the method in a class is an overwritten method.
     * If overwritten, checks if correctly overwritten.
     */
    private void searchOverride(ClassRef ref, NQJFunctionDecl f) {
        NQJClassDecl decl = ref.decl;
        NQJClassDecl extended = decl.getDirectSuperClass();

        if (extended != null) {
            for (NQJFunctionDecl fun : extended.getMethods()) {
                if (f.getName().equals(fun.getName())) {
                    checkOverride(f, fun);
                    return;
                }
            }
            ClassRef higher = classTable.lookupClass(extended.getName());
            // search override in higher extensions
            searchOverride(higher, f);
        }
    }

    private void checkOverride(NQJFunctionDecl m, NQJFunctionDecl sm) {
        if (m.getFormalParameters().size() != sm.getFormalParameters().size()) {
            analysis.addError(m, "Method " + m.getName()
                    + " must have same number of parameters as method in super class.");
            return;
        }
        for (int i = 0; i < m.getFormalParameters().size(); i++) {
            Type t1 = type(m.getFormalParameters().get(i).getType());
            Type t2 = type(sm.getFormalParameters().get(i).getType());
            if (!t1.isEqualToType(t2)) {
                analysis.addError(m.getFormalParameters().get(i),
                        "Parameter types must be equal for overridden methods.");
            }
        }
        if (!type(m.getReturnType()).isSubtypeOf(type(sm.getReturnType()))) {
            analysis.addError(m.getReturnType(), "Return type must be a subtype of overridden method.");
        }
    }

    public ClassTable getClassTable() {
        return classTable;
    }
}