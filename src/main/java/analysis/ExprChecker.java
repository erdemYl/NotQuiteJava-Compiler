package analysis;

import analysis.ClassRepresentations.ClassContext;
import analysis.ClassRepresentations.ClassRef;
import analysis.ClassRepresentations.ClassTable;
import analysis.ClassRepresentations.ClassType;
import notquitejava.ast.*;

/**
 * Performs type analysis of expressions.
 */
public class ExprChecker implements NQJExpr.Matcher<Type>, NQJExprL.Matcher<Type> {

    private final Analysis analysis;
    private final ClassContext currClass;
    private final MethodScope currMethod;
    private final AnalyseProgram analyse;


    public ExprChecker(Analysis analysis, ClassContext ctxt, MethodScope method, AnalyseProgram analyse) {
        this.analysis = analysis;
        currClass = ctxt;
        currMethod = method;
        this.analyse = analyse;
    }

    Type check(NQJExpr e) {
        return e.match(this);
    }

    Type check(NQJExprL e) {
        return e.match(this);
    }

    void expect(NQJExpr e, Type expected) {
        Type actual = check(e);

        // classes can be null
        if (actual == Type.NULL
                && expected instanceof ClassType)
            return;

        if (!actual.isSubtypeOf(expected)) {
            analysis.addError(e, "Expected expression of type " + expected
                    + " but found " + actual + ".");
        }
    }

    Type expectArray(NQJExpr e) {
        Type actual = check(e);
        if (!(actual instanceof ArrayType)) {
            analysis.addError(e, "Expected expression of array type,  but found " + actual + ".");
            return Type.ANY;
        } else {
            return actual;
        }
    }

    /**
     * Lookup a method in a class and in its super classes.
     */
    NQJFunctionDecl examineClassesMethod(String methodName , ClassType classType) {
        ClassRef firstRef = classType.getClassRef();
        ClassRef extRef = classType.getExtClassRef();

        for (NQJFunctionDecl f : firstRef.decl.getMethods()) {
            if (methodName.equals(f.getName())) {
                return f;
            }
        }

        if (extRef == null)
            return null;

        return examineClassesMethod(methodName, extRef.getType());
    }

    /**
     * Lookup a field in a class and in its super classes.
     * Returns that fields type.
     */
    Type examineClassesVar(String varName, ClassType classType) {
        ClassRef firstRef = classType.getClassRef();
        ClassRef extRef = classType.getExtClassRef();

        for (NQJVarDecl v : firstRef.decl.getFields()) {
            if (varName.equals(v.getName()))
                return analyse.type(v.getType());
        }

        if (extRef == null)
            return Type.ANY;

        return examineClassesVar(varName, extRef.getType());
    }

    /**
     * Returns the founded variable declaration
     *
     * Examines a class and its super classes to
     * found the wanted declaration.
     */
    NQJVarDecl examineClassesVarDecl(String varName, ClassType classType) {
        ClassRef firstRef = classType.getClassRef();
        ClassRef extRef = classType.getExtClassRef();

        for (NQJVarDecl v : firstRef.decl.getFields()) {
            if (varName.equals(v.getName()))
                return v;
        }

        if (extRef == null)
            return null;

        return examineClassesVarDecl(varName, extRef.getType());
    }

    @Override
    public Type case_ExprUnary(NQJExprUnary exprUnary) {
        return exprUnary.getUnaryOperator().match(new NQJUnaryOperator.Matcher<Type>() {

            @Override
            public Type case_UnaryMinus(NQJUnaryMinus unaryMinus) {
                expect(exprUnary.getExpr(), Type.INT);
                return Type.INT;
            }

            @Override
            public Type case_Negate(NQJNegate negate) {
                expect(exprUnary.getExpr(), Type.BOOL);
                return Type.BOOL;
            }
        });
    }

    @Override
    public Type case_MethodCall(NQJMethodCall methodCall) {
        String methodName = methodCall.getMethodName();
        Type receiverType = check(methodCall.getReceiver());

        // receiver is a class type
        if (receiverType instanceof ClassType) {
            String className = ((ClassType) receiverType).getClassRef().getName();
            ClassRef ref = analyse.getClassTable().lookupClass(className);

            // class is not declared
            if (ref == null) {
                analysis.addError(methodCall, "Receiver is a not declared class name.");
                return Type.ANY;
            }

            // search the method in the class and in its super classes
            else {
                NQJFunctionDecl foundMeth = examineClassesMethod(methodName, ref.getType());

                if (foundMeth == null) {
                    analysis.addError(methodCall, "There is no method with name "
                            + methodName + " in class " + className + ".");
                    return Type.ANY;
                }

                NQJVarDeclList params = foundMeth.getFormalParameters();
                NQJExprList args = methodCall.getArguments();

                if (params.size() < args.size()) {
                    analysis.addError(methodCall, "Too many arguments.");
                }
                else {
                    if (params.size() > args.size())
                        analysis.addError(methodCall, "Not enough arguments.");
                    else {
                        for (int i = 0; i < params.size(); i++) {
                            expect(args.get(i), analyse.type(params.get(i).getType()));
                        }
                    }
                }
                methodCall.setFunctionDeclaration(foundMeth);
                return analyse.type(foundMeth.getReturnType());
            }
        }

        // receiver is not a class
        analysis.addError(methodCall, "Receiver is not a class.");
        return Type.ANY;
    }


    @Override
    public Type case_ArrayLength(NQJArrayLength arrayLength) {
        expectArray(arrayLength.getArrayExpr());
        return Type.INT;
    }

    @Override
    public Type case_ExprThis(NQJExprThis exprThis) {
        if (currClass == null) {
            analysis.addError(exprThis, "Cannot use the keyword 'this'.");
            return Type.ANY;
        }
        return currClass.getClassRef().getType();
    }

    @Override
    public Type case_ExprBinary(NQJExprBinary exprBinary) {
        return exprBinary.getOperator().match(new NQJOperator.Matcher<>() {
            @Override
            public Type case_And(NQJAnd and) {
                expect(exprBinary.getLeft(), Type.BOOL);
                expect(exprBinary.getRight(), Type.BOOL);
                return Type.BOOL;
            }

            @Override
            public Type case_Times(NQJTimes times) {
                return case_intOperation();
            }

            @Override
            public Type case_Div(NQJDiv div) {
                return case_intOperation();
            }

            @Override
            public Type case_Plus(NQJPlus plus) {
                return case_intOperation();
            }

            @Override
            public Type case_Minus(NQJMinus minus) {
                return case_intOperation();
            }

            private Type case_intOperation() {
                expect(exprBinary.getLeft(), Type.INT);
                expect(exprBinary.getRight(), Type.INT);
                return Type.INT;
            }

            @Override
            public Type case_Equals(NQJEquals equals) {
                Type l = check(exprBinary.getLeft());
                Type r = check(exprBinary.getRight());

                if (l instanceof ClassType && r == Type.NULL)
                    return Type.BOOL;

                if (!l.isSubtypeOf(r) && !r.isSubtypeOf(l)) {
                    analysis.addError(exprBinary, "Cannot compare types " + l + " and " + r + ".");
                }
                return Type.BOOL;
            }

            @Override
            public Type case_Less(NQJLess less) {
                expect(exprBinary.getLeft(), Type.INT);
                expect(exprBinary.getRight(), Type.INT);
                return Type.BOOL;
            }
        });
    }

    @Override
    public Type case_ExprNull(NQJExprNull exprNull) {
        return Type.NULL;
    }

    @Override
    public Type case_FunctionCall(NQJFunctionCall functionCall) {
        String funName = functionCall.getMethodName();
        // examine the call in currClass
        if (currClass != null) {
            MethodScope foundMeth = currClass.lookupMethod(funName);

            // functionCall is a method in the class
            if (foundMeth != null) {
                NQJExprList args = functionCall.getArguments();
                NQJVarDeclList params = foundMeth.decl.getFormalParameters();
                if (args.size() < params.size()) {
                    analysis.addError(functionCall, "Not enough arguments.");
                } else if (args.size() > params.size()) {
                    analysis.addError(functionCall, "Too many arguments.");
                } else {
                    for (int i = 0; i < params.size(); i++) {
                        expect(args.get(i), analyse.type(params.get(i).getType()));
                    }
                }
                functionCall.setFunctionDeclaration(foundMeth.decl);
                return foundMeth.getReturnType();

            } else {
                return globalOrInheritedFunCall(functionCall);
            }
        }

        else return globalOrInheritedFunCall(functionCall);
    }

    @Override
    public Type case_Number(NQJNumber number) {
        return Type.INT;
    }

    @Override
    public Type case_NewArray(NQJNewArray newArray) {
        expect(newArray.getArraySize(), Type.INT);
        ArrayType t = new ArrayType(analyse.type(newArray.getBaseType()));
        newArray.setArrayType(t);
        return t;
    }

    @Override
    public Type case_NewObject(NQJNewObject newObject) {
        String objName = newObject.getClassName();
        ClassRef objRef = analyse.getClassTable().lookupClass(objName);

        if (objRef == null) {
            analysis.addError(newObject, "There is no class with name " + objName + ".");
            return Type.ANY;
        }
        newObject.setClassDeclaration(objRef.decl);
        return objRef.getType();
    }

    @Override
    public Type case_BoolConst(NQJBoolConst boolConst) {
        return Type.BOOL;
    }

    @Override
    public Type case_Read(NQJRead read) {
        return read.getAddress().match(this);
    }

    @Override
    public Type case_FieldAccess(NQJFieldAccess fieldAccess) {
        String varName = fieldAccess.getFieldName();
        Type receiverType = fieldAccess.getReceiver().match(this);

        // receiver must be a class
        if (receiverType instanceof ClassType) {
            String className = ((ClassType) receiverType).getClassRef().getName();
            ClassTable table = analyse.getClassTable();
            // class must be a declared class
            if (table.lookupClass(className) == null) {
                analysis.addError(fieldAccess, "There is no declared class with name "
                        + className);
                return Type.ANY;
            }

            // examine field in declared class
            ClassRef ref = ((ClassType) receiverType).getClassRef();

            Type t = examineClassesVar(varName, ref.getType());
            if (t == Type.ANY)
                analysis.addError(fieldAccess, "There is no declared variable with name "
                        + varName + " in class " + ref.getName() + " and in its possible extensions.");
            return t;
        }

        else {
            analysis.addError(fieldAccess, "Receiver is not a class.");
            return Type.ANY;
        }
    }

    /**
     * Lookup variable first in current function,
     * then in current class.
     */
    @Override
    public Type case_VarUse(NQJVarUse varUse) {
        String varName = varUse.getVarName();
        VarRef refInClass =
                currClass == null
                ? null
                : currClass.lookupVar(varName);

        if (currMethod == null) {
            if (refInClass == null) {
                analysis.addError(varUse, "There is no variable with name "
                        + varName + " in current class " + currClass.getClassRef().getName());
                return Type.ANY;
            }
            varUse.setVariableDeclaration(refInClass.decl);
            return refInClass.type;
        }

        else {
            // look method ctx for variable
            VarRef refInMethod = currMethod.getVar(varName);
            if (refInMethod == null) {
                if (refInClass == null) {
                    if (currClass == null) {
                        analysis.addError(varUse, "There is no variable with name "
                                + varName + " in this function.");
                        return Type.ANY;
                    }
                    // search the class for variable
                    else {
                        ClassType classType = currClass.getClassRef().getType();
                        Type t = examineClassesVar(varName, classType);
                        NQJVarDecl varDecl = examineClassesVarDecl(varName, classType);
                        if (t == Type.ANY) {
                            analysis.addError(varUse, "There is no variable with name "
                                    + varName + " in current class " + currClass.getClassRef().getName()
                                    + " or in current method " + currMethod.getMethodName() + ".");
                            return t;
                        }
                        varUse.setVariableDeclaration(varDecl);
                        return t;
                    }
                }
                varUse.setVariableDeclaration(refInClass.decl);
                return refInClass.type;
            }

            else {
                varUse.setVariableDeclaration(refInMethod.decl);
                return refInMethod.type;
            }
        }
    }

    @Override
    public Type case_ArrayLookup(NQJArrayLookup arrayLookup) {
        Type type = analyse.checkExpr(arrayLookup.getArrayExpr());
        expect(arrayLookup.getArrayIndex(), Type.INT);
        if (type instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) type;
            arrayLookup.setArrayType(arrayType);
            return arrayType.getBaseType();
        }
        analysis.addError(arrayLookup, "Expected an array for array-lookup, but found " + type);
        return Type.ANY;
    }

    Type globalOrInheritedFunCall(NQJFunctionCall methodCall) {
        // global call
        if (currClass == null)
            return globalFunctionCall(methodCall);

        // first search inherited functions, then global functions
        else {
            String methodName = methodCall.getMethodName();
            ClassType classType = currClass.getClassRef().getType();
            NQJFunctionDecl methodDecl = examineClassesMethod(methodName, classType);

            // no such method exists in class, this means it is a global call
            if (methodDecl == null) {
                return globalFunctionCall(methodCall);
            }

            NQJExprList args = methodCall.getArguments();
            NQJVarDeclList params = methodDecl.getFormalParameters();
            if (args.size() < params.size()) {
                analysis.addError(methodCall, "Not enough arguments.");
            } else if (args.size() > params.size()) {
                analysis.addError(methodCall, "Too many arguments.");
            } else {
                for (int i = 0; i < params.size(); i++) {
                    expect(args.get(i), analyse.type(params.get(i).getType()));
                }
            }
            methodCall.setFunctionDeclaration(methodDecl);
            return analyse.type(methodDecl.getReturnType());
        }
    }

    Type globalFunctionCall(NQJFunctionCall functionCall) {
        String funName = functionCall.getMethodName();
        NQJFunctionDecl m = analysis.getNameTable().lookupFunction(funName);
        if (m == null) {
            analysis.addError(functionCall, "Function " + funName
                    + " does not exists.");
            return Type.ANY;
        }
        NQJExprList args = functionCall.getArguments();
        NQJVarDeclList params = m.getFormalParameters();
        if (args.size() < params.size()) {
            analysis.addError(functionCall, "Not enough arguments.");
        } else if (args.size() > params.size()) {
            analysis.addError(functionCall, "Too many arguments.");
        } else {
            for (int i = 0; i < params.size(); i++) {
                expect(args.get(i), analyse.type(params.get(i).getType()));
            }
        }
        functionCall.setFunctionDeclaration(m);
        return analyse.type(m.getReturnType());
    }
}