package translation;

import analysis.ArrayType;
import analysis.ClassRepresentations.ClassType;
import minillvm.ast.*;
import notquitejava.ast.*;

import java.util.*;
import java.util.stream.Collectors;

import static minillvm.ast.Ast.*;

/**
 * Translates object-oriented constructs of given
 * NQJ program to LLVM.
 */
public class ClassTranslator {
    private final FunTranslator funTranslator;
    private final CurrentStates currStates;
    private final NQJProgram javaProg;
    private final Prog llvmProg;

    // for classes
    private final Map<NQJVarDecl, TemporaryVar> localMethodVars = new HashMap<>();
    private final Map<TypeStruct, NQJClassDecl> classStructs = new HashMap<>();
    final Map<NQJClassDecl, List<Proc>> classMethods = new HashMap<>();

    //
    final Set<Proc> noReturnProcs = new HashSet<>();

    /**
     * Maps classes to their constructors
     */
    final Map<NQJClassDecl, Proc> constructors = new HashMap<>();

    public ClassTranslator(FunTranslator funTr) {
        funTranslator = funTr;
        currStates = funTr.currStates;
        javaProg = funTr.getJavaProg();
        llvmProg = funTr.getProg();
    }

    void initializeClasses() {
        NQJClassDeclList classes = javaProg.getClassDecls();
        initAllClasses();

        for (NQJClassDecl decl : classes) {
            initFields(decl);
            initMethods(decl);
        }

        for (NQJClassDecl decl : classes) {
            examineInheritance(decl, getStructOf(decl));
        }

        initConstructors(classes);
    }

    void translateClasses() {
        NQJClassDeclList classes = javaProg.getClassDecls();
        for (NQJClassDecl decl : classes) {
            translateClass(decl);
        }
    }

    /** Initialises all classes with empty field list */
    private void initAllClasses() {
        for (NQJClassDecl decl : javaProg.getClassDecls()) {
            TypeStruct struct = TypeStruct(decl.getName(), StructFieldList());
            llvmProg.getStructTypes().add(struct);
            classStructs.put(struct, decl);
        }
    }

    private void initFields(NQJClassDecl classDecl) {
        TypeStruct struct = getStructOf(classDecl);
        struct.setFields(
                classDecl.getFields()
                .stream()
                .map(decl -> StructField(
                        translateType(decl.getType()), decl.getName()))
                .collect(Collectors.toCollection(Ast::StructFieldList)));
    }

    /**
     * Examines inheritance of fields.
     */
    private void examineInheritance(NQJClassDecl classDecl, TypeStruct struct) {
        NQJClassDecl superClass = classDecl.getDirectSuperClass();

        if (superClass != null) {

            // super class may inherit other fields
            TypeStruct superStruct = getStructOf(superClass);
            examineInheritance(superClass, superStruct);

            // inheriting
            List<String> thisClassVarNames = classDecl.getFields()
                    .stream()
                    .map(decl -> decl.getName())
                    .collect(Collectors.toList());

            List<StructField> inheritedFields = superStruct.getFields()
                    .stream()
                    .filter(field -> !thisClassVarNames.contains(field.getName()))
                    .collect(Collectors.toList());

            List<String> inheritedFieldNames = inheritedFields
                    .stream()
                    .map(StructField::getName)
                    .collect(Collectors.toList());

            // finally add inherited variables to struct:
            // appending inherited fields to class fields
            inheritedFields.addAll(
                    struct.getFields()
                    .stream()
                    .filter(field -> !inheritedFieldNames.contains(field.getName()))
                    .collect(Collectors.toList())
            );

            //copying fields
            StructFieldList newFields = inheritedFields
                    .stream()
                    .map(StructField::copy)
                    .collect(Collectors.toCollection(Ast::StructFieldList));

            struct.setFields(newFields);

        }
    }

    /**
     * Creates object constructor procedures for all declared classes
     */
    private void initConstructors(NQJClassDeclList classes) {
        for (NQJClassDecl decl : classes) {
            TypeStruct objStruct =  getStructOf(decl);
            TemporaryVar objCreate = TemporaryVar("obj_allocated");
            TemporaryVar newObj = TemporaryVar("new_obj");

            Proc proc = Proc(
                    objStruct.getName() + "_" + "Create_" + "Default",
                    TypePointer(objStruct),
                    ParameterList(),
                    BasicBlockList()
            );
            BasicBlock block = newBlockWithName("Constructor: ");

            // allocating space in the heap
            block.add(Alloc(
                    objCreate,
                    byteSize(objStruct)));
            // casting allocated bytes to type pointer
            block.add(Bitcast(
                    newObj,
                    TypePointer(objStruct),
                    VarRef(objCreate)));

            StructFieldList fields = objStruct.getFields();

            // setting fields to initial values
            for (int i = 0; i < fields.size(); i++) {
                StructField field = fields.get(i);
                TemporaryVar defaultField = TemporaryVar(field.getName());
                block.add(GetElementPtr(
                        defaultField, VarRef(newObj), OperandList(ConstInt(0), ConstInt(i))
                ));

                // all fields are instantiated with default values
                Operand defaultValue = defaultValue(field.getType());
                block.add(Store(
                        VarRef(defaultField), defaultValue
                ));
            }
            block.add(ReturnExpr(VarRef(newObj)));
            proc.getBasicBlocks().add(block);
            addProcedure(proc);
            constructors.put(decl, proc);
        }
    }

    /**
     * All methods without inherited methods are initialised
     */
    private void initMethods(NQJClassDecl classDecl) {
        TypeStruct struct = getStructOf(classDecl);
        List<Proc> methodList = new LinkedList<>();

        for (NQJFunctionDecl method : classDecl.getMethods()) {
            Type resultType = translateType(method.getReturnType());
            ParameterList params = method.getFormalParameters()
                    .stream()
                    .map(p -> Parameter(translateType(p.getType()), p.getName()))
                    .collect(Collectors.toCollection(Ast::ParameterList));

            // adding as first parameter the class struct
            params.addFront(Parameter(TypePointer(struct), "this"));

            Proc proc = Proc(
                    classDecl.getName() + "_" + method.getName(),
                    resultType,
                    params,
                    BasicBlockList()
            );
            addProcedure(proc);
            methodList.add(proc);
        }
        classMethods.put(classDecl, methodList);
    }

    private void translateClass(NQJClassDecl classDecl) {
        setCurrentClass(classDecl);
        translateMethods(classDecl);
        setCurrentClass(null);
    }

    private void translateMethods(NQJClassDecl classDecl) {
        for (NQJFunctionDecl method : classDecl.getMethods()) {
            Proc proc = getMethodProcedure(classDecl, method);
            BasicBlock initBlock = newBlockWithName("MethodBegin");

            setCurrentProc(proc);
            addBasicBlock(initBlock);
            setCurrentBlock(initBlock);
            localMethodVars.clear();

            // store copies of the parameters in Allocas, to make uniform read/write access possible
            int i = 1;
            for (NQJVarDecl param : method.getFormalParameters()) {
                TemporaryVar v = TemporaryVar(param.getName());
                addInstruction(Alloca(v, translateType(param.getType())));
                addInstruction(Store(VarRef(v), VarRef(proc.getParameters().get(i))));
                localMethodVars.put(param, v);
                i++;
            }

            allocaSpaceForLocals(method.getMethodBody());
            translateStmt(method.getMethodBody());
        }
    }

    Operand exprLvalue(NQJExprL e) {
        return funTranslator.exprLvalue(e);
    }

    Operand exprRvalue(NQJExpr e) {
        return funTranslator.exprRvalue(e);
    }

    void translateStmt(NQJStatement s) {
        funTranslator.translateStmt(s);
    }

    /** allocates space for method variables */
    private void allocaSpaceForLocals(NQJBlock methodBody) {
        methodBody.accept(new NQJElement.DefaultVisitor() {
            @Override
            public void visit(NQJVarDecl localVar) {
                super.visit(localVar);
                TemporaryVar v = TemporaryVar(localVar.getName());
                addInstruction(Alloca(v, translateType(localVar.getType())));
                localMethodVars.put(localVar, v);
            }
        });
    }

    void addProcedure(Proc proc) {
        llvmProg.getProcedures().add(proc);
    }

    TypeStruct getStructOf(NQJClassDecl classDecl) {
        for (TypeStruct struct : llvmProg.getStructTypes()) {
            if (struct.getName().equals(classDecl.getName()))
                return struct;
        }
        return null;
    }

    TypeStruct getStructOfMethod(NQJClassDecl classDecl, NQJFunctionDecl funDecl) {
        NQJClassDecl superClass = classDecl.getDirectSuperClass();

        for (Proc p : classMethods.get(classDecl)) {
            if (p.getName().equals(
                    classDecl.getName() + "_" + funDecl.getName())) {
                return getStructOf(classDecl);
            }
        }

        return superClass == null
                ? null
                : getStructOfMethod(superClass, funDecl);
    }

    NQJClassDecl getClassDeclOf(TypeStruct struct) {
        return classStructs.get(struct);
    }

    Type translateType(NQJType type) {
        return translateType(type.getType());
    }

    // Type Translator
    Type translateType(analysis.Type t) {
        Type result = funTranslator.getTranslatedType().get(t);
        if (result == null) {
            if (t == analysis.Type.INT) {
                result = TypeInt();
            } else if (t == analysis.Type.BOOL) {
                result = TypeBool();
            } else if (t instanceof ArrayType) {
                ArrayType at = (ArrayType) t;
                result = TypePointer(funTranslator.getArrayStruct(translateType(at.getBaseType())));
            } else {
                result = translateClassType((ClassType) t);
            }
            funTranslator.addTranslatedType(t, result);
        }
        return result;
    }

    Type translateClassType(ClassType type) {
        NQJClassDecl classDecl = type.getClassRef().decl;
        for (TypeStruct struct : llvmProg.getStructTypes()) {
            if (struct.getName().equals(classDecl.getName()))
                return TypePointer(struct);
        }

        return null;
    }

    BasicBlock newBlockWithName(String name) {
        BasicBlock block = BasicBlock();
        block.setName(name);
        return block;
    }

    CurrentStates getStates() {
        return currStates;
    }

    Proc getMethodProcedure(NQJClassDecl classDecl, NQJFunctionDecl method) {

        // lookup method in given class
        for (Proc proc : classMethods.get(classDecl)) {
            if (proc.getName().equals(
                    classDecl.getName() + "_" + method.getName()))
                return proc;
        }

        // lookup method in super classes of given class
        // this means, method is inherited
        NQJClassDecl directSuper = classDecl.getDirectSuperClass();
        return directSuper != null
                ? getMethodProcedure(directSuper, method)
                : null;
    }

    Map<NQJVarDecl, TemporaryVar> getLocalMethodVars() {
        return localMethodVars;
    }

    void setCurrentProc(Proc proc) {
        currStates.setProc(proc);
    }

    void setCurrentClass(NQJClassDecl classDecl) {
        currStates.setClass(classDecl);
    }

    void setCurrentBlock(BasicBlock block) {
        currStates.setBlock(block);
    }

    void addBasicBlock(BasicBlock block) {
        currStates.getProc().getBasicBlocks().add(block);
    }

    void addInstruction(Instruction instruction) {
        currStates.addInstructionToBlock(instruction);
    }

    public Operand byteSize(Type type) {
        return type.match(new Type.Matcher<>() {

            @Override
            public Operand case_TypeByte(TypeByte typeByte) {
                return ConstInt(1);
            }

            @Override
            public Operand case_TypeArray(TypeArray typeArray) {
                Type type = typeArray.getOf();
                int size = typeArray.getSize();
                TemporaryVar sizeWithoutLen = TemporaryVar("withoutLen");

                addInstruction(BinaryOperation(
                        sizeWithoutLen, byteSize(type), Mul(), ConstInt(size)
                ));
                TemporaryVar sizeWithLen = TemporaryVar("withLen");

                addInstruction(BinaryOperation(
                        sizeWithLen, VarRef(sizeWithoutLen), Add(), ConstInt(4)
                ));

                return VarRef(sizeWithLen);
            }

            @Override
            public Operand case_TypeProc(TypeProc typeProc) {
                TemporaryVar overallByte = TemporaryVar("overallByte");
                BasicBlock byteAlloc = newBlockWithName("byte_Alloc");
                setCurrentBlock(byteAlloc);
                Operand bytesForResult = byteSize(typeProc.getResultType());
                addInstruction(Alloca(overallByte, TypeInt()));
                addInstruction(Store(VarRef(overallByte), bytesForResult));

                for (Type t : typeProc.getArgTypes()) {
                    Operand bytesForArg = byteSize(t);
                    addInstruction(BinaryOperation(
                            overallByte, VarRef(overallByte), Add(), bytesForArg
                    ));
                }

                return VarRef(overallByte);
            }

            @Override
            public Operand case_TypeInt(TypeInt typeInt) {
                return ConstInt(4);
            }

            @Override
            public Operand case_TypeStruct(TypeStruct typeStruct) {
                return Sizeof(typeStruct);
            }

            @Override
            public Operand case_TypeNullpointer(TypeNullpointer typeNullpointer) {
                return ConstInt(8);
            }

            @Override
            public Operand case_TypeVoid(TypeVoid typeVoid) {
                return ConstInt(0);
            }

            @Override
            public Operand case_TypeBool(TypeBool typeBool) {
                return ConstInt(1);
            }

            @Override
            public Operand case_TypePointer(TypePointer typePointer) {
                return ConstInt(8);
            }
        });
    }

    Operand defaultValue(Type componentType) {
        return componentType.match(new Type.Matcher<>() {
            @Override
            public Operand case_TypeByte(TypeByte typeByte) {
                return ConstInt(8);
            }

            @Override
            public Operand case_TypeArray(TypeArray typeArray) {
                return Nullpointer();
            }

            @Override
            public Operand case_TypeProc(TypeProc typeProc) {
                TypeRefList typesOfArgs = typeProc.getArgTypes();
                ParameterList params = ParameterList();
                int i = 1;

                for (Type t : typesOfArgs) {
                    params.add(Parameter(t, "Param" + i));
                    i++;
                }

                Proc proc = Proc(
                        "default", typeProc.getResultType(), params, BasicBlockList()
                );

                return ProcedureRef(proc);
            }

            @Override
            public Operand case_TypeInt(TypeInt typeInt) {
                return ConstInt(0);
            }

            @Override
            public Operand case_TypeStruct(TypeStruct typeStruct) {
                return Nullpointer();
            }

            @Override
            public Operand case_TypeNullpointer(TypeNullpointer typeNullpointer) {
                return Nullpointer();
            }

            @Override
            public Operand case_TypeVoid(TypeVoid typeVoid) {
                return Nullpointer();
            }

            @Override
            public Operand case_TypeBool(TypeBool typeBool) {
                return ConstBool(false);
            }

            @Override
            public Operand case_TypePointer(TypePointer typePointer) {
                return Nullpointer();
            }
        });
    }
}
