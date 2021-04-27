package translation;

import analysis.ArrayType;
import minillvm.ast.*;
import notquitejava.ast.*;

import java.util.*;
import java.util.stream.Collectors;

import static frontend.AstPrinter.print;
import static minillvm.ast.Ast.*;


/**
 * Translates global functions and arrays of an NQJ program to LLVM.
 */
public class FunTranslator {

    private final StmtTranslator stmtTranslator;
    private final ExprRValue exprRValue;
    private final ExprLValue exprLValue;
    final CurrentStates currStates;
    private final ClassTranslator classTr;

    // for functions and arrays
    private final Map<NQJFunctionDecl, Proc> functionImpl = new HashMap<>();
    private final Prog prog = Prog(TypeStructList(), GlobalList(), ProcList());
    private final NQJProgram javaProg;
    private final Map<NQJVarDecl, TemporaryVar> localVarLocation = new HashMap<>();
    private final Map<analysis.Type, Type> translatedType = new HashMap<>();
    private final Map<Type, TypeStruct> arrayStruct = new HashMap<>();
    private final Map<Type, Proc> newArrayFuncForType = new HashMap<>();

    public FunTranslator(NQJProgram javaProg) {
        this.javaProg = javaProg;
        currStates = new CurrentStates();
        classTr = new ClassTranslator(this);
        stmtTranslator = new StmtTranslator(this, classTr);
        exprRValue = new ExprRValue(this, classTr);
        exprLValue = new ExprLValue(this, classTr);
    }

    NQJProgram getJavaProg() {
        return javaProg;
    }

    Map<analysis.Type, Type> getTranslatedType() {
        return translatedType;
    }

    Prog getProg() {
        return prog;
    }

    TemporaryVar getLocalVarLocation(NQJVarDecl varDecl) {
        return localVarLocation.get(varDecl);
    }

    void addTranslatedType(analysis.Type type, Type llvmType) {
        translatedType.put(type, llvmType);
    }

    void finishNewArrayProcs() {
        for (Type type : newArrayFuncForType.keySet()) {
            finishNewArrayProc(type);
        }
    }

    private void finishNewArrayProc(Type componentType) {
        final Proc newArrayFunc = newArrayFuncForType.get(componentType);
        final Parameter size = newArrayFunc.getParameters().get(0);

        addProcedure(newArrayFunc);
        setCurrentProc(newArrayFunc);

        BasicBlock init = newBasicBlock("init");
        addBasicBlock(init);
        setCurrentBlock(init);
        TemporaryVar sizeLessThanZero = TemporaryVar("sizeLessThanZero");
        addInstruction(BinaryOperation(sizeLessThanZero,
                VarRef(size), Slt(), ConstInt(0)));
        BasicBlock negativeSize = newBasicBlock("negativeSize");
        BasicBlock goodSize = newBasicBlock("goodSize");
        addInstruction(Branch(VarRef(sizeLessThanZero), negativeSize, goodSize));

        addBasicBlock(negativeSize);
        negativeSize.add(HaltWithError("Array Size must be positive"));

        addBasicBlock(goodSize);
        setCurrentBlock(goodSize);

        // allocate space for the array

        TemporaryVar arraySizeInBytes = TemporaryVar("arraySizeInBytes");
        addInstruction(BinaryOperation(arraySizeInBytes,
                VarRef(size), Mul(), classTr.byteSize(componentType)));

        // 4 bytes for the length
        TemporaryVar arraySizeWithLen = TemporaryVar("arraySizeWitLen");
        addInstruction(BinaryOperation(arraySizeWithLen,
                VarRef(arraySizeInBytes), Add(), ConstInt(4)));

        TemporaryVar mallocResult = TemporaryVar("mallocRes");
        addInstruction(Alloc(mallocResult, VarRef(arraySizeWithLen)));
        TemporaryVar newArray = TemporaryVar("newArray");
        addInstruction(Bitcast(newArray,
                getArrayPointerType(componentType), VarRef(mallocResult)));

        // store the size
        TemporaryVar sizeAddr = TemporaryVar("sizeAddr");
        addInstruction(GetElementPtr(sizeAddr,
                VarRef(newArray), OperandList(ConstInt(0), ConstInt(0))));
        addInstruction(Store(VarRef(sizeAddr), VarRef(size)));

        // initialize Array with zeros:
        final BasicBlock loopStart = newBasicBlock("loopStart");
        final BasicBlock loopBody = newBasicBlock("loopBody");
        final BasicBlock loopEnd = newBasicBlock("loopEnd");
        final TemporaryVar iVar = TemporaryVar("iVar");
        addInstruction(Alloca(iVar, TypeInt()));
        addInstruction(Store(VarRef(iVar), ConstInt(0)));
        addInstruction(Jump(loopStart));

        // loop condition: while i < size
        addBasicBlock(loopStart);
        setCurrentBlock(loopStart);
        final TemporaryVar i = TemporaryVar("i");
        final TemporaryVar nextI = TemporaryVar("nextI");
        loopStart.add(Load(i, VarRef(iVar)));
        TemporaryVar smallerSize = TemporaryVar("smallerSize");
        addInstruction(BinaryOperation(smallerSize,
                VarRef(i), Slt(), VarRef(size)));
        addInstruction(Branch(VarRef(smallerSize), loopBody, loopEnd));

        // loop body
        addBasicBlock(loopBody);
        setCurrentBlock(loopBody);
        // ar[i] = 0;
        final TemporaryVar iAddr = TemporaryVar("iAddr");
        addInstruction(GetElementPtr(iAddr,
                VarRef(newArray), OperandList(ConstInt(0), ConstInt(1), VarRef(i))));
        addInstruction(Store(VarRef(iAddr), classTr.defaultValue(componentType)));

        // nextI = i + 1;
        addInstruction(BinaryOperation(nextI, VarRef(i), Add(), ConstInt(1)));
        // store new value in i
        addInstruction(Store(VarRef(iVar), VarRef(nextI)));

        loopBody.add(Jump(loopStart));

        addBasicBlock(loopEnd);
        loopEnd.add(ReturnExpr(VarRef(newArray)));
    }

    void translateFunctions() {
        for (NQJFunctionDecl functionDecl : javaProg.getFunctionDecls()) {
            if (functionDecl.getName().equals("main")) {
                continue;
            }
            initFunction(functionDecl);
        }
        for (NQJFunctionDecl functionDecl : javaProg.getFunctionDecls()) {
            if (functionDecl.getName().equals("main")) {
                continue;
            }
            translateFunction(functionDecl);
        }
    }

    void translateMainFunction() {
        NQJFunctionDecl f = null;
        for (NQJFunctionDecl functionDecl : javaProg.getFunctionDecls()) {
            if (functionDecl.getName().equals("main")) {
                f = functionDecl;
                break;
            }
        }

        if (f == null) {
            throw new IllegalStateException("Main function expected");
        }

        Proc proc = Proc("main", TypeInt(), ParameterList(), BasicBlockList());
        addProcedure(proc);
        functionImpl.put(f, proc);

        setCurrentProc(proc);
        BasicBlock initBlock = newBasicBlock("init");
        addBasicBlock(initBlock);
        setCurrentBlock(initBlock);

        // allocate space for the local variables
        allocaLocalVars(f.getMethodBody());

        // translate
        translateStmt(f.getMethodBody());
    }

    private void initFunction(NQJFunctionDecl f) {
        Type returnType = classTr.translateType(f.getReturnType());
        ParameterList params = f.getFormalParameters()
                .stream()
                .map(p -> Parameter(classTr.translateType(p.getType()), p.getName()))
                .collect(Collectors.toCollection(Ast::ParameterList));
        Proc proc = Proc(f.getName(), returnType, params, BasicBlockList());
        addProcedure(proc);
        functionImpl.put(f, proc);
    }

    private void translateFunction(NQJFunctionDecl m) {
        Proc proc = functionImpl.get(m);
        setCurrentProc(proc);
        BasicBlock initBlock = newBasicBlock("init");
        addBasicBlock(initBlock);
        setCurrentBlock(initBlock);

        localVarLocation.clear();

        // store copies of the parameters in Allocas, to make uniform read/write access possible
        int i = 0;
        for (NQJVarDecl param : m.getFormalParameters()) {
            TemporaryVar v = TemporaryVar(param.getName());
            addInstruction(Alloca(v, classTr.translateType(param.getType())));
            addInstruction(Store(VarRef(v), VarRef(proc.getParameters().get(i))));
            localVarLocation.put(param, v);
            i++;
        }

        // allocate space for the local variables
        allocaLocalVars(m.getMethodBody());

        translateStmt(m.getMethodBody());
    }

    void translateStmt(NQJStatement s) {
        addInstruction(CommentInstr(sourceLine(s) + " start statement : " + printFirstline(s)));
        s.match(stmtTranslator);
        addInstruction(CommentInstr(sourceLine(s) + " end statement: " + printFirstline(s)));
    }

    int sourceLine(NQJElement e) {
        while (e != null) {
            if (e.getSourcePosition() != null) {
                return e.getSourcePosition().getLine();
            }
            e = e.getParent();
        }
        return 0;
    }

    private String printFirstline(NQJStatement s) {
        String str = print(s);
        str = str.replaceAll("\n.*", "");
        return str;
    }

    BasicBlock newBasicBlock(String name) {
        BasicBlock block = BasicBlock();
        block.setName(name);
        return block;
    }

    void addBasicBlock(BasicBlock block) {
        currStates.addBasicBlockToProc(block);
    }

    void setCurrentBlock(BasicBlock currentBlock) {
        currStates.setBlock(currentBlock);
    }

    void addProcedure(Proc proc) {
        prog.getProcedures().add(proc);
    }

    void setCurrentProc(Proc currentProc) {
        if (currentProc == null) {
            throw new RuntimeException("Cannot set proc to null");
        }
        currStates.setProc(currentProc);
    }

    private void allocaLocalVars(NQJBlock methodBody) {
        methodBody.accept(new NQJElement.DefaultVisitor() {
            @Override
            public void visit(NQJVarDecl localVar) {
                super.visit(localVar);
                TemporaryVar v = TemporaryVar(localVar.getName());
                addInstruction(Alloca(v, translateType(localVar.getType())));
                localVarLocation.put(localVar, v);
            }
        });
    }

    void addInstruction(Instruction instruction) {
        currStates.addInstructionToBlock(instruction);
    }

    Type translateType(NQJType type) {
        return translateType(type.getType());
    }

    Type translateType(analysis.Type t) {
        return classTr.translateType(t);
    }

    Operand exprLvalue(NQJExprL e) {
        return e.match(exprLValue);
    }

    Operand exprRvalue(NQJExpr e) {
        return e.match(exprRValue);
    }

    void addNullcheck(Operand arrayAddr, String errorMessage) {
        TemporaryVar isNull = TemporaryVar("isNull");
        addInstruction(BinaryOperation(isNull, arrayAddr.copy(), Eq(), Nullpointer()));

        BasicBlock whenIsNull = newBasicBlock("whenIsNull");
        BasicBlock notNull = newBasicBlock("notNull");
        addInstruction(Branch(VarRef(isNull), whenIsNull, notNull));

        addBasicBlock(whenIsNull);
        whenIsNull.add(HaltWithError(errorMessage));

        addBasicBlock(notNull);
        setCurrentBlock(notNull);
    }

    Operand getArrayLen(Operand arrayAddr) {
        TemporaryVar addr = TemporaryVar("length_addr");
        addInstruction(GetElementPtr(addr,
                arrayAddr.copy(), OperandList(ConstInt(0), ConstInt(0))));
        TemporaryVar len = TemporaryVar("len");
        addInstruction(Load(len, VarRef(addr)));
        return VarRef(len);
    }

    public Operand getNewArrayFunc(Type componentType) {
        Proc proc = newArrayFuncForType.computeIfAbsent(componentType, this::createNewArrayProc);
        return ProcedureRef(proc);
    }

    private Proc createNewArrayProc(Type componentType) {
        Parameter size = Parameter(TypeInt(), "size");
        return Proc("newArray",
                getArrayPointerType(componentType), ParameterList(size), BasicBlockList());
    }

    private Type getArrayPointerType(Type componentType) {
        return TypePointer(getArrayStruct(componentType));
    }

    TypeStruct getArrayStruct(Type type) {
        return arrayStruct.computeIfAbsent(type, t -> {
            TypeStruct struct = TypeStruct("array_" + type, StructFieldList(
                    StructField(TypeInt(), "length"),
                    StructField(TypeArray(type, 0), "data")
            ));
            prog.getStructTypes().add(struct);
            return struct;
        });
    }

    Operand addCastIfNecessary(Operand value, Type expectedType) {
        if (expectedType.equalsType(value.calculateType())) {
            return value;
        }
        TemporaryVar castValue = TemporaryVar("castValue");
        addInstruction(Bitcast(castValue, expectedType, value));
        return VarRef(castValue);
    }

    BasicBlock unreachableBlock() {
        return BasicBlock();
    }

    Type getCurrentReturnType() {
        return currStates.getProc().getReturnType();
    }

    ClassTranslator getClassTranslator() {
        return classTr;
    }

    public Proc loadFunctionProc(NQJFunctionDecl funDecl) {
        return functionImpl.get(funDecl);
    }
}