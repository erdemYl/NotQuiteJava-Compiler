package translation;

import minillvm.ast.*;
import notquitejava.ast.*;

import java.util.List;

import static minillvm.ast.Ast.*;


/**
 * Evaluate r values.
 */
public class ExprRValue implements NQJExpr.Matcher<Operand> {
    private final FunTranslator funTr;
    private final ClassTranslator classTr;
    private final CurrentStates currStates;

    public ExprRValue(FunTranslator funTr, ClassTranslator classTr) {
        this.funTr = funTr;
        this.classTr = classTr;
        this.currStates = classTr.getStates();
    }

    @Override
    public Operand case_ExprUnary(NQJExprUnary e) {
        Operand expr = funTr.exprRvalue(e.getExpr());

        return e.getUnaryOperator().match(new NQJUnaryOperator.Matcher<>() {

            @Override
            public Operand case_UnaryMinus(NQJUnaryMinus unaryMinus) {
                TemporaryVar v = TemporaryVar("minus_res");
                funTr.addInstruction(BinaryOperation(v, ConstInt(0), Ast.Sub(), expr));
                return VarRef(v);
            }

            @Override
            public Operand case_Negate(NQJNegate negate) {
                TemporaryVar v = TemporaryVar("neg_res");
                funTr.addInstruction(BinaryOperation(v, Ast.ConstBool(false), Eq(), expr));
                return VarRef(v);
            }
        });
    }

    @Override
    public Operand case_ArrayLength(NQJArrayLength e) {
        Operand a = funTr.exprRvalue(e.getArrayExpr());
        funTr.addNullcheck(a,
                "Nullpointer exception when reading array length in line " + funTr.sourceLine(e));
        return funTr.getArrayLen(a);
    }

    @Override
    public Operand case_NewArray(NQJNewArray newArray) {
        Type componentType = classTr.translateType(newArray.getArrayType().getBaseType());
        Operand arraySize = funTr.exprRvalue(newArray.getArraySize());
        Operand proc = funTr.getNewArrayFunc(componentType);
        TemporaryVar res = TemporaryVar("newArray");
        addInstruction(Ast.Call(res, proc, OperandList(arraySize)));
        return VarRef(res);
    }

    @Override
    public Operand case_ExprBinary(NQJExprBinary e) {
        Operand left = funTr.exprRvalue(e.getLeft());
        return e.getOperator().match(new NQJOperator.Matcher<>() {
            @Override
            public Operand case_And(NQJAnd and) {
                BasicBlock andRight = funTr.newBasicBlock("and_first_true");
                BasicBlock andEnd = funTr.newBasicBlock("and_end");
                TemporaryVar andResVar = TemporaryVar("andResVar");
                addInstruction(Ast.Alloca(andResVar, Ast.TypeBool()));
                addInstruction(Ast.Store(VarRef(andResVar), left));
                addInstruction(Ast.Branch(left.copy(), andRight, andEnd));

                addBasicBlock(andRight);
                setCurrentBlock(andRight);

                Operand right = funTr.exprRvalue(e.getRight());

                addInstruction(Ast.Store(VarRef(andResVar), right));
                addInstruction(Ast.Jump(andEnd));

                addBasicBlock(andEnd);
                setCurrentBlock(andEnd);
                TemporaryVar andRes = TemporaryVar("andRes");
                andEnd.add(Ast.Load(andRes, VarRef(andResVar)));
                return VarRef(andRes);
            }


            private Operand normalCase(Operator op) {
                Operand right = funTr.exprRvalue(e.getRight());
                TemporaryVar result = TemporaryVar("res" + op.getClass().getSimpleName());
                funTr.addInstruction(BinaryOperation(result, left, op, right));
                return VarRef(result);
            }

            @Override
            public Operand case_Times(NQJTimes times) {
                return normalCase(Ast.Mul());
            }


            @Override
            public Operand case_Div(NQJDiv div) {
                Operand right = funTr.exprRvalue(e.getRight());
                TemporaryVar divResVar = TemporaryVar("divResVar");
                addInstruction(Ast.Alloca(divResVar, Ast.TypeInt()));
                TemporaryVar isZero = TemporaryVar("isZero");
                addInstruction(BinaryOperation(isZero, right, Eq(), ConstInt(0)));
                BasicBlock ifZero = funTr.newBasicBlock("ifZero");
                BasicBlock notZero = funTr.newBasicBlock("notZero");

                addInstruction(Ast.Branch(VarRef(isZero), ifZero, notZero));

                addBasicBlock(ifZero);
                ifZero.add(Ast.HaltWithError("Division by zero in line " + funTr.sourceLine(e)));


                addBasicBlock(notZero);
                setCurrentBlock(notZero);

                BasicBlock divEnd = funTr.newBasicBlock("div_end");
                BasicBlock divNoOverflow = funTr.newBasicBlock("div_noOverflow");

                TemporaryVar isMinusOne = TemporaryVar("isMinusOne");
                addInstruction(BinaryOperation(isMinusOne,
                        right.copy(), Eq(), ConstInt(-1)));
                TemporaryVar isMinInt = TemporaryVar("isMinInt");
                addInstruction(BinaryOperation(isMinInt,
                        left.copy(), Eq(), ConstInt(Integer.MIN_VALUE)));
                TemporaryVar isOverflow = TemporaryVar("isOverflow");
                addInstruction(BinaryOperation(isOverflow,
                        VarRef(isMinInt), And(), VarRef(isMinusOne)));
                addInstruction(Ast.Store(VarRef(divResVar), ConstInt(Integer.MIN_VALUE)));
                addInstruction(Ast.Branch(VarRef(isOverflow), divEnd, divNoOverflow));


                addBasicBlock(divNoOverflow);
                setCurrentBlock(divNoOverflow);
                TemporaryVar divResultA = TemporaryVar("divResultA");
                addInstruction(BinaryOperation(divResultA, left, Ast.Sdiv(), right.copy()));
                addInstruction(Ast.Store(VarRef(divResVar), VarRef(divResultA)));
                addInstruction(Ast.Jump(divEnd));


                addBasicBlock(divEnd);
                setCurrentBlock(divEnd);
                TemporaryVar divResultB = TemporaryVar("divResultB");
                addInstruction(Ast.Load(divResultB, VarRef(divResVar)));
                return VarRef(divResultB);
            }

            @Override
            public Operand case_Plus(NQJPlus plus) {
                return normalCase(Ast.Add());
            }

            @Override
            public Operand case_Minus(NQJMinus minus) {
                return normalCase(Ast.Sub());
            }

            @Override
            public Operand case_Equals(NQJEquals equals) {
                Operator op = Eq();
                Operand right = funTr.exprRvalue(e.getRight());
                TemporaryVar result = TemporaryVar("res" + op.getClass().getSimpleName());
                right = funTr.addCastIfNecessary(right, left.calculateType());
                addInstruction(BinaryOperation(result, left, op, right));
                return VarRef(result);
            }

            @Override
            public Operand case_Less(NQJLess less) {
                return normalCase(Ast.Slt());
            }
        });
    }

    @Override
    public Operand case_ExprNull(NQJExprNull e) {
        return Ast.Nullpointer();
    }

    @Override
    public Operand case_Number(NQJNumber e) {
        return ConstInt(e.getIntValue());
    }

    @Override
    public Operand case_FunctionCall(NQJFunctionCall e) {
        // special case: printInt
        if (e.getMethodName().equals("printInt")) {
            NQJExpr arg1 = e.getArguments().get(0);
            Operand op = funTr.exprRvalue(arg1);
            addInstruction(Ast.Print(op));
            return ConstInt(0);
        } else {
            NQJFunctionDecl funDecl = e.getFunctionDeclaration();

            OperandList args = OperandList();
            for (int i = 0; i < e.getArguments().size(); i++) {
                Operand arg = funTr.exprRvalue(e.getArguments().get(i));
                NQJVarDeclList formalParameters = funDecl.getFormalParameters();
                arg = funTr.addCastIfNecessary(arg, classTr.translateType(
                        formalParameters.get(i).getType())
                );
                args.add(arg);
            }

            // first, lookup in global functions
            Proc proc = funTr.loadFunctionProc(funDecl);

            if (proc == null) {
                // then, lookup in current class methods, including inherited methods
                NQJClassDecl currClass = currStates.getCurrClass();
                proc = classTr.getMethodProcedure(currClass, funDecl);

                // Method may be inherited, so its class can be different
                // from current class
                TypeStruct structOfMethod =
                        classTr.getStructOfMethod(currClass, funDecl);

                // first parameter of current method is the class pointer
                Operand casted = funTr.addCastIfNecessary(
                        VarRef(currStates.getProc().getParameters().get(0)),
                        TypePointer(structOfMethod)
                );
                args.addFront(casted);
            }

            // do the call
            TemporaryVar result = TemporaryVar(e.getMethodName() + "_result");
            addInstruction(Ast.Call(result, ProcedureRef(proc), args));
            return VarRef(result);
        }
    }

    @Override
    public Operand case_BoolConst(NQJBoolConst e) {
        return Ast.ConstBool(e.getBoolValue());
    }

    @Override
    public Operand case_Read(NQJRead read) {
        TemporaryVar res = TemporaryVar("read");
        Operand op = funTr.exprLvalue(read.getAddress());
        addInstruction(Ast.Load(res, op));
        return VarRef(res);
    }

    @Override
    public Operand case_MethodCall(NQJMethodCall e) {
        // receiver is a class type
        var receiver = funTr.exprRvalue(e.getReceiver());
        Type type = receiver.calculateType();

        // receiver must not be null
        funTr.addNullcheck(
                receiver, "Nullpointer Exception in line "
                + e.getSourcePosition().getLine() + ":"
                        + e.getSourcePosition().getColumn() + ".");

        // Get rid of pointers and get the class struct
        while (!(type instanceof TypeStruct)) {
            type = ((TypePointer) type).getTo();
        }

        TypeStruct classStruct = (TypeStruct) type;
        String className = classStruct.getName();

        // find the proc of method
        NQJFunctionDecl funDecl =
                e.getFunctionDeclaration();
        NQJClassDecl receiverClass =
                classTr.getClassDeclOf(classStruct);
        Proc method =
                classTr.getMethodProcedure(receiverClass, funDecl);

        OperandList args = OperandList();

        // method may be inherited, so its class may be different from current class
        TypeStruct methodsClass = classTr.getStructOfMethod(receiverClass, funDecl);

        // casting the receiver's class to method's class structure
        Operand casted = funTr.addCastIfNecessary(
                receiver, TypePointer(methodsClass)
        );

        // first arg is pointer to current class
        args.addFront(casted);

        for (int i = 0; i < e.getArguments().size(); i++) {
            Operand arg = funTr.exprRvalue(e.getArguments().get(i));
            NQJVarDeclList formalParameters = funDecl.getFormalParameters();
            arg = funTr.addCastIfNecessary(
                    arg, classTr.translateType(formalParameters.get(i).getType())
            );
            args.add(arg);
        }

        // do the call
        TemporaryVar result = TemporaryVar(className + "_" + e.getMethodName() + "_result");
        addInstruction(Ast.Call(
                result, ProcedureRef(method), args
        ));
        return VarRef(result);
    }

    @Override
    public Operand case_NewObject(NQJNewObject e) {
        NQJClassDecl objClass = e.getClassDeclaration();
        Proc create = classTr.constructors.get(objClass);

        TemporaryVar object = TemporaryVar("new_object");
        addInstruction(Call(
                object, ProcedureRef(create), OperandList()
        ));

        return VarRef(object);
    }

    @Override
    public Operand case_ExprThis(NQJExprThis e) {
        Proc currMethod = currStates.getProc();
        TypeStruct currClassStruct = classTr.getStructOf(currStates.getCurrClass());
        Operand casted = funTr.addCastIfNecessary(
                VarRef(currMethod.getParameters().get(0)),
                TypePointer(currClassStruct)
        );
        return casted;
    }

    private void addInstruction(Instruction i) {
        currStates.addInstructionToBlock(i);
    }

    private void setCurrentBlock(BasicBlock b) {
        currStates.setBlock(b);
    }

    private void addBasicBlock(BasicBlock b) {
        currStates.addBasicBlockToProc(b);
    }
}
