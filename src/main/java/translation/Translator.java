package translation;

import minillvm.ast.Prog;
import notquitejava.ast.NQJProgram;

/**
 * NQJ -> LLVM translation.
 */
public class Translator {
    private final FunTranslator funTr;
    private final ClassTranslator classTr;

    public Translator(NQJProgram program) {
        funTr = new FunTranslator(program);
        classTr = funTr.getClassTranslator();
    }

    /**
     * Translates given program to llvm.
     */
    public Prog translate() {

        // init all classes, fields and methods
        classTr.initializeClasses();

        // translate global functions except main
        funTr.translateFunctions();

        // translates all classes, fields and methods
        classTr.translateClasses();

        // translate main function
        funTr.translateMainFunction();

        funTr.finishNewArrayProcs();

        return funTr.getProg();
    }
}
