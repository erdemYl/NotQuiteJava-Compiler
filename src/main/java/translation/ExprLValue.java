package translation;

import minillvm.ast.*;
import notquitejava.ast.*;

import static minillvm.ast.Ast.*;

/**
 * Evaluate L values.
 */
public class ExprLValue implements NQJExprL.Matcher<Operand> {
    private final FunTranslator funTr;
    private final ClassTranslator classTr;
    private final CurrentStates currStates;

    public ExprLValue(FunTranslator funTr, ClassTranslator classTr) {
        this.funTr = funTr;
        this.classTr = classTr;
        this.currStates = classTr.getStates();
    }

    @Override
    public Operand case_ArrayLookup(NQJArrayLookup e) {
        Operand arrayAddr = funTr.exprRvalue(e.getArrayExpr());
        funTr.addNullcheck(arrayAddr, "Nullpointer exception in line " + funTr.sourceLine(e));

        Operand index = funTr.exprRvalue(e.getArrayIndex());

        Operand len = funTr.getArrayLen(arrayAddr);
        TemporaryVar smallerZero = Ast.TemporaryVar("smallerZero");
        TemporaryVar lenMinusOne = Ast.TemporaryVar("lenMinusOne");
        TemporaryVar greaterEqualLen = Ast.TemporaryVar("greaterEqualLen");
        TemporaryVar outOfBoundsV = Ast.TemporaryVar("outOfBounds");
        final BasicBlock outOfBounds = funTr.newBasicBlock("outOfBounds");
        final BasicBlock indexInRange = funTr.newBasicBlock("indexInRange");


        // smallerZero = index < 0
        addInstruction(BinaryOperation(smallerZero, index, Slt(), Ast.ConstInt(0)));
        // lenMinusOne = length - 1
        addInstruction(BinaryOperation(lenMinusOne, len, Sub(), Ast.ConstInt(1)));
        // greaterEqualLen = lenMinusOne < index
        addInstruction(BinaryOperation(greaterEqualLen,
                VarRef(lenMinusOne), Slt(), index.copy()));
        // outOfBoundsV = smallerZero || greaterEqualLen
        addInstruction(BinaryOperation(outOfBoundsV,
                VarRef(smallerZero), Or(), VarRef(greaterEqualLen)));

        addInstruction(Ast.Branch(VarRef(outOfBoundsV), outOfBounds, indexInRange));

        addBasicBlock(outOfBounds);
        outOfBounds.add(Ast.HaltWithError("Index out of bounds error in line " + funTr.sourceLine(e)));

        addBasicBlock(indexInRange);
        setCurrentBlock(indexInRange);
        TemporaryVar indexAddr = Ast.TemporaryVar("indexAddr");
        addInstruction(Ast.GetElementPtr(indexAddr, arrayAddr, Ast.OperandList(
                Ast.ConstInt(0),
                Ast.ConstInt(1),
                index.copy()
        )));
        return VarRef(indexAddr);
    }

    @Override
    public Operand case_FieldAccess(NQJFieldAccess e) {
        // find the class struct of receiver object
        var objRef = funTr.exprRvalue(e.getReceiver());
        TypePointer pointerToStruct = (TypePointer) objRef.calculateType();
        TypeStruct struct = (TypeStruct) pointerToStruct.getTo();

        // receiver cannot be null
        funTr.addNullcheck(objRef, "Nullpointer Exception in line "
                + e.getSourcePosition().getLine() + ":"
                + e.getSourcePosition().getColumn()  + ".");

        // find the index of field
        int index = 0;
        String fieldName = e.getFieldName();
        StructFieldList fields = struct.getFields();

        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).getName().equals(fieldName)) {
                index = i;
                break;
            }
        }

        // accessing field
        TemporaryVar access = TemporaryVar("fieldAccess");

        addInstruction(Ast.GetElementPtr(
                access, objRef, OperandList(ConstInt(0), ConstInt(index))
        ));
        return Ast.VarRef(access);
    }

    @Override
    public Operand case_VarUse(NQJVarUse e) {
        NQJClassDecl currentClass = currStates.getCurrClass();
        NQJVarDecl varDecl = e.getVariableDeclaration();

        if (currentClass == null) {
            // then, variable of a global function
            return VarRef(funTr.getLocalVarLocation(varDecl));
        }

        else {

            // Variable of current method, because of shadowing, consider this case
            // before considering class variable case
            for (NQJVarDecl v : classTr.getLocalMethodVars().keySet()) {
                if (v.getName().equals(varDecl.getName())) {
                    return VarRef(classTr.getLocalMethodVars().get(v));
                }
            }

            // Variable of current class.
            TypeStruct classStruct = classTr.getStructOf(currentClass);
            StructFieldList fields = classStruct.getFields();
            Proc currProc = currStates.getProc();
            Parameter classPointer = currProc.getParameters().get(0);

            Operand castToThisClass = funTr.addCastIfNecessary(
                    VarRef(classPointer),
                    TypePointer(classStruct)
            );

            // Find the variable in field list
            for (int i = 0; i < fields.size(); i++) {
                StructField field = fields.get(i);
                if (field.getName().equals(varDecl.getName())) {
                    TemporaryVar classVarP = TemporaryVar("classVar_" + field.getName());
                    addInstruction(GetElementPtr(
                            classVarP,
                            castToThisClass,
                            OperandList(ConstInt(0), ConstInt(i))
                    ));
                    return VarRef(classVarP);
                }
            }

            return null;
        }
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
