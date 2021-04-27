package translation;

import minillvm.ast.BasicBlock;
import minillvm.ast.Instruction;
import minillvm.ast.Proc;
import notquitejava.ast.NQJClassDecl;
import notquitejava.ast.NQJFunctionDecl;

import java.util.List;

/**
 * Represents the objects, which are at the moment
 * in translation.
 */
public class CurrentStates {
    private NQJClassDecl currentClass;
    private Proc currentProc;
    private BasicBlock currentBlock;

    void setClass(NQJClassDecl c) {
        currentClass = c;
    }

    void setProc(Proc p) {
        currentProc = p;
    }

    void setBlock(BasicBlock b) {
        currentBlock = b;
    }

    void addInstructionToBlock(Instruction i) {
        currentBlock.add(i);
    }

    void addBasicBlockToProc(BasicBlock b) {
        currentProc.getBasicBlocks().add(b);
    }

    NQJClassDecl getCurrClass() {
        return currentClass;
    }

    Proc getProc() {
        return currentProc;
    }

    BasicBlock getBlock() {
        return currentBlock;
    }
}
