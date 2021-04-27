package translation;

import minillvm.ast.*;
import notquitejava.ast.*;

/**
 * Statement Translator.
 */
public class StmtTranslator implements NQJStatement.MatcherVoid {

    private final FunTranslator funTr;
    private final ClassTranslator classTr;
    private final CurrentStates currStates;

    public StmtTranslator(FunTranslator funTr, ClassTranslator classTr) {
        this.funTr = funTr;
        this.classTr = classTr;
        this.currStates = classTr.getStates();
    }

    @Override
    public void case_VarDecl(NQJVarDecl s) {
        // no code, space is allocated at beginning of method
    }

    @Override
    public void case_StmtWhile(NQJStmtWhile s) {
        final BasicBlock whileStart = funTr.newBasicBlock("whileStart");
        final BasicBlock loopBodyStart = funTr.newBasicBlock("loopBodyStart");
        final BasicBlock endloop = funTr.newBasicBlock("endloop");

        // goto loop start
        addInstruction(Ast.Jump(whileStart));

        addBasicBlock(whileStart);
        setCurrentBlock(whileStart);
        // evaluate condition
        Operand condition = funTr.exprRvalue(s.getCondition());
        // branch based on condition
        addInstruction(Ast.Branch(condition, loopBodyStart, endloop));

        // translate loop body
        addBasicBlock(loopBodyStart);
        setCurrentBlock(loopBodyStart);
        funTr.translateStmt(s.getLoopBody());
        // at end of loop body go to loop start
        addInstruction(Ast.Jump(whileStart));

        // continue after loop:
        addBasicBlock(endloop);
        setCurrentBlock(endloop);

    }

    @Override
    public void case_StmtExpr(NQJStmtExpr s) {
        // just translate the expression
        funTr.exprRvalue(s.getExpr());
    }

    @Override
    public void case_StmtAssign(NQJStmtAssign s) {
        // first translate the left hand side
        final Operand lAddr = funTr.exprLvalue(s.getAddress());

        // then translate the right hand side
        final Operand rValue = funTr.exprRvalue(s.getValue());

        // case: rValue is a pointer to object and lAddr is variable usage
        if (lAddr.calculateType() instanceof TypePointer) {
            final Operand rValueCasted = funTr.addCastIfNecessary(
                    rValue,
                    ((TypePointer) lAddr.calculateType()).getTo());
            funTr.addInstruction(Ast.Store(lAddr, rValueCasted));
            return;
        }

        // finally store the result
        addInstruction(Ast.Store(lAddr, rValue));
    }

    @Override
    public void case_StmtIf(NQJStmtIf s) {
        final BasicBlock ifTrue = funTr.newBasicBlock("ifTrue");
        final BasicBlock ifFalse = funTr.newBasicBlock("ifFalse");
        final BasicBlock endif = funTr.newBasicBlock("endif");

        // translate the condition
        Operand condition = funTr.exprRvalue(s.getCondition());

        // jump based on condition
        addInstruction(Ast.Branch(condition, ifTrue, ifFalse));

        // translate ifTrue
        addBasicBlock(ifTrue);
        setCurrentBlock(ifTrue);
        funTr.translateStmt(s.getIfTrue());
        addInstruction(Ast.Jump(endif));

        // translate ifFalse
        addBasicBlock(ifFalse);
        setCurrentBlock(ifFalse);
        funTr.translateStmt(s.getIfFalse());
        addInstruction(Ast.Jump(endif));

        // continue at endif
        addBasicBlock(endif);
        setCurrentBlock(endif);
    }

    @Override
    public void case_Block(NQJBlock block) {
        for (NQJStatement s : block) {
            funTr.translateStmt(s);
        }
    }

    @Override
    public void case_StmtReturn(NQJStmtReturn s) {
        Operand result = funTr.exprRvalue(s.getResult());

        Operand castedResult = funTr.addCastIfNecessary(result, funTr.getCurrentReturnType());

        addInstruction(Ast.ReturnExpr(castedResult));

        // set to dummy block, so that nothing is overwritten
        setCurrentBlock(funTr.unreachableBlock());
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
