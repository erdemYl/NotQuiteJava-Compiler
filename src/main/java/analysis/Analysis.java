package analysis;

import java.util.*;
import notquitejava.ast.*;


/**
 * Main class for name and type analysis.
 */
public class Analysis {

    private final NQJProgram prog;
    private final List<TypeError> typeErrors = new ArrayList<>();
    private final AnalyseProgram programAnalyser;
    private NameTable nameTable;

    public void addError(NQJElement element, String message) {
        typeErrors.add(new TypeError(element, message));
    }

    public Analysis(NQJProgram prog) {
        this.prog = prog;
        this.programAnalyser = new AnalyseProgram(prog, this);
    }

    /**
     * Checks, if given NQJ program is semantically sound
     */
    public void check() {
        programAnalyser.createClassTable();
        createNameTable();
        verifyMainMethod();
        verifyReturns(); // CFS
        prog.accept(programAnalyser);
    }

    /**
     * Performs name analysis of global declared functions, and
     * creates name table for global functions and arrays
     */
    private void createNameTable() {
        nameTable = new NameTable(this, prog);
    }

    private void verifyMainMethod() {
        var main = nameTable.lookupFunction("main");
        if (main == null) {
            typeErrors.add(new TypeError(prog, "Method int main() must be present"));
            return;
        }
        if (!(main.getReturnType() instanceof NQJTypeInt)) {
            typeErrors.add(new TypeError(main.getReturnType(),
                    "Return type of the main method must be int"));
        }
        if (!(main.getFormalParameters().isEmpty())) {
            typeErrors.add(new TypeError(main.getFormalParameters(),
                    "Main method does not take parameters"));
        }
        // Check if return statement is there as the last statement
        NQJStatement last = null;
        for (NQJStatement nqjStatement : main.getMethodBody()) {
            last = nqjStatement;
        }
        if (!(last instanceof NQJStmtReturn)) {
            typeErrors.add(new TypeError(main.getFormalParameters(),
                    "Main method does not have a return statement as the last statement"));
        }
    }

    private void verifyReturns() {
        verifyReturnStmts(prog.getFunctionDecls());
        for (NQJClassDecl c : prog.getClassDecls())
            verifyReturnStmts(c.getMethods());
    }

    /**
     * CFS
     */
    private void verifyReturnStmts(NQJFunctionDeclList funList) {
        for (NQJFunctionDecl fun : funList) {
            if (fun.getName().equals("main")) {
                continue;
            }
            else {
                onlyOneReturn(fun.getMethodBody());
                NQJStatement last = null;
                for (NQJStatement stmt : fun.getMethodBody()) {
                    last = stmt;
                }
                if (!(last instanceof NQJStmtReturn))
                    addError(fun, "Method " + fun.getName()
                            + " does not have a return statement as the last statement.");
            }
        }
    }

    /**
     * CFS: Checks returns in block statements
     */
    private void onlyOneReturn(NQJBlock block) {
        List<NQJStatement> afterReturn = new LinkedList<>();
        NQJStatement returnStmt = null;

        for (NQJStatement stmt : block) {
            if (stmt instanceof NQJStmtIf) {
                returnsInIf((NQJStmtIf) stmt);
            }

            if (returnStmt instanceof NQJStmtReturn)
                afterReturn.add(stmt);
            else returnStmt = stmt;

            if (stmt instanceof NQJBlock)
                onlyOneReturn((NQJBlock) stmt);
        }
        afterReturn.forEach(s ->
                addError(s, "Unreachable statement."));
    }

    /**
     * CFS: Checks returns in if-statements
     */
    private void returnsInIf(NQJStmtIf s) {
        NQJStatement ifTrue = s.getIfTrue();
        NQJStatement ifFalse = s.getIfFalse();

        if (ifTrue instanceof NQJBlock) {
            onlyOneReturn((NQJBlock) ifTrue);
        }

        if (ifFalse instanceof NQJBlock) {
            onlyOneReturn((NQJBlock) ifFalse);
        }
    }

    public NameTable getNameTable() {
        return nameTable;
    }

    public NQJProgram getProg() {
        return prog;
    }

    public List<TypeError> getTypeErrors() {
        return new ArrayList<>(typeErrors);
    }
}
